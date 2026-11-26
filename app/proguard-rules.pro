# Keep all LibVLC native JNI classes and methods from being renamed or stripped
-keep class org.videolan.libvlc.** { *; }
-keepclassmembers class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**

# Keep Kotlin & Coroutines metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
