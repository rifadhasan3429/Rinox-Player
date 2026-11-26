# Protect Media3 ExoPlayer & NextLib FFmpeg Native JNI from R8 Obfuscation
-keep class androidx.media3.** { *; }
-keep class io.github.anilbeesetti.nextlib.** { *; }
-keepclassmembers class io.github.anilbeesetti.nextlib.** { *; }
-dontwarn androidx.media3.**
-dontwarn io.github.anilbeesetti.nextlib.**

# Keep Kotlin & Coroutines metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
