/*
 * Copyright (c) 2020-2023, NVIDIA CORPORATION.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// This file exposes a C API to manage dynamic cubin loading.
// It is meant to be included in a .so file that is loaded dynamically
// as a python module, where the following functions can be referenced.
//
// The python code is expected to call `FlashInferSetCubinCallback` when
// loading the module. This sets a callback that can be called by the
// library to load a cubin.
// The callback is expected to call `FlashInferSetCurrentCubin` with the
// cubin data and the size of the cubin.
//
// Internally the library can just rely on the `getCubin` function to encapsulate
// this back and forth.
//
// This is a C API so that we can use it with ctypes and don't rely on pybind11,
// because pybind11 support of arbitrary callback requires >=python3.11.

// Callback into the python function that will get us the requested cubin.
void (*callbackGetCubin)(const char* path, const char* sha256) = nullptr;

// Set the python callback, called by the python code using ctypes.
extern "C" void FlashInferSetCubinCallback(void (*callback)(const char* path, const char* sha256)) {
  callbackGetCubin = callback;
}

// Thread-local variable that stores the current cubin.
// It is reset on every call to `getCubin()`.
thread_local std::string current_cubin;

// Called by the callback to set the current cubin.
extern "C" void FlashInferSetCurrentCubin(const char* binary, int size) {
  current_cubin = std::string(binary, size);
}

// Get the cubin from the python callback.
// This is the API for the native library to use.
std::string getCubin(const std::string& name, const std::string& sha256) {
  if (!callbackGetCubin) {
    throw std::runtime_error("FlashInferSetCubinCallback not set");
  }
  callbackGetCubin(name.c_str(), sha256.c_str());
  return current_cubin;
}
