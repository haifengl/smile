# =============================================================================
# CMakeLists.txt — smile-torch Hourglass C API
#
# Builds a shared library (smile_torch) that exposes a stable C ABI over
# libtorch.  The Java/JNI layer (or any other caller) links only against
# this library and is therefore insulated from libtorch's C++ ABI.
#
# Usage (out-of-source build):
#
#   cmake -S deep/src/main/cpp \
#         -B build/cpp \
#         -DCMAKE_BUILD_TYPE=Release \
#         [-DLIBTORCH_ROOT=<path/to/libtorch>] \
#         [-DCMAKE_INSTALL_PREFIX=<install-dir>]
#   cmake --build build/cpp --config Release -j$(nproc)
#   cmake --install build/cpp
#
# The default LIBTORCH_ROOT is ${PROJECT_SOURCE_DIR}/../../../libtorch
# which resolves to deep/libtorch when the source directory is
# deep/src/main/cpp (i.e. the standard smile project layout).
# =============================================================================

cmake_minimum_required(VERSION 3.22)

project(smile_torch
    VERSION 3.1.0
    DESCRIPTION "Hourglass C API over libtorch for the SMILE deep-learning module"
    LANGUAGES CXX C)

# --------------------------------------------------------------------------
# C++ standard — libtorch 2.13+ requires C++20; keep parity with libtorch
# --------------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --------------------------------------------------------------------------
# Default build type
# --------------------------------------------------------------------------
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    message(STATUS "No build type set; defaulting to Release")
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# --------------------------------------------------------------------------
# Locate libtorch
# --------------------------------------------------------------------------
# Allow the user to point to a custom libtorch installation.
# Fall back to the bundled copy in deep/libtorch.
if(NOT LIBTORCH_ROOT)
    # ${CMAKE_CURRENT_SOURCE_DIR} == deep/src/main/cpp
    set(LIBTORCH_ROOT
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../libtorch"
        CACHE PATH "Root directory of the libtorch installation")
endif()

set(CMAKE_MACOSX_RPATH ON)
set(CMAKE_INSTALL_RPATH "@loader_path/;@loader_path/../libtorch")

# Resolve relative paths so find_package can locate TorchConfig.cmake.
get_filename_component(LIBTORCH_ROOT "${LIBTORCH_ROOT}" ABSOLUTE)
message(STATUS "LIBTORCH_ROOT = ${LIBTORCH_ROOT}")

list(APPEND CMAKE_PREFIX_PATH "${LIBTORCH_ROOT}/share/cmake/Torch")

# Minimum supported GPU is Ampere sm_80 (A100). Also emit Hopper (90) and
# Blackwell (100) for H100 / B200 (native FP4). Pin Torchs NVCC arch list
# BEFORE find_package(Torch) or cuda.cmake may inject -gencode arch=compute_50.
# Override with -DTORCH_CUDA_ARCH_LIST=... or ENV TORCH_CUDA_ARCH_LIST.
if(DEFINED ENV{TORCH_CUDA_ARCH_LIST} AND NOT DEFINED TORCH_CUDA_ARCH_LIST)
    set(TORCH_CUDA_ARCH_LIST "$ENV{TORCH_CUDA_ARCH_LIST}" CACHE STRING
        "CUDA arches for LibTorch / smile_torch (min sm_80)")
elseif(NOT DEFINED TORCH_CUDA_ARCH_LIST OR TORCH_CUDA_ARCH_LIST STREQUAL "")
    set(TORCH_CUDA_ARCH_LIST "8.0;9.0;10.0" CACHE STRING
        "CUDA arches for LibTorch / smile_torch (sm_80+; includes Blackwell FP4)")
endif()
message(STATUS "TORCH_CUDA_ARCH_LIST = ${TORCH_CUDA_ARCH_LIST}")

find_package(Torch REQUIRED)

message(STATUS "Torch version  : ${Torch_VERSION}")
message(STATUS "Torch libraries: ${TORCH_LIBRARIES}")
message(STATUS "Torch CXX flags: ${TORCH_CXX_FLAGS}")

# Apply flags required by libtorch (e.g. -D_GLIBCXX_USE_CXX11_ABI=1)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${TORCH_CXX_FLAGS}")

# --------------------------------------------------------------------------
# Optional CUDA support
#
# CUDA device introspection (cudaMemGetInfo, emptyCache, …) requires both:
#   1. A CUDA-enabled LibTorch (libc10_cuda), and
#   2. The CUDA Toolkit headers/runtime for compiling against cuda_runtime.h.
#
# Building against a CPU LibTorch, or without CUDAToolkit, produces a CPU-only
# smile_torch where smile_cuda_mem_get_info always fails with a clear error.
# --------------------------------------------------------------------------
option(SMILE_TORCH_USE_CUDA "Enable CUDA device helpers in smile_torch" ON)

