# iOS 版本技术方案

> 状态：待评审 ｜ 产出时间：2026-10-06 ｜ 基于 Android v1.6.0（versionCode 12）的实测数据

---

## 一、现状量测（不是估算，是数出来的）

| 包 | 文件 | 代码行 | 依赖 `android.*` |
|---|---|---|---|
| `ui`（主页 / 字库 / 进度 / 设置 / 适配器） | 12 | 3010 | 11 |
| `data`（仓库 / 进度存储 / 档案） | 7 | 814 | 6 |
| **`model`** | 13 | 787 | **0** |
| `game` | 4 | 627 | 1 |
| `util`（CSV / TTS / 拼音） | 3 | 457 | 1 |
| `SplashActivity` | 1 | 40 | 1 |
| **合计** | **40** | **5735** | 20 |

**平台耦合度：**

| | 文件 | 代码行 | 占比 |
|---|---|---|---|
| 纯逻辑（无 `android.*`） | 20 | 1109 | **19%** |
| 依赖 Android | 20 | 4626 | 81% |

**测试：13 个文件 / 1252 行，`android.*` 依赖数 = 0。**
全部是纯 JVM 测试 —— 这是整个项目最值得保护的资产。

### 🎯 关键发现：逻辑层已经是 KMP 就绪状态

对 20 个纯逻辑文件做了 import 扫描，结果是**零个非 Kotlin 标准库依赖**：

```
GameMode.kt            → com.studyword.literacy.model.StudyMode      (项目内部)
GameQuestion.kt        → com.studyword.literacy.model.StudyItem      (项目内部)
GameRound.kt           → com.studyword.literacy.model.Difficulty     (项目内部)
ProgressCsvImporter.kt → com.studyword.literacy.util.CsvFormat       (项目内部)
其余 16 个文件          → 零 import
```

具体证据：

| 文件 | import 数 | 说明 |
|---|---|---|
| `ReviewScheduler.kt` | **0** | 时间以 `now: Long` **参数**传入（`isDue(state, now)` / `onWrong(prev, now)` / `dueKeys(states, now)`），不依赖系统时钟 |
| `PinyinConverter.kt` | **0** | 拼音表以 `table: Map<String,String>?` **参数**注入 |
| `CsvFormat.kt` / `ProgressCsvImporter.kt` | 无 `java.*` | 纯字符串处理，无 `java.io` |
| `ProgressRules.kt` / `ChildProfile.kt` / `LibrarySearch.kt` / … | 0 | 纯计算 |

`System.currentTimeMillis()` 的调用点**全部落在平台层**：
`ProgressStore`（存储）/ `ProfileStore`（存储）/ `MainActivity` / `ProgressActivity` /
`GameActivity` / `ConfettiOverlayView` —— 一个都不在纯逻辑里。

**结论：这 1109 行搬进 `shared/commonMain` 基本不需要改代码**
（只需处理同模块内的 import 路径）。这是本方案风险最低、收益最高的一步。

**资源：**

| 资源 | 体积 | iOS 能否直接复用 |
|---|---|---|
| `character_sets.json` | 550 KB | ✅ 完全一致 |
| `english_sets.json` | 40 KB | ✅ |
| `pinyin_table.json` | 50 KB | ✅ |
| `studyword_kai.ttf` | 1355 KB | ✅ TTF 通用（需 Info.plist 注册） |
| 10 个布局 XML + 29 个 drawable | — | ❌ iOS 用 SwiftUI / Asset Catalog 重写 |

---

## 二、四条路线对比

