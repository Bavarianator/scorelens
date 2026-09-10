-keepattributes *Annotation*, InnerClasses
-dontwarn okhttp3.**
-dontwarn okio.**
# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.freedarts.scorer.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.freedarts.scorer.**$$serializer { *; }
