// PocketTrainer - Android JNI Bridge
// 兼容 MobileFineTuner operator 库的端侧训练引擎

#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <vector>
#include <atomic>
#include <thread>
#include <fstream>
#include <cstring>

#include "gpt2_model.h"
#include "lora_injector.h"
#include "safetensors_reader.h"
#include "text_dataset.h"
#include "wikitext2_dataset.h"
#include "loRA_trainer.h"
#include "trainer_config.h"

#define TAG "PocketTrainer_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace pocket_trainer;

// ── Global state ────────────────────────────────────────────────
static std::unique_ptr<GPT2Model>         g_model;
static std::unique_ptr<LoraInjector>      g_lora_injector;
static std::unique_ptr<LoRATrainer>       g_trainer;
static std::unique_ptr<TextDataset>    g_train_ds;
static std::unique_ptr<TextDataset>    g_eval_ds;
static std::unique_ptr<GPTConfig>         g_config;
static bool g_initialized = false;

// ── Training control ────────────────────────────────────────────
static std::atomic<bool> g_pause_requested{false};
static std::atomic<bool> g_stop_requested{false};
static std::atomic<bool> g_training_active{false};
static std::thread       g_train_thread;

// ── JNI callback refs (cached at init) ─────────────────────────
static JavaVM* g_jvm = nullptr;
static jobject g_callback_obj = nullptr;
static jmethodID g_on_progress = nullptr;
static jmethodID g_on_complete = nullptr;
static jmethodID g_on_error    = nullptr;

jint JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// ── Helpers ─────────────────────────────────────────────────────
static std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return "";
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

static std::vector<int32_t> dummy_encode(const std::string& text) {
    std::vector<int32_t> ids;
    ids.reserve(text.size());
    for (unsigned char c : text) ids.push_back(static_cast<int32_t>(c));
    return ids;
}

// ── Callback helpers (called from training thread) ──────────────
static JNIEnv* get_env() {
    JNIEnv* env = nullptr;
    if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    return env;
}

static void report_progress(int epoch, int total_epochs, int step, int total_steps, float loss) {
    if (!g_callback_obj || !g_on_progress) return;
    JNIEnv* env = get_env();
    if (!env) return;
    env->CallVoidMethod(g_callback_obj, g_on_progress,
                        (jint)epoch, (jint)total_epochs,
                        (jint)step, (jint)total_steps,
                        (jfloat)loss);
}

static void report_complete(const std::string& output_path) {
    if (!g_callback_obj || !g_on_complete) return;
    JNIEnv* env = get_env();
    if (!env) return;
    jstring jpath = env->NewStringUTF(output_path.c_str());
    env->CallVoidMethod(g_callback_obj, g_on_complete, jpath);
    env->DeleteLocalRef(jpath);
}

static void report_error(const std::string& msg) {
    if (!g_callback_obj || !g_on_error) return;
    JNIEnv* env = get_env();
    if (!env) return;
    jstring jmsg = env->NewStringUTF(msg.c_str());
    env->CallVoidMethod(g_callback_obj, g_on_error, jmsg);
    env->DeleteLocalRef(jmsg);
}

// ── JNI Functions ───────────────────────────────────────────────