| | A. KMP 共享逻辑 + SwiftUI | B. 纯 Swift 重写 | C. Compose Multiplatform | D. Flutter / RN |
|---|---|---|---|---|
| 复用已有逻辑 | ✅ 1109 行 | ❌ 全丢 | ✅ | ❌ 全丢 |
| 复用 141 个测试 | ✅ 同源 | ❌ 要重写 | ✅ | ❌ |
| 需重写代码量（iOS） | ~3000 行 UI | ~4700 行 | ~5700 行（**含 Android UI**） | ~5700 行 |
| 对**现有 Android App** 的风险 | 低（仅抽模块） | 无 | **高（要废弃现有 UI）** | **高（整体重写）** |
| iOS 原生体验 | ✅ 最佳 | ✅ 最佳 | ⚠️ 良好但非原生控件 | ⚠️ 需插件桥接 |
| iOS 侧技术风险 | 低 | 低 | 中（CMP iOS 2025-05 才稳定） | 中 |
| 长期维护 | 逻辑一份，UI 两份 | 两份全分叉 | 一份 | 一份 |

**结论：选 A。**

理由：
1. Android App 已经做好且经过大量真机验证，**不能为了 iOS 把它推倒**
2. 逻辑层（进度键 / 迁移 / SRS / CSV / 搜索 / 家长门规则 / 档案编码）是最容易出错、也最需要单一事实源的部分 —— 正好全部落在 1109 行纯逻辑里
3. 141 个测试零 `android.*` 依赖，天然就是 KMP 的 `commonTest`
4. iOS UI 无论如何都要新写，A 相比 B 净省下 1109 行逻辑 + 141 个测试的重写

---

## 三、目标结构

```
StudyWord/
├─ shared/                      KMP 模块（新）
│   ├─ build.gradle.kts
│   └─ src/
│       ├─ commonMain/kotlin/    ← 从 app/model、app/util、app/game 平移
│       │   ├─ model/            ProgressKey / ProgressMapping / ProgressRules
│       │   │                    ReviewScheduler / ChildProfile / StudyItem
│       │   │                    EnglishItem / LibrarySearch / BackPolicy
│       │   │                    StudyMode / LearningCharacter / WordEntry
│       │   ├─ util/             CsvFormat / PinyinConverter
│       │   ├─ game/             GameRound / GameMode / GameQuestion
│       │   └─ platform/         expect：存储 / 资源 / 时间 / 随机
│       ├─ commonTest/kotlin/    ← 现有 13 个测试文件原样搬入（141 个用例）
│       ├─ androidMain/kotlin/   actual：SharedPreferences / AssetManager
│       └─ iosMain/kotlin/       actual：NSUserDefaults / NSBundle
├─ app/                         现有 Android 模块（改动最小，仅改为依赖 shared）
└─ iosApp/                      Xcode 工程 + SwiftUI（新）
    └─ StudyWord/
        ├─ App/                  SplashView / 根导航
        ├─ Features/
        │   ├─ Home/             字卡 + 左右滑动 + 底部 4 按钮
        │   ├─ Library/          搜索 + 筛选 + 网格
        │   ├─ Progress/         统计 + 折线 + 饼图（Swift Charts）
        │   ├─ Settings/         档案 / 导入导出 / 重置 / 家长门
        │   └─ Game/             听音找字 / 看拼音选字
        └─ Resources/            Assets.xcassets / studyword_kai.ttf / *.json
```

---

## 四、可复用 / 必须重写（逐文件）

### ✅ 直接搬进 `commonMain`（1109 行）

```
model/ProgressCsvImporter.kt   149    CSV 导入（RFC4180，跨平台数据交换的关键）
util/PinyinConverter.kt        129    注音/拼音转换
model/ProgressRules.kt         112    掌握度规则
model/ReviewScheduler.kt       111    Leitner 间隔重复（0/1/3/7/16 天）
model/ChildProfile.kt          102    档案 + 编解码 + 规则
model/StudyItem.kt              88    统一条目抽象
util/CsvFormat.kt               74    CSV 生成/解析
model/LibrarySearch.kt          62    搜索匹配（忽略声调）
model/EnglishItem.kt            47
game/GameRound.kt               45    出题与星级
model/BackPolicy.kt             33    返回键策略
game/GameMode.kt                29
data/ProgressMapping.kt         26    旧位置 id → 内容键
model/ProgressKey.kt            25    内容键（跨平台契约）
model/StudyMode.kt              18
model/LearningCharacter.kt      16
game/GameQuestion.kt            14
model/ExampleSentence.kt        12
model/WordEntry.kt              12
ui/CharacterResult.kt            5
```

