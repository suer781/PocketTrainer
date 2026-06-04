// PocketTrainer JNI Bridge — 真实版本
// 连接 Kotlin UI ↔ MobileFineTuner C++ 训练引擎

#include <jni.h>
#include <string>
#include <memory>
#include <atomic>
#include <mutex>
#include <thread>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "PocketTrainer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

// MobileFineTuner 头文件
#include "core/tensor.h"
#include "core/safetensors_loader.h"
#include "graph/gpt2_model.h"
#include "graph/lora_injector.h"
#include "optim/trainer.h"
#include "optim/adam.h"
#include "data/wikitext2_dataset.h"

// 全局训练状态
struct TrainingState {
    std::unique_ptr<ops::GPT2Model> model;
    std::unique_ptr<ops::SafeTensorsLoader> loader;
    std::unique_ptr<ops::LoraInjector> lora_injector;
    std::unique_ptr<ops::WikiText2Dataset> train_dataset;
    std::unique_ptr<ops::WikiText2Dataset> eval_dataset;
    std::unique_ptr<ops::LoRATrainer> trainer;

    std::atomic<bool> is_initialized{false};
    std::atomic<bool> has_lora{false};
    std::atomic<bool> has_data{false};
    std::atomic<bool> is_training{false};
    std::atomic<bool> should_stop{false};
    std::atomic<bool> is_paused{false};

    std::unique_ptr<std::thread> training_thread;
    std::mutex mutex;

    void reset() {
        std::lock_guard<std::mutex> lock(mutex);
        should_stop = true;
        if (training_thread && training_thread->joinable()) training_thread->join();
        trainer.reset();
        train_dataset.reset();
        eval_dataset.reset();
        lora_injector.reset();
        loader.reset();
        model.reset();
        is_initialized = has_lora = has_data = is_training = should_stop = is_paused = false;
    }
};

static TrainingState g_state;

// JNI 回调
struct JNICallback {
    JavaVM* jvm;
    jobject callback_obj;
    jmethodID on_progress_method;
    jmethodID on_complete_method;
    jmethodID on_error_method;

    void onProgress(int epoch, int total, int step, float loss) {
        JNIEnv* env;
        bool attached = false;
        if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
            jvm->AttachCurrentThread(&env, nullptr);
            attached = true;
        }
        env->CallVoidMethod(callback_obj, on_progress_method, epoch, total, step, loss);
        if (attached) jvm->DetachCurrentThread();
    }

    void onComplete(bool success, const std::string& msg) {
        JNIEnv* env;
        bool attached = false;
        if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
            jvm->AttachCurrentThread(&env, nullptr);
            attached = true;
        }
        jstring jmsg = env->NewStringUTF(msg.c_str());
        env->CallVoidMethod(callback_obj, on_complete_method, success, jmsg);
        env->DeleteLocalRef(jmsg);
        if (attached) jvm->DetachCurrentThread();
    }

    void onError(const std::string& err) {
        JNIEnv* env;
        bool attached = false;
        if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
            jvm->AttachCurrentThread(&env, nullptr);
            attached = true;
        }
        jstring jerr = env->NewStringUTF(err.c_str());
        env->CallVoidMethod(callback_obj, on_error_method, jerr);
        env->DeleteLocalRef(jerr);
        if (attached) jvm->DetachCurrentThread();
    }
};

