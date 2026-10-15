# ============================================================
# 识字小帮手 —— R8 / ProGuard 规则(v1.5.0 起真正启用)
#
# v1.4.4 时 release 是 minifyEnabled=false,本文件是 Android Studio 原始模板
# (全是被注释的示例,零条生效规则)。v1.5.0 打开 minify + shrinkResources 后,
# 这里必须有真实规则,否则运行时会出现"类被裁掉"的崩溃。
# ============================================================

# ---------- 通用:保留调试所需信息 ----------
# 保留行号,让线上崩溃堆栈可读;
# -renamesourcefileattribute 让反混淆后的文件名不暴露源码路径。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 注解 / 泛型签名 / 内部类:org.json 反射与 Kotlin 元数据都依赖它们
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ---------- Kotlin ----------
# Kotlin 标准库与协程自带 consumer rules,这里只兜底保留元数据
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ---------- 数据模型 ----------
# 本项目用 org.json 手写解析(无反射),理论上不需要 keep;
# 但数据类会被 Parcelable/序列化相关代码间接引用,且保留可让堆栈更可读。
-keep class com.studyword.literacy.model.** { *; }

# ---------- TTS / 系统组件 ----------
# UtteranceProgressListener 的回调由系统框架调用,方法名不能被混淆
-keep class com.studyword.literacy.util.TtsManager { *; }
-keep class * extends android.speech.tts.UtteranceProgressListener { *; }

# Activity / 自定义 View 由框架按类名实例化,必须保留
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---------- 第三方库 ----------
# MPAndroidChart 通过接口回调 + 反射构造部分对象
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Material Components 自带 consumer rules,这里仅避免其内部反射被裁
-dontwarn com.google.android.material.**

# ---------- 其他常见噪音 ----------
-dontwarn org.jetbrains.annotations.**
