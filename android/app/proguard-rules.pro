# Kotlin/Coroutines
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Androidx
-keep class androidx.** { *; }
-dontwarn androidx.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Serialization
-keepclasseswithmembers class **.*$* {
    kotlinx.serialization.KSerializer serializer(...);
}

# Application specific
-keep class dev.crossflow.android.** { *; }
-keep class dev.crossflow.android.Protocol { *; }

# Reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