find_library(SMILE_C10_CUDA_LIBRARY c10_cuda
    PATHS "${LIBTORCH_ROOT}/lib"
    NO_DEFAULT_PATH)
find_package(CUDAToolkit QUIET)

set(SMILE_TORCH_CUDA_ENABLED OFF)
set(SMILE_TORCH_NCCL_ENABLED OFF)
if(SMILE_TORCH_USE_CUDA AND SMILE_C10_CUDA_LIBRARY AND CUDAToolkit_FOUND)
    set(SMILE_TORCH_CUDA_ENABLED ON)
    message(STATUS "CUDA enabled: toolkit ${CUDAToolkit_VERSION}, c10_cuda=${SMILE_C10_CUDA_LIBRARY}")
    # Minimum sm_80 (A100); also Hopper (90) and Blackwell (100, native FP4).
    set(CMAKE_CUDA_ARCHITECTURES "80;90;100" CACHE STRING
        "NVCC GPU arches (minimum sm_80)" FORCE)
    enable_language(CUDA)
    set(CMAKE_CUDA_STANDARD 20)
    set(CMAKE_CUDA_STANDARD_REQUIRED ON)
    # Torch may still inject -gencode below sm_80; strip those so nvcc succeeds
    # (CUDA 13 rejects sm_50; we require Ampere+).
    foreach(_cuda_flag_var CMAKE_CUDA_FLAGS CMAKE_CUDA_FLAGS_RELEASE
                           CMAKE_CUDA_FLAGS_DEBUG CMAKE_CUDA_FLAGS_RELWITHDEBINFO)
        if(DEFINED ${_cuda_flag_var} AND NOT "${${_cuda_flag_var}}" STREQUAL "")
            set(_f "${${_cuda_flag_var}}")
            string(REGEX REPLACE "[-]gencode[= ]arch=compute_[5-7][0-9]a?,code=[^, ]+" "" _f "${_f}")
            string(REGEX REPLACE "  +" " " _f "${_f}")
            set(${_cuda_flag_var} "${_f}" CACHE STRING "" FORCE)
        endif()
    endforeach()
elseif(SMILE_TORCH_USE_CUDA AND SMILE_C10_CUDA_LIBRARY AND NOT CUDAToolkit_FOUND)
    message(WARNING
        "LibTorch has c10_cuda but CUDAToolkit was not found — "
        "building CPU-only smile_torch. Install the CUDA Toolkit matching "
        "this LibTorch (e.g. CUDA 13.2 for cu132) and reconfigure.")
elseif(SMILE_TORCH_USE_CUDA AND CUDAToolkit_FOUND AND NOT SMILE_C10_CUDA_LIBRARY)
    message(WARNING
        "CUDAToolkit ${CUDAToolkit_VERSION} found but LibTorch has no c10_cuda — "
        "building CPU-only smile_torch. Use a CUDA LibTorch build (e.g. cu132).")
else()
    message(STATUS "CUDA not enabled — building CPU-only smile_torch")
endif()

# Optional NCCL (TP / future PP collectives)
if(SMILE_TORCH_CUDA_ENABLED)
    find_path(NCCL_INCLUDE_DIR nccl.h
        HINTS
            ${CUDAToolkit_INCLUDE_DIRS}
            /usr/include
            /usr/local/include
            /usr/include/x86_64-linux-gnu
        PATH_SUFFIXES nccl)
    find_library(NCCL_LIBRARY NAMES nccl libnccl.so.2
        HINTS
            ${CUDAToolkit_LIBRARY_DIR}
            /usr/lib
            /usr/local/lib
            /usr/lib/x86_64-linux-gnu
            /usr/local/cuda/lib64)
    if(NCCL_INCLUDE_DIR AND NCCL_LIBRARY)
        set(SMILE_TORCH_NCCL_ENABLED ON)
        message(STATUS "NCCL enabled: include=${NCCL_INCLUDE_DIR}, lib=${NCCL_LIBRARY}")
    else()
        message(WARNING "CUDA enabled but NCCL not found — peer-copy TP collectives only")
    endif()
endif()

# --------------------------------------------------------------------------
# smile_torch shared library target
# --------------------------------------------------------------------------
set(SMILE_TORCH_SOURCES smile_torch.cpp)
if(SMILE_TORCH_CUDA_ENABLED)
    list(APPEND SMILE_TORCH_SOURCES smile_gated_delta.cu)
endif()

add_library(smile_torch SHARED ${SMILE_TORCH_SOURCES})

target_include_directories(smile_torch
    # Public: callers only need the C header
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
        $<INSTALL_INTERFACE:include>
    # Private: the implementation needs libtorch headers
    PRIVATE
        "${LIBTORCH_ROOT}/include"
        "${LIBTORCH_ROOT}/include/torch/csrc/api/include"
)

target_link_libraries(smile_torch PRIVATE ${TORCH_LIBRARIES})

