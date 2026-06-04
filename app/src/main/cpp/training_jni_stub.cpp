#include <jni.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PT", __VA_ARGS__)
#else
#define LOGI(...) ((void)0)
#endif

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeInitialize(JNIEnv* e, jobject, jstring p, jint t) {
    const char* path = e->GetStringUTFChars(p, nullptr);
    LOGI("init: %s threads=%d", path, t);
    e->ReleaseStringUTFChars(p, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeSetupLoRA(JNIEnv*, jobject, jint r, jfloat) {
    LOGI("lora: rank=%d", r);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeLoadDataset(JNIEnv* e, jobject, jstring p, jint s, jint b) {
    const char* path = e->GetStringUTFChars(p, nullptr);
    LOGI("data: %s seq=%d batch=%d", path, s, b);
    e->ReleaseStringUTFChars(p, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeTrain(JNIEnv*, jobject, jint ep, jint, jfloat lr, jfloat) {
    LOGI("train: %d epochs lr=%.6f", ep, lr);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeSave(JNIEnv* e, jobject, jstring p) {
    const char* path = e->GetStringUTFChars(p, nullptr);
    LOGI("save: %s", path);
    e->ReleaseStringUTFChars(p, path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_pockettrainer_training_NativeTraining_nativeStop(JNIEnv*, jobject) { LOGI("stop"); }
JNIEXPORT void JNICALL Java_com_pockettrainer_training_NativeTraining_nativePause(JNIEnv*, jobject) { LOGI("pause"); }
JNIEXPORT void JNICALL Java_com_pockettrainer_training_NativeTraining_nativeResume(JNIEnv*, jobject) { LOGI("resume"); }
JNIEXPORT void JNICALL Java_com_pockettrainer_training_NativeTraining_nativeRelease(JNIEnv*, jobject) { LOGI("release"); }
JNIEXPORT jint JNICALL Java_com_pockettrainer_training_NativeTraining_nativeGetLoRAParamCount(JNIEnv*, jobject) { return 0; }
JNIEXPORT jstring JNICALL Java_com_pockettrainer_training_NativeTraining_nativeGetModelInfo(JNIEnv* e, jobject) { return e->NewStringUTF("Stub - no model loaded"); }
JNIEXPORT jint JNICALL Java_com_pockettrainer_training_NativeTraining_nativeGetDatasetSize(JNIEnv*, jobject) { return 0; }

}