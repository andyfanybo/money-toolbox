# Compose / AndroidX 默认规则已足够,这里只做少量补充。

# 保留崩溃堆栈可读性
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# DataStore / kotlinx.coroutines 内部有少量反射依赖,忽略无关警告
-dontwarn kotlinx.coroutines.debug.**
