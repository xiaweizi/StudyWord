# 版本历史

本项目版本号遵循 [语义化版本 2.0](https://semver.org/lang/zh-CN/) 规范。
格式参考 [Keep a Changelog 1.1](https://keepachangelog.com/zh-CN/1.1.0/)。

## [1.7.0] - 2026-10-07

### 新增
- **内置语音包** —— 不依赖系统 TTS 也能发音。
  起因是用户在 HarmonyOS 手机上完全没有发音:查证后确认那是**平台边界** ——
  兼容层里 `TextToSpeech.getEngines()` 返回 0、系统"文本转语音"页卡在"正在检查",
  而鸿蒙自己的语音是 ArkTS 的 Core Speech Kit,**安卓 APK 调不到**。
  但该设备 **MediaPlayer 播音频文件正常**(答题音效有声),于是把语音预生成成
  音频打进 APK。共 **5865 条 / 26.28 MB**,覆盖简单档全部 1167 字的
  「单字 + 词组 + 例句」(100%)与全部 150 个英文单词。
  播放策略:内置语音包优先 → 回退系统 TTS → 都不可用才提示。
- 「语音诊断」入口(设置页),远程定性 TTS 问题。
- iOS 版本技术方案(基于代码库实测,`docs/ios-porting-plan.md`)。

### 变更
- **中文难度改按教学适用性划分**(不再按源列表位置切):
  降档 70 个不适合 3~6 岁的字(抽象/书面/负面,如 乙/尸/亡/歹/乞),
  升档 37 个部编版一年级字(春雪清情请种样秋说很音思前故看晚笑真着高迷造凉语香相首树亮美要点常病星晴跑)。
  简单 1200→1167 / 中等 1000→1035 / 困难 800→798,总数仍 3000。
- **英文单词难度按教学规律重划**:原先手工分的 50/50/50 判据混杂,
  竟把 **red/blue/green 判为「困难」而 yellow/black/white 在「简单」** ——
  同为基础颜色却分成两档。新判据为「概念熟悉度 > Dolch 高频词优先 > 拼写长度」,
  改为 简单 64 / 中等 48 / 困难 38。
- 主页卡片固定:内容放得下一屏时完全不响应上下拖拽;英文单词按长度设字号上限。

### 修复
- **播放器池取还语义**:`obtain()` 用轮转式导致实例在播放期间仍留在池里,
  归还时被误判"已在池中"而销毁,池里留下已销毁的引用 ——
  症状是"**只有开头几声能响,之后全没声音**"。改为取走式。
- **例句只读第一句**:卡片把全部例句换行显示,点击却固定只传 `first.sentence`(239 个字受影响)。
  现在朗读内容严格跟随显示内容;两句之间加 550ms 停顿。
- **滑动返回绕过退出确认直接退出 App**:返回键路径会 `finish()` 主页,
  使源页成为栈里唯一 Activity,再滑动返回就清空任务栈。
  修复后 MainActivity 里的 `finish()` 只剩退出确认一处。
- **系统栏遮挡导致顶栏点击失效**:游戏页/进度页/设置页/字库页都漏了避让,
  返回箭头压在状态栏下、点击被状态栏吃掉。抽出 `applyStatusBarTopPadding()` 统一处理。
- 长英文单词溢出卡片(word 也走 100sp 的中文单字控件)、字库格子省略号截断
  (autoSize 用的是平台属性,AppCompatTextView 只认 `app:` 属性)。
- 字库徽章高低不齐(自动缩字导致行高浮动)、顶栏标签被挤到第二行、
  英文字母不该出现在「中等」「困难」。
- **音频生成脚本性能**:`transcode()` 是同步 `subprocess.run` 却在协程里直接调用,
  阻塞整个事件循环 —— 实测 **8 条/分钟 → 204 条/分钟**(25 倍)。
- **一个空格导致音频永远播不出**:`乐` 的例句末尾多一个空格,
  与生成脚本 `strip()` 后的 key 对不上。清理数据 + 键统一规整。

### 工具与防回退
- `verify_character_sets.py` 新增断言:最先学的高频词不得落在「困难」档、
  不适合幼儿的字不得回到「简单」档、简单档必须 100% 有词组、
  **简单档每个字都必须有单字音频**、内容字段不得有首尾空白。
- 顺序基线刷新为 `5ce563a1…`(用户明确不考虑老用户进度迁移)。
- 单测 141 → **157**。

## [1.6.0] - 2026-10-05

本版本加入**错题本 + 间隔重复**，并把词组/例句覆盖从 100 扩到 **200** 个常用字。

### 新增

- 📝 **错题本 + 简化版 Leitner 间隔重复**
  - 点「不认识」→ 该字进入错题本,盒子归零;**答对 → 盒子 +1**,复习间隔按
    **0 → 1 → 3 → 7 → 16 天**拉长（盒子 0 特意设为 0 天,让孩子**当下就能重练刚错的字**;
    若设成 1 天,功能上线第一天会显示"没有要复习的字",看起来像坏的）。
  - 抽屉新增「复习错题」入口:题池**只包含现在到期的错题**,并按"错得最多、最久没复习"排序;
    没有到期项时提示"太棒了,今天没有要复习的字!"并自动退出复习模式。
  - 复习模式主页统计条显示「错题复习中」,避免家长误以为字库只剩这几个字。
  - 切换语种 / 难度 / 子模式会自动退出复习模式,防止题池与选择不一致。
  - 新增 `model/ReviewScheduler`（Leitner 调度,纯函数）与 `model/ReviewCodec`（编解码,纯函数）,
    以及 `ProgressStore` 的 `recordWrong/recordCorrect/dueReviewKeys`。
  - 只为**曾进过错题本**的字写记录,不会给 3000 个字都留一条无意义数据。
  - 按语言隔离:重置中文不会清掉英文错题本,反之亦然。
  - 新增 **24 个单元测试**（`ReviewSchedulerTest` 13 + `ReviewCodecTest` 8 + `ProgressRulesTest` 3）,
    单元测试总数 **60 → 85**。
  - 真机验证:标 春 为"不认识" → prefs 写入 `review_state = 春|1|0|<ts>` →
    点「复习错题」→ 字卡显示 **春**、统计条显示 **错题复习中**,无崩溃。
- 📚 **词组 / 例句覆盖扩到 200 个字**
  - 第二批补齐 easy #100~199 共 100 个字（叉 巾 乞 丸 丫 刃 兀 不 中 为 以 方 分 开 从 无 长 公 文 见 …）。
  - 内容由子代理撰写、脚本用 pypinyin 按上下文注音;其中 8 个生僻字（兀 尤 氏 仇 曰 戈 仆 厄）
    只找到 1 个适合幼儿的真实词组,**按规则只写 1 个并在条目里加 `note` 说明,没有编造凑数**。
  - 现在前 200 个简单字共有 **约 580 个词组 / 200 条例句**。
- ✅ **内容质量门禁**:`fill_easy_words.py` 与 `verify_character_sets.py` 都会校验
  **每个词组/例句必须包含目标字**（否则这个字等于没被教到）。
  - 这道门禁立刻抓出一处**既有内容 bug**:字「人」的词组里写着「小朋友」,
    根本不含「人」——已替换为「好人」(hǎo rén)。
  - `verify_character_sets.py` 门槛同步提到"前 200 字必须有词组",并继续守住顺序指纹。

### UI 重做（统一清爽浅色）

先把你手机上的 6 个界面截图逐一查看后定位问题，而非凭感觉重画。**客观缺陷**与改法：

- 🎨 **卡片发脏（最主要的"难看"来源）**
  - 原因:卡片背景是 `white → mint → white` 的薄荷绿渐变,压在浅蓝紫背景上会**发灰发绿**,
    像没洗干净。真机截图确认所有页面的卡片都是这个脏绿。
  - 改法:卡片改**纯白 + 1dp 极细描边**(`border_soft`),不再依赖渐变;层次靠描边而非阴影。
- 🌈 **背景降饱和**:`sky_blue(#A7D8FF) → lavender(#F0D9FF)` 太艳,
  改为接近白的 `#EDF3FF → #F6EFFF`,让白色卡片成为唯一焦点。
- 🔘 **按钮风格由 3 种杂乱渐变收敛为 2 种**
  - 原来:「认识」蓝→橘横渐变、「不认识」深蓝→天蓝竖渐变、「换一个字」白底蓝边、「玩游戏」又是蓝→橘 —— 四不像。
  - 现在:**认识 = 实心深蓝**、**不认识 = 实心橘**(同为实心、换色相,一眼可分)、
    **换一个字 = 白底浅灰描边**(弱化)、**玩游戏 = 白底深蓝描边**。
- ✍️ **四个主按钮补上文字标签**
  - 原来只有 emoji(😊 😢 ➡️ 🎮),家长第一次用**不知道哪个是"认识"**。
  - 现在:**矢量图标 + 中文标签**(认识 / 不认识 / 换一个字 / 玩游戏)。
- 🔣 **emoji 图标全部换成矢量图标**
  - 新增 10 个 VectorDrawable(`ic_check/ic_close/ic_skip/ic_game/ic_review/ic_chart/
    ic_book/ic_tune/ic_speaker`),替代原来依赖系统 emoji 字体的图标 ——
    后者在不同机型上渲染差异大,观感不可控。
  - **保留**大号装饰性 emoji(卡片吉祥物 🦄🐻、庆祝 🎉),它们是有意为之的内容而非 UI 图标。
  - 同时清掉 28 条文案里的装饰 emoji(🌍🗣🎯📊🧩…),保留语言旗帜 🇨🇳🇬🇧(有辨识意义)。
- 🟨 **chip 选中态由亮黄改为主色淡底**
  - 原来选中是 `sunshine` 亮黄 + 白字,与整体蓝紫粉完全不搭,且白字在黄底上对比度差。
  - 改为 `primary_tint(#E8EDFF)` 淡蓝底 + **深蓝字**(对比度更好),未选中白底 + 浅描边。
- 🗂️ **抽屉 4 张撞色卡统一**
  - 原来是 4 套互不相干的渐变(黄橘青 / 紫粉 / 黄粉 / 青紫)+ 2.5dp 深蓝粗描边 + 白色圆图标底,
    是最"花"的地方。
  - 改为**统一白卡 + 1dp 描边**,靠**淡蓝圆底矢量图标**区分功能;问候卡的狐猴头像保留。
- 🏷️ **字库网格状态徽章修复**
  - 4 列网格里单元格内容宽度只剩约 53dp,而「已认识」这类 3 字徽章需要约 60dp,
    于是被压成**大圆圈 + 文字竖排折行**(真机截图可见)。
  - 改法:卡片内边距 16→10dp、徽章内边距 12→8dp、字号 12→11sp、`maxLines=1` + `singleLine`,
    现在是正常的一行胶囊。
- 🔊 **修复一处我自己引入的回归**:游戏页喇叭按钮在白色胶囊上看不见
  (原用系统 `@android:drawable/ic_lock_silent_mode_off`,在浅底上不显形),
  已换成矢量 `ic_speaker` + `app:tint` 深蓝着色。
- 🧹 删除因此产生的死资源:4 个旧抽屉撞色 drawable、未使用的 `ic_star`、`color/lavender`,
  并把之前误删、现在又需要的 `dimen/radius_button|radius_chip|radius_pill` 加回。

> 说明:上一轮 v1.4.4 曾出现「改成糖果风 → 反馈难看 → 回退简约风」的反复。
> 这次先截图定位问题、并让你选定方向(A 统一清爽浅色)后才动手,避免再翻烧饼。

### 第 9 轮：用户确认的 4 项

- 🎮 **游戏答错现在计入错题本**（原来只"读"进度、从不"写"）
  - 原因:`GameActivity` 只用 `loadKnown/loadUnknown` 出题,**从不写回**,
    于是孩子在游戏里反复错的字永远进不了复习队列。
  - 改动:答错时做两件事,与主页点「再学一次」完全一致 ——
    ① 写错题本(盒子归零、错误次数累加,之后会出现在「复习错题」);
    ② 标为「待巩固」并从「已认识」移除(按语言分流到中/英各自的集合)。
  - **只记录错误、不记录正确**:游戏是练习场景,答对一次不足以证明掌握,
    掌握状态仍由主页的「认识了」来沉淀。
  - 真机验证:一轮 5 题全答错后,`review_state` 新增 5 条(L:A/L:E/L:F/L:K/L:S),
    `unknown_english_ids` 7 → 11。
- 🖌️ **字卡改用楷体**(新增 1.3 MB 字体资产)
  - 依据:教育部官方说明《通用规范汉字表》**只规范宋体字形**,
    而小学 1–3 年级课本与生字**都用楷体印刷**,两种字体笔形有差异
    ([出处](https://www.edu.cn/hzb_8413/20090820/t20090820_400832_1.shtml))。
    所以儿童识字用楷体是有官方依据的,不是审美偏好。
  - 做法:**不引入完整字体**。完整 LXGW 文楷 Regular 是 **24 MB**,而 release APK 才 2.5 MB。
    新增 `scripts/build_kai_font.py`,从 webfont 分片里只抽取本应用用到的
    **3151 个字符**(字库 3000 字 + 词组/例句用字 + ASCII/标点),
    合并后产出 **`res/font/studyword_kai.ttf`(1355 KB / 3149 字形)**。
  - **许可合规**:LXGW 文楷采用 SIL OFL 1.1,含"保留字体名"条款 ——
    子集化属于修改,衍生作品不得沿用原字体名。因此输出字体**改名 `StudyWordKai`**,
    版权信息注明来源与许可,并在 `licenses/OFL-LXGWWenKai.txt` 随附 OFL 原文。
  - 应用范围:**只用于"学习面"** —— 主页主字、字库网格汉字、游戏四个选项;
    界面文字仍用系统字体(楷体在小字号下可读性差)。
- 🔒 **新增家长门(Parent Gate)**
  - 依据:Apple 对 App Store Kids 类目**强制要求** parental gates
    (需完成成人级任务才能继续),并明确建议**面向不识字儿童时用语音提示让孩子去叫家长**;
    Google 儿童指南同样要求 "Gate parental controls"
    ([Apple](https://developer.apple.com/app-store/kids-apps/) /
    [Google](https://developers.google.cn/building-for-kids/designing-engaging-apps?hl=en))。
  - 实现:新增 `ui/ParentGate.kt`。点「更多设置」→ **TTS 播报"这个要请爸爸妈妈来哦"**
    → 弹两位数加法题(随机生成,3~6 岁无法完成、成人一眼可解)。
    **答错不关闭对话框**,可重试;答对才进入设置页。
  - **只给「更多设置」加门**:语言/难度/复习/进度/字库对孩子无害,不加门,
    免得家长每次都要解题。
  - 真机验证:`37 + 38 = ?` → 输入 1 点确定**不放行**;输入 75 → 进入 `SettingsActivity`。
- 🇬🇧 **英文词库 30 → 150 个单词**(每难度 10 → 50)
  - 原 30 个词相对中文 3000 字严重失衡(1%);现新增 120 个(EASY/MEDIUM/HARD 各 40),
    英文字母 26 个不变,英文条目合计 **56 → 176**。
  - 新词采用英式 IPA(如 `/beə(r)/`、`/ˈelɪfənt/`),例句 ≤6 词且必含该词;
    合并前用脚本校验字段/去重/音标格式/例句包含关系,合并后再由
    `verify_character_sets.py` 复验(新增英文数据校验段)。
- 🧹 **修掉一条会过期的硬编码文案**
  - 抽屉"字库浏览"副标题原写死为 `3000+ 字、26 字母、30 单词`,
    词库扩到 150 后这句就是错的。
  - 改为**运行时按仓库实际数量生成**(`nav_library_desc_format`),
    现显示 `3000 字、26 字母、150 单词`,数据驱动不会过期。

### 修复：内置语音包"只有开头几声能响"（第 17 轮）

**现象**：装上有声版后，用户报告「二和二月有声音，其他没了」。

**定位过程（靠两个具体观察锁死范围）**

| 用户的回答 | 排除/指向 |
|---|---|
| 点击没声音时**底部有提示** | 说明播放**主动返回了失败**并回退 TTS —— 不是"播了但听不见" |
| **只有开头能响**，之后都不行 | 说明不是编码/文件问题（否则第一次就该失败），而是**状态被破坏** |

**真因：播放器池的取/还语义写错了**

```kotlin
// 旧实现(错误)
private fun obtain(): MediaPlayer {
    if (pool.isNotEmpty()) {
        val mp = pool.removeFirst()
        pool.addLast(mp)      // ← 轮转:实例在播放期间仍留在池里
        return mp
    }
    return MediaPlayer()
}
private fun release(mp: MediaPlayer) {
    if (pool.size < POOL_SIZE && !pool.contains(mp)) pool.addLast(mp)
    else mp.release()          // ← 看到实例还在池中,于是把**正在用的**销毁了
}
```

| 第几次点击 | 结果 |
|---|---|
| 第 1 次 | 池空 → 新建 A（不在池中）→ 播完 `release` → A 入池 ✓ **能响** |
| 第 2 次 | 从池取 A（轮转后仍在池中）→ 播完 `release` 判定"已在池中" → **`A.release()` 销毁** → 池里留下死实例 |
| 第 3 次起 | 取到已销毁的 A → `reset()` 抛 `IllegalStateException` → 被 catch → 回退 TTS → TTS 不可用 → **弹提示、没声音** |

**修复**
1. `obtain()` 改为**取走式**（`removeFirst()` 后不再放回），让"在池中"严格等价于"空闲可用"
2. 错误回调里也归还实例 —— 否则错误发生后实例会一直挂在"使用中"
3. 偶发失败**不再永久拉黑**：同一 key 连续失败 2 次才判定不可用，
   避免一次抖动就让某个字本次进程内永远没声音
4. 把池逻辑抽成纯类 `SimplePool`，并用 **8 个 JVM 单测**锁住语义
   （`MediaPlayer` 无法在单测里构造，所以必须把池独立出来才测得到）。
   测试数 147 → **155**

**顺带：音频编码从 Opus 改为 MP3**

排查初期曾怀疑"设备不支持 Ogg/Opus"（依据：该设备答题音效是 Vorbis 且有声音），
但**用户的"开头能响"证明第一次播放是成功的 —— 编码从一开始就没问题**，
真因是上面的池 bug。

仍改为 **MP3 40kbps 单声道 22050Hz**，理由与故障无关、独立成立：

| 编码 | 全集体积 | 兼容性 |
|---|---|---|
| Opus 24k | 6.35 MB | Android 声明 API 21+ 支持，但**厂商实现参差**；短音频容器开销占比高 |
| **MP3 40k** | **11.01 MB** | Android 从 1.0 起硬件解码，兼容面最广 |
| Vorbis 24k | 19.16 MB | 低码率下效率最差 |

在已经见过该设备"兼容层实现不完整"的前提下，用 MP3 换取最大兼容性；
代价是 APK 从 10.35 MB 增到 15.02 MB。

**验证**：2707 个 MP3 全部可解码、清单零缺失、APK 内全部 STORED 未压缩；
155 单测全过 / lint 0 error。

### 新增：内置语音包 —— 不依赖系统 TTS 也能发音（第 16 轮）

**起因**：用户在 HarmonyOS 手机上完全没有发音。查证后确认这是**平台边界**：

| 证据 | 含义 |
|---|---|
| 第一台华为（Android 12）发音正常 | 标准 AOSP 上 TTS 可用，App 侧无问题 |
| 鸿蒙那台 `TextToSpeech.getEngines()` 返回 0 | 兼容层没有注册任何 Android TTS 引擎 |
| 系统自带「文本转语音」页卡在「默认语言状态：正在检查…」 | 连系统自己都查不到引擎 |
| **小艺能朗读** | 那是鸿蒙系统能力，走 ArkTS 的 Core Speech Kit，**安卓 APK 调不到** |

即：鸿蒙的语音合成是 **ArkTS 原生 API**，运行在兼容层里的 APK 无法调用。

**但是**：那台设备上 **MediaPlayer 播放音频文件正常**（答题音效有声）。
所以把语音预生成成音频文件打进 APK，就能在**任何设备**上都有发音。

**实现**

| 环节 | 做法 |
|---|---|
| 合成 | `scripts/generate_audio.py` 用 **edge-tts**（微软免费神经网络语音） |
| 音色 | 中文 `zh-CN-XiaoyiNeural`（语速 -8%）；英文 `en-US-AnaNeural`（儿童音色） |
| 编码 | ffmpeg 转 **Opus 24kbps 单声道 Ogg**（Android API 21+ 原生支持） |
| 静音裁剪 | 裁掉首尾静音 —— edge-tts 两端常带 0.3~0.8s 静音，幼儿点一下要等半秒才出声，体感像"坏了"。实测单字 **1.5s → 0.61s，体积再降 42%** |
| 命名 | key 的 **CRC32** → `k1a2b3c4.ogg`。纯 ASCII、与内容一一对应且稳定；构建时**断言无碰撞** |
| 打包 | `assets/audio/`，并在 build.gradle 里 `noCompress 'ogg'` —— 否则 `openFd` 拿不到可 seek 的 FD，MediaPlayer 播不出声 |

**键的约定**（生成脚本与 App 侧必须一致）
```
c:<汉字>  w:<中文词组>  e:<中文例句>  z:<其它中文>
l:<字母>  n:<英文单词/例词>  s:<英文例句>
```
`z:` 把"中文意思 / 例词中文 / 例句翻译"合到一个命名空间，相同文本自动去重。

**覆盖面（本轮）**：简单字 1~600 的**单字 + 词组 + 例句**、26 个英文字母的
**字母 + 例词 + 例词中文**、50 个简单单词的**单词 + 中文意思 + 例句 + 翻译**
—— 共 2833 条，去重后 **2707 个音频文件，约 7 MB**。

**播放策略**：`AudioClips`（MediaPlayer + 播放器池）**优先**，没有再回退系统 TTS；
两者都不可用才提示用户。已接入主页（卡片 / 字母行 / 词组 chip / 例句）、
游戏页（出题发音 / 答错重播）。

**跨语言契约测试**：文件名由 Python 生成、由 Kotlin 查找，两边 CRC32 实现不同 ——
一旦规则分叉，症状是"明明生成了却播不出声"且**没有任何报错**。
因此把命名规则抽成纯函数 `AudioClipNaming`，并用**从 Python 算出的固定期望值**
写测试锁死（6 个用例）。手工拼十六进制而不用 `String.format("%08x")`，
是为了避免 Locale 造成非 ASCII 数字。

### 修复：TTS 静默失败 —— 鸿蒙手机点卡片没声音也无提示（第 15 轮）

**现象**：用户在一台 HarmonyOS 手机上安装 APK 后，点卡片**没有声音，也没有任何提示**；
但答题音效（走 MediaPlayer）正常。

**根因（代码缺陷，不是设备问题）**

| 问题 | 后果 |
|---|---|
| `init()` **只尝试一次**默认引擎 + `Locale.SIMPLIFIED_CHINESE` | 失败即 `isReady=false` **永久沉默**；系统里其它支持中文的引擎**从不去找** |
| 失败原因被**静默吞掉**（只返回 false） | 界面无法向用户解释"为什么没声音" |
| 全项目**没有一条**"语音不可用"的文案 | 用户无从得知该去装中文语音 |
| 卡片点击 `speakCurrentItem()` 忽略返回值 | 点下去毫无反应 |

答题音效正常这一点很关键 —— 它说明**音频输出通路没问题**，只有 TTS 失败，
即那台设备上没有可用的中文语音引擎。

**修复内容**

1. `TtsManager` 重写引擎探测（**API 签名全部保留**：`speak` / `speakEnglish` /
   `speakPhraseCharByChar` / `speakSequential` / `stop` / `setSpeechRate` / `shutdown`）：
   - 依次探测**默认引擎 + 系统里所有引擎**，取第一个支持中文的
   - 中文 Locale 按 `zh_CN` → `zh` 顺序尝试（部分引擎只认其中一种）
   - 新增 `status`（NO_ENGINE / NO_CHINESE / INIT_FAILED）+ `enginePackage` + `diagnostics`
   - 新增 `retryIfNeeded()`：用户去系统设置装好语音后，回到前台**自动生效**，不必重启 App
2. `MainActivity`：4 个发音入口（卡片 / 字母行 / 词组 chip / 例句）加守卫，
   不可用时弹 **Snackbar 说明原因 + 「去设置」按钮**直接跳到系统语音设置；
   `onResume` 接入自动重试
3. strings.xml 新增 6 条文案

**实现踩坑（已用 javap 核实，不靠记忆）**

- `TextToSpeech.getEngines()` / `getDefaultEngine()` 是**实例方法**，不是静态方法 ——
  必须先建一个默认引擎实例才能枚举系统引擎，再逐个新建实例去试
- `android.provider.Settings` 里**没有** `ACTION_TTS_SETTINGS` 常量
  （`javap android-34` 核实），改用系统实际使用的 action 字符串
  `com.android.settings.TTS_SETTINGS`，并保留
  `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA` / `Settings.ACTION_SETTINGS` 作为降级链

**验证**：141 单测全过 / lint 0 error / assembleDebug + assembleRelease 成功。
真机行为待用户在鸿蒙设备上确认 —— 现在即使仍然没声音，也会**明确告诉用户原因**。

### 新增：App 图标换成黏土风插画（第 14 轮）

**设计稿**：黏土 3D 风——地球 + 「字」 + ABCD 字母 + 小狮子 + 星星/蜡笔，
同时表达了「中文字 + 英文字母」的双语范围，与应用定位一致。
原图归档在 `design/app_icon_source.png`（不参与打包）。

**为什么不能直接把整张图当自适应图标**
Android 自适应图标是 **108dp 画布**，但只有中间 **72dp(66.7%) 直径的圆**保证可见，
外围会被启动器裁成圆形/方形/水滴形。

设计稿 1536×1536 里，装饰（星星/蜡笔）最远点达半宽的 **110%**，远超安全半径 33.3%。
整张直接用 → **地球左缘和小狮子右侧会被裁掉**。

**做法**（`scripts/build_launcher_icon.py`，可重跑）
1. 取四角中位数作背景色（`#FDF3EA`）
2. **连通域**分离主体与装饰：主体（地球+小狮子）是最大的一块连通区域
   - 试过按**饱和度**分，**分不开**（星星也是高饱和的黄色）
   - 阈值也不能太低：阈值 52 时地面阴影把蜡笔连进主体，
     包围盒虚胖到 `x 80~1444`；阈值 150 时才干净分开（`x 224~1440`）
3. 缩放依据是**主体像素到自身质心的真实最大半径**（725px），
   而不是包围盒对角线 —— 主体是"圆球 + 侧面的狮子"而非填满的矩形，
   用对角线会缩放过度、图标显得很小
4. 裁剪越界处**用背景色补边**而不是把裁剪框夹断到图内 ——
   夹断会让主体偏离裁剪图中心，贴到画布上时主体就偏了
5. 背景层用取样到的米色，与前景边缘**完全同色** → 接缝不可见

**产出**
- `mipmap-{mdpi…xxxhdpi}/`: `ic_launcher`（方形）/ `ic_launcher_round`（圆形）/
  `ic_launcher_foreground`（自适应前景）/ `ic_launcher_monochrome`
- `values/ic_launcher_background.xml`（背景色）
- `mipmap-anydpi-v26/ic_launcher{,_round}.xml`（自适应图标）
- `icons/launcher_preview.png`（各遮罩 + 各桌面尺寸的效果预览）
- 删除被取代的旧蓝色占位矢量 `drawable/ic_launcher_foreground.xml`

**内建校验（分两档口径）**
| 密度 | 主体半径 / 安全半径 | 结果 |
|---|---|---|
| mdpi | 33.9 / 36.0 | OK |
| hdpi | 50.8 / 54.0 | OK |
| xhdpi | 67.7 / 72.0 | OK |
| xxhdpi | 101.6 / 108.1 | OK |
| xxxhdpi | 135.4 / 144.1 | OK |

主体半径稳定在安全圆的 **94%**（既不越界、也不显得小）；
装饰外扩（如 mdpi 38.2）**超出部分会被启动器裁掉，这是预期行为**，
所以校验里把"主体"作为硬指标、"含装饰"只做报告。

**顺带**：加入 `<monochrome>` 主题图标层（Android 13+ 主题图标用「字」剪影），
同时消除了 lint 的 2 条 `MonochromeLauncherIcon` 警告。

### 新增：卡片左右滑动切换上一张 / 下一张（第 13 轮）

**需求**：主页卡片能左右滑动，跳到上一个 / 下一个。

**实现**
- 向左滑 = 下一张，向右滑 = 上一张
- 维护两个栈：
  - `cardHistory`：看过的卡片，右滑回退用
  - `cardForward`：从历史回退过之后再左滑，**按原路返回**
    （否则"回退一步再往前"会变成重新抽卡，回不到刚才那张）
- 新增 `setCurrentItem()` 作为**唯一的卡片切换入口**并在此记历史 ——
  此前 `currentItem` 在多处直接赋值（`loadNextItem` / `loadItemById` /
  `restorePreJumpState`），把记历史散在各处必然漏掉某条路径
- 已经是第一张时右滑给一句提示，而不是静默无反应
- 手势判定：横向位移需超过 28dp 且**明显大于纵向位移**，避免和上下滚动打架

**踩到的坑（值得记下来）**
第一版把 `GestureDetector` 挂在 `binding.homeScrollView` 的 `OnTouchListener` 上，
**真机实测完全无效**（卡片一直不变）。原因：卡片里的字卡、词组 chip 都带点击监听，
手指落在它们上面时**子 View 成为 touch target**，父容器的 `OnTouchListener`
就再也收不到事件了。
改为在 **Activity 层重写 `dispatchTouchEvent`** 旁路一份手势，才能保证
任何位置滑动都能识别；且**不消费事件**，纵向滚动与点击照常工作。
抽屉打开时不响应翻卡。

**验证（真机）**
```
A → E → F → K → O → P     左滑 5 次，每次一张新卡
P → O → K → F → E → A     右滑 5 次，逐张原路返回
```
右滑精确回到起点、共 6 张不同卡片、纵向滚动仍可用、无 FATAL EXCEPTION。

### 新增：10 个 App 图标候选（第 13 轮）

**背景**：需要换一个好看的 APK 图标，先出 10 个候选供挑选。

**做法**：不依赖外部绘图工具，用 `scripts/build_app_icons.py` **用代码生成**
（Pillow + 项目自带的楷体子集），所以图标字形与 App 内字卡是同一套。

**设计约束（不是随手画）**
1. **自适应图标安全区**：Android 自适应图标是 108dp 画布，但只有中间
   **72dp(66.7%)** 保证可见，外围会被启动器裁成圆形/方形/水滴形。
   焦点元素必须落在中央 66% 内（背景渐变/纹理可以出血）。
2. **小尺寸可读**：桌面可能只有 48px，所以每张只设一个视觉焦点。
3. **配色沿用应用设计 token**（deep_blue / tangerine / cream / mint …），
   避免"图标一个色系、App 另一个色系"。

**产出**：`icons/candidates/icon_01..10.png`（512×512）+ 拼图
`contact_sheet.png`（含 48px 小尺寸预览，用于判断缩小后是否还认得出）。

**脚本内建安全区校验**：每个设计分「背景层」与「焦点层」，
用 `focal.getbbox()` **精确**量焦点范围并报告是否越界。

> 第一版校验用"角落颜色当背景基准"去猜墨迹，结果把**渐变背景本身**误判成
> 越界元素（报了 8 个假阳性）。改成量独立焦点层后才准 ——
> 据此修掉 5 个真实越界的设计（描边会被裁成断环、下划线会被圆角切掉等）。

### 修复：抽屉（汉堡）图标视觉上偏低（第 12 轮）

用户反馈"总觉得抽屉的位置稍微低了些，你仔细测量"。**这次先量再改** ——
而且量的是**渲染后的像素**，不是控件 bounds（上一轮刚吃过"读属性查不出视觉问题"的亏）：

| 元素 | 视觉范围 | 视觉中心 |
|---|---|---|
| 标签「中文 · 难度 简单」 | y 211~257 | 234 |
| 抽屉「≡」**改前** | y 222~268 | **245** ← 低 11px（≈3.4dp） |
| 抽屉图标**改后** | y 214~253 | **233** |

**为什么 bounds 对齐了、看起来却还是歪**：三个元素的 **bounds 中心都是 234**，
但 `menuButton` 当时是用**字符 `≡`(U+2261) 当图标**的 —— 它是**数学符号**，
在字体里的垂直位置本身就偏下（基线位置与普通汉字/字母不同），
于是控件居中、字形却沉下去 11px。

**改法**：不再用字符当图标，新增 `drawable/ic_menu.xml`（三条横线的矢量图标），
`menuButton` 改用 `app:icon` + `iconGravity="textStart"` + 空文本，
由 MaterialButton 精确居中，彻底摆脱字体度量影响。

**验证（像素测量，跳转态下三个元素同排）**
| 元素 | 视觉中心 |
|---|---|
| 标签 | 234 |
| 返回按钮 | 234 |
| 抽屉图标 | 233 |

横向位置不变：标签 x 78~745 · 返回按钮 x 745~1065 · 抽屉 x 1078~1208
（返回箭头仍在抽屉左侧）。

顺带：`Text` 里那个 `≡` 是布局里最后一处**用字符当图标**的地方，
现在全项目的图标都是矢量资源了。

### 修复：英文词库页状态徽章被裁成 2 个字（第 11 轮，用户第 3 次反馈）

**这次终于找到了真因 —— 而且不是文案问题，是视觉裁切。**

用户连续三次反馈"中文字：未学习等 3 个字，英文：未学等 2 个字，要统一"。
前两轮我都往"文案不统一"的方向查，结果都没解决，原因如下:

| 轮次 | 我做的 | 为什么没解决 |
|---|---|---|
| 第 8 轮 | 把英文侧界面文案（`Known/Review/English Stats`…）改成中文 | 方向对但**不是这一处** |
| 第 10 轮 | 把进度页 `认识：X` 统一成 `已认识：X`、总览标点改全角 | 确实修掉了一处**别的**不一致，但仍不是用户看到的那个 |
| 第 11 轮 | **截图逐像素看**，发现英文格子里的徽章显示的是 **`待巩` / `已认`** | ✅ 这才是真因 |

**真因与验证盲区**
全库字符串里**根本不存在"未学"**（只有 3 字的 `未学习`）。用户看到的 2 个字是
**被裁切后的渲染结果**：

| | 容器内边距 | 可用宽度 | 徽章需要 |
|---|---|---|---|
| 中文格子 | 10dp | ≈53dp | ≈52dp → 刚好放下 |
| 英文格子 | **14dp** | ≈45dp | ≈52dp → **裁掉一字** |

更糟的是：**上一轮我为了修"换行成圆圈"，给徽章加了 `maxLines="1"` + `singleLine`**，
于是失败模式从"换行"变成了"**静默裁切**"——看起来像文案写错了，实际是布局放不下。

**为什么我前两轮没发现**：我用 `uiautomator dump` 读 `text` 属性做"验证"，
但 **uiautomator 返回的是 TextView 的完整文本，即使视觉上被裁切也照样返回 `已认识`**。
也就是说我的检查方法**天生看不到这个 bug**。这轮改成**看截图**才暴露出来。

**改法**
- 英文格子容器内边距 `14dp → 10dp`（与中文格子对齐）
- 两个格子的徽章：内边距 `8dp → 6dp`、字号 `12sp → 11sp`

**验证（这次是看截图，不是读属性）**
- 英文词库页：`待巩固` / `已认识` / `未学习` **全部完整显示 3 个字**（此前是 `待巩` / `已认`）
- 中文词库页：同样完整
- 两个模式的徽章字号与内边距现在完全一致，不会再出现"一边 3 字一边 2 字"

**遗留的脆弱点（如实记录）**：徽章宽度受格子宽度限制，
如果将来把英文格子内容改宽（更长的音标）或把网格改成 5 列，可能再次触发裁切。
这类问题**读属性查不出来**，验收必须看截图。

### 数据审计：词组覆盖与英文字段完整性（第 11 轮）

用户反馈"个别中文字没有词组和例句，同时检查英文字母和单词"。审计结果：

**中文（3000 字，其中 558 个有词组）**
| 分段 | 有词组 |
|---|---|
| 简单 1~600 | **558 / 600** |
| 简单 601~1200 | 0 / 600（尚未开始） |
| 中等 1000 | 0 / 1000（尚未开始） |
| 困难 800 | 0 / 800（尚未开始） |

- 简单 1~600 里缺的 **42 个**全部是**已登记确认无合适幼儿词组**的例外
  （死 杀 血 伤 亦 岂 汝 毋 弗 奴 囚…），不是漏做；
- 已有词条的**拼音 100% 完整**，没有空拼音；
- 门槛之外还有 **2442 字**属于"内容尚未创作"（用户此前指示先不扩）。

**英文：完全完整**
- 26 个字母：6 个字段（字母/大写/小写/音标/示例词/示例词中文）**全部非空**；
- 150 个单词：6 个字段**全部非空**，且**每条例句都包含该单词**。

**顺手加固的校验**（此前只检查字段名集合，不检查值是否为空）：
- 英文字母/单词逐条检查**字段值非空** —— 空音标/空例句在界面上就是一块空白，
  用户会当成 bug；
- 分桶报告词组覆盖度，让"还剩多少没做"一眼可见，避免"以为做完了/以为漏了"两种误判。

### 修复：顶栏对齐 / 返回状态残留 / 中英文案不统一（第 10 轮）

**1. 顶栏三个元素永远对不齐（含"刚打开 APK 就没对齐"）**
根因不是间距没调好，而是**它们根本不在同一个容器里**：
- `topLabel` 在**滚动区的 ConstraintLayout** 内
- `menuButton` / `backJumpButton` 在**外层 FrameLayout** 内（用 `layout_gravity`）

实测中心线 `topLabel 267` vs `menuButton 234`，**差 33px**；而且一个高 66px、一个高 130px。
另外 `refreshBackJumpButton()` 里还有一段
`params.marginStart = if (jumpSource == null) 0 else dp(110)` ——
返回按钮一出现就用 110dp 把标签推开，这才是"跳转后错位"的直接原因。

改法：
- 新建固定顶栏 `@+id/topBar`（`LinearLayout` 48dp、`gravity=center_vertical`），
  把三个元素放进**同一行**：标签 `weight=1` 占满左侧，返回箭头与抽屉在右侧成组。
- 滚动区改挂到 `topBar` 之下；卡片顶部约束由 `topLabel` 改为 `parent`。
- **删掉那段动态 margin** —— 返回箭头的显隐不再影响标签位置。

实测：正常态 `topLabel 中心 234 / menuButton 中心 234`；
跳转后 `topLabel 234 / backJumpButton 234 / menuButton 234` ——
**三者完全水平对齐，且标签在跳转前后位置分毫未动**；
返回箭头横向位于抽屉左侧（x 709~1029 vs 抽屉 1042~1208）。

**2. 返回后状态残留（"再点左上角箭头又回到刚才那张卡"）**
源页（进度页 / 字库页）**用自己的返回箭头退出时不会 setResult**，
而主页在 `resultCode != RESULT_OK` 时直接 `return`，什么都不做 ——
于是 `jumpSource` 和跳转过来的卡片一直留在主页上。

改法：离开主页去打开源页时**先快照**，源页无选择地退出时**恢复快照**：
- 卡片回到跳转前那一张
- 返回按钮回到原来的显隐状态（通常就是隐藏）
- 快照**只在非跳转态**记录 —— 否则点「← 返回字库」这一步会用"被跳转过来的卡片"
  覆盖快照，导致从源页退出时回到那张卡，而不是用户最初离开主页时的状态
- 「← 返回」按钮也改走同一个 launcher，这样源页用自身箭头退出时同样能收到回调

实测：卡片 `友` → 跳转 `一`（返回按钮出现）→ 点「← 返回字库」→ 从字库退出 →
**回到 `友` 且返回按钮隐藏** ✓

**3. 中英文案不统一（用户第 2 次反馈）**
上次只统一了设置页的英文侧文案，漏了**进度页的统计标签**：
- 中文区用 `认识：X`（2 字），英文区用 `已认识：X`（3 字）
- 设置页总览中文行用半角 `: `，英文行用全角 `：`

现在统一为「已认识 / 待巩固 / 未学习」+ 全角标点，且总量行结构对齐：
```
中文：3000 字            英文：26 字母 · 150 单词
中文已学：25 · 待巩固：4   英文已学：20 · 待巩固：11
```
另加了一条自动检查：扫描全部文案，确认**没有"汉字后接半角冒号"**的残留。

### 修复：主页卡片过高，底部两个按钮要上滑才看得见

**这是我的回归。** 上一轮"幼儿友好化"把主字放大到 140sp、按钮加到 88dp，
单看每项都合理，**加起来把内容顶出了屏幕**。

**先量，再改**（`dumpsys`/uiautomator + 像素级测量）
- 可用视口 2460px，卡片独占 **1814px(558dp)**，底部按钮行溢出约 200px。
- 逐项量出占用：主字 143dp、吉祥物 75dp、拼音 54dp、词组/例句标题各 35dp、例句 55dp、卡片内边距 48dp。

**第一层：按比例收（仍明显大于常规）**
主字 140→**100sp**、英文字母 120→88sp、拼音 40→32sp、例句 32→26sp、
分区标题 26→21sp、吉祥物 56→40sp、卡片内边距 24→20dp、分隔线留白 16/12→10/8dp。
收完中文模式 `scrollable=false`（一屏正好放下）。

**第二层：发现更深的问题 —— 靠"刚好放得下"是不可靠的**
字库里有 **14 个字**的例句（如「凡是自己能做的事，就自己做。」），
在 26sp 下会换成两行 → 又会把按钮顶下去。也就是说，这次修完，
用户碰到那些字还会再遇到同样的问题。

所以做了**结构性修复：把两个按钮行移出滚动区，固定在底部**。
- 主页改为 `ConstraintLayout`：滚动区用 `top→parent` / `bottom→actionBar.top` 占满剩余空间，
  按钮栏 `bottom→parent` 固定。**无论卡片内容多长、例句是否换行，按钮永远可见。**
- 滚动区只放"内容"（卡片 + 统计条），按钮栏独立成 `LinearLayout @+id/actionBar`。

**第三层：系统栏避让**
`dumpsys window` 显示窗口是 `Requested w=1260 h=2720` —— **内容会一直画到物理底部**。
手势导航机型看不出问题，但**三键导航**机型的系统导航栏会压住按钮一截。
已在 `MainActivity.setupSystemBarInsets()` 里按 `systemBars` 的 inset
给滚动区顶部与按钮栏底部**累加**内边距（不是覆盖，保留原设计间距）。

**验证**
| 项 | 结果 |
|---|---|
| 中文模式滚动 | `homeScrollView scrollable = false` —— 一屏放得下 ✓ |
| 四个按钮 | 认识了 / 再学一次 / 先跳过 / 玩游戏 **全部可见** ✓ |
| 按钮实际高度（像素测量） | 主按钮 **286px = 88dp**，与设定一致 ✓ |
| 底部留白（像素测量） | 最靠下内容距屏幕底 **78px = 24dp**，与设定一致 ✓ |
| 英文模式 | 卡片更矮，余量 225px ✓ |
| 抽屉 | 四张入口卡与问候语完好（改动过程中误伤过这里的标签，已修复）✓ |
| 崩溃 | 无 FATAL EXCEPTION ✓ |

**过程中的两个失误（记录备查）**
1. 用行级手术改 XML 时，误把抽屉区域的一处 `</LinearLayout>` 替换成了 `</ConstraintLayout>`
   （`rindex` 匹配到了最后一个而非目标那处），导致 XML 解析失败。已定位并修正。
2. 一度把 uiautomator 报告的 `actionBar 172dp` 当成"被压缩"，白折腾了一轮显式高度实验 ——
   实际是 **uiautomator 会把节点 bounds 裁剪到可见窗口**（2590），
   而按钮栏真实范围是 2031~2720。**改用像素级测量（Pillow 扫色块）才拿到真相**：
   按钮就是 88dp、底部留白就是 24dp，布局一直是对的。教训：容器类节点的 bounds 不可尽信。

### 新增：CSV 导入 + 字库搜索（P2-3）

**CSV 导入**
- 此前只有「导出」，没有导入 —— 导出的文件等于导出即废,换手机或换档案都回不来。
- 新增 `util/CsvFormat.parseAll()`：**RFC 4180 解析器**。
  导出侧早就有 `cell()` 转义(含逗号/引号/换行的值会被引号包裹),但导入侧一直缺解析,
  若按 `split(",")` 拆,遇到 `"Hello, world."` 这种就会整列错位。
  现在支持引号内逗号/换行、`""` 转义、CRLF/LF、空白行跳过。
- 新增 `model/ProgressCsvImporter.kt`（纯逻辑,可单测）：
  - **按表头取列,不按位置** —— 将来导出多插一列(如"笔画数")也不会静默错位
  - 段落由 `# Chinese characters` / `# English letters` / `# English words` 识别
  - 认不出的行**跳过并计数**,不让一行坏数据毁掉整次导入
  - 兼容中英文两套状态词(认识/known 混用也能认)
- 设置页新增「导入学习进度（CSV）」：
  - 读取 → 解析 → **先弹预览确认**（"中文：认识 3 · 不认识 1…"）→ 再合并。
    导入会改动学习进度,家长需要在动手前看到到底会应用多少条。
  - **只合并、不覆盖**:文件里标「未测试/new」的条目**不改动本地记录**,
    所以"导入"永远不会让现有进度变少。
  - 过滤掉字库里不存在的内容键(换过字库或手改过文件时会遇到),不写无效数据。
  - 刻意不写 `recordSnapshot`:导入不是一次"学习",让它冒充当天的学习量会污染掌握率趋势图。
- 真机验证:推送一份 CSV(六/七/八=认识、花=不认识、pencil=known)→ 选择文件 →
  预览显示"中文：认识 3 · 不认识 1 / 英文：认识 1" → 导入后
  `花` 进入待巩固(4→5)、`W:pencil` 进入英文已认识(19→20)、三个字都在已认识里 ✓

**字库搜索**
- 新增 `model/LibrarySearch.kt`（纯逻辑）：中文支持**汉字或拼音**,英文支持
  字母/单词/中文意思。
- **拼音搜索忽略声调** —— 家长通常打不出「bǐ」,输入 `bi` 必须能搜到。
  内部把声调符号归一化,并去掉空格与隔音符号(`xiǎo péng yǒu` → `xiaopengyou`)。
- 真机验证:输入 `bi` → **找到 46 条**,命中 笔bǐ / 比bǐ / 币bì / 必bì /
  边biān / 丙bǐng / 并bìng / 冰bīng ✓

**开发中抓到的一个真 bug**
- 结果条数提示最初读的是 `adapter.itemCount`,而 `ListAdapter.submitList()` 是**异步**的 ——
  读到的是上一轮的旧值,真机表现为"列表已经空了,却显示**找到 3000 条**"。
- 改为把过滤后的实际条数显式传给提示函数。

**测试**：新增 30 个用例（`CsvFormatParseTest` 9 + `ProgressCsvImporterTest` 12 +
`LibrarySearchTest` 14，含 CSV 往返、列顺序变化、拼音去声调等),
单元测试总数 **104 → 141**。

### 新增：孩子档案（P2-2 多用户档案）

**动机**：一台设备往往是多个孩子（或兄弟姐妹）共用，而此前所有进度都在同一份
SharedPreferences 里，一个孩子点「认识了」会覆盖另一个孩子的记录。

**实现**
- 新增 `model/ChildProfile.kt`：`ChildProfile` 数据类 + 两个纯逻辑对象
  - `ProfileRules`：默认命名（宝贝 / 宝贝2 / …）、名字规范化、能否删除
  - `ProfileCodec`：档案列表的持久化编解码
- 新增 `data/ProfileStore.kt`：档案注册表。**单独放一个 prefs 文件**（`literacy_profiles`），
  因为"有哪些孩子、当前是谁"不能存在任何一个孩子的档案里 —— 否则删掉某孩子会连带删掉注册表。
- `ProgressStore` 新增 `active(context)` 工厂与 `prefsNameFor(profileId)`：
  - **文件名规则收敛到一处**，`ProfileStore` 删除档案时也复用它，
    避免"删了注册表却没删数据"这种两处各写一份的经典 bug
  - 5 个 Activity 的 `ProgressStore(this)` 全部改为 `ProgressStore.active(this)`
- 设置页新增「孩子档案」卡片：chip 列表切换、新增、重命名、删除（含二次确认）。
  整个设置页本来就在[家长门][ParentGate]后面，所以这里不重复加门。

**两个容易做错的地方（都已处理并写进测试）**
1. **档案名要转义**。名字是**家长手输的**，完全可能含 `|` `;` `\`（例如「小明;3岁」）。
   `ReviewCodec` 那边可以直接用分隔符拼字符串，是因为进度键只可能是汉字或 `L:A`；
   这里输入不可控，所以 `ProfileCodec` 做了完整转义。
   测试专门覆盖了「名字含全部特殊字符也不串档」。
2. **切换档案后必须换掉 `ProgressStore` 实例**。每个档案是独立的 prefs 文件，
   沿用旧的会继续读写上一个孩子的数据。`MainActivity` 是长驻的，
   所以在 `onResume` 里比对新旧档案 id，变了就重建 `progressStore` 并清掉当前队列。

**兼容性**：默认档案的 id 固定为 `"default"`，用的正是历史文件名 `literacy_progress` ——
**老用户升级后进度照旧，不会丢**（首次运行自动创建 id=default、名为「宝贝」的档案）。

**删除语义**：只剩一个档案时拒绝删除（否则数据无处可放）；删除会**清空该孩子的全部进度**
（认识 / 待巩固 / 错题本）；删的若是当前档案，自动切到剩下的第一个。

**真机验证（逐项跑通）**
| 场景 | 结果 |
|---|---|
| 首次运行自动建档案 | 注册表 `default\|宝贝`，当前 `default` ✓ |
| 新增档案 | 注册表变成两条，新档案**自动设为当前**，chip 选中态正确 ✓ |
| **数据隔离** | 宝贝 `已认识 23 / 3000` → 切到新档案 `0 / 3000` ✓ |
| 切回并返回主页 | 主页统计恢复为 `已认识 23 / 3000 · 待巩固 4` ✓ |
| 删除档案 | 确认框含"无法恢复"提示；删后自动切回宝贝，主页数据完好 ✓ |
| 崩溃 | 无 FATAL EXCEPTION ✓ |

**测试**：新增 19 个用例（`ProfileCodecTest` 9 + `ProfileRulesTest` 10），
单元测试总数 **85 → 104**。

### 词组 / 例句覆盖 395 → 558 个字（第四批 easy #400~599）

- 补入 163 个字，其中 9 个生僻字只找到 1 个适合幼儿的真实词组（已加 `note`）。
- **37 个字按"宁缺毋滥"原则省略，没有编造**，分三类：
  - **负面或成人义**（10）：死 杀 血 伤 奸 邪 伪 妄 讼 刑
  - **文言/生僻，只见于书面语或成语**（17）：亦 岂 汝 迄 忖 讳 迂 兆 夷 旬 旨 廷 吏 玑 吁 讽 牟
    （兆/旬 虽常见但义项抽象，如"预兆""上旬"，对 3~6 岁不适用）
  - **生僻姓氏用字或词义抽象**（5+5）：伦 邦 仲 贞 妃 执 劣 巩 朽 匈
- 已把这 37 字连同第一批的 5 字一起登记到 `verify_character_sets.py` 的 `KNOWN_NO_WORDS`，
  校验门槛相应为 **558/558**（另有 42 字确认无合适幼儿词组）。
- 顺序指纹仍为 `40b3c1d5d0f62a4a`（未变）。

### 构建工具链升级（AGP 8.1.0 → 8.7.2）

**动机**：AGP/Gradle/JDK 三者互相锁死，导致一个持续性的摩擦：

| 组件 | 升级前 | 升级后 |
|---|---|---|
| AGP | 8.1.0（2023 年中） | **8.7.2** |
| Gradle | 8.0 | **8.10.2** |
| Kotlin | 1.9.0 | **1.9.24** |
| 构建用 JDK | **必须 17** | **17 和 21 都可以** |

升级前的问题：AGP 8.1 只在 JDK 17 上工作，而 Gradle 8.0 跑不了 JDK 21，
但 **Android Studio 自带的 JBR 就是 JDK 21** —— 所以每次命令行构建都得先
`$env:JAVA_HOME = "...jdk-17..."`，在 Studio 里点构建则会失败。

**收益（均已实测）**
1. **JDK 21 构建通过**：用 `Android Studio\jbr`（openjdk 21.0.10）跑
   `assembleDebug` → BUILD SUCCESSFUL。不再需要单独装 JDK 17。
2. **配置缓存恢复可用**：AGP 8.1 的 `JdkImageTransform` 不支持配置缓存
   （报 `field generatedModuleFile of JdkImageInput`），当初因此把
   `org.gradle.configuration-cache` 关掉。现在重新开启，实测输出
   `Configuration cache entry stored.`，**二次构建 8 秒**。
3. **release 包反而变小**：3.42 MB → **3.01 MB**（-0.41 MB，新版 R8 优化更好）。

**过程中的两个坑（记录备查）**
- 这台机器**下载 Gradle 发行版会停滞**（`services.gradle.org` 的 `.part` 一直 0 字节），
  但 Maven 仓库（Google Maven / Maven Central）完全正常。
  解决办法：改用**腾讯云镜像** `mirrors.cloud.tencent.com/gradle/`
  （实测 **4.7 MB/s**，130 MB 仅 25 秒）下载后放进 wrapper 缓存目录即可，
  wrapper 会直接解压本地 zip，不再联网下载。
  这个镜像也值得写进项目文档，方便以后换机器。
- 构建曾无故卡住 40 分钟不产出（CPU 仅 7 秒），根因是**残留的旧 Gradle 守护进程占着锁**
  （一个 8 小时前启动的 `GradleDaemon`）。清掉守护进程后立即恢复正常。
  **注意**：清理时不要误杀 Android Studio 自身的 java 进程。

**兼容性验证**：85 个单元测试全过、lint 0 error（23 warning，与升级前一致）、
clean + assembleDebug + assembleRelease 全通过、真机安装启动正常。

### 修复与改进（第 8 轮：用户逐条反馈）

- 🐛 **跳转卡片后"返回来源页"按钮从不出现**（用户报告）
  - 原因:进度页与字库页在跳转时**同时**发了 `EXTRA_SOURCE_PAGE` 和 `EXTRA_CLEAR_SOURCE=true`,
    而主页的逻辑是 `jumpSource = if (clearSource) null else ...` ——
    等于**源页面自己把这个功能关掉了**,返回按钮在任何跳转后都不会显示。
  - 修复:去掉 `EXTRA_CLEAR_SOURCE` 这套自相矛盾的标志(常量一并删除),
    只要是跳转就记录来源;并在 `onResume` 也刷新一次按钮状态。
  - 真机验证:字库点字后主页左上角出现 **「← 返回字库」**,进度页同理显示「← 返回进度」。
- 🎨 **设置中心 4 个按钮颜色不一致**（用户报告）
  - 原来:浏览=白底蓝边 / 导出=实心蓝 / 重置中文=实心橘 / 重置英文=实心橘(四不像)。
  - 改为 **2 组一致的观感**:「浏览全部字词」「导出学习进度」= 白底**深蓝**描边;
    「重置中文进度」「重置英文进度」= 白底**深橘**描边(破坏性操作)。
  - 新增 `bg_button_danger_outline` + `Widget.StudyWord.DangerOutlineButton` 与 `orange_deep`
    (用深橘而非 tangerine —— 后者太亮,做文字/描边对比度不足)。
- 🈶 **字库总览的中英文状态文案不一致**（用户报告）
  - 原来中文区「中文已学 / 待巩固」,英文区却是 `English: Known / Review`、
    `Known English Items`、`English Stats` 等英文表述。
  - 已统一为中文且**用词完全对齐**:`英文已学：X · 待巩固：Y`、「英文统计」、
    「当前认识的字母 / 单词」、「还需巩固的字母 / 单词」,顶栏统计也统一为
    `已认识 X / Y · 待巩固 Z · 未测 W`。
  - 保留英文的只有语言名 `ENGLISH`(语言切换用母语书写更易识别)。
- 👶 **按幼儿产品的通行做法做了一轮"去成人化"**（附调研依据）
  - **主字放大**:汉字 80 → **140sp**、字母 80 → 120sp、拼音 28 → 40sp、
    例句 22 → 32sp、分区标题 18 → 26sp。
  - **按钮加大**:主操作 76 → **88dp**、次要 64 → 76dp,间距 12 → 16dp
    (WCAG 2.2 SC 2.5.8 通用下限是 24×24px,且规范明确"越大越好用";
    Google 儿童指南要求 "Create big touch targets" 但未给具体 dp 值)。
  - **去掉自我否定的措辞**:按钮由「认识 / 不认识 / 换一个字」改为
    **「认识了 / 再学一次 / 先跳过」**,并把图标从 ✗ 换成"重来"箭头
    (Google 儿童指南:答错要 "Create encouraging errors"、用视觉提示而非文字指责)。
  - 依据出处:[Google Building for kids](https://developers.google.cn/building-for-kids/designing-engaging-apps?hl=en)、
    [WCAG 2.2 SC 2.5.8](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html)。
  - **未做但已记录**:字卡改用楷体。教育部官方说明《通用规范汉字表》**只规范宋体字形**,
    而小学 1–3 年级课本用**楷体**印刷、两者笔形有差异 —— 所以儿童识字用楷体有官方依据,
    但 Android 系统不含楷体,需自带字体文件(数 MB,当前 release 仅 2.5MB),
    属于需要单独决策的改动,本轮未擅自引入。
- 📚 **词组 / 例句覆盖 200 → 395 个字**（第三批 easy #200~399）
  - 补入 195 个字;其中 14 个生僻字只找到 1 个适合幼儿的真实词组(已加 `note`),
    尹/卢/冯 为姓氏用字(词组为姓氏搭配,已注明)。
  - **5 个字确认找不到任何适合幼儿的词组**:`毋 弗 奴 囚 弘`
    (现代汉语中只有书面语/负面/姓氏用法:毋庸、自愧弗如、奴隶、囚禁、弘扬)。
    已登记到 `verify_character_sets.py` 的 `KNOWN_NO_WORDS` 白名单,
    **宁缺毋滥、不编造**;校验门槛相应为 395/395。
  - 顺序指纹仍为 `40b3c1d5d0f62a4a`(未变)。

### 修复（v1.6.0 开发中发现并修掉的回归）

- 🐛 **「复习错题」点了没反应**（严重）
  - 原因:复习模式的题池**复用了"当前难度 / 子模式"的题池**,
    于是错题只要不属于当前难度(或属于另一种语言),题池就是空的 →
    被判为"没有要复习的字" → **静默退回普通出题模式**。用户看到的现象就是"点了完全没反应"。
  - 修复:`rebuildReviewQueue()` 改为按到期键**从整个仓库取卡片**,不受难度 / 子模式限制
    （复习本来就不该被这些筛选影响）。
  - 另外补了明确反馈:进入复习模式时弹「错题复习：共 N 个字」;
    若错题确实在另一种语言里,提示「错题在另一种语言里，请先切换语言」,不再静默。
  - 真机验证:难度=简单、错题「春」属于中等 → 点复习错题**成功**显示 春 + 「错题复习中」+ Snackbar。
- 🐛 **字库浏览点字跳转卡片错误**
  - 原因:`loadItemById` 此前**只对英文**分支对齐子模式/难度;中文沿用"当前难度题池"。
    于是在字库页点了**其它难度**的字时,`pendingItems` 里找不到该 id → 走**静默 return**
    → 主页字卡纹丝不动(用户看到的就是"跳转错误/跳到别的字")。
  - 修复:中文也改为**按全库定位**并自动切换难度与对应 chip;
    并且不再静默失败 —— 若重建队列后仍找不到目标,直接用仓库构造该卡片显示。
  - 真机验证:主页难度=简单 → 字库筛选"中等" → 点「春」→ 主页显示 **春** 且难度**自动切到中等** ✓
  - 「学习进度」页跳转走同一条 `loadItemById` 路径,一并验证通过(点「一」→ 主页显示「一」)✓

### 兼容

- `versionName 1.5.0 → 1.6.0`（`versionCode` 待发布时再递增）
- 字库**顺序未变**（指纹 `40b3c1d5d0f62a4a` 一致）⇒ v1.4.4 升级用户的进度迁移仍然有效
- 新增 prefs 键 `review_state`;老用户首次运行时错题本为空,不影响既有功能
- 本次改动经校验:100 条变更全部落在 easy #100~199,medium/hard 与条目总数均未变

## [1.5.0] - 2026-10-04

本版本是一次**工程地基重构**，不新增用户可见功能，集中解决"数据会错、状态会丢、改动无测试"三类隐患，
并修复一个在真机上确认的**启动崩溃**。

### ⚠️ 数据格式变更（自动迁移，无需用户操作）

- 🗝️ **进度主键从「位置 id」改为「内容键」**
  - 旧实现:解析字库时按顺序 `nextId++` 生成 int id，进度存 id 集合。
    只要在 `character_sets.json` 中间增删一个字，**其后所有 id 平移**，
    历史进度会整体错位到别的字上，且**静默无报错**；越界旧 id 还会让"认识：N"虚高
    （总数算进去，但下方字形列表被 `mapNotNull` 过滤掉，出现"认识 1200 个、列表只有 1199 个"）。
  - 新实现:中文直接存汉字（`"天"`），英文存 `"L:A"` / `"W:apple"`。字库顺序可任意调整，进度天然稳定。
  - 新增 `model/ProgressKey`（键规则，各层共用）、`data/ProgressMapping`（纯映射，可单测）、
    `data/ProgressMigrator`（启动时一次性迁移，幂等）。
  - **旧 key 内容保留不删**，便于核对或回滚到 1.4.4；迁移后写入标记位 `progress_keys_v2_migrated`。
  - 真机实测:中文 10+10 条、英文 23+7 条全部正确映射，越界 id 丢弃 0 条；二次启动不重复迁移。

### 修复

- 💥 **Android 8/9（API 26~28）启动崩溃**（严重，此前从未被 lint 发现）
  - `PinyinConverter` 使用 `android.icu.text.Transliterator#getInstance / #transliterate`，
    而这两个方法**到 API 29 才进入公开 API**，应用 `minSdk` 却是 26。
    拼音在首屏就要为 3000 个字生成 ⇒ **Android 8.0/9.0 用户一打开就崩**。
  - 方案:改为**构建期生成、运行期查表**。新增 `scripts/generate_pinyin.py`（pypinyin），
    产出 `assets/pinyin_table.json`（3000 字全覆盖）；`PinyinConverter` 不再依赖任何 Android API。
  - 顺带收益:多音字更准（ICU 的 `Han-Latin/Names` 是人名规则集，pypinyin 按词频），
    且首启不再有 3000 次 ICU 转写 + 数千次正则编译。
  - 读音优先级:字库显式 `pinyin` > 人工 `overrides` 表 > 离线拼音表。人工表从 12 条扩充到 60+ 条常见多音字。
- 🖱️ **顶部三个按钮可能"点击失效"**（v1.4.3 修过的 bug 又出现在新按钮上）
  - `menuButton` / `backJumpButton` / `drawerBackButton` 是全项目**唯三不带 `style=`** 的 MaterialButton，
    且只写了 `background` + `backgroundTint=transparent`，**漏掉**了 `themes.xml` 自己记录的修复配方
    （`backgroundTint=@null` + `stateListAnimator=@null`）。它们是主页唯一导航入口，一旦复现连抽屉都打不开。
  - 方案:统一到新样式 `Widget.StudyWord.ChromeButton`。真机实测可点、关闭、并能重复打开。
- 🔊 **游戏页退出后仍在朗读 / 仍在出题**
  - `GameActivity.onDestroy()` 为空，700ms 的 `speakCurrentPrompt` 与 800ms 的"进入下一题"
    四个 `postDelayed` 从不取消 ⇒ 孩子按返回后喇叭还在念、回合仍在后台推进。
  - 方案:改用可取消的 Runnable 列表 + `isFinishing/isDestroyed` 双保险，`onDestroy` 里逐个 `removeCallbacks`。
  - `MainActivity` 的撒花结束回调同样加了取消与存活判断。
- 💬 **退出对话框文案与事实不符**
  - 原文"今天的进度还没保存，确定要退出吗？"是**假信息**：每次判定都立即 `persistRecord` 落盘。
  - 改为"确定要退出吗？下次打开还能接着练。"（入 `strings.xml`）。
- 📊 **趋势图年视图失真**：`MAX_HISTORY_SIZE=60` 配合"近一年"区间，年视图永远不可能超过 60 个点，已在注释中说明口径。
- 📄 **CSV 导出未转义**：值里出现半角逗号（如 `Hello, world.`）会让整列错位。
  新增 `util/CsvFormat`（RFC 4180 转义）并补单测。
- 📱 **横屏下游戏无法作答 / 抽屉入口不可达**（真机复现，v1.4.4 既有）
  - 游戏页：根布局无滚动容器、固定内容约 690dp，而横屏可用高度仅约 330~360dp
    ⇒ `optionA~D` / `optionsGrid` / `skipButton` **全部被挤出屏幕**，既不能答题也不能跳过；
    真机实测横屏可见元素只剩顶部栏与题目卡。
  - 抽屉：`activity_main` 抽屉内容约 720dp（问候卡 + 3 组 chip + 3 张入口卡），
    横屏下 3 张入口卡完全无法到达。
  - 方案：新增横屏专用 `res/layout-land/activity_game.xml`，改为**左右分栏**（左题目 / 右 2×2 选项），
    整屏无需滚动即可完成一轮；竖屏与抽屉则包进 `NestedScrollView` 兜底。
  - 真机验证：横屏 `optionA~D` + `skipButton` 全部可见，答完 5 题正常结算（`答对 3 / 5`）；
    抽屉滚动后可访问全部 3 个入口。
- 🔧 顺带修掉 `GridLayout` 的 `layout_rowWeight` 在本页**不按行均分**的问题
  （实测第一行吃满整格、第二行被裁出屏幕），改用 `LinearLayout` 嵌套等分。

### 架构与可维护性

- 🎉 **抽出 `ui/ConfettiOverlayView`**：撒花粒子系统此前在 `MainActivity` 与 `GameActivity`
  **各写一份**（创建 TextView、`ValueAnimator` + `cos/sin` 抛物线、随机角度/速度/时长、
  透明度包络、移除视图），两处几乎逐行相同。现在统一为
  `burst()` / `floatMessage()` / `cancelAll()`，两个 Activity 合计删除约 230 行。
- 🔙 **抽出 `model/BackPolicy`**：主页返回键的"关抽屉 / 回来源页 / 弹退出确认"优先级
  从 `OnBackPressedCallback` 的内联分支变成纯函数，并由 5 个单测锁定
  （抽屉 → 来源页 → 退出）。这块正是 v1.4.2~v1.4.4 反复出问题的地方。
- 🎨 **新增 `res/values/dimens.xml`**：间距 / 圆角 / 触控目标（`touch_target = 48dp`）/ 抽屉宽度。
  此前圆角出现过 14~28dp 九档、卡片 padding 五档，且 `cardCornerRadius`（26/28dp）与内部
  drawable 圆角（24dp）不一致，每张卡片四角都会露出缺口。
- 🌐 **文案全面资源化**：布局里 **88 处**字面量 + Kotlin 侧约 40 处硬编码文案改用 `@string`，
  并优先复用 v1.1~v1.3 就已写好、却一直没被引用的死资源
  （`game_title`、`game_skip`、`game_replay`、`game_exit`、`words_label`、`library_all_label` 等）。
  其中 `SettingsActivity` 此前**从未引用过任何字符串资源**（连 `R` 都没有 import）。
  结果：lint 警告 **202 → 55**，未被引用的 string **31 → 0**。
- ♿ `backButton` 触控目标 40dp → 48dp；选项按钮 `100dp` 固定高 → `minHeight 92dp`
  （系统字体放大 1.5× 时不再裁切）；`speakPromptButton` 补 `contentDescription`。
- ♿ **无障碍补齐**
  - 装饰性 emoji / 箭头（`🎯 😊 😢 ➡️ 🎮 ≡ ← 🦄 📊 📖 ⚙️ › ×3 🦁`）统一加
    `android:importantForAccessibility="no"`，避免读屏念出一串表情符号；
    同时以 `tools:ignore="HardcodedText"` 标注它们是**刻意不入资源**的图标字形。
  - `languageLabel` / `difficultyLabel` / `englishSubModeLabel` 补 `android:labelFor`，
    使读屏能把标签与对应 chip 组关联。
  - **游戏答对/答错增加语音播报**（`announceForAccessibility`）：此前只换背景色 + 图标，
    且答错后按钮被 `isEnabled=false` 锁死会移出无障碍树 —— 视障用户拿不到任何反馈。
  - 进度页动态 chip 恢复 `setEnsureMinTouchTargetSize(true)`：原先被显式关掉，
    触控区被压到视觉高度（约 40dp），低于 48dp 推荐值；恢复后只扩大触摸热区、不改变视觉。
- 🧹 **继续消除重复**
  - 抽出 `ui/SwipeBackDelegate`：`ProgressActivity` 与 `CharacterLibraryActivity`
    此前各写一份**完全相同**的左边缘右滑返回（含三个阈值常量）。
  - `CharacterLibraryActivity` 的中/英文状态对话框合并为 `showStatusDialog(title, onChosen)`。
  - 删除确认无用的资源：`drawable/difficulty_chip_background.xml`、`xml/file_paths.xml`、
    `color/gray_light`、5 个未被引用的 `dimen`。
  - `AndroidManifest.xml` 补 `android:roundIcon`（`mipmap/ic_launcher_round` 此前存在但从未被引用）。
- 📉 **lint 债务清理**：警告 **202 → 23**，且 `HardcodedText` 与 `UnusedResources` 归零。
  剩余主要是依赖版本提示（`GradleDependency` 4）、`NestedWeights` 4、
  `MonochromeLauncherIcon` / `UselessParent` / `ObsoleteSdkInt` / `PluralsCandidate` 各 2。

### 构建 / 发布 / CI

- 🔐 **release 打开混淆与资源裁剪**：`minifyEnabled false → true`，新增 `shrinkResources true`。
  此前 `proguard-rules.pro` 是 Android Studio 原始模板（**零条生效规则**），
  现已写入真实 keep 规则：Activity / 自定义 View 构造函数、`UtteranceProgressListener`
  （系统框架按名回调）、`model.**`、MPAndroidChart、Kotlin 元数据与行号信息。
- 🔑 **签名配置从 `keystore.properties` 读取**（该文件不入库）。
  文件不存在时**不配置签名**，`assembleRelease` 仍产出未签名 APK —— 便于 CI/本地
  只验证 R8 与资源裁剪，不会误发"看似可用但未签名"的包。
- 📦 **显式声明 `androidx.recyclerview:recyclerview:1.3.2`**：
  `CharacterLibraryActivity` 与两个 Adapter 一直直接用 RecyclerView，
  却只靠 `material`/`appcompat` 的传递依赖获得 —— 一次依赖升级就可能静默换版本或丢失。
- 🪟 **补上缺失的 `gradlew.bat`**：项目在 Windows 上开发，此前根目录只有 `gradlew`(sh)，
  用 cmd/PowerShell 根本无法执行 `.\gradlew`。
- ⚠️ **工具链约束（已实测确认，尚未解决）**：Gradle 版本被 AGP 卡住
  - **AGP 8.1.0 需要 JDK 17**：用 JDK 21 会在
    `JdkImageTransform: core-for-system-modules.jar` 处失败。
  - 而 **Gradle 8.0 又不支持 Java 21 作为启动 JVM**
    （`Unsupported class file major version 65`）。
  - 所以当前唯一可用组合是 **Gradle 8.0 + JDK 17**。
    本机 `JAVA_HOME` 指向 Android Studio 的 JBR 21，故直接用 `gradlew.bat` 会失败，
    需先把 `JAVA_HOME` 指向 JDK 17（已在 `gradle-wrapper.properties` 里写明）。
  - 彻底解决需把 **AGP 升到 8.5+**（届时可同时用 Java 21 并重新开启配置缓存），
    属独立工具链升级，留给下个版本。
- 🧹 **`org.gradle.configuration-cache` 关闭**：开启状态下构建**直接失败**——
  `Configuration cache state could not be cached: field generatedModuleFile of JdkImageInput`
  → `Execution failed for JdkImageTransform`。根因是 AGP 8.1.0 的该 transform 不支持配置缓存
  （Gradle 8.0 时代只是"没缓存成功"，到 8.14 变成硬失败）。
- 🧹 `build.gradle` 的 `clean` 任务改为 `tasks.register('clean', Delete)` +
  `layout.buildDirectory`：原写法在执行期跨项目访问已废弃的 `rootProject.buildDir`。
- 🤖 **新增 GitHub Actions CI**（`.github/workflows/ci.yml`）：每次 push / PR 执行
  `testDebugUnitTest` → `lintDebug` → `assembleDebug` → `assembleRelease`（验证 R8）。
  此前项目**没有任何 CI，lint 从未在提交前跑过** —— 而 Android 8/9 启动崩溃
  正是靠 lint 才发现的（见上方"修复"第一节）。
- 🌙 **深色模式定为"锁定浅色"**：主题父类
  `Theme.MaterialComponents.DayNight.NoActionBar → Light.NoActionBar`，
  并设 `android:forceDarkAllowed=false`（避免厂商强制反色把卡通配色弄乱）。
  原因：项目没有 `values-night`、颜色只有浅色一份、背景是写死的浅色渐变，
  继续挂着 DayNight 会出现"浅底 + 深色组件"的不可控混搭。

### 新增

- 🧪 **单元测试基础设施（此前为 0）**
  - 新建 `app/src/test`，**55 个用例**覆盖:进度键规则、进度改写互斥、题池排序优先级、
    迁移映射（含越界 id 丢弃、重排后不错位）、拼音 overrides、拼音表回落、CSV 转义。
  - 为让逻辑可纯 JVM 测试，抽出 `model/ProgressRules`（互斥改写 + 出题优先级）与 `util/CsvFormat`，
    顺带消除了 `MainActivity` 与 `CharacterLibraryActivity` 各写一份的实现。
  - 新增 `scripts/generate_pinyin.py`（可重复执行，输出确定性排序，便于 diff）。
- 🧠 **ViewModel**（`ui/StudyViewModels.kt`）
  - `MainViewModel`:语种 / 难度 / 子模式 / 待测队列 / 当前字卡 / 跳转来源。
  - `GameViewModel`:整轮 `GameRound`（含已答题数、正确数、当前题号）。
  - 真机实测:队列推进到第 2 张卡后旋转，仍停留在同一张（旧行为会被重建队列替换）；
    游戏整轮不再因旋转作废。
- ♿ **无障碍标签**：`menuButton`（打开菜单）、`drawerBackButton`（关闭菜单）、
  四个主操作按钮（标记为认识 / 标记为不认识 / 换一个字 / 进入游戏）补 `contentDescription`。
- 🎨 `Widget.StudyWord.ChromeButton` 样式；`PrimaryButton`/`SecondaryButton`/`UnknownButton`/`OptionButton` 补 `rippleColor`（此前点击无按压反馈）。
- 🔤 字库解析支持**可选 `pinyin` 字段**（对象格式），便于人工校正读音而无需改代码。

### 改动

- 🧩 **难度 / 英文子模式持久化**：此前完全没落盘，`setupDifficultyToggle` 每次 `onCreate` 都强制 `chipEasy`。
  结果:选「困难」后**旋转屏幕或重启即悄悄回到「简单」**。现在写入 `current_difficulty` / `english_sub_mode` 并回显。
- 🚫 **移除运行时 ICU 依赖**：`PinyinConverter` 从"Android API + 正则"变为纯查表，可完整单测。
- 🧹 删除死代码：`ProgressStore.markEnglishKnown/markEnglishUnknown/studiedEnglishIds/reset`、
  `TtsManager` 的 char-by-char 队列（`charQueue`/`speakNextInQueue`，v1.4.2 起已无生产者）等；
  `speakWordOrSentence` 移除从未使用的 `pinyin` 参数。
- 📦 **依赖/构建**：`app/build.gradle` 升到 `versionCode 11` / `versionName 1.5.0`。
- 📚 文档：`CLAUDE.md` 收敛为指向 `AGENTS.md`（此前写 `minSdk 21`，与实际 26 冲突），
  并补上 Windows 下没有 `gradlew.bat` 时的 wrapper 调用方式。
- 🗂️ `.gitignore` 补齐 `dh*.xml` / `window_dump*.xml` / `dlib*.xml` / `dset.xml` / `dump*.xml` /
  `samples.txt` / 截图子目录 / 签名文件。工作区未跟踪项从 **108 项降到 21 项**
  （此前这些文件只是"没人 `git add -A`"才没入库，并非有策略保护）。

### 已知问题（下个版本修）

- 📉 **词组 / 例句覆盖率仍偏低**：目前 **100/3000（3.3%）**。
  前 100 个简单字已全覆盖（作者入门最先遇到的字），但剩余 easy 1100 字、medium/hard 尚无词组。
  补齐属**内容创作**（需逐字审核，避免生成不通顺或超纲的词组），建议按批次推进。
- ♿ 无障碍仍待补：装饰性 emoji（`🎯 🦄 📊 📖 ⚙️`）缺 `importantForAccessibility="no"`；
  表单标签缺 `android:labelFor`；游戏答对/答错缺 `announceForAccessibility` 语音播报；
  `activity_splash.xml` 的 `contentDescription` 仍是硬编码。
- 🗄️ **`SharedPreferences` → `DataStore` 迁移：评估后暂缓**
  - 动机（写入事务化、避免丢写、历史不再拼字符串）在当前代码下**收益有限**：
    所有读写都在主线程、`apply()` 会同步更新内存并在 `onStop` 前落盘，
    审计未找到可复现的丢失更新；数据稳定性问题已由"内容键"改造解决。
  - 而代价不小：`ProgressStore` 需改为挂起 + `Flow`，5 个 Activity 的同步读取点
    全部要改成协程收集，属高风险重构。
  - 结论：**暂不迁移**；若将来出现多进程写入或需要历史 JSON 化，再单独排期。
    残留的真实弱点是历史曲线仍用 `|`/`,` 拼字符串（当前只存 3 个数字，安全但不好扩展）。
- ⚙️ 尚未开始：错题本 + 间隔重复、多用户档案、CSV 导入、字库搜索。

### 兼容

- `versionCode 10 → 11`，`versionName 1.4.4 → 1.5.0`
- **进度自动迁移**（见上），无需用户操作；旧 key 保留可回滚
- 字库 `character_sets.json` / `english_sets.json` **内容与顺序均未改动**，确保迁移映射正确
- 新增 `assets/pinyin_table.json`（约 51 KB）
- `LearningCharacter` 新增可选 `pinyin` 字段，默认空，旧数据无需迁移
- `ProgressStore` 方法名变更：`reset()` → `resetChinese()`；`save/saveEnglish` 参数类型由 `Set<Int>` 改为 `Set<String>`
- `StudyItem` 新增 `progressKey` 属性；`EnglishSubMode` 从 `MainActivity` 私有枚举提升为顶层

## [1.4.4] - 2026-10-04

本版本针对 v1.4.3 之后的 5 项用户反馈做 UI 简约化回归、退出流程修复、导航栈清理。

### 新增
- ✋ **退出软件前确认对话框** — 在主页按系统返回键弹"退出识字小帮手?"对话框
  - 确定 → `finish()` 退出 app
  - 取消 / "再练一会儿" → 留在主页继续学习
  - 抽屉打开时按返回键 → 先关闭抽屉,不弹退出对话框
  - 通过 `OnBackPressedCallback` 实现,与 `MaterialAlertDialogBuilder` 配合
- ⬅️ **抽屉左侧"← 返回主页"按钮** — `drawerBackButton` 在 drawerContainer 顶部左侧
  - 透明背景,文字 32sp,与抽屉整体同高
  - 点击关闭抽屉回主页(主页本来就在后台栈,不 finish 任何 Activity)
  - 与右上角 `menuButton` 风格一致:透明背景 + ripple + deep_blue 文字

### 改动
- 🎨 **UI 回退到 v1.4.0 简约风**(用户反馈"UI 太难看了,恢复改动前的样子")
  - `bg_main_gradient`:3 段 sky_blue→lavender→peach (135°) → 2 段 sky_blue→lavender (45°)
  - `bg_card_playful`:3 层糖果(厚 2.5dp 描边 + 顶部高光)→ 单层 white→mint→white 90° 渐变
  - `bg_button_primary`:3 层深蓝→深蓝→橘红糖果 → 单层 deep_blue→tangerine 0°
  - `bg_button_unknown`:3 层深红橘糖果 → 2 层 deep_blue→sky_blue 90° + 1dp 深蓝描边
  - `bg_button_secondary`:3 层糖果 → 单层白底 + 2dp 深蓝描边
  - `currentCharacterCard`:cardCornerRadius 28dp → 26dp,cardElevation 8dp → 0dp,padding 22dp → 24dp
  - 主页 padding 20dp/12dp → 24dp/32dp
  - 所有按钮 `MaterialButton` 样式保留 v1.4.3 修的 `UnelevatedButton` 父样式 + `backgroundTint=@null` + `stateListAnimator=@null` + `elevation=0dp`(防点击 bug),但去掉 layer-list 多层糖果渐变,回到 v1.4.0 单层 shape background
- 🍔 **右上角汉堡按钮改重设计** — "融入背景底色 + 缩小 + 与 topLabel 同高"(用户反馈)
  - 56×56dp 白底圆圈 + elevation=6dp 阴影 → 40dp 高 + 完全透明背景 + elevation=0dp + ripple
  - 与左上 `topLabel` 高度一致(40dp),无视觉重量
- 🔙 **"← 返回"按钮同样融入背景** — 48dp 高 + 白底 + elevation=6dp → 40dp 高 + 透明 + elevation=0dp
  - `refreshTopLabel()` 让位 margin 从 140dp → 110dp(按钮缩了)

### 修复
- 🧭 **返回栈清理** — 源页内 chip 主动跳转回主页后,主页不再显示"← 返回"按钮
  - `ProgressActivity.jumpBackToHome()` / `CharacterLibraryActivity.jumpBackToHome()` 新增 `EXTRA_CLEAR_SOURCE=true` 标记
  - `MainActivity.pageResultLauncher` 收到后 `jumpSource = null`,刷新 topLabel marginStart 为 0
  - 工具栏箭头 `finish()` 路径不变(仍显示"← 返回"),用户可一键回溯
- 行为对齐:
  - 主页 + `jumpSource` != null + 系统返回键 → finish 主页回到源页(单步返回,不弹退出)
  - 主页 + `jumpSource` == null + 系统返回键 → 弹退出确认
  - 抽屉打开 + 系统返回键 → 关闭抽屉

### 兼容
- `versionCode 9 → 10`,`versionName 1.4.3 → 1.4.4`
- `EXTRA_CLEAR_SOURCE` 是 result intent 新增 extra,旧发送方不传时默认 false,行为不变
- `OnBackPressedCallback` 在 onCreate 中 addCallback,与 super.onCreate() 不冲突
- 数据层完全兼容 v1.4.3,无任何数据迁移
- `drawerBackButton` 是新 view,旧 binding 不存在,需重新编译

## [1.4.2] - 2026-10-03

本版本针对 v1.4.1 的 4 项遗留 UX 问题逐项处理 + 8 张文生图提示词。

### 新增
- ✂️ **删主页头部 chrome** — 删 `titleText`("🌈 识字小帮手")+ `subtitle`("一起开启有趣的识字冒险！")
  - `englishSubModeGroup` marginTop 6→0dp,卡片首屏即可见完整:汉字 + 拼音 + 难度 + 词组 + 例句 + 4 emoji 按钮
  - 顶部只剩右上角汉堡按钮(≡),空白处变清爽
- 🎨 **抽屉全面重设计** — 不再是 "chip + 3 个灰按钮"
  - 顶部 greeting 卡:随机动物 emoji + "你好呀!" 问候 + 副标题,3D 糖果风
  - "🌍 学习设置" 合并卡:语言 + 难度 chip 同卡,减少视觉割裂
  - 3 张大入口卡(学习进度 / 字库浏览 / 更多设置),每张含:圆形图标底 + 标题 + 描述 + ›
    蓝紫渐变 / 粉橘渐变 / 薄荷渐变 区分功能
  - 新增 5 个 drawable:`bg_drawer_avatar`、`bg_drawer_nav_icon`、`bg_drawer_nav_{blue,pink,mint}`
- 🔙 **跳转源页"← 返回"按钮** — 字库 / 进度点击跳转后,主页顶部左侧显示"← 返回进度" 或 "← 返回字库"
  - ProgressActivity / CharacterLibraryActivity `setResult` 时新增 `EXTRA_SOURCE_PAGE` 标记源页
  - MainActivity 收到后保存 `jumpSource`,显示按钮
  - 点击重启动源 Activity + 关闭主页(不会因 stack 空而退出 app)
  - 正常启动时不显示按钮,不占位
- 🗣️ **中文整段正常语速朗读**
  - `DEFAULT_SPEECH_RATE` 0.7f → 1.0f(用户反馈"词组例句读得慢、卡")
  - `speakPhraseCharByChar` 改为薄包装,内部直接走 `speak()` 整段朗读
  - `speakCurrentItem` 中文字不再 append 拼音(否则 "日 rì" 被读两遍),拼音仅作为卡片下方的 visual hint

### 文生图提示词(交付)
- 2 张开机图英文 prompt,用于 splash 真实图片上线:
  - **Prompt 1 — 故事书主题**:3D 卡通熊猫坐在大打开的故事书上,周围漂浮认 / 字 / 大 / 家 立体字块
  - **Prompt 2 — 字母动物主题**:3D 兔子戴巫师帽挥出金色闪光,周围 A B C 字母块 + 天 字块
  - 详见会话上下文(本次对话顶部)

### 兼容
- `versionCode 7 → 8`,`versionName 1.4.1 → 1.4.2`
- `EXTRA_SOURCE_PAGE` 是 result intent 新增 extra,旧接收方忽略无副作用
- 数据层完全兼容 v1.4.1,无任何数据迁移

## [1.4.3] - 2026-10-04

本版本针对 v1.4.2 之后的 8 项用户反馈继续打磨 UI 细节、修复 MaterialButton 关键 bug、补齐缺失的英文功能。

### 新增
- 🇨🇳🇬🇧 **主页顶部语言 · 难度 标签** — 替换 v1.4.2 英文 mode 时压在卡片上方的"字母/单词"子模式 chip
  - `topLabel` TextView 位于 ConstraintLayout 左上角,显示 `🇨🇳 中文 · 难度 简单` / `🇬🇧 ENGLISH · 难度 简单`
  - 中英文 mode 卡片位置完全一致(因为子模式 chip 不再顶在卡片上方)
  - `refreshTopLabel()` 在语言 / 难度切换、`jumpSource` 变化时刷新文本 + 动态 marginStart(避免和"← 返回"按钮重叠)
- 📚 **抽屉"子模式"chip 组** — 英文 mode 子模式 chip 从主页搬入抽屉"学习设置"卡
  - 3 组 chip(语言 / 难度 / 子模式)用同一个 `Widget.StudyWord.DifficultyChip`,大小完全一致 40dp 高
  - 英文 mode 才显示子模式;中文 mode 自动隐藏(`englishSubModeLabel.visibility = GONE`)
  - 抽屉顶部 greeting 卡高度自适应,3 组 chip 不再溢出
- 🎯 **字库英文难度过滤** — 字库总览英文 mode 现在按难度过滤 word(letter 不过滤)
  - `difficultyFilterGroup` 在英文 mode 始终可见,不再仅显示"全部难度"
  - 新增 `matchesDifficultyEnglish(item)`:letter 始终显示,word 按 `DifficultyFilter` 过滤
  - "困难"只剩字母 + 困难单词(nose / mouth / hair / day / night / happy / love / red / blue / green)
  - "简单"只剩字母 + 简单单词(cat / dog / apple / ball / sun / moon / star / fish / bird / milk)
- 🎬 **游戏 0.7 秒延迟 + 中文只读一遍汉字**
  - `showCurrentQuestion()` 中 LISTEN / LISTEN_LETTER / LISTEN_WORD 模式:
    `binding.root.postDelayed({ speakCurrentPrompt() }, 700L)` — 给孩子一点准备时间再发音
  - `speakCurrentPrompt()` 中文分支只读 `q.correct.character.hanzi`,不再 append 拼音
    (v1.4.2 之前 "日 rì" 被 TTS 读两遍,体验不好)
  - 英文 letter / word 走 `TtsManager.speakEnglish`,语速 1.0f
- 🎨 **3D 糖果按钮** — 主页 / 设置 / 游戏页所有按钮统一升级为深色渐变 + 圆角 24dp
  - `bg_button_primary`:`deep_blue → deep_blue → tangerine` 顶到底渐变,白字清晰
  - `bg_button_unknown`:`#A8341F → tangerine → tangerine` 深红橘,白字清晰
  - 去掉 v1.4.1 的天空蓝中段(白字看不清)
  - `bg_button_secondary` 保留浅薄荷绿底 + 深蓝字
- ✨ **整体 UI 提升** — 字号 / 间距 / 圆角统一梳理
  - 字卡 padding 24dp,emoji 与字之间 margin 12dp,卡圆角 28dp
  - 抽屉 greeting 卡 + 3 张入口卡 圆角统一 28dp
  - 主页 4 emoji 按钮圆角 24dp,与卡片风格一致

### 修复
- 🔧 **MaterialButton 点击失效**(v1.4.1 → v1.4.2 遗留严重 bug)
  - 现象:主页 😊/😢/➡️/🎮 4 个按钮、设置中心"导出学习进度"/"重置中文"/"重置英文" 3 个按钮显示正常但点击没反应
  - 根因:`Widget.MaterialComponents.Button.TextButton` 父样式 + `app:backgroundTint="@null"` 时,
    内部 `MaterialShapeDrawable` 拦截触摸事件,logcat 报 `InputTransport detect abnormal consume, count is 0`
  - 修复:按钮样式改用 `Widget.MaterialComponents.Button.UnelevatedButton` 父样式,
    同时显式声明 `backgroundTint=@null` / `android:backgroundTint=@null` / `stateListAnimator=@null` / `elevation=0dp`,
    才能让 layer-list 背景生效并正常接收点击
  - 影响:导出 CSV、重置进度、主页"换一个" / "进入游戏" 等按钮从 v1.4.3 起可点击
- 🔙 **返回按钮布局** — 字库 / 进度跳转卡片后,主页左上角"← 返回"按钮与 topLabel 重叠
  - `refreshTopLabel()` 根据 `jumpSource` 动态设置 `marginStart`:
    `if (jumpSource == null) 0 else dp(140)`(140dp 留给返回按钮)
- 🚪 **返回按钮导致退出 app** — 主页 finish() 后源页左上角箭头 finish() 会直接退出 app
  - `setupBackJumpButton()` 去掉 `finish()`,主页留在背景
  - 任务栈:`[主页, 源页]`,源页左上箭头 → 回到主页,主页 onResume → 仍可见,back 按钮仍可再跳

### 文生图提示词(交付)
- 2 张开机图 **中文详细版** prompt,用于 splash 真实图片上线:
  - **Prompt 1 — 故事书主题**:3D 卡通熊猫坐在大打开的故事书上,周围漂浮 认 / 字 / 大 / 家 立体字块
  - **Prompt 2 — 字母动物主题**:3D 兔子戴巫师帽挥出金色闪光,周围 A B C 字母块 + 天 字块
  - 详见会话上下文(本次对话顶部)

### 兼容
- `versionCode 8 → 9`,`versionName 1.4.2 → 1.4.3`
- `jumpSource` / `englishSubModeLabel` 是 MainActivity 新增字段,无外部依赖
- `CharacterLibraryActivity.matchesDifficultyEnglish` 新增私有方法,无外部 API 变化
- `GameActivity.speakCurrentPrompt` 行为变更(0.7s 延迟 + 中文只读汉字),无外部依赖
- 数据层完全兼容 v1.4.2,无任何数据迁移

## [1.4.1] - 2026-10-03

本版本是一次"体验打磨"补丁,针对 v1.4.0 的 8 个 UX 问题逐项处理。

### 修复
- 🐛 **例句英文 + 中文 TTS 重叠** —— `TtsManager.speakSequential` 内部每段都走 `QUEUE_FLUSH`,导致每一段都打断上一段,英文读一半中文就插进来,两段叠在一起
  - 方案:抽出 `speakInternal(text, isEnglish, utteranceId, flush)`,`speakSequential` 只在第一项用 `QUEUE_FLUSH` 清空旧队列,余项用 `QUEUE_ADD` 串接,引擎按顺序朗读
  - 默认段间隔 `delayMs` 由 200ms 提到 500ms,贴合孩子的反应节奏
- 🐛 **灰色按钮中间有一条白条** —— 抽屉里 3 个 SecondaryButton 与主页 "➡️" 显示为"灰色底 + 白色中条"
  - 原因:`Widget.MaterialComponents.Button` 父样式默认有 `app:backgroundTint = colorPrimary`,即使用了 `bg_button_secondary.xml` 也会被父样式 tint 覆盖
  - 方案:主题里 `PrimaryButton / SecondaryButton / UnknownButton / OptionButton` 全部改用 `Widget.MaterialComponents.Button.UnelevatedButton` 父样式,无默认 tint,drawable 完全控制外观
- 🐛 **字库 / 进度页点英文 id 跳转静默失败** —— 点 letter "G" 后跳回主页还是上一个字,点英文单词也一样
  - 原因:`loadItemById` 只在当前子模式的 pool 中查找,英文 letter id 在 words pool 中找不到
  - 方案:`EnglishRepository.WORD_ID_OFFSET = 1000`,`id < 1000` 视为 letter 自动切到 `LETTERS` 子模式 + 字母难度,`id >= 1000` 视为 word 自动切到 `WORDS` + 当前难度

### 新增
- 🔠 **字母行 Aa 一行展示** —— 之前 letter 卡片是双行大写 + 小写,占纵向 ~120dp
  - 现在大写 80sp 在左、小写 44sp 在右(baseline 对齐),整行约 80dp,首屏可见
  - 点击整行(`letterContainer`)→ 只朗读字母名,不会顺带读 example word 与中文意思
- 🏷️ **英文 word 卡片显示中文意思** —— `wordMeaning` TextView,字号 24sp,与字母卡的 "example word 中文" 视觉一致
- 🖐️ **字库 / 进度页加边滑返回** —— 左边缘 24dp 区域内右滑 → `finish()` 关闭 Activity
  - `GestureDetector.SimpleOnGestureListener.onFling` 监听,`SWIPE_DISTANCE_THRESHOLD=120dp`,`SWIPE_VELOCITY_THRESHOLD=400`
  - 配套保留顶部返回按钮(已在 v1.3.x 加),方便喜欢点按的家长
- 🎨 **3D 糖果卡通风全面重设计** —— 参考 Khan Academy Kids / Duolingo ABC,告别平面感
  - 背景:`bg_main_gradient` 三段渐变,天蓝 → 淡紫 → 蜜桃(135°),全屏柔和
  - 字卡:`bg_card_playful` = 2.5dp 深蓝描边 + 白→薄荷→粉渐变 + 顶部高光,看起来像会发光的糖果
  - 主按钮:`bg_button_primary` = 蓝→深蓝→橘渐变 + 顶部高光
  - 次按钮:`bg_button_secondary` = 深蓝描边 + 白→薄荷渐变
  - 警告按钮:`bg_button_unknown` = 橙→橘→红渐变
  - 所有 `MaterialCardView` 升级 `cardElevation 0 → 6~8dp`、`cornerRadius 26 → 28dp`
  - 右上角汉堡按钮单独加 `app:elevation="6dp"`,立体跳出
- 📐 **主页头部压缩** —— 标题 28sp → 22sp,subtitle 与标题 baseline 对齐合并到一行
  - `paddingTop` 32dp → 16dp,englishSubModeGroup `marginTop` 12dp → 6dp
  - 720p 屏幕上卡片首屏可见 ~75%,不再需要上滑才能看到字卡

### 兼容
- `versionCode 6 → 7`,`versionName 1.4.0 → 1.4.1`
- 数据层完全兼容 v1.4.0,无任何数据迁移
- `english_sets.json` / `characters.json` 内容未改

## [1.4.0] - 2026-10-03

### 新增
- 📂 **右滑抽屉** — 主页右上角新增汉堡菜单,点击打开右侧 DrawerLayout(280dp 宽)
  - 抽屉内容:语言切换 chip、难度切换 chip、3 个导航按钮(学习进度 / 字库 / 更多设置)
  - 主页一屏完整可见卡片 + 4 个底部 emoji 按钮,顶部不再被多个 chip 占据
- 🗣️ **点击卡片发音** — 删除原"听一听"按钮,改为点击卡片任意位置触发 `speakCurrentItem()`
  - 中文:整字/词组/例句按上下文路由到 char-by-char 队列发音
  - 英文 letter / word / 例句分别按规则串发(详见下方)
- ✨ **底部 4 个 emoji 按钮** — 认识(😊)、不认识(😢)、游戏(🎮)、下一个(➡️),每个按钮点击后有中文 Toast 提示
  - 认识啦! / 没关系,下次记住! / 来玩游戏吧! / 下一个!
- 🌐 **英语子模式细分** — 英文 mode 顶部加 chip `[字母][单词]`,单词 mode 加难度 chip `[简单][中等][困难]`
  - 字母模式用 `EnglishRepository.letters()`(26 个均匀分布)
  - 单词模式按当前难度从 `byCategoryAndDifficulty(category, difficulty)` 取子集
- 🔊 **英文复合串发音**
  - 点 letter:`uppercase`(英文) + `exampleWord`(英文) + `exampleWordChinese`(中文)三段串发,200ms 间隔
  - 点 word:`word`(英文) + `chineseMeaning`(中文)两段串发
  - 点 word 例句:`exampleSentence`(英文) + `exampleSentenceTranslation`(中文)两段串发
- 📊 **进度页可跳转** — ProgressActivity 上"认识汉字 / 待巩固汉字 / 英文 known / 英文 review"四个 chip 全部可点
  - 点击 → `setResult(EXTRA_SELECTED_ID, EXTRA_SELECTED_LANG)` + `finish()`,MainActivity 通过 `registerForActivityResult` 接住并 `loadItemById`
  - 跳转过程中自动切换语言(中 → 英 / 英 → 中)
- 📚 **字库页可跳转** — CharacterLibraryActivity 短按 item → 跳回主页对应卡片;长按 item → 弹状态对话框(原行为)
- 🎮 **游戏改进**
  - 字母选项 split view:大写 60sp 在左、小写 36sp 在右(SpannableString AbsoluteSizeSpan,中间 4 空格拉开)
  - 长单词自动缩字:`setAutoSizeTextTypeUniformWithConfiguration(14, 44, 1, SP)`,溢出按钮内不换行
  - 答错反馈三件套:背景变红 + 按钮左右抖动 380ms (ObjectAnimator translationX) + 短震动 80ms (Vibrator / VibratorManager + VibrationEffect)
- 🌅 **开机启动页** — SplashActivity 5 张 splash_*.xml 自动翻页
  - 每张停留 600ms,共 3 秒后启动 MainActivity
  - drawable/splash_1..5.xml 当前是占位纯色(用户将提供 PNG 替换,无需改代码)
  - AndroidManifest LAUNCHER intent-filter 已从 MainActivity 移到 SplashActivity
  - 新增 `VIBRATE` 权限供游戏答错震动使用

### 修复
- 🐛 **中文多音字 TTS bug** — 词组 / 例句第二遍读时把"日子"的"日"读错音
  - 原因:之前的 `toPinyin("日") = "rì"` 加 `_pinyin` 拼接后,引擎在 `QUEUE_FLUSH` 重发时偶然解析为别的音
  - 方案:`TtsManager.speakPhraseCharByChar(text)` 按字拆,逐字 `QUEUE_ADD`,引擎走默认发音(对常用字最准)
  - 同步加 `PinyinConverter` overrides 表修正已知坏 case:`了→le`、`地→dì`、`么→me`、`还→hái`、`便→pián` 等
- 🐛 **跳转回主页失效 bug** — 点 ProgressActivity / CharacterLibraryActivity 上的字 / 字母 / 单词,跳回主页后显示的不是被点击的那一项
  - 原因:`MainActivity.onResume` 无条件调用 `loadNextItem()`,把 `loadItemById` 设置的 currentItem 替换成队列的下一项
  - 修复:仅当 `currentItem == null`(进程被回收 / 首次启动)时才在 onResume 里 `rebuildQueue + loadNextItem`
- 英文单词发音增加 200ms 间隔,避免连读成一团

### 数据

- 📚 **词组 / 例句覆盖 56 → 100 个字**（前 100 个简单字全覆盖）
  - 此前 `generate_words.py` 有两个根因，导致"前 100 个字"里只有 56 个真正有内容，
    另外 **44 个被写成了 `{"char":"X"}` 空对象**（既无词组也无例句，还把原字符串格式改写了一遍）：
    1. 它按 **下标切片**（`--limit 100` 只处理 easy 的前 100 项），而字典命中的字散落在别处；
    2. 对字典里**没有**的字也照样改写成对象形式 → 产生空对象。
  - 修复：`generate_words.py` 改为**按字典命中**处理全部条目，字典未覆盖的字**原样保留**
    （实测：补全 35 项 / 跳过 56 项 / 保持原样 1109 项，不再产生空对象）。
  - 新增 `scripts/fill_easy_words.py` 补齐这 44 个字：**拼音由 pypinyin 依据上下文生成**
    （不手写，避免出错）。效果示例：
    `萝卜 → luó bo`（轻声正确）、`占卜 → zhān bǔ`（多音字按词区分）、
    `与人为善 → yǔ rén wéi shàn`（「为」取 wéi）。
  - 现在前 100 个简单字共有 **293 个词组 / 100 条例句**，全部带非空拼音。
- 🔒 **新增 `scripts/verify_character_sets.py` + CI 步骤**，守住字库的数据契约：
  - **顺序指纹**：`character_sets.json` 的 3000 字顺序一旦变化就直接失败
    —— 因为 v1.4.4 升级用户的进度要靠「位置 id → 汉字」翻译一次，
    顺序一变就会静默错位到别的字上（已用故障注入验证：对调两字后脚本退出码 1）。
  - 数量（1200/1000/800）、无重复字、无空对象、词组/例句拼音非空、前 100 字覆盖。
  - 本次改动经校验：**3000 字顺序完全未变**（指纹一致），仅 44 条被补全。
- 26 字母每个新增 `exampleWordChinese`(Apple→苹果、Bus→公交车 等)
- 30 单词每个新增 `difficulty`(EASY / MEDIUM / HARD,各 10 个)与 `exampleSentenceTranslation`(中文翻译)
- `EnglishRepository.byCategoryAndDifficulty(category, difficulty)` API 新增

### 兼容性
- `versionCode 5 → 6`,`versionName 1.3.0 → 1.4.0`
- ProgressStore / SharedPreferences 完全兼容 v1.3.0 数据,新增字段不影响历史记录
- 字库 / 进度页 RecyclerView adapter 增加可选 `onItemLongClicked` 参数(v1.3.0 的 `AllCharactersAdapter` / `AllEnglishItemsAdapter` 调用代码不受影响)

## [1.3.0] - 2026-10-03

### 新增
- 🌐 **中英双语切换** —— 主页顶部新增"中文 / ENGLISH"chip group,持久化到 SharedPreferences
  - 切换语种后立即清空当前学习项,按新语种重新出题池
  - 英文模式自动隐藏"难度选择"(英文只有 LETTERS / WORDS 两类,无需用户选择)
  - 字卡、词组、例句、TTS 朗读全部按语种路由
- 🔤 **英文字母 + 单词卡片**
  - 大写 + 小写双行(字母模式:"Aa" 大字 + 小字)
  - 中文 / 英文 word 模式沿用 60sp 大字
  - 音标 / 拼音共用 `currentPinyin` TextView
  - 英文 word 例句下方追加中文释义(如 "I love my cat. — 猫")
- 🎮 **英文题型**
  - LISTEN_LETTER:听字母名,从 4 个 Aa 字母卡中选出听到的字母
  - LISTEN_WORD:听单词,从 4 个英文单词中选出听到的单词
  - 题目 / 选项区自动按语种切换,英文题朗读走 `TtsManager.speakEnglish(en-US)`
- 📚 **字库英文页**
  - 字库总览加语言切换 chip
  - 英文模式用新 layout `item_english_status.xml`(Aa + 音标 + 状态)
  - 已学 / 待巩固 / 未学习 三态在英文维度独立计算
- 📊 **进度页加英文统计**
  - 总数(26 letters · 30 words)/ Known / Review / Mastery
  - Known / Review chip 列表,letter 显示 "Aa",word 显示 "cat·猫"
- ⚙️ **设置页扩展**
  - 字库总览卡片:中英数字分开显示
  - 重置按钮拆为"重置中文进度" / "Reset English Progress",互不影响
  - CSV 导出文件改名为 `StudyWord_yyyyMMdd.csv`,内含 3 段:中文 / English letters / English words

### 数据模型
- 新增 `model/StudyMode.kt`(CHINESE / ENGLISH)
- 新增 `model/StudyItem.kt` —— sealed interface,把 LearningCharacter / EnglishLetter / EnglishWord 统一为同一形态
  - 三个实现:`ChineseStudyItem` / `EnglishLetterItem` / `EnglishWordItem`
  - UI 层(主页 / 游戏 / 字库 / 进度)全部面向 StudyItem 编程,不再关心语种
- `game/GameMode` 加 `language: StudyMode` 字段,新增 `LISTEN_LETTER` / `LISTEN_WORD` 两个 enum
- `game/GameQuestion` 改为 `correct: StudyItem / options: List<StudyItem>`
- `game/GameRound.difficulty` 改为可空 `Difficulty?`(英文模式不需要)
- `data/EnglishRepository` 加 `findByLetterById` / `findByWordById` 助手,单词 id 偏移 1000 避免与字母冲突
- `data/ProgressStore` 加 `loadLanguage` / `saveLanguage`(中文 / 英文 维度独立持久化)

### 改进
- 主页顶部三个 chip group 现在线性排布:语言 → 难度 → 字卡
- 主页"🎮 玩游戏"按钮按当前语种自动选择默认游戏模式
- 主页鼓励语新增 "Try again!"(英文模式鼓励文案)
- 字卡 emoji 在英文 word 模式显示 🔤(中文模式仍随机动物头像)
- 词组 chip 在英文 letter 模式显示为"🌟 示例词",例句显示为"📖 Example"
- GameActivity 题目区"提示文字"在英文模式动态切换英文提示语

### 兼容
- ProgressStore 复用 v1.2.1 的 SharedPreferences,新增 KEY(known_english_ids / unknown_english_ids / current_language)
- 现有 v1.2.1 中文用户数据(known_ids / unknown_ids / history_entries)完全不受影响
- v1.3.0 首次启动默认 current_language = CHINESE,沿用 v1.2.1 主页布局

## [1.2.0] - 2026-10-03

### 新增
- 🧩 **字卡下方新增"词组"区** —— 把"单字"扩展到"字 + 词",帮助 3-5 岁孩子从"认字"过渡到"组词"
  - 每个常用字配 2~3 个词组(如 "天" -> "天空 / 今天 / 天气")
  - 词组以可点击 chip 形式排列,点击即触发 TTS 朗读(汉字 + 拼音)
  - 数据缺失的字(老字库中暂未手写词组的字)整块区域自动隐藏,不显示空标签
- 📖 **字卡下方新增"例句"区** —— 让字进入"语境",帮助理解字义
  - 每个常用字配 1 条例句(如 "天" -> "今天天气真好。")
  - 例句整行可点击,点击即触发 TTS 朗读(汉字 + 拼音)
- 🗂️ **`scripts/generate_words.py`** —— 一键为字库前 N 个常用字注入词组 + 例句数据
  - 内置 100 个最常见字的 curated 字典,默认覆盖字库前 100 项
  - 支持 `--limit`、`--dry-run` 参数,可重复执行(已转换的条目会自动跳过)

### 数据模型
- 新增 `model/WordEntry.kt`(词组 + 拼音)
- 新增 `model/ExampleSentence.kt`(例句 + 拼音)
- `LearningCharacter` 新增 `words: List<WordEntry>` 与 `examples: List<ExampleSentence>` 字段,**默认值空列表,保证向后兼容**
- `CharacterRepository` 支持两种 JSON 格式并存:旧格式 `"天"` 与新格式 `{"char":"天","words":[…],"examples":[…]}` 都能解析,旧字库无需迁移

### 改进
- 字卡新增 `Widget.StudyWord.WordChip` 样式,沿用 Material chip 风格(浅蓝底 + 深蓝字 + 圆角)
- 字卡内布局紧凑化(emoji/字/拼音/难度之间的间距从 12dp 调到 8dp),为新内容腾出空间

## [1.1.1] - 2026-10-03

### 修复
- 🎮 游戏题池改为"已学过的字(认识 ∪ 待巩固) ∩ 当前难度"
  - 修复 v1.1.0 题池使用全部字库的 bug,导致游戏出现孩子完全没见过的字
  - 未学过任何字时显示空状态:"还没有学过的字,先去主页学习几个汉字,再来玩吧!" + "返回主页" 按钮

## [1.1.0] - 2026-10-03

### 新增
- 🎮 **游戏模式(识字闯关)** —— 把"被动认字"变成"主动闯关"
  - **听音找字**:TTS 自动朗读,孩子从 4 个汉字里选出听到的字
  - **看拼音选字**:屏幕显示拼音,孩子从 4 个汉字里选出对应的字
  - 每轮 5 题,答完给出 ⭐⭐⭐ / ⭐⭐ / ⭐ / 无星 四档评级
  - 全屏撒花动画 + 正向反馈文案(不打孩子自尊)
  - 主页新增 "🎮 玩游戏" 入口按钮
- 🔊 **TTS 朗读支持** —— 主页 "🔊 听一听" 按钮触发,孩子主动听读音

### 改进
- 抽取 `util/TtsManager` 工具类,全局单例 TTS 引擎,主页与游戏页共用
- 主页"换一个"按钮与"听一听"合并为一行,减少竖向占用

## [1.0.0] - 2025-10-15

### 新增
- 3000 常用字字库(简单 1200 + 中等 1000 + 困难 800),按笔画由浅入深
- 测验主页:逐字测验 + 撒花动画反馈
- 学习进度统计页(掌握率 + 折线趋势图 + 难度饼图)
- 设置页:字库总览 + CSV 进度导出 + 一键重置
- 本地存储(SharedPreferences),无需联网、无账号、无广告
