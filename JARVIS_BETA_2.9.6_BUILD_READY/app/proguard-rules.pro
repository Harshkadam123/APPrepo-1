# Keep Room-generated database/DAO classes and their annotations.
-keep class com.harsh.jarvis.** { *; }

# osmdroid uses reflection for some tile sources/archives.
-keep class org.osmdroid.** { *; }
-keep interface org.osmdroid.** { *; }

# Keep Kotlin metadata used by reflective libraries.
-keep class kotlin.Metadata { *; }
