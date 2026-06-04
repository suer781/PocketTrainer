# PocketTrainer ProGuard Rules

# Keep JNI native methods
-keepclasseswithmembernames class com.pockettrainer.training.NativeTraining {
    native <methods>;
}

# Keep training callback interface (called from JNI)
-keep class com.pockettrainer.training.TrainingCallback { *; }

# Keep data classes used by JNI
-keep class com.pockettrainer.data.** { *; }
