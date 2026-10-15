# Repository Guidelines

## 项目结构与模块组织
本仓库为单模块 Android 项目，核心代码位于 `app` 模块。Kotlin 源码放在 `app/src/main/java/com/studyword/literacy`，按 `ui`、`data`、`model`、`util` 等功能包划分。静态资源（含主字库 `character_sets.json`）位于 `app/src/main/assets`，调整字库时保持难度分组顺序。XML 资源集中在 `app/src/main/res`，布局文件遵循 `activity_*.xml`、`item_*.xml` 命名，Drawable 依据类型拆分子目录。根目录保留 `build.gradle`、`settings.gradle` 与 `gradle/` wrapper，勿随意删除以免破坏持续集成脚本。

## 构建、测试与开发命令
使用 `./gradlew assembleDebug` 生成可调试 APK 并验证依赖。执行 `./gradlew installDebug` 将构建结果安装到已连接设备或模拟器。提交前运行 `./gradlew lint` 捕获 Android Lint 警告；需要释放包时，可调用 `./gradlew assembleRelease` 并确保签名配置完整。`./gradlew test` 负责 JVM 单元测试，配合 `./gradlew connectedAndroidTest` 校验仪器化测试。若遭遇缓存异常，可通过 `./gradlew clean` 清理构建产物，必要时在 Android Studio 内同步 Gradle 以刷新 IDE 配置。

## 代码风格与命名约定
项目采用 Kotlin 官方代码风格（`kotlin.code.style=official`）。保持四空格缩进，优先使用 `val`，函数逻辑尽量集中且控制在 60 行左右。类、Activity 使用 UpperCamelCase，状态存储或 ViewModel 追加语义化后缀（如 `ProgressStore`）。资源 ID 与布局文件延续 snake_case；新增文案统一放入 `res/values/strings.xml`，颜色优先复用 `res/values/colors.xml` 中的定义。提交前使用 Android Studio 的 `Code > Reformat Code` 与 `Optimize Imports`，保持团队风格一致。

## 测试规范
单元测试位于 `app/src/test/java`，使用 JUnit4（`@Test` 标注），包名与被测类保持一致，文件名后缀 `Test`，如 `ProgressStoreTest`。UI 与集成测试放在 `app/src/androidTest/java`，在 API 26+ 模拟器上执行 `./gradlew connectedAndroidTest`。为关键边界条件编写 KDoc 注释，便于后续维护。修改仓储或持久化逻辑时至少补齐回归用例，并在 PR 描述中说明测试覆盖的业务场景。

## 提交与 Pull Request 指南
遵循现有提交历史：提交标题使用简洁中文，空一行后在 “详细说明：” 下用列表描述关键变更。协作时保留 `Co-Authored-By: weizi <1012126908@qq.com>`。发起 Pull Request 时说明改动范围、关联 Issue、列出手动测试记录（设备、构建类型、命令），UI 相关改动附关键截图。确保 lint 与单元测试通过再请求评审，并在 PR 中勾选适用的检查清单以便快速复查。

## 数据与配置提示
更新 `character_sets.json` 时保留简单/中等/困难分组，并按笔画数排序以维持学习节奏。新增持久化字段需在 `ProgressStore` 中提供向后兼容的默认值。除非与维护者协商，请保持 `minSdk 26` 与 `targetSdk 34` 配置不变。`local.properties` 存放本地 SDK 路径，不要提交到版本库；环境变量变化时在 README 中同步说明。

## 构建环境（踩过的坑，务必先读）

工具链版本：**AGP 8.7.2 + Gradle 8.10.2 + Kotlin 1.9.24**，构建用 JDK **17 或 21 均可**
（AGP 8.1 时代只支持 JDK 17，而 Android Studio 自带 JBR 是 21，导致命令行必须覆盖
`JAVA_HOME`、Studio 内构建反而失败；升级后两边都正常）。
`org.gradle.configuration-cache=true` 已启用（AGP 8.1 的 `JdkImageTransform` 不支持它，升级后才恢复）。

### 1. 下载 Gradle 发行版会停滞 → 用国内镜像
本机访问 `services.gradle.org` 下载 130 MB 的 Gradle 发行版时，wrapper 的 `.part`
文件会一直停在 0 字节（**但 Google Maven / Maven Central 完全正常**，所以只有
"换 Gradle 版本"会卡住，日常构建不受影响）。

绕法：用镜像下好 zip，放进 wrapper 缓存目录，wrapper 会直接解压本地 zip 不再联网。
```powershell
# 腾讯云镜像实测 4.7 MB/s（130 MB 约 25 秒）；官方源几乎为 0
curl.exe -L -o "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.10.2-bin\<hash>\gradle-8.10.2-bin.zip" `
  "https://mirrors.cloud.tencent.com/gradle/gradle-8.10.2-bin.zip"
```
`<hash>` 是 wrapper 依据 `distributionUrl` 生成的目录名；目录已存在时直接放 zip 即可。

### 2. 构建无故挂起 → 先查残留 Gradle 守护进程
曾出现构建 40 分钟无产出、进程 CPU 仅 7 秒的情况，根因是**旧的 `GradleDaemon`
占着文件锁**。处理方式：只结束命令行含 `GradleDaemon` / `kotlin-daemon` 的 java 进程，
**绝不要误杀 Android Studio 自身的 java 进程**。

### 3. 其它
- 不要在同一个项目上并行跑两个 Gradle 构建，会读到 0 字节的 class 文件。
- 字库 `character_sets.json` 的**字符顺序不可变**（v1.4.4 升级用户的进度依赖它做位置翻译），
  改完必须跑 `python scripts/verify_character_sets.py` 校验顺序指纹。
- 词组/例句内容靠 `python scripts/fill_easy_words.py` 写入，楷体子集靠
  `python scripts/build_kai_font.py` 重建，两者都可复现，不要手工改字体或手工塞数据。