### ❌ 必须在 iOS 重写（4626 行，按真实行数）

| Android 文件 | 行数 | iOS 对应 |
|---|---|---|
| `ui/MainActivity` | **1184** | `HomeView` + 字卡 + 滑动翻卡 + 抽屉 |
| `game/GameActivity` | 539 | `GameView` |
| `ui/SettingsActivity` | 408 | `SettingsView` + `.fileImporter/.fileExporter` |
| `ui/ProgressActivity` | 397 | `ProgressView` + Swift Charts |
| `ui/CharacterLibraryActivity` | 387 | `LibraryView` + `LazyVGrid` |
| `ui/ConfettiOverlayView` | 261 | 撒花动画（SwiftUI 动画重写） |
| `util/TtsManager` | 254 | `SpeechService`（AVSpeechSynthesizer） |
| `data/ProgressStore` | 227 | 走 shared + `NSUserDefaults` actual |
| `data/EnglishRepository` | 194 | 读同一份 JSON（或走 shared） |
| `data/CharacterRepository` | 148 | 同上 |
| `data/ProfileStore` | 108 | 走 shared + `NSUserDefaults` actual |
| `ui/ParentGate` | 84 | `ParentGateView`（复用同一套出题逻辑） |
| `ui/SwipeBackDelegate` | 67 | SwiftUI 侧滑返回（系统自带，可省） |
| `ui/AllEnglishItemsAdapter` | 66 | `LazyVGrid` 局部视图 |
| `ui/AllCharactersAdapter` | 62 | 同上 |
| `ui/StudyViewModels` | 60 | 状态管理需重写（Android 用 ViewModel + StateFlow） |
| `data/PinyinTable` | 56 | `AssetReader` actual + 换掉 `org.json` |
| `data/ProgressMigrator` | 55 | 一次性迁移逻辑（iOS 首版可省，但保险起见保留） |
| `literacy/SplashActivity` | 40 | `SplashView`（同一张图） |
| `ui/ProgressMarkerView` | 29 | Swift Charts 内置标记 |
| **合计** | **4626** | |

---

## 五、需要 `expect`/`actual` 的平台边界

这是 KMP 落地的**真正工作量所在**，必须一开始就划清：

| 能力 | `commonMain` (expect) | `androidMain` (actual) | `iosMain` (actual) |
|---|---|---|---|
| 键值存储 | `Settings { fun getString/setString }` | `SharedPreferences` | `NSUserDefaults` |
| 读内置 JSON | `AssetReader { fun read(name): String }` | `AssetManager` | `NSBundle.pathForResource` |
| 发音 | `Speaker { fun speak(text, lang) }` | `TextToSpeech` | `AVSpeechSynthesizer` |
| JSON 解析 | `parseJsonMap(text): Map<String,String>` | `org.json.JSONObject` | `kotlinx.serialization` 或 `NSJSONSerialization` |

> ✅ **好消息**：**不需要 `Clock` 抽象**。
> 原以为 `ReviewScheduler` 依赖系统时钟，实测它是 **0 import**、
> 时间全部以 `now: Long` 参数传入。间隔重复的测试本来就注入时间，天然可跨平台。
>
> 真正要处理的只有两处：
> - `ProgressStore` / `ProfileStore` 直接调 `SharedPreferences` + `System.currentTimeMillis()`
>   → 抽出 `Settings` 接口（含 `nowMillis()`）
> - `PinyinTable.load(context)` 读 assets，且用了 **Android 专有的 `org.json`**
>   → iOS 侧要换成 `NSJSONSerialization` 或引入 `kotlinx.serialization`
>     （后者更优：一次编写、两端通用，`pinyin_table.json` 是纯 `Map<String,String>`）

