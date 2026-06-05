// loRA_trainer.cpp — Training loop with pause/stop/callback + LR scheduling + early stopping
#include "loRA_trainer.h"
#include "core/tensor.h"
#include "core/lm_loss.h"
#include "optim/adam.h"
#include "graph/lora_saver.h"

#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <chrono>

#define TAG "LoRATrainer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace pocket_trainer {

LoRATrainer::LoRATrainer(ops::GPT2Model& model, ops::LoraInjector& lora,
                         TextDataset& train_ds, TextDataset& eval_ds,
                         const TrainerConfig& config)
    : model_(model), lora_(lora), train_ds_(train_ds), eval_ds_(eval_ds), config_(config)
{
    LOGI("LoRATrainer: epochs=%d lr=%.6f batch=%d lora_rank=%d",
         config_.num_epochs, config_.learning_rate, config_.batch_size, config_.lora_rank);
}

LoRATrainer::~LoRATrainer() { LOGI("LoRATrainer destroyed"); }

float LoRATrainer::compute_learning_rate(int step, int total_steps) const {
    if (total_steps <= 0) return config_.learning_rate;
    int warmup_steps = static_cast<int>(total_steps * config_.warmup_ratio);
    if (step < warmup_steps)
        return config_.learning_rate * static_cast<float>(step) / std::max(1, warmup_steps);
    float progress = static_cast<float>(step - warmup_steps) / std::max(1, total_steps - warmup_steps);
    if (config_.scheduler_type == "cosine")
        return config_.learning_rate * 0.5f * (1.0f + std::cos(M_PI * progress));
    if (config_.scheduler_type == "constant_with_warmup")
        return config_.learning_rate;
    return config_.learning_rate * (1.0f - progress);  // linear
}

void LoRATrainer::apply_gradient_clipping() {
    if (config_.max_grad_norm <= 0.0f) return;
    auto params = lora_.collect_lora_parameters();
    float total_norm_sq = 0.0f;
    for (const auto& p : params) {
        if (p && p->grad()) {
            float n = p->grad()->norm().item<float>();
            total_norm_sq += n * n;
        }
    }
    float total_norm = std::sqrt(total_norm_sq);
    if (total_norm > config_.max_grad_norm) {
        float s = config_.max_grad_norm / (total_norm + 1e-6f);
        for (const auto& p : params) { if (p && p->grad()) p->grad()->mul_(s); }
    }
}

float LoRATrainer::run_evaluation() {
    float total_loss = 0.0f;
    int n = 0, eval_sz = static_cast<int>(eval_ds_.size() * config_.val_split);
    if (eval_sz <= 0) return 0.0f;
    int start = eval_ds_.size() - eval_sz;
    for (int i = start; i < eval_ds_.size(); i += config_.batch_size) {
        int bs = std::min(config_.batch_size, eval_ds_.size() - i);
        if (bs <= 0) break;
        auto batch = eval_ds_.get_batch(i, bs);
        auto logits = model_.forward(batch.input_ids, batch.attention_mask);
        auto loss = ops::lm_loss(logits, batch.labels);
        total_loss += loss.item<float>(); n++;
        if (n >= 10) break;
    }
    return n > 0 ? total_loss / n : 0.0f;
}

void LoRATrainer::save_checkpoint(int step) {
    std::string p = config_.output_dir + "/checkpoint-step" + std::to_string(step);
    LOGI("Saving checkpoint: %s", p.c_str());
    try { ops::LoraSaver().save(model_, lora_, p); }
    catch (const std::exception& e) { LOGE("Checkpoint failed: %s", e.what()); }
}

float LoRATrainer::train() {
    int ds = train_ds_.size();
    if (ds <= 0) { LOGE("Empty dataset!"); return -1.0f; }
    int spe = ds / config_.batch_size;
    int total = spe * config_.num_epochs;
    LOGI("Training: %d samples, %d steps/epoch, %d total", ds, spe, total);

    ops::Adam opt; opt.set_lr(config_.learning_rate);
    auto lp = lora_.collect_lora_parameters();
    for (const auto& p : lp) opt.add_param(p);

    float run_loss = 0.f; int accum = 0, gs = 0;
    float best_eval = 1e9f; int patience = 0;
    auto t0 = std::chrono::steady_clock::now();

    for (int ep = 0; ep < config_.num_epochs; ++ep) {
        if (stop_requested_.load()) break;
        train_ds_.shuffle(config_.seed + ep);
        for (int si = 0; si < spe; ++si) {
            while (pause_requested_.load() && !stop_requested_.load())
                std::this_thread::sleep_for(std::chrono::milliseconds(200));
            if (stop_requested_.load()) break;

            int bs = std::min(config_.batch_size, ds - si * config_.batch_size);
            if (bs <= 0) break;
            auto batch = train_ds_.get_batch(si * config_.batch_size, bs);
            auto logits = model_.forward(batch.input_ids, batch.attention_mask);
            auto loss = ops::lm_loss(logits, batch.labels);
            if (config_.gradient_accumulation_steps > 1)
                loss = loss / static_cast<float>(config_.gradient_accumulation_steps);
            loss.backward();
            run_loss += loss.item<float>() * config_.gradient_accumulation_steps;
            accum++;

            if (accum >= config_.gradient_accumulation_steps) {
                apply_gradient_clipping();
                float lr = compute_learning_rate(gs, total);
                opt.set_lr(lr); opt.step(); opt.zero_grad();
                gs++; accum = 0;

                if (gs % config_.logging_steps == 0) {
                    float avg = run_loss / config_.logging_steps;
                    float cl = compute_learning_rate(gs, total);
                    auto now = std::chrono::steady_clock::now();
                    float elapsed = std::chrono::duration<float>(now - t0).count();
                    LOGI("Step %d/%d | Ep %d | Loss %.4f | LR %.6f | %.1f st/s",
                         gs, total, ep+1, avg, cl, gs/std::max(0.01f, elapsed));
                    if (progress_cb_ && !progress_cb_(ep, gs, avg, cl)) {
                        stop_requested_.store(true); break;
                    }
                    run_loss = 0.f;
                }
                if (config_.eval_steps > 0 && gs % config_.eval_steps == 0) {
                    float el = run_evaluation();
                    LOGI("Eval step %d: loss=%.4f best=%.4f patience=%d/%d",
                         gs, el, best_eval, patience, config_.early_stopping_patience);
                    if (config_.early_stopping) {
                        if (el < best_eval - config_.early_stopping_min_delta) {
                            best_eval = el; patience = 0; save_checkpoint(gs);
                        } else {
                            patience++;
                            if (patience >= config_.early_stopping_patience) {
                                LOGI("Early stop at step %d", gs);
                                stop_requested_.store(true); break;
                            }
                        }
                    }
                }
                if (config_.save_steps > 0 && gs % config_.save_steps == 0) save_checkpoint(gs);
            }
        }
        if (stop_requested_.load()) break;
    }
    save_checkpoint(gs);
    float fl = run_loss / std::max(1, accum);
    float tt = std::chrono::duration<float>(std::chrono::steady_clock::now() - t0).count();
    LOGI("Done: %d steps in %.1fs, final_loss=%.4f", gs, tt, fl);
    return fl;
}

}  // namespace pocket_trainer
