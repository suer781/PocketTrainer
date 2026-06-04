# PocketTrainer — 手机端大模型微调工具
# CMake 构建配置
# 训练核心基于 MobileFineTuner (Apache 2.0)

cmake_minimum_required(VERSION 3.18)
project(PocketTrainer CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ============================================================
# MobileFineTuner operator 库配置
# ============================================================

# 针对 Android 优化
set(USE_NEW_AUTOGRAD_ENGINE ON CACHE BOOL "" FORCE)
set(USE_MOBILE_OPTIMIZER ON CACHE BOOL "" FORCE)
set(ENABLE_MEMORY_MODULE ON CACHE BOOL "" FORCE)
set(ENABLE_ACTIVATIONS_MODULE ON CACHE BOOL "" FORCE)

# BLAS 加速（OpenBLAS for Android ARM）
if(DEFINED ENV{OPENBLAS_ROOT})
    set(USE_BLAS ON CACHE BOOL "" FORCE)
    message(STATUS "🚀 OpenBLAS enabled: $ENV{OPENBLAS_ROOT}")
else()
    set(USE_BLAS OFF CACHE BOOL "" FORCE)
    message(STATUS "ℹ️  OpenBLAS not found, using pure C++")
endif()

# 关闭不需要的功能
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_LEGACY_CLI OFF CACHE BOOL "" FORCE)
set(ENABLE_PROFILING OFF CACHE BOOL "" FORCE)
set(AUTOGRAD_DEBUG OFF CACHE BOOL "" FORCE)
set(ENABLE_GPT2_DEBUG_PRINT OFF CACHE BOOL "" FORCE)
set(USE_ARENA_ALLOCATOR ON CACHE BOOL "" FORCE)

add_subdirectory(operator)

# ============================================================
# JNI 库
# ============================================================

set(JNI_SOURCES training_jni.cpp)
add_library(pocket_trainer SHARED ${JNI_SOURCES})

target_include_directories(pocket_trainer PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/operator
    ${CMAKE_CURRENT_SOURCE_DIR}/operator/finetune_ops
    ${CMAKE_CURRENT_SOURCE_DIR}/operator/opt_ops
    ${CMAKE_CURRENT_SOURCE_DIR}/operator/opt_ops/core_ext
    ${CMAKE_CURRENT_SOURCE_DIR}/operator/opt_ops/parameter
)

if(USE_BLAS AND DEFINED ENV{OPENBLAS_ROOT})
    target_include_directories(pocket_trainer PRIVATE $ENV{OPENBLAS_ROOT}/include)
endif()

# Android 编译优化
if(ANDROID)
    target_compile_options(pocket_trainer PRIVATE -O2 -ffast-math -fvisibility=hidden)
    if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
        target_compile_options(pocket_trainer PRIVATE -march=armv8.2-a+fp16)
    elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
        target_compile_options(pocket_trainer PRIVATE -mfpu=neon)
    endif()
endif()

find_library(log-lib log)
target_link_libraries(pocket_trainer operators ${log-lib} android)

if(USE_BLAS AND DEFINED ENV{OPENBLAS_ROOT})
    target_link_libraries(pocket_trainer $ENV{OPENBLAS_ROOT}/lib/libopenblas.a)
endif()