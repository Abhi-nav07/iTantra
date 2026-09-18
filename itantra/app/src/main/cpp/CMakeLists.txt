cmake_minimum_required(VERSION 3.18)
project(itantra_mt_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

# Set CTranslate2 options
set(WITH_MKL OFF CACHE BOOL "" FORCE)
set(WITH_CUDA OFF CACHE BOOL "" FORCE)
set(WITH_RUY ON CACHE BOOL "" FORCE)
set(ENABLE_CPU_DISPATCH OFF CACHE BOOL "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WITH_OPENMP OFF CACHE BOOL "" FORCE)
set(WITH_OPENMP OFF CACHE BOOL "" FORCE)
set(OPENMP_RUNTIME NONE CACHE STRING "" FORCE)

# Force -fPIC globally for all dependencies
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")

if(ANDROID)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D\"pthread_setaffinity_np(a,b,c)=0\"")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -llog")
endif()

# Fetch CTranslate2
FetchContent_Declare(
    ctranslate2
    GIT_REPOSITORY https://github.com/OpenNMT/CTranslate2.git
    GIT_TAG        v4.8.2
    GIT_SHALLOW    ON
)

# Fetch SentencePiece
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    sentencepiece
    GIT_REPOSITORY https://github.com/google/sentencepiece.git
    GIT_TAG        v0.2.0
    GIT_SHALLOW    ON
)

FetchContent_MakeAvailable(ctranslate2 sentencepiece)

add_library(
    itantra_mt_jni
    SHARED
    itantra_mt_jni.cpp
)

target_link_libraries(
    itantra_mt_jni
    ctranslate2
    sentencepiece-static
    log
)

target_include_directories(
    itantra_mt_jni
    PRIVATE
    ${sentencepiece_SOURCE_DIR}/src
)
