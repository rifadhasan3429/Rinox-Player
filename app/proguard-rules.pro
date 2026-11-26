# Protect Media3 ExoPlayer & Jellyfin FFmpeg 10-bit/5.1 Decoder from R8 Obfuscation
-keep class androidx.media3.** { *; }
-keep class org.jellyfin.media3.** { *; }
-keepclassmembers class org.jellyfin.media3.** { *; }
-dontwarn androidx.media3.**
-dontwarn org.jellyfin.media3.**

# Keep Kotlin & Coroutines metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