if(SMILE_TORCH_CUDA_ENABLED)
    target_compile_definitions(smile_torch PRIVATE USE_CUDA)
    target_link_libraries(smile_torch PRIVATE CUDA::cudart)
    # Minimum sm_80 (A100); also Hopper (90) and Blackwell (100, native FP4).
    set_target_properties(smile_torch PROPERTIES
        CUDA_SEPARABLE_COMPILATION OFF
        CUDA_ARCHITECTURES "80;90;100"
    )
endif()

if(SMILE_TORCH_NCCL_ENABLED)
    target_compile_definitions(smile_torch PRIVATE USE_NCCL)
    target_include_directories(smile_torch PRIVATE ${NCCL_INCLUDE_DIR})
    target_link_libraries(smile_torch PRIVATE ${NCCL_LIBRARY})
endif()

# Hide all symbols by default; only SMILE_API-annotated functions are exported.
set_target_properties(smile_torch PROPERTIES
    CXX_VISIBILITY_PRESET     hidden
    VISIBILITY_INLINES_HIDDEN ON
    POSITION_INDEPENDENT_CODE ON
)

# On Windows libtorch ships as import-library + DLL; copy the DLLs on MSVC.
if(MSVC)
    # Ensure Release runtime is used (libtorch links against it)
    set_property(TARGET smile_torch PROPERTY
        MSVC_RUNTIME_LIBRARY "MultiThreadedDLL")

    # Copy libtorch & c10 DLLs next to the output library so the loader finds them.
    file(GLOB TORCH_DLLS "${LIBTORCH_ROOT}/lib/*.dll")
    add_custom_command(TARGET smile_torch POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            ${TORCH_DLLS}
            $<TARGET_FILE_DIR:smile_torch>
        COMMENT "Copying libtorch DLLs to output directory")
endif()

# --------------------------------------------------------------------------
# Compiler warnings & hardening
# --------------------------------------------------------------------------
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang|AppleClang")
    # Scope warning flags to CXX only so nvcc does not see them.
    target_compile_options(smile_torch PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:-Wall>
        $<$<COMPILE_LANGUAGE:CXX>:-Wextra>
        $<$<COMPILE_LANGUAGE:CXX>:-Wpedantic>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-unused-parameter>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated-declarations>
        # Macro-style one-liners (MAKE_TENSOR / BORROW_MODULE) trigger this.
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-misleading-indentation>
    )
    if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        target_compile_options(smile_torch PRIVATE
            $<$<COMPILE_LANGUAGE:CXX>:-Wno-maybe-uninitialized>)
    endif()
    if(CMAKE_BUILD_TYPE STREQUAL "Release")
        target_compile_options(smile_torch PRIVATE
            $<$<COMPILE_LANGUAGE:CXX>:-O3>
            $<$<COMPILE_LANGUAGE:CXX>:-DNDEBUG>)
    endif()
endif()

# --------------------------------------------------------------------------
# Build a small smoke-test executable (only when BUILD_TESTING is ON)
# --------------------------------------------------------------------------
include(CTest)
if(BUILD_TESTING)
    add_executable(smile_torch_test
        test/smile_torch_test.c
    )
    target_include_directories(smile_torch_test PRIVATE
        # The test includes <smile_torch.h> which lives in this directory.
        ${CMAKE_CURRENT_SOURCE_DIR}
    )
    target_link_libraries(smile_torch_test PRIVATE smile_torch)
    add_test(NAME SmileTorchSmokeTest COMMAND smile_torch_test)
endif()

# --------------------------------------------------------------------------
# Install rules
# --------------------------------------------------------------------------
include(GNUInstallDirs)

install(TARGETS smile_torch
    EXPORT  SmileTorchTargets
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}   # Windows DLL
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}   # Unix .so
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}   # Windows .lib / Unix .a
)

install(FILES smile_torch.h
    DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})

install(EXPORT SmileTorchTargets
    FILE        SmileTorchTargets.cmake
    NAMESPACE   smile::
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/SmileTorch)

# Generate and install a config file so downstream projects can use
# find_package(SmileTorch).
include(CMakePackageConfigHelpers)
configure_package_config_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake/SmileTorchConfig.cmake.in"
    "${CMAKE_CURRENT_BINARY_DIR}/SmileTorchConfig.cmake"
    INSTALL_DESTINATION "${CMAKE_INSTALL_LIBDIR}/cmake/SmileTorch"
)
write_basic_package_version_file(
    "${CMAKE_CURRENT_BINARY_DIR}/SmileTorchConfigVersion.cmake"
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY SameMajorVersion
)
install(FILES
    "${CMAKE_CURRENT_BINARY_DIR}/SmileTorchConfig.cmake"
    "${CMAKE_CURRENT_BINARY_DIR}/SmileTorchConfigVersion.cmake"
    DESTINATION "${CMAKE_INSTALL_LIBDIR}/cmake/SmileTorch")

