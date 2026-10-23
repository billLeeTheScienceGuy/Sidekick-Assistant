cmake_minimum_required(VERSION 3.22.1)
project(sidekick_speech LANGUAGES C CXX)
include(FetchContent)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
# Portable CPU baseline; no private Tensor API, GPU service or cloud backend.
FetchContent_Declare(whisper
    URL https://codeload.github.com/ggml-org/whisper.cpp/zip/a8d002cfd879315632a579e73f0148d06959de36
    URL_HASH SHA256=4f02191b61135324a80137e459cd239e037ef3852bad4e6a2ea69490f3d89490)
FetchContent_MakeAvailable(whisper)
add_library(sidekick_speech SHARED speech.cpp vad.cpp)
option(SIDEKICK_BENCHMARK "Build opt-in fixture benchmarks" OFF)
if(SIDEKICK_BENCHMARK)
    target_sources(sidekick_speech PRIVATE ../../benchmark/cpp/vad_benchmark.cpp)
endif()
include(ExternalProject)
set(INTENT_BINARY "${CMAKE_CURRENT_BINARY_DIR}/intent-build")
set(INTENT_LIBRARY "${INTENT_BINARY}/libsidekick_intent.so")
ExternalProject_Add(intent_build
    SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../intent-cpp"
    BINARY_DIR "${INTENT_BINARY}"
    CMAKE_ARGS
        "-DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}"
        "-DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}"
        "-DANDROID_ABI=${ANDROID_ABI}"
        "-DANDROID_PLATFORM=android-26"
        "-DANDROID_STL=c++_static"
        "-DCMAKE_BUILD_TYPE=Release"
    BUILD_COMMAND "${CMAKE_COMMAND}" --build <BINARY_DIR> --target sidekick_intent --parallel 4
    INSTALL_COMMAND ""
    BUILD_ALWAYS TRUE
    BUILD_BYPRODUCTS "${INTENT_LIBRARY}")
add_library(intent_jni SHARED IMPORTED GLOBAL)
set_target_properties(intent_jni PROPERTIES IMPORTED_LOCATION "${INTENT_LIBRARY}")
add_dependencies(intent_jni intent_build)
target_link_libraries(sidekick_speech PRIVATE whisper intent_jni android log)
target_link_options(sidekick_speech PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--exclude-libs,ALL" "-Wl,-Bsymbolic")
