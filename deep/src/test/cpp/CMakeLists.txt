# =============================================================================
# CMakeLists.txt — smile_torch C API tests
#
# Can be built either as part of the top-level build (via BUILD_TESTING=ON)
# or standalone after smile_torch has been installed:
#
#   cmake -S deep/src/test/cpp \
#         -B build/cpp-test \
#         -DCMAKE_BUILD_TYPE=Release \
#         -DSmileTorch_ROOT=<install-prefix>
#   cmake --build build/cpp-test
#   ctest --test-dir build/cpp-test
# =============================================================================

cmake_minimum_required(VERSION 3.22)

project(smile_torch_tests LANGUAGES C)

# --------------------------------------------------------------------------
# Locate smile_torch (installed or in-tree)
# --------------------------------------------------------------------------
# When built as a standalone project find the installed package.
# When included from the parent CMakeLists the smile_torch target already exists.
if(NOT TARGET smile_torch)
    find_package(SmileTorch REQUIRED)
endif()

include(CTest)

add_executable(smile_torch_test smile_torch_test.c)

target_link_libraries(smile_torch_test PRIVATE smile_torch)

# smile_torch.h is exported as part of the smile_torch target's usage requirements
# (INTERFACE_INCLUDE_DIRECTORIES), so no explicit include_directories needed here.

add_test(NAME SmileTorchSmokeTest COMMAND smile_torch_test)

