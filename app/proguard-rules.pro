# 入口类只被 META-INF/xposed/java_init.list 里的字符串引用，R8 视为不可达，必须显式保留。
# 匿名内部类（Hooker 实现）目前靠可达性也能存活，但整包 keep 更稳，
# 避免以后新增的类被优化掉之后运行时才炸（ClassNotFoundException / NoSuchMethodError）。
-keep class io.ads.peeler.** { *; }

# libxposed API 由框架在运行时提供（compileOnly，不打包进 APK），抑制警告。
-dontwarn io.github.libxposed.**

# 保留行号，便于实机异常栈定位。
-keepattributes SourceFile,LineNumberTable
