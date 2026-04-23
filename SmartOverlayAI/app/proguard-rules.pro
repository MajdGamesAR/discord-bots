# Add project specific ProGuard rules here.
-keep class com.google.mlkit.** { *; }
-keep class org.tensorflow.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
