cmake_minimum_required(VERSION 3.22)
project(pocket_trainer LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# MobileFineTuner — the CMakeLists.txt is at operator/, not operator/finetune_ops/
set(MFT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/MobileFineTuner/operator)
add_subdirectory(${MFT_DIR} ${CMAKE_CURRENT_BINARY_DIR}/mft_build)

# Include paths — our headers + all MobileFineTuner subdirs
set(FT_DIR ${MFT_DIR}/finetune_ops)
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${FT_DIR}
    ${FT_DIR}/core
    ${FT_DIR}/graph
    ${FT_DIR}/optim
    ${FT_DIR}/data
    ${FT_DIR}/transformer
    ${FT_DIR}/nn
    ${FT_DIR}/memory
)

add_library(pocket_trainer SHARED
    training_jni.cpp
    loRA_trainer.cpp
    cJSON.c
)

target_link_libraries(pocket_trainer PRIVATE operators)
find_library(log-lib log)
target_link_libraries(pocket_trainer PRIVATE ${log-lib})
