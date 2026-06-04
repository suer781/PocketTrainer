cmake_minimum_required(VERSION 3.22)
project(pocket_trainer LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# MobileFineTuner operator library (git submodule)
set(MFT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/MobileFineTuner/operator/finetune_ops)
add_subdirectory(${MFT_DIR} ${CMAKE_CURRENT_BINARY_DIR}/mft_build)

# Include paths — our headers + all MobileFineTuner subdirs
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${MFT_DIR}
    ${MFT_DIR}/core
    ${MFT_DIR}/graph
    ${MFT_DIR}/optim
    ${MFT_DIR}/data
    ${MFT_DIR}/transformer
    ${MFT_DIR}/nn
    ${MFT_DIR}/memory
)

add_library(pocket_trainer SHARED
    training_jni.cpp
    loRA_trainer.cpp
)

target_link_libraries(pocket_trainer PRIVATE operators)
find_library(log-lib log)
target_link_libraries(pocket_trainer PRIVATE ${log-lib})
