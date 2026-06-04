// trainer_config.h — Pulls TrainerConfig from MobileFineTuner, extends with our fields
#pragma once

#include "optim/trainer.h"
#include <string>
#include <functional>

namespace pocket_trainer {

struct TrainerConfig : public ops::TrainerConfig {
    // Extended fields for full-featured training
    int   batch_size           = 4;
    int   save_steps           = 500;
    int   seed                 = 42;
    int   n_threads            = 4;
    int   lora_rank            = 8;
    float lora_alpha           = 16.0f;
    float lora_dropout         = 0.05f;
    float val_split            = 0.1f;
    std::string preprocessing  = "none";
    std::string scheduler_type = "linear";
    bool  early_stopping       = false;
    int   early_stopping_patience    = 3;
    float early_stopping_min_delta   = 0.001f;
    std::string resume_from_checkpoint;
    int   save_total_limit     = 3;
    // Callback
    std::function<bool(int epoch, int step, float loss, float lr)> on_progress;
};

}  // namespace pocket_trainer