---

## 六、iOS 侧技术选型

| 维度 | 选型 | 理由 |
|---|---|---|
| UI 框架 | **SwiftUI**（iOS 16+） | 声明式，与项目"卡片式"界面契合 |
| 最低版本 | **iOS 16.0** | 可用 Swift Charts；Kotlin/Native 默认最低 15.0 |
| 图表 | **Swift Charts** | 替代 MPAndroidChart（掌握率折线 + 难度饼图） |
| 字体 | Info.plist `UIAppFonts` 注册 `studyword_kai.ttf` | 与应用内字形一致 |
| 发音 | `AVSpeechSynthesizer`，`zh-CN` / `en-US` | 替代 Android TTS |
| 导入导出 | `.fileImporter` / `.fileExporter` | 替代 SAF 的 CreateDocument/OpenDocument |
| 触觉反馈 | `UIImpactFeedbackGenerator` | 替代 `Vibrator` |
| 家长门 | 沿用两位数加法（同一套出题逻辑） | 逻辑复用，无需 Screen Time API |
| 左右滑动翻卡 | `DragGesture` + 阈值判定 | 与 Android 同样的"横向位移 > 纵向 1.5 倍"规则 |

---

## 七、分阶段计划与**可验证性**

这是本方案最关键的一节 —— 明确每阶段**在哪台机器上能验证到什么程度**。

| 阶段 | 内容 | 在哪验证 | 能验证到什么 |
|---|---|---|---|
| **P0** | 抽出 `shared` 模块，Android 改为依赖它 | **本机 Windows** | ✅ 141 个测试全跑在 `commonTest`；Android 回归（真机全流程） |
| **P1** | `shared` 增加 `iosArm64` / `iosSimulatorArm64` target | **本机 Windows** | ✅ 可产出 Apple target 的 `.klib`（无限 cinterop 时）；❌ 不能链接 framework |
| **P1'** | `iosMain` 的 actual 实现 | 需 macOS | ⚠️ Windows 只能编译到 klib；**链接与运行必须 macOS** |
| **P2** | Xcode 工程 + SwiftUI 外壳 + 开机页 + 主页 | 需 macOS 或 CI | ⚠️ 本机只能做代码审查 |
| **P3** | 字库 / 进度 / 设置 / 游戏 | 需 macOS + 模拟器 | ⚠️ 同上 |
| **P4** | 字体 / TTS / 家长门 / 滑动翻卡 打磨 | 需真机 | ⚠️ 同上 |
| **P5** | 真机分发包 / App Store | 需 Apple 开发者账号 | — |

### 官方依据

