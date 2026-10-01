-keep class androidx.media3.** { *; }
-keep class com.bumptech.glide.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
