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

# --- Obfuscation only: no shrinking, no optimization (safe, avoids runtime breakage) ---
-dontshrink
-dontoptimize
-dontwarn **
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }
-keepclassmembers enum * { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class android.os.** { *; }
# Room entities / DAOs / database and everything the app persists by name
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.data.** { *; }
-keep class com.example.server.ServerModels* { *; }
# WorkManager workers are instantiated by name
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }
# ViewModels, activities, receivers, providers referenced from manifest
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends android.app.Activity
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
# Shizuku reflection on the app side
-keep class com.example.shizuku.** { *; }
