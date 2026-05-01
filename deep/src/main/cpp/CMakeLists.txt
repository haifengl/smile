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
    LANGUAGES CXX)

# --------------------------------------------------------------------------
# C++ standard — libtorch 2.x requires C++17; keep parity with libtorch
# --------------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 17)
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

# Resolve relative paths so find_package can locate TorchConfig.cmake.
get_filename_component(LIBTORCH_ROOT "${LIBTORCH_ROOT}" ABSOLUTE)
message(STATUS "LIBTORCH_ROOT = ${LIBTORCH_ROOT}")

list(APPEND CMAKE_PREFIX_PATH "${LIBTORCH_ROOT}/share/cmake/Torch")

find_package(Torch REQUIRED)

message(STATUS "Torch version  : ${Torch_VERSION}")
message(STATUS "Torch libraries: ${TORCH_LIBRARIES}")
message(STATUS "Torch CXX flags: ${TORCH_CXX_FLAGS}")

# Apply flags required by libtorch (e.g. -D_GLIBCXX_USE_CXX11_ABI=1)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${TORCH_CXX_FLAGS}")

# --------------------------------------------------------------------------
# Optional CUDA support
# --------------------------------------------------------------------------
find_package(CUDAToolkit QUIET)
if(CUDAToolkit_FOUND)
    message(STATUS "CUDA found: ${CUDAToolkit_VERSION}")
    add_compile_definitions(USE_CUDA)
else()
    message(STATUS "CUDA not found — building CPU-only")
endif()

# --------------------------------------------------------------------------
# smile_torch shared library target
# --------------------------------------------------------------------------
add_library(smile_torch SHARED
    smile_torch.cpp
)

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
    target_compile_options(smile_torch PRIVATE
        -Wall -Wextra -Wpedantic
        -Wno-unused-parameter       # libtorch headers trigger this
        -Wno-deprecated-declarations
    )
    if(CMAKE_BUILD_TYPE STREQUAL "Release")
        target_compile_options(smile_torch PRIVATE -O3 -DNDEBUG)
    endif()
endif()

# --------------------------------------------------------------------------
# Build a small smoke-test executable (only when BUILD_TESTING is ON)
# --------------------------------------------------------------------------
include(CTest)
if(BUILD_TESTING)
    # Test sources live in deep/src/test/cpp — two levels up from this file,
    # then back down into src/test/cpp.
    set(SMILE_TORCH_TEST_DIR
        "${CMAKE_CURRENT_SOURCE_DIR}/../../test/cpp"
        CACHE PATH "Directory containing smile_torch C API tests")
    get_filename_component(SMILE_TORCH_TEST_DIR "${SMILE_TORCH_TEST_DIR}" ABSOLUTE)

    add_executable(smile_torch_test
        "${SMILE_TORCH_TEST_DIR}/smile_torch_test.c"
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

