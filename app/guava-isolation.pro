# Vendor frameworks may preload an incompatible com.google.common implementation.
# Rename only Guava; retain all application, Media3 and JNI class/member names.
-dontshrink
-dontoptimize
-keepattributes *
-keep class !com.google.common.**,** { *; }
-keep,allowobfuscation class com.google.common.** { *; }
-repackageclasses xyz.etkn.tv.internal.guava