static std::unique_ptr<JNICallback> g_callback;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeInitialize(
    JNIEnv* env, jobject, jstring modelDirPath, jint numThreads) {
    const char* path = env->GetStringUTFChars(modelDirPath, nullptr);
    std::string model_path(path);
    env->ReleaseStringUTFChars(modelDirPath, path);
    LOGI("init: %s threads=%d", model_path.c_str(), numThreads);
    try {
        if (numThreads > 0) ops::set_num_threads(numThreads);
        auto config = ops::GPT2Config::from_pretrained(model_path);
        g_state.model = std::make_unique<ops::GPT2Model>(config);
        g_state.loader = std::make_unique<ops::SafeTensorsLoader>();
        g_state.loader->load(model_path + "/model.safetensors");
        g_state.loader->apply_to_model(*g_state.model);
        g_state.model->set_training_mode(false);
        g_state.is_initialized = true;
        LOGI("Model loaded: %d layers", config.n_layers);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("init failed: %s", e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeSetupLoRA(
    JNIEnv*, jobject, jint rank, jfloat alpha) {
    if (!g_state.is_initialized) return JNI_FALSE;
    LOGI("lora: rank=%d alpha=%.1f", rank, alpha);
    try {
        g_state.lora_injector = std::make_unique<ops::LoraInjector>();
        ops::LoraSpec spec;
        spec.rank = rank;
        spec.alpha = alpha;
        spec.split_qkv = true;
        g_state.lora_injector->inject(*g_state.model, spec);
        g_state.has_lora = true;
        LOGI("LoRA injected: %zu trainable params", g_state.model->trainable_parameters().size());
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("lora failed: %s", e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeLoadDataset(
    JNIEnv* env, jobject, jstring dataPath, jint seqLen, jint batchSize) {
    const char* path = env->GetStringUTFChars(dataPath, nullptr);
    std::string data_path(path);
    env->ReleaseStringUTFChars(dataPath, path);
    LOGI("data: %s seq=%d batch=%d", data_path.c_str(), seqLen, batchSize);
    try {
        g_state.train_dataset = std::make_unique<ops::WikiText2Dataset>(data_path + "/train.txt", seqLen, batchSize);
        g_state.eval_dataset = std::make_unique<ops::WikiText2Dataset>(data_path + "/valid.txt", seqLen, batchSize);
        g_state.has_data = true;
        LOGI("Dataset: train=%d eval=%d", g_state.train_dataset->size(), g_state.eval_dataset->size());
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("data failed: %s", e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeTrain(
    JNIEnv* env, jobject, jint epochs, jint gradAccum, jfloat lr, jfloat maxNorm, jobject callback) {
    if (!g_state.is_initialized || !g_state.has_lora || !g_state.has_data) return JNI_FALSE;

    g_callback = std::make_unique<JNICallback>();
    env->GetJavaVM(&g_callback->jvm);
    g_callback->callback_obj = env->NewGlobalRef(callback);
    jclass cls = env->GetObjectClass(callback);
    g_callback->on_progress_method = env->GetMethodID(cls, "onProgress", "(IIIF)V");
    g_callback->on_complete_method = env->GetMethodID(cls, "onComplete", "(ZLjava/lang/String;)V");
    g_callback->on_error_method = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");

    ops::TrainerConfig config;
    config.num_epochs = epochs;
    config.learning_rate = lr;
    config.weight_decay = 0.01f;
    config.warmup_steps = 10;
    config.max_grad_norm = maxNorm;
    config.log_interval = 10;
    config.eval_interval = 500;

    LOGI("train: %d epochs lr=%.6f", epochs, lr);
    g_state.should_stop = false;
    g_state.is_paused = false;

    g_state.training_thread = std::make_unique<std::thread>([config, gradAccum, lr]() {
        try {
            g_state.is_training = true;
            g_state.trainer = std::make_unique<ops::LoRATrainer>(
                *g_state.model, *g_state.lora_injector,
                *g_state.train_dataset, *g_state.eval_dataset, config);

            for (int epoch = 0; epoch < config.num_epochs; epoch++) {
                if (g_state.should_stop) break;
                g_state.train_dataset->shuffle();
                int total_steps = g_state.train_dataset->size();
                for (int step = 0; step < total_steps; step++) {
                    if (g_state.should_stop) break;
                    while (g_state.is_paused && !g_state.should_stop)
                        std::this_thread::sleep_for(std::chrono::milliseconds(100));
                    float loss = g_state.trainer->train_step(epoch, step, lr, gradAccum);
                    if (step % config.log_interval == 0)
                        g_callback->onProgress(epoch, config.num_epochs, step, loss);
                    ops::clip_grad_norm(g_state.model->trainable_parameters(), config.max_grad_norm);
                }
                if (g_state.should_stop) break;
                g_state.trainer->evaluate(epoch);
            }
            g_state.is_training = false;
            g_callback->onComplete(true, "Training completed");
        } catch (const std::exception& e) {
            g_state.is_training = false;
            g_callback->onError(std::string("Training failed: ") + e.what());
        }
    });
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeSave(JNIEnv* env, jobject, jstring outputPath) {
    const char* path = env->GetStringUTFChars(outputPath, nullptr);
    LOGI("save: %s", path);
    try {
        g_state.lora_injector->save_lora_safetensors(std::string(path));
        env->ReleaseStringUTFChars(outputPath, path);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(outputPath, path);
        LOGE("save failed: %s", e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL Java_com_pockettrainer_training_NativeTraining_nativeStop(JNIEnv*, jobject) { g_state.should_stop = true; }
JNIEXPORT void JNICALL Java_com_pockettrainer_training_NativeTraining_nativePause(JNIEnv*, jobject) { g_state.is_paused = true; }
JNIEXPORT void JNICALL Java_com_pockettrainer_training_NativeTraining_nativeResume(JNIEnv*, jobject) { g_state.is_paused = false; }
JNIEXPORT void JNICALL Java_com_pockettrainer_training_NativeTraining_nativeRelease(JNIEnv* env, jobject) {
    g_state.reset();
    if (g_callback) { env->DeleteGlobalRef(g_callback->callback_obj); g_callback.reset(); }
}
JNIEXPORT jint JNICALL Java_com_pockettrainer_training_NativeTraining_nativeGetLoRAParamCount(JNIEnv*, jobject) { return g_state.has_lora ? g_state.model->trainable_parameters().size() : 0; }
JNIEXPORT jstring JNICALL Java_com_pockettrainer_training_NativeTraining_nativeGetModelInfo(JNIEnv* env, jobject) {
    if (!g_state.is_initialized) return env->NewStringUTF("No model");
    auto& c = g_state.model->get_config();
    char buf[256]; snprintf(buf, sizeof(buf), "GPT-2 | %d layers | %d hidden | %d heads", c.n_layers, c.n_embd, c.n_head);
    return env->NewStringUTF(buf);
}
JNIEXPORT jint JNICALL Java_com_pockettrainer_training_NativeTraining_nativeGetDatasetSize(JNIEnv*, jobject) { return g_state.has_data ? g_state.train_dataset->size() : 0; }
JNIEXPORT jboolean JNICALL Java_com_pockettrainer_training_NativeTraining_nativeIsTraining(JNIEnv*, jobject) { return g_state.is_training; }

}