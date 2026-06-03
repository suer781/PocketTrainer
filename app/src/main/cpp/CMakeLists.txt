cmake_minimum_required(VERSION 3.18)
project(PocketTrainer)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# TODO: 集成 MobileFineTuner operator 库
# TODO: 集成 llama.cpp 推理引擎

# 训练引擎源文件
set(TRAINER_SOURCES
    qlora_trainer.cpp
    qlora_backward.cpp
    ggml_hooks.cpp
    training_jni.cpp
)

# JNI 库
add_library(pocket_trainer SHARED
    ${TRAINER_SOURCES}
)

target_include_directories(pocket_trainer PRIVATE
    ${CMAKE_SOURCE_DIR}
)

find_library(log-lib log)
target_link_libraries(pocket_trainer ${log-lib} android)