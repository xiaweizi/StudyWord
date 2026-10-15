# CLAUDE.md

本文件面向 Claude Code 等 AI 编码助手。

> **仓库的唯一事实来源是 [AGENTS.md](AGENTS.md)。**
> 本文件此前包含与实现不符的重复内容(例如写 `minSdk 21`,而 `app/build.gradle` 实际是 **26**;
> 版本号、Gradle/Kotlin 版本也各写一份),v1.5.0 起收敛为一处,避免误导。

请先阅读 `AGENTS.md`,其中包含:

- 项目结构与模块组织
- 构建、测试与开发命令
- 代码风格与命名约定
- 测试规范(单元测试 / 仪器化测试)
- 提交与 Pull Request 指南
- 数据与配置提示(`character_sets.json` 顺序约束、持久化向后兼容、`minSdk 26` / `targetSdk 34`)

## 快速命令

Windows 上仓库**只有 `gradlew`(sh)**,没有 `gradlew.bat`。
若 shell 里没有 `sh`,可直接调用 wrapper:

```powershell
java -classpath .\gradle\wrapper\gradle-wrapper.jar `
     org.gradle.wrapper.GradleWrapperMain -p . <task>
```

常用任务:

```bash
./gradlew assembleDebug        # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM 单元测试(纯逻辑,无需模拟器)
./gradlew lintDebug            # Android Lint(必须 0 error)
```

## 提交信息

使用中文,格式与协作要求见 `AGENTS.md`。
