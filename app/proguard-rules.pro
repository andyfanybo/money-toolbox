# Compose / AndroidX 默认规则已足够,这里只做少量补充。

# 保留崩溃堆栈可读性
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# DataStore / kotlinx.coroutines 内部有少量反射依赖,忽略无关警告
-dontwarn kotlinx.coroutines.debug.**

# MuPDF 的 libmupdf_java.so 通过 JNI 按原始类名、字段名和方法名访问 Java 绑定。
# R8 若改名或删除 Context$Log / Context.log 等成员，发布版打开 PDF 会崩溃。
-keep class com.artifex.mupdf.fitz.** { *; }
-keep interface com.artifex.mupdf.fitz.** { *; }
-keepattributes InnerClasses,EnclosingMethod
