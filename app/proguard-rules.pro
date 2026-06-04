# PocketTrainer ProGuard Rules

# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativeTraining class
-keep class com.pockettrainer.training.NativeTraining { *; }
-keep class com.pockettrainer.training.NativeTraining$* { *; }

# Keep data classes
-keep class com.pockettrainer.data.** { *; }