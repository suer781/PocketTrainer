// loRA_trainer.h — Wraps MobileFineTuner trainer with pause/stop/callback
#pragma once

#include "trainer_config.h"
#include "graph/gpt2_model.h"
#include "graph/lora_injector.h"
#include "text_dataset.h"

#include <string>
#include <functional>
#include <atomic>

namespace pocket_trainer {

using ProgressCallback = std::function<bool(int epoch, int step, float loss, float lr)>;

class LoRATrainer {
public:
    LoRATrainer(ops::GPT2Model& model,
                ops::LoraInjector& lora,
                TextDataset& train_ds,
                TextDataset& eval_ds,
                const TrainerConfig& config);
    ~LoRATrainer();

    float train();

    void request_pause()  { pause_requested_.store(true);  }
    void request_resume() { pause_requested_.store(false); }
    void request_stop()   { stop_requested_.store(true); pause_requested_.store(false); }
    bool is_paused()  const { return pause_requested_.load(); }
    bool is_stopped() const { return stop_requested_.load();  }
    void set_progress_callback(ProgressCallback cb) { progress_cb_ = std::move(cb); }

private:
    ops::GPT2Model&    model_;
    ops::LoraInjector& lora_;
    TextDataset&       train_ds_;
    TextDataset&       eval_ds_;
    TrainerConfig      config_;
    std::atomic<bool>  pause_requested_{false};
    std::atomic<bool>  stop_requested_{false};
    ProgressCallback   progress_cb_;

    float compute_learning_rate(int step, int total_steps) const;
    float run_evaluation();
    void  save_checkpoint(int step);
    void  apply_gradient_clipping();
};

}  // namespace pocket_trainer
