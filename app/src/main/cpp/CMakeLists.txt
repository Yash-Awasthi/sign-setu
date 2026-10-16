cmake_minimum_required(VERSION 3.22.1)
project(assistant LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp (MIT) at a pinned release, verified by hash before it is built (SEC-50).
include(FetchContent)
FetchContent_Declare(
    llama
    URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/v0.5.0.tar.gz
    URL_HASH SHA256=fef9ed754f4e031fb5c663c29260feda4ebc241abb68d64a81c0f1df5f1748e2
)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
# The x86_64 build only serves the emulator, whose host CPU has AVX2.
# ponytail: arm64 stays on the plain ARMv8 baseline; per-CPU variants (GGML_CPU_ALL_VARIANTS) if phones prove slow.
if(ANDROID_ABI STREQUAL "x86_64")
    set(GGML_AVX ON CACHE BOOL "" FORCE)
    set(GGML_AVX2 ON CACHE BOOL "" FORCE)
    set(GGML_FMA ON CACHE BOOL "" FORCE)
    set(GGML_F16C ON CACHE BOOL "" FORCE)
endif()
FetchContent_MakeAvailable(llama)

add_library(assistant SHARED assistant.cpp)
target_link_libraries(assistant llama log)
# Android 15 and later need native libraries aligned to 16 KB pages.
target_link_options(assistant PRIVATE "-Wl,-z,max-page-size=16384")
