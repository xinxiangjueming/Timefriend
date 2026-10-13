# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Kotlin serialization / WebDAV JSON
-keepattributes *Annotation*
-keep class com.example.timefriend.data.** { *; }

# Google Tink (used by security-crypto / EncryptedSharedPreferences)
-dontwarn com.google.errorprone.annotations.**

# 小米 Vela interconnect 手机端 SDK（xms-wearable）。
# AAR 自带 consumer proguard 规则，但显式 keep 本工程接收器，避免 R8 误删入口类。
-keep class com.example.timefriend.wearable.** { *; }
-dontwarn com.xiaomi.xms.wearable.**