extern "C" {

// Register callback object
JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeSetCallback(
        JNIEnv* env, jobject /* this */, jobject callback) {
    if (g_callback_obj) {
        env->DeleteGlobalRef(g_callback_obj);
        g_callback_obj = nullptr;
    }
    if (callback) {
        g_callback_obj = env->NewGlobalRef(callback);
        jclass cls = env->GetObjectClass(callback);
        g_on_progress = env->GetMethodID(cls, "onProgress", "(IIIIF)V");
        g_on_complete = env->GetMethodID(cls, "onComplete", "(Ljava/lang/String;)V");
        g_on_error    = env->GetMethodID(cls, "onError",    "(Ljava/lang/String;)V");
        LOGI("Callback registered");
    }
}

// Load model config from safetensors
JNIEXPORT jlong JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeLoadConfig(
        JNIEnv* env, jobject, jstring jpath) {
    std::string path = jstr(env, jpath);
    LOGI("nativeLoadConfig: %s", path.c_str());

    try {
        SafeTensorsReader reader(path);
        auto header = reader.read_header();

        g_config = std::make_unique<GPTConfig>();

        // 推断模型参数
        for (const auto& [name, info] : header.tensors) {
            if (name == "wte.weight" && info.shape.size() == 2) {
                g_config->vocab_size = static_cast<int>(info.shape[0]);
                g_config->n_embd = static_cast<int>(info.shape[1]);
            }
            if (name.find("h.0.ln_1.weight") != std::string::npos) {
                g_config->n_embd = g_config->n_embd ?: 768;
            }
        }

        int max_layer = 0;
        for (const auto& [name, info] : header.tensors) {
            if (name.substr(0, 2) == "h.") {
                int layer = std::stoi(name.substr(2));
                max_layer = std::max(max_layer, layer);
            }
        }
        g_config->n_layer = max_layer + 1;

        // 推断 n_head
        for (const auto& [name, info] : header.tensors) {
            if (name.find("attn.c_attn.weight") != std::string::npos && info.shape.size() == 2) {
                g_config->n_head = 12;
                break;
            }
        }

        LOGI("Model config: vocab=%d, embd=%d, layers=%d, heads=%d",
             g_config->vocab_size, g_config->n_embd, g_config->n_layer, g_config->n_head);

        return 1;
    } catch (const std::exception& e) {
        LOGE("nativeLoadConfig failed: %s", e.what());
        return 0;
    }
}

// Load model weights + inject LoRA
JNIEXPORT jlong JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeLoadModel(
        JNIEnv* env, jobject, jstring jpath) {
    std::string path = jstr(env, jpath);
    LOGI("nativeLoadModel: %s", path.c_str());

    try {
        if (!g_config) {
            LOGE("Config not loaded, call nativeLoadConfig first");
            return 0;
        }

        g_model = std::make_unique<GPT2Model>(*g_config);

        // 从 safetensors 加载权重
        SafeTensorsReader reader(path);
        auto tensors = reader.read_all();
        for (auto& [name, tensor] : tensors) {
            g_model->load_weight(name, tensor);
        }

        // 注入 LoRA
        g_lora_injector = std::make_unique<LoraInjector>();
        g_lora_injector->inject(*g_model, 8, 16.0, 0.05);

        g_initialized = true;
        LOGI("Model loaded, LoRA injected");
        return reinterpret_cast<jlong>(g_model.get());
    } catch (const std::exception& e) {
        LOGE("nativeLoadModel failed: %s", e.what());
        return 0;
    }
}

// Start training (blocking — run on background thread)
JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeStartTraining(
        JNIEnv* env, jobject,
        jstring jdataset_path, jstring joutput_path,
        jstring jsystem_prompt,
        jint epochs, jint batch_size, jfloat learning_rate,
        jint lora_rank, jfloat lora_alpha, jint n_threads) {
    if (!g_initialized) {
        LOGE("Model not loaded");
        return JNI_FALSE;
    }

    std::string dataset_path = jstr(env, jdataset_path);
    std::string output_path  = jstr(env, joutput_path);
    std::string system_prompt = jstr(env, jsystem_prompt);

    LOGI("nativeStartTraining: dataset=%s, output=%s, epochs=%d, bs=%d, lr=%f",
         dataset_path.c_str(), output_path.c_str(), epochs, batch_size, learning_rate);

    try {
        // 如果指定新 LoRA 参数，重新注入
        if (lora_rank > 0) {
            g_lora_injector = std::make_unique<LoraInjector>();
            g_lora_injector->inject(*g_model, lora_rank, lora_alpha, 0.05);
        }

        // 加载数据集（支持 .txt/.jsonl/.json/.csv，可选系统提示词注入）
        g_train_ds = std::make_unique<TextDataset>(dataset_path, 128, system_prompt);
        g_eval_ds  = std::make_unique<TextDataset>(dataset_path, 128, system_prompt, 12345);

        // 配置训练器
        TrainerConfig cfg;
        cfg.num_epochs          = epochs;
        cfg.batch_size          = batch_size;
        cfg.learning_rate       = learning_rate;
        cfg.warmup_ratio        = 0.1f;
        cfg.weight_decay        = 0.01f;
        cfg.max_grad_norm       = 1.0f;
        cfg.gradient_accumulation_steps = 4;
        cfg.num_threads         = n_threads;
        cfg.eval_steps          = 100;
        cfg.save_steps          = 500;
        cfg.output_dir          = output_path;

        g_trainer = std::make_unique<LoRATrainer>(
            *g_model, *g_lora_injector, cfg, *g_train_ds, *g_eval_ds);

        // 设置训练回调
        g_trainer->set_progress_callback([](int epoch, int total_epochs,
                                            int step, int total_steps, float loss) {
            if (g_stop_requested.load()) {
                throw std::runtime_error("Training stopped by user");
            }
            // 简单的暂停实现：自旋等待
            while (g_pause_requested.load() && !g_stop_requested.load()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
            report_progress(epoch, total_epochs, step, total_steps, loss);
        });

        g_training_active.store(true);
        g_trainer->train();
        g_training_active.store(false);

        report_complete(output_path);
        LOGI("Training complete");
        return JNI_TRUE;

    } catch (const std::exception& e) {
        g_training_active.store(false);
        std::string msg = e.what();
        if (msg.find("stopped by user") == std::string::npos) {
            report_error(msg);
        }
        LOGE("nativeStartTraining failed: %s", e.what());
        return JNI_FALSE;
    }
}

// Start training on background thread
JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeStartTrainingAsync(
        JNIEnv* env, jobject obj,
        jstring jdataset_path, jstring joutput_path,
        jstring jsystem_prompt,
        jint epochs, jint batch_size, jfloat learning_rate,
        jint lora_rank, jfloat lora_alpha, jfloat lora_dropout,
        jfloat warmup_ratio, jfloat weight_decay,
        jfloat max_grad_norm, jint grad_accum_steps,
        jint max_seq_len, jfloat val_split,
        jint preprocessing, jint scheduler_type,
        jint early_stopping, jint early_stopping_patience,
        jfloat early_stopping_min_delta,
        jstring jresume_checkpoint, jint save_total_limit,
        jint n_threads, jint save_steps, jint seed) {
    std::string dataset_path = jstr(env, jdataset_path);
    std::string output_path  = jstr(env, joutput_path);
    std::string system_prompt = jstr(env, jsystem_prompt);
    std::string resume_checkpoint = jstr(env, jresume_checkpoint);

    // capture all params
    int   _epochs = epochs, _bs = batch_size, _lr_r = lora_rank, _nt = n_threads;
    int   _ga = grad_accum_steps, _sl = max_seq_len, _ss = save_steps, _seed = seed;
    int   _prep = preprocessing, _sched = scheduler_type;
    int   _es = early_stopping, _es_pat = early_stopping_patience, _stl = save_total_limit;
    float _lr = learning_rate, _la = lora_alpha, _ld = lora_dropout;
    float _wr = warmup_ratio, _wd = weight_decay, _gn = max_grad_norm;
    float _vs = val_split, _es_delta = early_stopping_min_delta;

    g_stop_requested.store(false);
    g_pause_requested.store(false);

    g_train_thread = std::thread([dataset_path, output_path, system_prompt, resume_checkpoint,
                                   _epochs, _bs, _lr, _lr_r, _la, _ld,
                                   _wr, _wd, _gn, _ga, _sl, _vs,
                                   _prep, _sched, _es, _es_pat, _es_delta, _stl,
                                   _nt, _ss, _seed]() {
        JNIEnv* thread_env = nullptr;
        g_jvm->AttachCurrentThread(&thread_env, nullptr);

        try {
            if (!g_initialized) throw std::runtime_error("Model not loaded");

            if (_lr_r > 0) {
                g_lora_injector = std::make_unique<LoraInjector>();
                g_lora_injector->inject(*g_model, _lr_r, _la, _ld);
            }

            g_train_ds = std::make_unique<TextDataset>(dataset_path, _sl, system_prompt, _seed);
            g_eval_ds  = std::make_unique<TextDataset>(dataset_path, _sl, system_prompt, _seed + 1);

            TrainerConfig cfg;
            cfg.num_epochs    = _epochs;
            cfg.batch_size    = _bs;
            cfg.learning_rate = _lr;
            cfg.warmup_ratio  = _wr;
            cfg.weight_decay  = _wd;
            cfg.max_grad_norm = _gn;
            cfg.gradient_accumulation_steps = _ga;
            cfg.num_threads   = _nt;
            cfg.eval_steps    = _ss;
            cfg.save_steps    = _ss;
            cfg.output_dir    = output_path;

            g_trainer = std::make_unique<LoRATrainer>(
                *g_model, *g_lora_injector, cfg, *g_train_ds, *g_eval_ds);

            g_trainer->set_progress_callback([](int epoch, int total_epochs,
                                                int step, int total_steps, float loss) {
                if (g_stop_requested.load()) throw std::runtime_error("Training stopped by user");
                while (g_pause_requested.load() && !g_stop_requested.load()) {
                    std::this_thread::sleep_for(std::chrono::milliseconds(100));
                }
                report_progress(epoch, total_epochs, step, total_steps, loss);
            });

            g_training_active.store(true);
            g_trainer->train();
            g_training_active.store(false);
            report_complete(output_path);

        } catch (const std::exception& ex) {
            g_training_active.store(false);
            std::string msg = ex.what();
            if (msg.find("stopped by user") == std::string::npos) report_error(msg);
        }

        g_jvm->DetachCurrentThread();
    });
    g_train_thread.detach();
}

// Pause training
JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativePauseTraining(
        JNIEnv*, jobject) {
    g_pause_requested.store(true);
    LOGI("Training pause requested");
}

// Resume training
JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeResumeTraining(
        JNIEnv*, jobject) {
    g_pause_requested.store(false);
    LOGI("Training resumed");
}

// Stop training
JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeStopTraining(
        JNIEnv*, jobject) {
    g_stop_requested.store(true);
    g_pause_requested.store(false);
    LOGI("Training stop requested");
}

// Cleanup all resources
JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeCleanup(
        JNIEnv* env, jobject) {
    LOGI("nativeCleanup");
    g_stop_requested.store(true);
    g_training_active.store(false);

    g_trainer.reset();
    g_train_ds.reset();
    g_eval_ds.reset();
    g_lora_injector.reset();
    g_model.reset();
    g_config.reset();

    if (g_callback_obj) {
        env->DeleteGlobalRef(g_callback_obj);
        g_callback_obj = nullptr;
    }
    g_initialized = false;
}

// Export LoRA weights
JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeExportModel(
        JNIEnv* env, jobject, jstring joutput_path) {
    if (!g_lora_injector) {
        LOGE("No LoRA to export");
        return JNI_FALSE;
    }
    std::string output_path = jstr(env, joutput_path);
    try {
        g_lora_injector->save_lora_safetensors(output_path);
        LOGI("LoRA exported: %s", output_path.c_str());
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Export failed: %s", e.what());
        return JNI_FALSE;
    }
}

// Import LoRA weights from file and apply to current model
JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeImportLora(
        JNIEnv* env, jobject, jstring jlora_path) {
    if (!g_initialized || !g_model) {
        LOGE("Model not loaded, cannot import LoRA");
        return JNI_FALSE;
    }
    std::string lora_path = jstr(env, jlora_path);
    LOGI("nativeImportLora: %s", lora_path.c_str());
    try {
        g_lora_injector = std::make_unique<LoraInjector>();
        g_lora_injector->load_lora_safetensors(lora_path, *g_model);
        LOGI("LoRA imported successfully");
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Import failed: %s", e.what());
        return JNI_FALSE;
    }
}

// Merge LoRA into base model and save as safetensors
JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeMergeAndSave(
        JNIEnv* env, jobject, jstring jmerged_path) {
    if (!g_initialized || !g_model) {
        LOGE("Model not loaded");
        return JNI_FALSE;
    }
    std::string merged_path = jstr(env, jmerged_path);
    LOGI("nativeMergeAndSave: %s", merged_path.c_str());
    try {
        if (g_lora_injector) {
            g_lora_injector->merge_to_base(*g_model);
        }
        g_model->save_safetensors(merged_path);
        LOGI("Merged model saved: %s", merged_path.c_str());
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Merge failed: %s", e.what());
        return JNI_FALSE;
    }
}

} // extern "C"
