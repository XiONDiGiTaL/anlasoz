cmake_minimum_required(VERSION 3.22)
project(anlasoz_llm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp is pinned as a submodule (third_party/llama.cpp, tag b12059).
# Only the library is built: examples, tests, tools and the HTTP server are off.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# GPU offload. llama.cpp reaches the phone's GPU through its Vulkan backend,
# which is not something the NDK can build on its own: it needs glslc, the
# Vulkan headers and a host C++ compiler, because the compute shaders are
# compiled to SPIR-V while the app is being built. Those live outside the
# project, so the backend is opt-in. Without it the engine stays CPU-only and
# the app simply does not offer the offload switch.
#
# App is built with: -DANLASOZ_VULKAN=ON -DVULKAN_SDK=<path to a Vulkan SDK>
# NNAPI is not an option any more: llama.cpp removed that backend upstream.
option(ANLASOZ_VULKAN "Build llama.cpp's Vulkan backend for GPU offload" OFF)
if(ANLASOZ_VULKAN)
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
    # The NDK toolchain limits find_package(CONFIG) to its own sysroot, which
    # hides the host packages the Vulkan shader generator needs (SPIRV-Headers
    # comes from the SDK, not from Android). Host packages are safe to find
    # here: none of them are linked into the app.
    set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
else()
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

# Android 15 and later run on 16 KB pages; every shared library in the APK has
# to be aligned for it or the platform warns and future devices will refuse to
# load the app. Applies to llama.cpp's libraries too, so it is set globally.
add_link_options("-Wl,-z,max-page-size=16384")
add_compile_options("-Wl,-z,max-page-size=16384")

# CMake's debug default is -O0, which makes ggml roughly an order of magnitude
# slower and would make on-device generation look broken. Keep symbols, keep
# optimisation.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_C_FLAGS_DEBUG "-O2 -g")
    set(CMAKE_CXX_FLAGS_DEBUG "-O2 -g")
endif()

# Without this ggml compiles its baseline ARMV8.0 kernels and reports
# "NEON = 1" with no DOTPROD — several times slower for quantised matmul, which
# is the whole workload here. Dot products arrived with ARMv8.2 (Cortex-A75/A55
# and later), which is every phone that can hold a small model in memory.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8.2-a+dotprod+fp16)
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp)

if(NOT EXISTS ${LLAMA_SRC}/CMakeLists.txt)
    message(FATAL_ERROR
        "llama.cpp is missing. Run: git submodule update --init --recursive")
endif()

add_subdirectory(${LLAMA_SRC} ${CMAKE_BINARY_DIR}/llama.cpp)

add_library(anlasoz_llm SHARED llama_jni.cpp)

target_include_directories(anlasoz_llm PRIVATE
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include)

target_link_libraries(anlasoz_llm PRIVATE llama android log)

if(ANLASOZ_VULKAN)
    target_compile_definitions(anlasoz_llm PRIVATE ANLASOZ_VULKAN=1)
    target_link_libraries(anlasoz_llm PRIVATE vulkan)
endif()
