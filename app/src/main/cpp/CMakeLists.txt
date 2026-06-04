cmake_minimum_required(VERSION 3.18)
project(PocketTrainer CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# MobileFineTuner operator 库
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_LEGACY_CLI OFF CACHE BOOL "" FORCE)
set(ENABLE_PROFILING OFF CACHE BOOL "" FORCE)
set(AUTOGRAD_DEBUG OFF CACHE BOOL "" FORCE)
set(ENABLE_GPT2_DEBUG_PRINT OFF CACHE BOOL "" FORCE)
set(USE_NEW_AUTOGRAD_ENGINE ON CACHE BOOL "" FORCE)
set(USE_MOBILE_OPTIMIZER ON CACHE BOOL "" FORCE)
set(ENABLE_MEMORY_MODULE ON CACHE BOOL "" FORCE)
set(ENABLE_ACTIVATIONS_MODULE ON CACHE BOOL "" FORCE)
set(USE_ARENA_ALLOCATOR ON CACHE BOOL "" FORCE)
set(USE_BLAS OFF CACHE BOOL "" FORCE)
set(ENABLE_ENERGY_MONITOR OFF CACHE BOOL "" FORCE)
set(ENABLE_SHARDING_MODULE OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/MobileFineTuner/operator operators_build)

# JNI 库
add_library(pocket_trainer SHARED training_jni.cpp)

target_include_directories(pocket_trainer PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/MobileFineTuner/operator/finetune_ops
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/MobileFineTuner/operator/opt_ops
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/MobileFineTuner/operator/opt_ops/core_ext
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/MobileFineTuner/operator/opt_ops/parameter
)

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
