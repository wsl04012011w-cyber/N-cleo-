cmake_minimum_required(VERSION 3.22.1)
project(nucleo_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Caminho do repositório llama.cpp (passado pelo Gradle/CI via -DLLAMA_CPP_DIR=...)
if(NOT DEFINED LLAMA_CPP_DIR)
    set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/llama.cpp")
endif()

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp não encontrado em: ${LLAMA_CPP_DIR}")
endif()

option(NUCLEO_ENABLE_VULKAN "Habilitar backend Vulkan no ggml/llama.cpp" OFF)

# ==============================================================================
# Configurações e otimizações recomendadas pelo llama.cpp para Android (arm64)
# ==============================================================================
# Garante otimização -O3 -DNDEBUG para ggml/llama mesmo quando o Gradle rodar assembleDebug
set(CMAKE_BUILD_TYPE "Release" CACHE STRING "Build type" FORCE)
set(CMAKE_C_FLAGS_DEBUG "-O3 -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG" CACHE STRING "" FORCE)

# 1. Cross-compilation obrigatória: desabilita -march=native do host x86_64
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# 2. Threading: desabilita OpenMP para evitar dependência de libomp.so ausente
#    no sistema Android; utiliza o threadpool nativo leve do ggml.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# 3. Arm KleidiAI + ISA alvo armv8.2-a+dotprod+fp16 (Cortex-A78, sem i8mm)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
else()
    set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
endif()

# 4. Desativa componentes de desktop/servidor e mantém apenas a engine core
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

if(NUCLEO_ENABLE_VULKAN)
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
else()
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory("${LLAMA_CPP_DIR}" llama.cpp EXCLUDE_FROM_ALL)

message(STATUS "[NucleoCMake] CMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE} | C_FLAGS_DEBUG=${CMAKE_C_FLAGS_DEBUG} | CXX_FLAGS_DEBUG=${CMAKE_CXX_FLAGS_DEBUG} | GGML_CPU_ARM_ARCH=${GGML_CPU_ARM_ARCH} | GGML_CPU_KLEIDIAI=${GGML_CPU_KLEIDIAI}")
if(TARGET ggml-cpu)
    get_target_property(_ggml_cpu_opts ggml-cpu COMPILE_OPTIONS)
    message(STATUS "[NucleoCMake] ggml-cpu COMPILE_OPTIONS=${_ggml_cpu_opts}")
endif()

add_library(nucleo_llama SHARED
    nucleo_llama_jni.cpp
)

target_compile_options(nucleo_llama PRIVATE
    -O3
    -DNDEBUG
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

# Alinhamento ELF de 16 KB obrigatório para Android 15+ (NDK r28+)
target_link_options(nucleo_llama PRIVATE
    -Wl,--gc-sections
    -Wl,-z,max-page-size=16384
)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

target_link_libraries(nucleo_llama PRIVATE
    llama
    ggml
    ${ANDROID_LIB}
    ${LOG_LIB}
)
