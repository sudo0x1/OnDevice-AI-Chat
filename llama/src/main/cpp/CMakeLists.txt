#cmake_minimum_required(VERSION 3.22.1)
#
#project(llama-jni LANGUAGES C CXX)
#
#set(CMAKE_CXX_STANDARD 17)
#set(CMAKE_CXX_STANDARD_REQUIRED ON)
#set(CMAKE_CXX_EXTENSIONS OFF)
#
## ============================================================
## Android ARM64 CPU configuration
## ============================================================
#
#if(ANDROID AND ANDROID_ABI STREQUAL "arm64-v8a")
#
#    message(STATUS "Building llama.cpp for Android ARM64")
#
#    # Enable ARM NEON
#    add_compile_options(
#            -march=armv8-a
#
#    )
#
#endif()
#
#
## ============================================================
## llama.cpp
## ============================================================
#
## Disable unnecessary llama.cpp components.
#set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
#set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
#set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
#set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
#
## GGML CPU backend
#set(GGML_CPU ON CACHE BOOL "" FORCE)
#
#add_subdirectory(
#        llama.cpp
#        ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp
#)
#
#
## ============================================================
## JNI library
## ============================================================
#
#add_library(llama-jni SHARED
#        llama-jni.cpp
#)
#
#
## ============================================================
## Include directories
## ============================================================
#
#target_include_directories(llama-jni
#        PRIVATE
#
#        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
#
#        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
#)
#
#
## ============================================================
## Link libraries
## ============================================================
#
#target_link_libraries(llama-jni
#        PRIVATE
#
#        llama
#
#        android
#
#        log
#)
#
#
## ============================================================
## Compiler warnings
## ============================================================
#
#target_compile_options(llama-jni
#        PRIVATE
#
#        -Wall
#        -Wextra
#        -Wno-unused-parameter
#)

cmake_minimum_required(VERSION 3.22.1)

project(llama-jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ============================================================
# Always optimize. A Gradle *debug* build uses -O0, which makes
# llama.cpp many times slower.
# ============================================================
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_C_FLAGS_DEBUG   "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")
set(CMAKE_C_FLAGS_RELEASE   "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")

# ============================================================
# llama.cpp options
# ============================================================
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)

set(GGML_CPU    ON  CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)   # ggml's own thread pool is faster on Android
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)   # we choose the ARM arch explicitly

if(ANDROID AND ANDROID_ABI STREQUAL "arm64-v8a")
    message(STATUS "Building llama.cpp for Android ARM64")

    # dotprod  -> fast int8 dot products (big speedup for Q4/Q8 models)
    # fp16     -> native half-precision math
    # Works on most phones from ~2018 on (Snapdragon 845+, Tensor, Dimensity...).
    #
    # Newer chips (Snapdragon 8 Gen 1+, Dimensity 9000+) also support i8mm.
    # If you only target those, use: armv8.2-a+dotprod+fp16+i8mm
    # (running that on an older phone crashes with SIGILL).
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()

add_subdirectory(
        llama.cpp
        ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp
)

# ============================================================
# JNI library
# ============================================================
add_library(llama-jni SHARED llama-jni.cpp)

target_include_directories(llama-jni
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

target_link_libraries(llama-jni
        PRIVATE
        llama
        android
        log
)

target_compile_options(llama-jni
        PRIVATE
        -Wall
        -Wextra
        -Wno-unused-parameter
)