[Kotlin/Native 支持的目标和主机](https://kotlinlang.org/docs/native-target-support.html) 明确写道：

> **Building final binaries for Apple targets on Linux and Windows is also not possible.**

| Host | Building final binaries | Producing `.klib` |
|---|---|---|
| macOS (ARM64 / x86_64) | 任意目标 | 任意目标 |
| **Windows (MinGW) x86_64** | **除 Apple 目标外均可** | **Apple 目标仅当无 cinterop 依赖时** |

所以：
- ❌ 在 Windows 上**无法**构建 iOS framework、无法运行 iOS App
- ✅ 但共享模块是纯 Kotlin（无 cinterop），**能在 Windows 上编译出 iOS 的 klib** —— 即"共享代码能否为 iOS 编译"这一层是**可以自动验证**的

### 建议：把 iOS 编译纳入 CI

项目已有 `.github/workflows/ci.yml`。建议增加一个 `macos-latest` job：

```yaml
ios-compile:
  runs-on: macos-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { distribution: temurin, java-version: '21' }
    - name: 编译 shared 的 iOS framework
      run: ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
    - name: 编译 iOS App
      run: |
        cd iosApp
        xcodebuild -project StudyWord.xcodeproj \
          -scheme StudyWord \
          -destination 'generic/platform=iOS Simulator' \
          build
```

这样即使没有本地 Mac，**每次提交都能自动检查"iOS 侧是否还能编译"**，
把"写完不知道能不能跑"的风险压到最低。

---

## 八、需要你拍板的问题

| # | 问题 | 影响 |
|---|---|---|
| 1 | **你有 Mac 吗？** 有的话 P2 之后能在本地迭代；没有则只能靠 CI 编译检查 + 你在 Mac 上偶尔验证 | 决定 P2 之后的节奏 |
| 2 | 最低支持 **iOS 16.0** 可以吗？ | 决定能否用 Swift Charts；覆盖约 95% 在用设备 |
| 3 | 要不要 **iPad 适配**？ | 现在 Android 只有 `layout-land` 一份横屏；iPad 要额外的多栏布局 |
| 4 | 是否需要 **iCloud 跨设备同步**？ | 目前 CSV 是唯一通道；iCloud 是独立工作量 |
| 5 | 有 **Apple 开发者账号**（$99/年）吗？ | 没有也能用模拟器开发，但装真机/上架需要 |
| 6 | iOS 版是**重新起一个仓库**还是在本仓库加 `iosApp/` 目录？ | 建议同仓库（共享模块必须同仓库） |

---

## 九、风险清单

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| 1 | **数据兼容**：进度键格式一旦两边不一致，CSV 跨平台导入就会出错 | 高 | `ProgressKey` / `ProgressCsvImporter` 放进 shared 成为**跨平台契约**，并在 shared 里加"契约测试"（固定样例 JSON ↔ 期望结果） |
| 2 | **`org.json` 不可移植**：`PinyinTable` 用了 Android 专有的 `org.json.JSONObject` | 中 | 引入 `kotlinx.serialization` 替代（一次编写两端通用）；数据是纯 `Map<String,String>`，改造很小 |
| 3 | **TTS 音色差异**：iOS 中文语音与 Android 完全不同，语速/停顿需重调 | 中 | P4 单独一轮真机调参；`Speaker` 接口暴露语速参数 |
| 4 | **字体渲染差异**：同一 TTF 在 iOS 上的行高/字重表现不同，卡片排版会变 | 中 | 楷体字卡的行高写成平台可配置 |
| 5 | **图表重写**：MPAndroidChart 的掌握率折线/难度饼图要在 Swift Charts 复刻 | 低 | 数据由 shared 提供，iOS 只做渲染 |
| 6 | **UI 分叉**：共享的只有逻辑，两套 UI 会逐渐不一致 | 中 | 建立"设计 token 表"（颜色/字号/间距）作为两边共同依据；本文档第五节 |
| 7 | **本机无法验证 iOS** | 高 | CI macOS job（见第七节）；P0/P1 尽量在 Windows 上做完并验证 |

---

## 十、工作量粗估

| 阶段 | 内容 | 依赖 | 粗估 |
|---|---|---|---|
| P0 | 抽 shared + Android 改造 + 测试迁移 | Windows 可完成 | 1~2 个工作轮 |
| P1 | iOS target + expect/actual | Windows 可完成（klib 级） | 1 个工作轮 |
| P2 | Xcode 工程 + 外壳 + 开机页 + 主页 | 需 Mac/CI | 2~3 个 |
| P3 | 字库 / 进度 / 设置 / 游戏 | 需 Mac/CI | 3~4 个 |
| P4 | 字体 / TTS / 家长门 / 打磨 | 需真机 | 2~3 个 |
| P5 | 上架材料 / 截图 / 隐私政策 | — | 1~2 个 |

**P0 + P1 可以在当前这台 Windows 上完成并验证**，是投入产出比最高的部分
（把最易出错的逻辑锁进单一事实源），且不会给现有 Android App 带来风险。
