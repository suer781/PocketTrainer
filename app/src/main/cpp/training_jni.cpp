#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#include "core/tensor.h"
#include "graph/safetensors_loader.h"
#include "graph/gpt2_model.h"
#include "graph/lora_injector.h"
#include "optim/trainer.h"
#include "optim/adam.h"
#include "data/wikitext2_dataset.h"

#define LOG_TAG "PocketTrainer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace ops;

static std::shared_ptr<GPT2Model> g_model;
static std::unique_ptr<LoraInjector> g_lora_injector;
static std::unique_ptr<LoRATrainer> g_trainer;
static std::shared_ptr<WikiText2Dataset> g_train_ds;
static std::shared_ptr<WikiText2Dataset> g_eval_ds;
static GPT2Config g_config;
static bool g_initialized = false;

static std::string jstr(JNIEnv* env, jstring js) {
    if (!js) return "";
    const char* c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

static std::vector<int32_t> dummy_encode(const std::string& s) {
    return std::vector<int32_t>(s.begin(), s.end());
}

// Load config from safetensors
extern "C" JNIEXPORT jlong JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeLoadConfig(
        JNIEnv* env, jobject, jstring path, jstring model_type) {
    std::string p = jstr(env, path);
    try {
        SafeTensorsReader reader(p);
        reader.parse_header();
        auto names = reader.get_tensor_names();
        for (auto& n : names) {
            if (n.find("wte.weight") != std::string::npos) {
                auto t = reader.load_tensor(n, false);
                g_config.vocab_size = t->shape()[0];
                g_config.n_embd = t->shape()[1];
            }
            if (n.find("h.0.ln_1.weight") != std::string::npos) {
                int max_layer = 0;
                for (auto& nn : names) {
                    size_t pos = nn.find("h.");
                    if (pos != std::string::npos) {
                        int l = std::atoi(nn.c_str() + pos + 2);
                        if (l > max_layer) max_layer = l;
                    }
                }
                g_config.n_layer = max_layer + 1;
            }
        }
        if (g_config.n_head == 0) g_config.n_head = g_config.n_embd / 64;
        LOGI("Config: n_layer=%d n_embd=%d n_head=%d vocab=%d",
             g_config.n_layer, g_config.n_embd, g_config.n_head, g_config.vocab_size);
        return 1;
    } catch (const std::exception& e) {
        LOGE("LoadConfig: %s", e.what());
        return 0;
    }
}

// Load model + inject LoRA
extern "C" JNIEXPORT jlong JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeLoadModel(
        JNIEnv* env, jobject, jstring path, jstring model_type) {
    std::string p = jstr(env, path);
    try {
        SafeTensorsReader reader(p);
        reader.parse_header();
        g_model = std::make_shared<GPT2Model>(g_config);
        auto names = reader.get_tensor_names();
        for (auto& name : names) {
            auto t = reader.load_tensor(name, true);
            g_model->assign_weight(name, t);
        }
        g_model->tie_weights();
        g_lora_injector = std::make_unique<LoraInjector>();
        LoraSpec spec;
        spec.rank = 8;
        spec.alpha = 16.0f;
        spec.dropout = 0.05f;
        g_lora_injector->inject(*g_model, spec);
        g_initialized = true;
        LOGI("Model loaded, LoRA injected. Trainable: %zu",
             g_lora_injector->get_trainable_params().size());
        return reinterpret_cast<jlong>(g_model.get());
    } catch (const std::exception& e) {
        LOGE("LoadModel: %s", e.what());
        return 0;
    }
}

// Start training (blocking)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeStartTraining(
        JNIEnv* env, jobject,
        jstring dataset_path, jstring output_path,
        jint epochs, jint batch_size, jfloat lr,
        jint lora_rank, jfloat lora_alpha,
        jint n_threads) {
    if (!g_model || !g_lora_injector) {
        LOGE("No model loaded");
        return JNI_FALSE;
    }
    std::string ds_path = jstr(env, dataset_path);
    std::string out_path = jstr(env, output_path);

    // Re-inject LoRA if rank changed
    if (lora_rank > 0) {
        g_lora_injector = std::make_unique<LoraInjector>();
        LoraSpec spec;
        spec.rank = lora_rank;
        spec.alpha = lora_alpha;
        spec.dropout = 0.05f;
        g_lora_injector->inject(*g_model, spec);
        LOGI("Re-injected LoRA: rank=%d alpha=%.1f", (int)lora_rank, (float)lora_alpha);
    }

    // Dataset
    try {
        WT2Config wt2;
        wt2.jsonl_train = ds_path;
        wt2.seq_len = g_config.n_positions > 0 ? g_config.n_positions : 256;
        wt2.stride = -1;
        g_train_ds = std::make_shared<WikiText2Dataset>(wt2, dummy_encode);
        // Use same dataset for eval (TODO: split)
        g_eval_ds = g_train_ds;
        LOGI("Dataset: %zu sequences", g_train_ds->num_sequences());
    } catch (const std::exception& e) {
        LOGE("Dataset: %s", e.what());
        return JNI_FALSE;
    }

    // Trainer config
    TrainerConfig cfg;
    cfg.num_epochs = epochs;
    cfg.learning_rate = lr;
    cfg.warmup_ratio = 0.05f;
    cfg.weight_decay = 0.01f;
    cfg.max_grad_norm = 1.0f;
    cfg.logging_steps = 10;
    cfg.eval_steps = 0;
    cfg.output_dir = out_path;

    g_trainer = std::make_unique<LoRATrainer>(
        *g_model, *g_lora_injector,
        *g_train_ds, *g_eval_ds, cfg);

    LOGI("Training: epochs=%d bs=%d lr=%.1e lora_rank=%d",
         (int)epochs, (int)batch_size, (float)lr, (int)lora_rank);
    try {
        g_trainer->train();
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Training: %s", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativePauseTraining(JNIEnv*, jobject) {
}

extern "C" JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeResumeTraining(JNIEnv*, jobject) {
}

extern "C" JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeStopTraining(JNIEnv*, jobject) {
}

extern "C" JNIEXPORT void JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeCleanup(JNIEnv*, jobject) {
    g_trainer.reset();
    g_lora_injector.reset();
    g_model.reset();
    g_train_ds.reset();
    g_eval_ds.reset();
    g_initialized = false;
    LOGI("Cleanup done");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pockettrainer_training_NativeTraining_nativeExportModel(
        JNIEnv* env, jobject, jstring path, jstring format) {
    if (!g_lora_injector) return JNI_FALSE;
    std::string p = jstr(env, path);
    try {
        g_lora_injector->save_lora_safetensors(p);
        LOGI("LoRA exported: %s", p.c_str());
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Export: %s", e.what());
        return JNI_FALSE;
    }
}
