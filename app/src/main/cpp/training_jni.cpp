// PocketTrainer JNI Bridge — 连接 C++ MobileFineTuner 和 Kotlin UI
#include <jni.h>
#include <string>
#include <memory>
#include <atomic>
#include <mutex>
#include "parallel_executor.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PocketTrainer", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "PocketTrainer", __VA_ARGS__)
#else
#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) printf(__VA_ARGS__)
#endif

// MobileFineTuner headers
#include "core/tensor.h"
#include "core/safetensors_loader.h"
#include "graph/gpt2_model.h"
#include "graph/lora_injector.h"
#include "optim/trainer.h"
#include "optim/adam.h"
#include "data/wikitext2_dataset.h"

struct TrainingState {
    std::unique_ptr<ops::GPT2Model> model;
    std::unique_ptr<ops::LoraInjector> lora;
    std::unique_ptr<ops::WikiText2Dataset> train_data, eval_data;
    std::unique_ptr<ops::LoRATrainer> trainer;
    std::atomic<bool> is_initialized{false}, has_lora{false}, has_data{false}, is_training{false};
    std::mutex mutex;
    void reset() { /* cleanup */ }
};
static TrainingState g_state;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeInitialize(
    JNIEnv* env, jobject, jstring modelPath, jint nThreads) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    try {
        auto config = ops::GPT2Config::from_pretrained(std::string(path));
        g_state.model = std::make_unique<ops::GPT2Model>(config);
        ops::SafeTensorsLoader loader;
        loader.load(std::string(path) + "/model.safetensors");
        loader.apply_to_model(*g_state.model);
        g_state.is_initialized = true;
        LOGI("Model loaded: %d layers", config.n_layers);
    } catch (const std::exception& e) { LOGE("Init failed: %s", e.what()); return JNI_FALSE; }
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeSetupLoRA(
    JNIEnv*, jobject, jint rank, jfloat alpha) {
    g_state.lora = std::make_unique<ops::LoraInjector>();
    ops::LoraSpec spec; spec.rank = rank; spec.alpha = alpha; spec.split_qkv = true;
    g_state.lora->inject(*g_state.model, spec);
    g_state.has_lora = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeLoadDataset(
    JNIEnv* env, jobject, jstring dataPath, jint seqLen, jint batchSize) {
    const char* path = env->GetStringUTFChars(dataPath, nullptr);
    std::string p(path);
    g_state.train_data = std::make_unique<ops::WikiText2Dataset>(p+"/train.txt", seqLen);
    g_state.eval_data = std::make_unique<ops::WikiText2Dataset>(p+"/valid.txt", seqLen);
    g_state.has_data = true;
    env->ReleaseStringUTFChars(dataPath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeTrain(
    JNIEnv*, jobject, jint epochs, jint gradAccum, jfloat lr, jfloat maxNorm) {
    ops::TrainerConfig cfg; cfg.num_epochs = epochs; cfg.learning_rate = lr;
    g_state.trainer = std::make_unique<ops::LoRATrainer>(*g_state.model, *g_state.lora, *g_state.train_data, *g_state.eval_data, cfg);
    g_state.trainer->train();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeStop(JNIEnv*, jobject) {}
JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativePause(JNIEnv*, jobject) {}
JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeResume(JNIEnv*, jobject) {}

JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeRelease(JNIEnv*, jobject) {
    g_state.reset();
}

}