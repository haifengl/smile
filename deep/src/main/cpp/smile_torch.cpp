/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * smile_torch.cpp — Hourglass C API implementation over libtorch.
 *
 * All C handles are thin heap wrappers around the corresponding libtorch
 * C++ objects.  Every public function catches std::exception and stores the
 * message so that callers can retrieve it via smile_last_error().
 */

#define SMILE_TORCH_BUILD

#include "smile_torch.h"

// ── libtorch headers ──────────────────────────────────────────────────────────
#include <torch/csrc/api/include/torch/torch.h>
#include <torch/csrc/api/include/torch/nn.h>
#include <torch/csrc/api/include/torch/optim.h>
#include <torch/csrc/api/include/torch/serialize.h>
#include <c10/core/ScalarType.h>
#include <c10/core/Device.h>
#include <c10/core/DeviceType.h>

// ── standard headers ──────────────────────────────────────────────────────────
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <stdexcept>
#include <string>
#include <vector>
#include <unordered_map>
#include <unordered_set>
#include <optional>
#include <limits>
#include <cmath>
#include <cstring>
#include <atomic>

// ── CUDA introspection (compiled only when CUDA is present) ───────────────────
#ifdef USE_CUDA
#  include <cuda_runtime.h>
#  include <c10/cuda/CUDACachingAllocator.h>
#  include <c10/cuda/CUDAGuard.h>
#  include <ATen/cuda/CUDAContext.h>
#  include "smile_gated_delta.cuh"
#  ifdef USE_FLASHINFER
#    include "smile_flashinfer_cuda.h"
#    include "smile_flashinfer_workspace.h"
#  endif
#endif

#ifdef USE_NCCL
#  include <nccl.h>
#endif

// =============================================================================
// Error state (thread-local so concurrent callers don't stomp one another)
// =============================================================================

static thread_local std::string g_last_error;

static void set_error(const char *msg) {
    g_last_error = msg ? msg : "(null)";
}
static void set_error(const std::string &msg) {
    g_last_error = msg;
}
static void clear_error() {
    g_last_error.clear();
}

extern "C" void smile_torch_set_error(const char *msg) { set_error(msg); }
extern "C" void smile_torch_clear_error(void) { clear_error(); }

#ifndef USE_CUDA
static void set_error_no_cuda_build() {
    set_error("smile_torch was built without CUDA (USE_CUDA not enabled at compile time)");
}
#endif

/** Helper macro — wraps a block and converts any C++ exception to an error. */
#define ST_TRY_BEGIN  try { clear_error();
#define ST_TRY_END    } catch (const std::exception &ex) { \
                          set_error(ex.what()); \
                      } catch (...) { \
                          set_error("unknown C++ exception"); \
                      }

/** Same but with a return value on failure. */
#define ST_TRY_RETURN(fail_val, ...) \
    ST_TRY_BEGIN __VA_ARGS__ ST_TRY_END return (fail_val)

extern "C" const char *smile_last_error(void) {
    return g_last_error.c_str();
}

// =============================================================================
// Helper: ScalarType / DeviceType / Layout conversions
// =============================================================================

static c10::ScalarType to_scalar_type(ST_DType dt) {
    switch (dt) {
        case ST_DTYPE_BYTE:            return c10::ScalarType::Byte;
        case ST_DTYPE_CHAR:            return c10::ScalarType::Char;
        case ST_DTYPE_SHORT:           return c10::ScalarType::Short;
        case ST_DTYPE_INT:             return c10::ScalarType::Int;
        case ST_DTYPE_LONG:            return c10::ScalarType::Long;
        case ST_DTYPE_HALF:            return c10::ScalarType::Half;
        case ST_DTYPE_FLOAT:           return c10::ScalarType::Float;
        case ST_DTYPE_DOUBLE:          return c10::ScalarType::Double;
        case ST_DTYPE_COMPLEX_HALF:    return c10::ScalarType::ComplexHalf;
        case ST_DTYPE_COMPLEX_FLOAT:   return c10::ScalarType::ComplexFloat;
        case ST_DTYPE_COMPLEX_DOUBLE:  return c10::ScalarType::ComplexDouble;
        case ST_DTYPE_BOOL:            return c10::ScalarType::Bool;
        case ST_DTYPE_QUINT8:          return c10::ScalarType::QUInt8;
        case ST_DTYPE_QINT8:           return c10::ScalarType::QInt8;
        case ST_DTYPE_QINT32:          return c10::ScalarType::QInt32;
        case ST_DTYPE_BFLOAT16:        return c10::ScalarType::BFloat16;
        case ST_DTYPE_QUInt4x2:        return c10::ScalarType::QUInt4x2;
        case ST_DTYPE_QUInt2x4:        return c10::ScalarType::QUInt2x4;
        case ST_DTYPE_Bits1x8:         return c10::ScalarType::Bits1x8;
        case ST_DTYPE_Bits2x4:         return c10::ScalarType::Bits2x4;
        case ST_DTYPE_Bits4x2:         return c10::ScalarType::Bits4x2;
        case ST_DTYPE_Bits8:           return c10::ScalarType::Bits8;
        case ST_DTYPE_Bits16:          return c10::ScalarType::Bits16;
        case ST_DTYPE_Float8_e5m2:     return c10::ScalarType::Float8_e5m2;
        case ST_DTYPE_Float8_e4m3fn:   return c10::ScalarType::Float8_e4m3fn;
        case ST_DTYPE_Float8_e5m2fnuz: return c10::ScalarType::Float8_e5m2fnuz;
        case ST_DTYPE_Float8_e4m3fnuz: return c10::ScalarType::Float8_e4m3fnuz;
        case ST_DTYPE_UInt16:          return c10::ScalarType::UInt16;
        case ST_DTYPE_UInt32:          return c10::ScalarType::UInt32;
        case ST_DTYPE_UInt64:          return c10::ScalarType::UInt64;
        default:                       return c10::ScalarType::Undefined;
    }
}

static c10::DeviceType to_device_type(int dt) {
    switch (dt) {
        case ST_DEVICE_CUDA: return c10::DeviceType::CUDA;
        case ST_DEVICE_MPS:  return c10::DeviceType::MPS;
        default:             return c10::DeviceType::CPU;
    }
}

static c10::Layout to_layout(ST_Layout l) {
    switch (l) {
        case ST_LAYOUT_SPARSE_COO: return c10::Layout::Sparse;
        case ST_LAYOUT_MKLDNN:     return c10::Layout::Mkldnn;
        default:                   return c10::Layout::Strided;
    }
}

// =============================================================================
// Opaque wrapper structs
// =============================================================================

struct ST_Tensor_        { at::Tensor t; };
struct ST_TensorOptions_ { at::TensorOptions opts; };
struct ST_Scalar_        { at::Scalar s; };
struct ST_Device_        { c10::Device d; };
struct ST_Module_        { std::shared_ptr<torch::nn::Module> m; };
struct ST_ModuleList_    { std::shared_ptr<torch::nn::ModuleListImpl> ml; };
struct ST_Conv2d_        { torch::nn::Conv2d mod; };
struct ST_BatchNorm1d_   { torch::nn::BatchNorm1d mod; };
struct ST_BatchNorm2d_   { torch::nn::BatchNorm2d mod; };
struct ST_Dropout_       { torch::nn::Dropout mod; };
struct ST_GroupNorm_     { torch::nn::GroupNorm mod; };
struct ST_MaxPool2d_     { torch::nn::MaxPool2d mod; };
struct ST_AvgPool2d_     { torch::nn::AvgPool2d mod; };
struct ST_AdaptiveAvgPool2d_ { torch::nn::AdaptiveAvgPool2d mod; };
struct ST_Optimizer_     { std::unique_ptr<torch::optim::Optimizer> opt; };
struct ST_InputArchive_  { torch::serialize::InputArchive  archive; };
struct ST_OutputArchive_ { torch::serialize::OutputArchive archive; };
struct ST_NoGradGuard_   { torch::NoGradGuard guard; };
struct ST_TensorIndex_   { torch::indexing::TensorIndex idx; };
struct ST_TensorIndexVec_{ std::vector<torch::indexing::TensorIndex> vec; };
struct ST_TensorVec_     { std::vector<at::Tensor> vec; };
struct ST_Slice_         { torch::indexing::Slice slice; };

// Linear / Embedding shells that allocate with torch::empty and optionally skip
// reset_parameters (Kaiming / normal). Used for inference weight load.
struct EmptyLinearImpl : torch::nn::Cloneable<EmptyLinearImpl> {
    torch::Tensor weight{nullptr};
    torch::Tensor bias{nullptr};
    int64_t in_features;
    int64_t out_features;
    bool with_bias;
    c10::Device device;

    EmptyLinearImpl(int64_t in_features_, int64_t out_features_, bool bias_,
                    c10::Device device_ = c10::Device(c10::kCPU))
        : in_features(in_features_),
          out_features(out_features_),
          with_bias(bias_),
          device(device_) {
        reset();
    }

    void reset() override {
        auto opts = torch::TensorOptions().device(device);
        weight = register_parameter(
            "weight", torch::empty({out_features, in_features}, opts));
        if (with_bias) {
            bias = register_parameter("bias", torch::empty({out_features}, opts));
        } else {
            bias = register_parameter("bias", {}, /*requires_grad=*/false);
        }
    }

    void reset_parameters() {
        torch::nn::init::kaiming_uniform_(
            weight, std::sqrt(5)); // NOLINT(cppcoreguidelines-avoid-magic-numbers)
        if (bias.defined()) {
            auto [fan_in, fan_out] =
                torch::nn::init::_calculate_fan_in_and_fan_out(weight);
            const auto bound = 1.0 / std::sqrt(static_cast<double>(fan_in));
            torch::nn::init::uniform_(bias, -bound, bound);
        }
    }

    torch::Tensor forward(const torch::Tensor& input) {
        return torch::linear(input, weight, bias);
    }
};
TORCH_MODULE(EmptyLinear);

struct EmptyEmbeddingImpl : torch::nn::Cloneable<EmptyEmbeddingImpl> {
    torch::Tensor weight{nullptr};
    int64_t num_embeddings;
    int64_t embedding_dim;
    c10::Device device;

    EmptyEmbeddingImpl(int64_t num_embeddings_, int64_t embedding_dim_,
                       c10::Device device_ = c10::Device(c10::kCPU))
        : num_embeddings(num_embeddings_),
          embedding_dim(embedding_dim_),
          device(device_) {
        reset();
    }

    void reset() override {
        auto opts = torch::TensorOptions().device(device);
        weight = register_parameter(
            "weight", torch::empty({num_embeddings, embedding_dim}, opts));
    }

    void reset_parameters() {
        torch::nn::init::normal_(weight);
    }

    torch::Tensor forward(const torch::Tensor& input) {
        return torch::embedding(weight, input);
    }
};
TORCH_MODULE(EmptyEmbedding);

struct ST_Linear_    { EmptyLinear mod; };
struct ST_Embedding_ { EmptyEmbedding mod; };

// =============================================================================
// Helpers: build TensorOptions from handle (NULL → default)
// =============================================================================

static at::TensorOptions get_opts(ST_TensorOptions opts) {
    return opts ? opts->opts : at::TensorOptions();
}

// =============================================================================
// Optimizers — helpers
// =============================================================================

static std::vector<at::Tensor> extract_params(ST_TensorVec v) {
    return v ? v->vec : std::vector<at::Tensor>{};
}

// =============================================================================
// Tensor — helper for shape vector
// =============================================================================

static std::vector<int64_t> to_shape(const int64_t *shape, int ndim) {
    return std::vector<int64_t>(shape, shape + ndim);
}

static int64_t shape_numel(const std::vector<int64_t> &sizes) {
    int64_t n = 1;
    for (auto s : sizes) n *= s;
    return n;
}

/**
 * Copies a host buffer into a fresh CPU tensor of the given dtype.
 * Avoids {@code torch::from_blob(...).clone()}, which can trip PyObjectSlot
 * asserts in libtorch builds without a Python interpreter (especially for
 * empty tensors after the process-wide default dtype/device has been changed).
 */
template <typename T>
static ST_Tensor tensor_from_host(const T *data, const int64_t *shape, int ndim,
                                  c10::ScalarType dtype) {
    auto sizes = to_shape(shape, ndim);
    ST_TRY_BEGIN
        auto opts = torch::TensorOptions().dtype(dtype).device(torch::kCPU);
        auto t = torch::empty(sizes, opts);
        int64_t n = shape_numel(sizes);
        if (n > 0) {
            if (!data) {
                set_error("tensor_from_host: null data with non-empty shape");
                return nullptr;
            }
            std::memcpy(t.data_ptr<T>(), data, static_cast<size_t>(n) * sizeof(T));
        }
        return new ST_Tensor_{ std::move(t) };
    ST_TRY_END
    return nullptr;
}

static std::optional<at::Scalar> maybe_scalar(int has, ST_Scalar s) {
    if (has && s) return s->s;
    return std::nullopt;
}

static std::vector<int64_t> param2(const int64_t *p, int64_t def) {
    if (p) return {p[0], p[1]};
    return {def, def};
}

// =============================================================================
// CUDA / Device utilities
// =============================================================================

extern "C" {

int smile_cuda_is_available(void) {
    ST_TRY_BEGIN
        return torch::cuda::is_available() ? 1 : 0;
    ST_TRY_END
    return 0;
}

int smile_cuda_device_count(void) {
    ST_TRY_BEGIN
        return static_cast<int>(torch::cuda::device_count());
    ST_TRY_END
    return 0;
}

int smile_cuda_runtime_version(char *buf, int buf_len) {
    if (!buf || buf_len <= 0) return -1;
#ifdef USE_CUDA
    ST_TRY_BEGIN
        int ver = 0;
        cudaError_t err = cudaRuntimeGetVersion(&ver);
        if (err != cudaSuccess) {
            set_error(cudaGetErrorString(err));
            return -1;
        }
        int major = ver / 1000;
        int minor = (ver % 1000) / 10;
        std::snprintf(buf, buf_len, "%d.%d", major, minor);
        return 0;
    ST_TRY_END
    return -1;
#else
    std::snprintf(buf, buf_len, "N/A (no CUDA build)");
    return 0;
#endif
}

int smile_cuda_device_name(int device_index, char *buf, int buf_len) {
    if (!buf || buf_len <= 0) return -1;
#ifdef USE_CUDA
    ST_TRY_BEGIN
        cudaDeviceProp prop{};
        cudaError_t err = cudaGetDeviceProperties(&prop, device_index);
        if (err != cudaSuccess) {
            set_error(cudaGetErrorString(err));
            return -1;
        }
        std::snprintf(buf, buf_len, "%s", prop.name);
        return 0;
    ST_TRY_END
    return -1;
#else
    std::snprintf(buf, buf_len, "N/A");
    return 0;
#endif
}

int64_t smile_cuda_total_memory(int device_index) {
#ifdef USE_CUDA
    ST_TRY_BEGIN
        cudaDeviceProp prop{};
        cudaError_t err = cudaGetDeviceProperties(&prop, device_index);
        if (err != cudaSuccess) {
            set_error(cudaGetErrorString(err));
            return -1;
        }
        return static_cast<int64_t>(prop.totalGlobalMem);
    ST_TRY_END
#else
    set_error_no_cuda_build();
#endif
    return -1;
}

int smile_cuda_mem_get_info(int device_index, int64_t *free_bytes, int64_t *total_bytes) {
    if (!free_bytes || !total_bytes) {
        set_error("smile_cuda_mem_get_info: null output pointer");
        return -1;
    }
#ifdef USE_CUDA
    ST_TRY_BEGIN
        // Prefer LibTorch's path (CUDAGuard + cudaMemGetInfo) so device
        // context matches the caching allocator used for KV / tensors.
        auto *alloc = c10::cuda::CUDACachingAllocator::get();
        if (!alloc) {
            set_error("CUDACachingAllocator is not initialized");
            return -1;
        }
        auto info = alloc->getMemoryInfo(static_cast<c10::DeviceIndex>(device_index));
        *free_bytes = static_cast<int64_t>(info.first);
        *total_bytes = static_cast<int64_t>(info.second);
        return 0;
    ST_TRY_END
#else
    set_error_no_cuda_build();
#endif
    return -1;
}

void smile_cuda_empty_cache(void) {
#ifdef USE_CUDA
    ST_TRY_BEGIN
        // cuBLAS workspaces are DataPtrs in the caching allocator; clear them
        // first or emptyCache cannot return that memory to the driver.
        at::cuda::clearCublasWorkspaces();
        c10::cuda::CUDACachingAllocator::emptyCache();
    ST_TRY_END
#endif
}

int smile_cuda_allocator_stats(int device_index, int64_t *allocated_bytes,
                               int64_t *reserved_bytes) {
    if (!allocated_bytes || !reserved_bytes) {
        set_error("smile_cuda_allocator_stats: null output pointer");
        return -1;
    }
#ifdef USE_CUDA
    ST_TRY_BEGIN
        auto stats = c10::cuda::CUDACachingAllocator::getDeviceStats(
                static_cast<c10::DeviceIndex>(device_index));
        // StatType::AGGREGATE == 0
        *allocated_bytes = stats.allocated_bytes[0].current;
        *reserved_bytes = stats.reserved_bytes[0].current;
        return 0;
    ST_TRY_END
#else
    set_error_no_cuda_build();
#endif
    return -1;
}

int smile_cuda_is_bf16_supported(void) {
#ifdef USE_CUDA
    ST_TRY_BEGIN
        int dev = at::cuda::current_device();
        cudaDeviceProp prop{};
        cudaError_t err = cudaGetDeviceProperties(&prop, dev);
        if (err != cudaSuccess) {
            set_error(cudaGetErrorString(err));
            return 0;
        }
        return (prop.major >= 8) ? 1 : 0;
    ST_TRY_END
#endif
    return 0;
}

int smile_cuda_compute_capability(int device_index, int *major, int *minor) {
    if (!major || !minor) {
        set_error("smile_cuda_compute_capability: null out-parameter");
        return -1;
    }
#ifdef USE_CUDA
    ST_TRY_BEGIN
        cudaDeviceProp prop{};
        cudaError_t err = cudaGetDeviceProperties(&prop, device_index);
        if (err != cudaSuccess) {
            set_error(cudaGetErrorString(err));
            return -1;
        }
        *major = prop.major;
        *minor = prop.minor;
        return 0;
    ST_TRY_END
    return -1;
#else
    set_error_no_cuda_build();
    return -1;
#endif
}

int smile_mps_is_available(void) {
    ST_TRY_BEGIN
        return at::hasMPS() ? 1 : 0;
    ST_TRY_END
    return 0;
}

void smile_mps_empty_cache(void) {
    /* torch::mps does not expose a public emptyCache() API; no-op. */
}

int  smile_get_num_threads(void) {
    ST_TRY_BEGIN
        return at::get_num_threads();
    ST_TRY_END
    return 1;
}

void smile_set_num_threads(int n) {
    ST_TRY_BEGIN
        at::set_num_threads(n);
    ST_TRY_END
}

ST_Device smile_device_create(int device_type, int8_t index) {
    ST_TRY_BEGIN
        auto *p = new ST_Device_{ c10::Device(to_device_type(device_type),
                                              static_cast<c10::DeviceIndex>(index)) };
        return p;
    ST_TRY_END
    return nullptr;
}

void smile_device_free(ST_Device d) { delete d; }

int   smile_device_is_cpu (ST_Device d) { return d && d->d.is_cpu()  ? 1 : 0; }
int   smile_device_is_cuda(ST_Device d) { return d && d->d.is_cuda() ? 1 : 0; }
int   smile_device_is_mps (ST_Device d) { return d && d->d.is_mps()  ? 1 : 0; }
int8_t smile_device_index (ST_Device d) {
    return d ? static_cast<int8_t>(d->d.index()) : 0;
}

int smile_device_str(ST_Device d, char *buf, int buf_len) {
    if (!d || !buf || buf_len <= 0) return -1;
    std::snprintf(buf, buf_len, "%s", d->d.str().c_str());
    return 0;
}

// =============================================================================
// TensorOptions
// =============================================================================

ST_TensorOptions smile_tensor_options_create(void) {
    ST_TRY_BEGIN
        return new ST_TensorOptions_{ at::TensorOptions() };
    ST_TRY_END
    return nullptr;
}

void smile_tensor_options_free(ST_TensorOptions opts) { delete opts; }

ST_TensorOptions smile_tensor_options_dtype(ST_TensorOptions opts, ST_DType dtype) {
    if (!opts) return nullptr;
    if (dtype == ST_DTYPE_UNDEFINED)
        opts->opts = opts->opts.dtype(std::optional<at::ScalarType>{});
    else
        opts->opts = opts->opts.dtype(to_scalar_type(dtype));
    return opts;
}

ST_TensorOptions smile_tensor_options_device(ST_TensorOptions opts, ST_Device device) {
    if (!opts) return nullptr;
    if (device)
        opts->opts = opts->opts.device(device->d);
    else
        opts->opts = opts->opts.device(std::optional<c10::Device>{});
    return opts;
}

ST_TensorOptions smile_tensor_options_layout(ST_TensorOptions opts, ST_Layout layout) {
    if (!opts) return nullptr;
    opts->opts = opts->opts.layout(to_layout(layout));
    return opts;
}

ST_TensorOptions smile_tensor_options_requires_grad(ST_TensorOptions opts, int requires_grad) {
    if (!opts) return nullptr;
    if (requires_grad < 0)
        opts->opts = opts->opts.requires_grad(std::optional<bool>{});
    else
        opts->opts = opts->opts.requires_grad(static_cast<bool>(requires_grad));
    return opts;
}

// =============================================================================
// Scalar
// =============================================================================

ST_Scalar smile_scalar_from_int  (int64_t value) {
    ST_TRY_BEGIN return new ST_Scalar_{ at::Scalar(value) }; ST_TRY_END return nullptr;
}
ST_Scalar smile_scalar_from_float(double value) {
    ST_TRY_BEGIN return new ST_Scalar_{ at::Scalar(value) }; ST_TRY_END return nullptr;
}
void smile_scalar_free(ST_Scalar s) { delete s; }

// =============================================================================
// Tensor — Construction
// =============================================================================

void smile_tensor_free  (ST_Tensor t) { delete t; }
ST_Tensor smile_tensor_clone(ST_Tensor t) {
    if (!t) return nullptr;
    ST_TRY_BEGIN return new ST_Tensor_{ t->t.clone() }; ST_TRY_END return nullptr;
}

#define MAKE_TENSOR(expr) \
    ST_TRY_BEGIN return new ST_Tensor_{ (expr) }; ST_TRY_END return nullptr

ST_Tensor smile_tensor_eye(const int64_t *shape, int ndim, ST_TensorOptions opts) {
    if (!shape || ndim < 1) return nullptr;
    if (ndim == 1) {
        MAKE_TENSOR(torch::eye(shape[0], get_opts(opts)));
    } else {
        MAKE_TENSOR(torch::eye(shape[0], shape[1], get_opts(opts)));
    }
}
ST_Tensor smile_tensor_full(const int64_t *shape, int ndim, ST_Scalar value, ST_TensorOptions opts) {
    if (!value) return nullptr;
    MAKE_TENSOR(torch::full(to_shape(shape, ndim), value->s, get_opts(opts)));
}
ST_Tensor smile_tensor_empty(const int64_t *shape, int ndim, ST_TensorOptions opts) {
    MAKE_TENSOR(torch::empty(to_shape(shape, ndim), get_opts(opts)));
}
ST_Tensor smile_tensor_zeros(const int64_t *shape, int ndim, ST_TensorOptions opts) {
    MAKE_TENSOR(torch::zeros(to_shape(shape, ndim), get_opts(opts)));
}
ST_Tensor smile_tensor_ones(const int64_t *shape, int ndim, ST_TensorOptions opts) {
    MAKE_TENSOR(torch::ones(to_shape(shape, ndim), get_opts(opts)));
}
ST_Tensor smile_tensor_rand(const int64_t *shape, int ndim, ST_TensorOptions opts) {
    MAKE_TENSOR(torch::rand(to_shape(shape, ndim), get_opts(opts)));
}
ST_Tensor smile_tensor_randn(const int64_t *shape, int ndim, ST_TensorOptions opts) {
    MAKE_TENSOR(torch::randn(to_shape(shape, ndim), get_opts(opts)));
}
ST_Tensor smile_tensor_arange(double start, double end, double step, ST_TensorOptions opts) {
    MAKE_TENSOR(torch::arange(at::Scalar(start), at::Scalar(end), at::Scalar(step), get_opts(opts)));
}

ST_Tensor smile_tensor_from_bool  (const uint8_t *data, const int64_t *shape, int ndim) {
    auto sizes = to_shape(shape, ndim);
    ST_TRY_BEGIN
        auto opts = torch::TensorOptions().dtype(at::kBool).device(torch::kCPU);
        auto t = torch::empty(sizes, opts);
        int64_t n = shape_numel(sizes);
        if (n > 0) {
            if (!data) {
                set_error("smile_tensor_from_bool: null data with non-empty shape");
                return nullptr;
            }
            // Bool storage is 1 byte per element; copy without data_ptr<uint8_t>().
            std::memcpy(t.data_ptr<bool>(), data, static_cast<size_t>(n));
        }
        return new ST_Tensor_{ std::move(t) };
    ST_TRY_END
    return nullptr;
}
ST_Tensor smile_tensor_from_byte  (const int8_t  *data, const int64_t *shape, int ndim) {
    return tensor_from_host(data, shape, ndim, at::kChar);
}
ST_Tensor smile_tensor_from_short (const int16_t *data, const int64_t *shape, int ndim) {
    return tensor_from_host(data, shape, ndim, at::kShort);
}
ST_Tensor smile_tensor_from_int   (const int32_t *data, const int64_t *shape, int ndim) {
    return tensor_from_host(data, shape, ndim, at::kInt);
}
ST_Tensor smile_tensor_from_long  (const int64_t *data, const int64_t *shape, int ndim) {
    return tensor_from_host(data, shape, ndim, at::kLong);
}
ST_Tensor smile_tensor_from_float (const float   *data, const int64_t *shape, int ndim) {
    return tensor_from_host(data, shape, ndim, at::kFloat);
}
ST_Tensor smile_tensor_from_double(const double  *data, const int64_t *shape, int ndim) {
    return tensor_from_host(data, shape, ndim, at::kDouble);
}

// =============================================================================
// Tensor — Metadata
// =============================================================================

int     smile_tensor_is_null       (ST_Tensor t) { return (!t || !t->t.defined()) ? 1 : 0; }
int     smile_tensor_is_view       (ST_Tensor t) { return t ? (t->t.is_view() ? 1 : 0) : 0; }
int     smile_tensor_dim           (ST_Tensor t) { return t ? static_cast<int>(t->t.dim()) : 0; }
int64_t smile_tensor_size          (ST_Tensor t, int64_t dim) { return t ? t->t.size(dim) : 0; }
ST_DType smile_tensor_dtype        (ST_Tensor t) {
    if (!t) return ST_DTYPE_UNDEFINED;
    auto st = t->t.scalar_type();
    return static_cast<ST_DType>(static_cast<int8_t>(st));
}
int smile_tensor_requires_grad     (ST_Tensor t) { return t ? (t->t.requires_grad() ? 1 : 0) : 0; }
void smile_tensor_set_requires_grad(ST_Tensor t, int rg) {
    if (t) t->t.set_requires_grad(static_cast<bool>(rg));
}
int smile_tensor_is_training(ST_Tensor t) { return 0; /* tensors have no training flag */ }

ST_Device smile_tensor_device(ST_Tensor t) {
    if (!t) return nullptr;
    ST_TRY_BEGIN return new ST_Device_{ t->t.device() }; ST_TRY_END return nullptr;
}

int smile_tensor_shape(ST_Tensor t, int64_t *shape, int max_dims) {
    if (!t || !shape) return 0;
    int ndim = static_cast<int>(t->t.dim());
    int n = std::min(ndim, max_dims);
    for (int i = 0; i < n; i++) shape[i] = t->t.size(i);
    return ndim;
}

// =============================================================================
// Tensor — Data Pointers
// =============================================================================

uint8_t *smile_tensor_data_ptr_bool  (ST_Tensor t) { return t ? reinterpret_cast<uint8_t*>(t->t.data_ptr<bool>()) : nullptr; }
int8_t  *smile_tensor_data_ptr_byte  (ST_Tensor t) { return t ? t->t.data_ptr<int8_t>()  : nullptr; }
int16_t *smile_tensor_data_ptr_short (ST_Tensor t) { return t ? t->t.data_ptr<int16_t>() : nullptr; }
int32_t *smile_tensor_data_ptr_int   (ST_Tensor t) { return t ? t->t.data_ptr<int32_t>() : nullptr; }
int64_t *smile_tensor_data_ptr_long  (ST_Tensor t) { return t ? t->t.data_ptr<int64_t>() : nullptr; }
float   *smile_tensor_data_ptr_float (ST_Tensor t) { return t ? t->t.data_ptr<float>()   : nullptr; }
double  *smile_tensor_data_ptr_double(ST_Tensor t) { return t ? t->t.data_ptr<double>()  : nullptr; }

int64_t smile_tensor_nbytes(ST_Tensor t) {
    return t ? static_cast<int64_t>(t->t.nbytes()) : 0;
}

void *smile_tensor_data_ptr(ST_Tensor t) {
    return t ? t->t.data_ptr() : nullptr;
}

// =============================================================================
// Tensor — Item
// =============================================================================

uint8_t smile_tensor_item_bool  (ST_Tensor t) { return t ? static_cast<uint8_t>(t->t.item<bool>())    : 0; }
int8_t  smile_tensor_item_byte  (ST_Tensor t) { return t ? t->t.item<int8_t>()  : 0; }
int16_t smile_tensor_item_short (ST_Tensor t) { return t ? t->t.item<int16_t>() : 0; }
int32_t smile_tensor_item_int   (ST_Tensor t) { return t ? t->t.item<int32_t>() : 0; }
int64_t smile_tensor_item_long  (ST_Tensor t) { return t ? t->t.item<int64_t>() : 0; }
float   smile_tensor_item_float (ST_Tensor t) { return t ? t->t.item<float>()   : 0.0f; }
double  smile_tensor_item_double(ST_Tensor t) { return t ? t->t.item<double>()  : 0.0; }

// =============================================================================
// Tensor — Type / Device casting
// =============================================================================

ST_Tensor smile_tensor_to_dtype(ST_Tensor t, ST_DType dtype) {
    if (!t) return nullptr;
    MAKE_TENSOR(t->t.to(to_scalar_type(dtype)));
}

ST_Tensor smile_tensor_to_device(ST_Tensor t, ST_Device device, ST_DType dtype) {
    if (!t || !device) return nullptr;
    if (dtype == ST_DTYPE_UNDEFINED) {
        MAKE_TENSOR(t->t.to(device->d));
    } else {
        MAKE_TENSOR(t->t.to(device->d, to_scalar_type(dtype)));
    }
}

// =============================================================================
// Tensor — Shape manipulation
// =============================================================================

ST_Tensor smile_tensor_reshape   (ST_Tensor t, const int64_t *s, int n) { MAKE_TENSOR(t->t.reshape(to_shape(s,n))); }
ST_Tensor smile_tensor_view      (ST_Tensor t, const int64_t *s, int n) { MAKE_TENSOR(t->t.view(to_shape(s,n))); }
ST_Tensor smile_tensor_flatten   (ST_Tensor t, int64_t a, int64_t b)    { MAKE_TENSOR(t->t.flatten(a, b)); }
ST_Tensor smile_tensor_expand    (ST_Tensor t, const int64_t *s, int n) { MAKE_TENSOR(t->t.expand(to_shape(s,n))); }
ST_Tensor smile_tensor_unsqueeze (ST_Tensor t, int64_t dim)              { MAKE_TENSOR(t->t.unsqueeze(dim)); }
ST_Tensor smile_tensor_permute   (ST_Tensor t, const int64_t *d, int n) { MAKE_TENSOR(t->t.permute(to_shape(d,n))); }
ST_Tensor smile_tensor_transpose (ST_Tensor t, int64_t d0, int64_t d1)  { MAKE_TENSOR(t->t.transpose(d0, d1)); }
ST_Tensor smile_tensor_contiguous(ST_Tensor t)                           { MAKE_TENSOR(t->t.contiguous()); }
ST_Tensor smile_tensor_triu      (ST_Tensor t, int64_t diag)             { MAKE_TENSOR(t->t.triu(diag)); }
void      smile_tensor_triu_     (ST_Tensor t, int64_t diag)             { if (t) t->t.triu_(diag); }

// =============================================================================
// Tensor — Autograd
// =============================================================================

void      smile_tensor_backward(ST_Tensor t) { if (t) { ST_TRY_BEGIN t->t.backward(); ST_TRY_END } }
ST_Tensor smile_tensor_detach  (ST_Tensor t) { MAKE_TENSOR(t->t.detach()); }

// =============================================================================
// Tensor — Reductions
// =============================================================================

ST_Tensor smile_tensor_sum (ST_Tensor t) { MAKE_TENSOR(t->t.sum()); }
ST_Tensor smile_tensor_mean(ST_Tensor t) { MAKE_TENSOR(t->t.mean()); }
ST_Tensor smile_tensor_min (ST_Tensor t) { MAKE_TENSOR(std::get<0>(t->t.min(0))); }
ST_Tensor smile_tensor_max (ST_Tensor t) { MAKE_TENSOR(std::get<0>(t->t.max(0))); }
ST_Tensor smile_tensor_all (ST_Tensor t) { MAKE_TENSOR(t->t.all()); }

ST_Tensor smile_tensor_sum_dims(ST_Tensor t, const int64_t *dims, int ndim,
                                int keepdim, ST_DType dtype) {
    auto d = to_shape(dims, ndim);
    if (dtype == ST_DTYPE_UNDEFINED) {
        MAKE_TENSOR(t->t.sum(d, static_cast<bool>(keepdim)));
    } else {
        MAKE_TENSOR(t->t.sum(d, static_cast<bool>(keepdim), to_scalar_type(dtype)));
    }
}

ST_Tensor smile_tensor_mean_dims(ST_Tensor t, const int64_t *dims, int ndim,
                                 int keepdim, ST_DType dtype) {
    auto d = to_shape(dims, ndim);
    if (dtype == ST_DTYPE_UNDEFINED) {
        MAKE_TENSOR(t->t.mean(d, static_cast<bool>(keepdim)));
    } else {
        MAKE_TENSOR(t->t.mean(d, static_cast<bool>(keepdim), to_scalar_type(dtype)));
    }
}

ST_Tensor smile_tensor_argmax(ST_Tensor t, int64_t dim, int keepdim, int has_dim) {
    if (!has_dim) {
        MAKE_TENSOR(t->t.argmax());
    } else {
        MAKE_TENSOR(t->t.argmax(dim, static_cast<bool>(keepdim)));
    }
}

int smile_tensor_topk(ST_Tensor t, int64_t k, int64_t dim, int largest, int sorted,
                      ST_Tensor *values_out, ST_Tensor *indices_out) {
    if (!t || !values_out || !indices_out) return -1;
    ST_TRY_BEGIN
        auto [vals, idxs] = t->t.topk(k, dim, static_cast<bool>(largest),
                                                static_cast<bool>(sorted));
        *values_out  = new ST_Tensor_{ vals };
        *indices_out = new ST_Tensor_{ idxs };
        return 0;
    ST_TRY_END
    return -1;
}

// =============================================================================
// Tensor — Arithmetic (out-of-place)
// =============================================================================

ST_Tensor smile_tensor_neg    (ST_Tensor t)                                    { MAKE_TENSOR(t->t.neg()); }
ST_Tensor smile_tensor_add_s  (ST_Tensor t, ST_Scalar s)                       { MAKE_TENSOR(t->t.add(s->s)); }
ST_Tensor smile_tensor_add_t  (ST_Tensor a, ST_Tensor b)                       { MAKE_TENSOR(a->t.add(b->t)); }
ST_Tensor smile_tensor_add_t_s(ST_Tensor a, ST_Tensor b, ST_Scalar alpha)      { MAKE_TENSOR(a->t.add(b->t, alpha->s)); }
ST_Tensor smile_tensor_sub_s  (ST_Tensor t, ST_Scalar s)                       { MAKE_TENSOR(t->t.sub(s->s)); }
ST_Tensor smile_tensor_sub_t  (ST_Tensor a, ST_Tensor b)                       { MAKE_TENSOR(a->t.sub(b->t)); }
ST_Tensor smile_tensor_sub_t_s(ST_Tensor a, ST_Tensor b, ST_Scalar alpha)      { MAKE_TENSOR(a->t.sub(b->t, alpha->s)); }
ST_Tensor smile_tensor_mul_s  (ST_Tensor t, ST_Scalar s)                       { MAKE_TENSOR(t->t.mul(s->s)); }
ST_Tensor smile_tensor_mul_t  (ST_Tensor a, ST_Tensor b)                       { MAKE_TENSOR(a->t.mul(b->t)); }
ST_Tensor smile_tensor_div_s  (ST_Tensor t, ST_Scalar s)                       { MAKE_TENSOR(t->t.div(s->s)); }
ST_Tensor smile_tensor_div_t  (ST_Tensor a, ST_Tensor b)                       { MAKE_TENSOR(a->t.div(b->t)); }
ST_Tensor smile_tensor_pow_s  (ST_Tensor t, ST_Scalar e)                       { MAKE_TENSOR(t->t.pow(e->s)); }

// =============================================================================
// Tensor — Arithmetic (in-place)
// =============================================================================

void smile_tensor_neg_      (ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.neg_(); ST_TRY_END }
}
void smile_tensor_add_s_    (ST_Tensor t, ST_Scalar s) {
    if (t&&s) { ST_TRY_BEGIN t->t.add_(s->s); ST_TRY_END }
}
void smile_tensor_add_t_    (ST_Tensor a, ST_Tensor b) {
    if (a&&b) { ST_TRY_BEGIN a->t.add_(b->t); ST_TRY_END }
}
void smile_tensor_add_t_s_  (ST_Tensor a, ST_Tensor b, ST_Scalar alpha) {
    if (a&&b&&alpha) { ST_TRY_BEGIN a->t.add_(b->t, alpha->s); ST_TRY_END }
}
void smile_tensor_sub_s_    (ST_Tensor t, ST_Scalar s) {
    if (t&&s) { ST_TRY_BEGIN t->t.sub_(s->s); ST_TRY_END }
}
void smile_tensor_sub_t_    (ST_Tensor a, ST_Tensor b) {
    if (a&&b) { ST_TRY_BEGIN a->t.sub_(b->t); ST_TRY_END }
}
void smile_tensor_sub_t_s_  (ST_Tensor a, ST_Tensor b, ST_Scalar alpha) {
    if (a&&b&&alpha) { ST_TRY_BEGIN a->t.sub_(b->t, alpha->s); ST_TRY_END }
}
void smile_tensor_mul_s_    (ST_Tensor t, ST_Scalar s) {
    if (t&&s) { ST_TRY_BEGIN t->t.mul_(s->s); ST_TRY_END }
}
void smile_tensor_mul_t_    (ST_Tensor a, ST_Tensor b) {
    if (a&&b) { ST_TRY_BEGIN a->t.mul_(b->t); ST_TRY_END }
}
void smile_tensor_div_s_    (ST_Tensor t, ST_Scalar s) {
    if (t&&s) { ST_TRY_BEGIN t->t.div_(s->s); ST_TRY_END }
}
void smile_tensor_div_t_    (ST_Tensor a, ST_Tensor b) {
    if (a&&b) { ST_TRY_BEGIN a->t.div_(b->t); ST_TRY_END }
}
void smile_tensor_pow_s_    (ST_Tensor t, ST_Scalar e) {
    if (t&&e) { ST_TRY_BEGIN t->t.pow_(e->s); ST_TRY_END }
}
void smile_tensor_fill_     (ST_Tensor t, ST_Scalar v) {
    if (t&&v) { ST_TRY_BEGIN t->t.fill_(v->s); ST_TRY_END }
}
void smile_tensor_bernoulli_ (ST_Tensor t, double p) {
    if (t) { ST_TRY_BEGIN t->t.bernoulli_(p); ST_TRY_END }
}
void smile_tensor_copy_(ST_Tensor dst, ST_Tensor src) {
    if (dst && src) { ST_TRY_BEGIN dst->t.copy_(src->t); ST_TRY_END }
}
void smile_tensor_mul_scalar_(ST_Tensor t, double s) {
    if (t) { ST_TRY_BEGIN t->t.mul_(at::Scalar(s)); ST_TRY_END }
}

// =============================================================================
// Tensor — Element-wise math
// =============================================================================

ST_Tensor smile_tensor_abs  (ST_Tensor t) { MAKE_TENSOR(t->t.abs()); }
ST_Tensor smile_tensor_log  (ST_Tensor t) { MAKE_TENSOR(t->t.log()); }
ST_Tensor smile_tensor_exp  (ST_Tensor t) { MAKE_TENSOR(t->t.exp()); }
ST_Tensor smile_tensor_rsqrt(ST_Tensor t) { MAKE_TENSOR(t->t.rsqrt()); }
ST_Tensor smile_tensor_cos  (ST_Tensor t) { MAKE_TENSOR(t->t.cos()); }
ST_Tensor smile_tensor_sin  (ST_Tensor t) { MAKE_TENSOR(t->t.sin()); }
ST_Tensor smile_tensor_acos (ST_Tensor t) { MAKE_TENSOR(t->t.acos()); }
ST_Tensor smile_tensor_asin (ST_Tensor t) { MAKE_TENSOR(t->t.asin()); }

void smile_tensor_abs_  (ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.abs_(); ST_TRY_END }
}
void smile_tensor_log_  (ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.log_(); ST_TRY_END }
}
void smile_tensor_exp_  (ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.exp_(); ST_TRY_END }
}
void smile_tensor_rsqrt_(ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.rsqrt_(); ST_TRY_END }
}
void smile_tensor_cos_  (ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.cos_(); ST_TRY_END }
}
void smile_tensor_sin_  (ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.sin_(); ST_TRY_END }
}
void smile_tensor_acos_ (ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.acos_(); ST_TRY_END }
}
void smile_tensor_asin_ (ST_Tensor t) {
    if (t) { ST_TRY_BEGIN t->t.asin_(); ST_TRY_END }
}

ST_Tensor smile_tensor_clamp(ST_Tensor t, int has_min, ST_Scalar mn,
                              int has_max, ST_Scalar mx) {
    MAKE_TENSOR(t->t.clamp(maybe_scalar(has_min, mn), maybe_scalar(has_max, mx)));
}
void smile_tensor_clamp_(ST_Tensor t, int has_min, ST_Scalar mn,
                          int has_max, ST_Scalar mx) {
    if (t) { ST_TRY_BEGIN
        t->t.clamp_(maybe_scalar(has_min, mn), maybe_scalar(has_max, mx));
    ST_TRY_END }
}

// =============================================================================
// Tensor — Comparison
// =============================================================================

ST_Tensor smile_tensor_eq_s(ST_Tensor t, ST_Scalar s) { MAKE_TENSOR(t->t.eq(s->s)); }
ST_Tensor smile_tensor_eq_t(ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.eq(b->t)); }
ST_Tensor smile_tensor_ne_s(ST_Tensor t, ST_Scalar s) { MAKE_TENSOR(t->t.ne(s->s)); }
ST_Tensor smile_tensor_ne_t(ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.ne(b->t)); }
ST_Tensor smile_tensor_lt_s(ST_Tensor t, ST_Scalar s) { MAKE_TENSOR(t->t.lt(s->s)); }
ST_Tensor smile_tensor_lt_t(ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.lt(b->t)); }
ST_Tensor smile_tensor_le_s(ST_Tensor t, ST_Scalar s) { MAKE_TENSOR(t->t.le(s->s)); }
ST_Tensor smile_tensor_le_t(ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.le(b->t)); }
ST_Tensor smile_tensor_gt_s(ST_Tensor t, ST_Scalar s) { MAKE_TENSOR(t->t.gt(s->s)); }
ST_Tensor smile_tensor_gt_t(ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.gt(b->t)); }
ST_Tensor smile_tensor_ge_s(ST_Tensor t, ST_Scalar s) { MAKE_TENSOR(t->t.ge(s->s)); }
ST_Tensor smile_tensor_ge_t(ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.ge(b->t)); }

// =============================================================================
// Tensor — Logical
// =============================================================================

ST_Tensor smile_tensor_logical_not (ST_Tensor t)              { MAKE_TENSOR(t->t.logical_not()); }
ST_Tensor smile_tensor_logical_and (ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.logical_and(b->t)); }
ST_Tensor smile_tensor_logical_or  (ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.logical_or(b->t)); }
void      smile_tensor_logical_not_(ST_Tensor t)              { if (t) t->t.logical_not_(); }
void      smile_tensor_logical_and_(ST_Tensor a, ST_Tensor b) { if (a&&b) a->t.logical_and_(b->t); }
void      smile_tensor_logical_or_ (ST_Tensor a, ST_Tensor b) { if (a&&b) a->t.logical_or_(b->t); }

// =============================================================================
// Tensor — Linear algebra
// =============================================================================

ST_Tensor smile_tensor_matmul (ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(a->t.matmul(b->t)); }

ST_Tensor smile_scaled_mm(ST_Tensor a, ST_Tensor b,
                          ST_Tensor scale_a, ST_Tensor scale_b,
                          int out_dtype) {
#ifdef USE_CUDA
    ST_TRY_BEGIN
        if (!a || !b || !scale_a || !scale_b) {
            set_error("smile_scaled_mm: null tensor argument");
            return nullptr;
        }
        c10::ScalarType out = to_scalar_type(static_cast<ST_DType>(out_dtype));
        // at::_scaled_mm(a, b.t(), scale_a, scale_b, ..., out_dtype)
        // LibTorch expects B as [N,K] (transposed weight) for linear-like GEMM.
        auto out_t = at::_scaled_mm(
                a->t, b->t,
                scale_a->t, scale_b->t,
                std::nullopt /*bias*/,
                std::nullopt /*scale_result*/,
                out);
        return new ST_Tensor_{ out_t };
    ST_TRY_END
    return nullptr;
#else
    set_error_no_cuda_build();
    return nullptr;
#endif
}
ST_Tensor smile_tensor_outer  (ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(at::outer(a->t, b->t)); }

ST_Tensor smile_tensor_scatter_reduce(ST_Tensor t, int64_t dim, ST_Tensor index,
                                      ST_Tensor src, const char *reduce) {
    if (!t || !index || !src || !reduce) return nullptr;
    MAKE_TENSOR(t->t.scatter_reduce(dim, index->t, src->t, reduce));
}
void smile_tensor_scatter_reduce_(ST_Tensor t, int64_t dim, ST_Tensor index,
                                  ST_Tensor src, const char *reduce) {
    if (t && index && src && reduce) {
        ST_TRY_BEGIN t->t.scatter_reduce_(dim, index->t, src->t, reduce); ST_TRY_END
    }
}

// =============================================================================
// Tensor — New-tensor creators
// =============================================================================

ST_Tensor smile_tensor_new_zeros(ST_Tensor t, const int64_t *s, int n) {
    MAKE_TENSOR(t->t.new_zeros(to_shape(s,n)));
}
ST_Tensor smile_tensor_new_ones(ST_Tensor t, const int64_t *s, int n) {
    MAKE_TENSOR(t->t.new_ones(to_shape(s,n)));
}

// =============================================================================
// Tensor — Indexing helpers
// =============================================================================

using Idx = torch::indexing::TensorIndex;

ST_TensorIndex smile_tensor_index_from_int(int64_t v) {
    ST_TRY_BEGIN return new ST_TensorIndex_{ Idx(v) }; ST_TRY_END return nullptr;
}
ST_TensorIndex smile_tensor_index_from_bool(int v) {
    ST_TRY_BEGIN return new ST_TensorIndex_{ Idx(static_cast<bool>(v)) }; ST_TRY_END return nullptr;
}
ST_TensorIndex smile_tensor_index_from_tensor(ST_Tensor t) {
    if (!t) return nullptr;
    ST_TRY_BEGIN return new ST_TensorIndex_{ Idx(t->t) }; ST_TRY_END return nullptr;
}
ST_TensorIndex smile_tensor_index_ellipsis(void) {
    ST_TRY_BEGIN return new ST_TensorIndex_{ torch::indexing::Ellipsis }; ST_TRY_END return nullptr;
}
ST_TensorIndex smile_tensor_index_slice(int64_t start, int64_t stop, int64_t step) {
    using SI  = c10::SymInt;
    using SIO = std::optional<c10::SymInt>;
    constexpr int64_t NONE = std::numeric_limits<int64_t>::min();
    SIO s = (start == NONE) ? SIO{} : SIO{SI(start)};
    SIO e = (stop  == NONE) ? SIO{} : SIO{SI(stop)};
    SIO st = (step  == NONE) ? SIO{} : SIO{SI(step)};
    ST_TRY_BEGIN
        return new ST_TensorIndex_{ Idx(torch::indexing::Slice(s, e, st)) };
    ST_TRY_END
    return nullptr;
}
ST_TensorIndex smile_tensor_index_none(void) {
    ST_TRY_BEGIN return new ST_TensorIndex_{ Idx(torch::indexing::None) }; ST_TRY_END return nullptr;
}
void smile_tensor_index_free(ST_TensorIndex idx) { delete idx; }

ST_TensorIndexVec smile_tensor_index_vec_create(void) {
    return new ST_TensorIndexVec_;
}
void smile_tensor_index_vec_push(ST_TensorIndexVec v, ST_TensorIndex idx) {
    if (v && idx) v->vec.push_back(idx->idx);
}
void smile_tensor_index_vec_free(ST_TensorIndexVec v) { delete v; }

ST_Tensor smile_tensor_index(ST_Tensor t, ST_TensorIndexVec indices) {
    if (!t || !indices) return nullptr;
    MAKE_TENSOR(t->t.index(indices->vec));
}
void smile_tensor_index_put_(ST_Tensor t, ST_TensorIndexVec indices, ST_Tensor src) {
    if (t && indices && src) {
        ST_TRY_BEGIN t->t.index_put_(indices->vec, src->t); ST_TRY_END
    }
}
void smile_tensor_index_put_scalar_(ST_Tensor t, ST_TensorIndexVec indices, ST_Scalar s) {
    if (t && indices && s) {
        ST_TRY_BEGIN t->t.index_put_(indices->vec, s->s); ST_TRY_END
    }
}

ST_TensorVec smile_tensor_vec_create(void)                       { return new ST_TensorVec_; }
void          smile_tensor_vec_push(ST_TensorVec v, ST_Tensor t) { if (v&&t) v->vec.push_back(t->t); }
void          smile_tensor_vec_free(ST_TensorVec v)              { delete v; }

// =============================================================================
// Global torch functions
// =============================================================================

ST_Tensor smile_torch_view_as_complex(ST_Tensor t) { MAKE_TENSOR(torch::view_as_complex(t->t)); }
ST_Tensor smile_torch_view_as_real   (ST_Tensor t) { MAKE_TENSOR(torch::view_as_real(t->t)); }
ST_Tensor smile_torch_polar          (ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(torch::polar(a->t, b->t)); }
ST_Tensor smile_torch_hstack         (ST_TensorVec v) { MAKE_TENSOR(torch::hstack(v->vec)); }
ST_Tensor smile_torch_vstack         (ST_TensorVec v) { MAKE_TENSOR(torch::vstack(v->vec)); }
ST_Tensor smile_torch_cumsum         (ST_Tensor t, int64_t dim) { MAKE_TENSOR(torch::cumsum(t->t, dim)); }
ST_Tensor smile_torch_multinomial    (ST_Tensor t, int64_t n)   { MAKE_TENSOR(torch::multinomial(t->t, n)); }
ST_Tensor smile_torch_gather         (ST_Tensor t, int64_t dim, ST_Tensor idx) {
    MAKE_TENSOR(torch::gather(t->t, dim, idx->t));
}
ST_Tensor smile_torch_isin   (ST_Tensor e, ST_Tensor o) { MAKE_TENSOR(torch::isin(e->t, o->t)); }
ST_Tensor smile_torch_dropout(ST_Tensor t, double p, int training) {
    MAKE_TENSOR(torch::dropout(t->t, p, static_cast<bool>(training)));
}
void smile_torch_print(ST_Tensor t) {
    if (t) { ST_TRY_BEGIN torch::print(t->t); ST_TRY_END }
}

int smile_torch_sort(ST_Tensor t, int64_t dim, int desc,
                     ST_Tensor *sv, ST_Tensor *iv) {
    if (!t || !sv || !iv) return -1;
    ST_TRY_BEGIN
        auto [vals, idxs] = torch::sort(t->t, dim, static_cast<bool>(desc));
        *sv = new ST_Tensor_{ vals };
        *iv = new ST_Tensor_{ idxs };
        return 0;
    ST_TRY_END
    return -1;
}

ST_Tensor smile_torch_where_tt(ST_Tensor c, ST_Tensor i, ST_Tensor o) {
    MAKE_TENSOR(torch::where(c->t, i->t, o->t));
}
ST_Tensor smile_torch_where_ts(ST_Tensor c, ST_Tensor i, ST_Scalar o) {
    MAKE_TENSOR(torch::where(c->t, i->t, o->s));
}

// =============================================================================
// Attention — scaled_dot_product_attention
// =============================================================================

ST_Tensor smile_torch_scaled_dot_product_attention(
    ST_Tensor query,
    ST_Tensor key,
    ST_Tensor value,
    ST_Tensor attn_mask,
    double dropout_p,
    int is_causal,
    int has_scale,
    double scale) {
    if (!query || !key || !value) return nullptr;
    ST_TRY_BEGIN
        // Pass nullopt when no mask is provided; an uninitialized at::Tensor
        // is rejected by SDPA's dtype validation.
        std::optional<at::Tensor> mask;
        if (attn_mask) mask = attn_mask->t;

        std::optional<double> scale_opt;
        if (has_scale) scale_opt = scale;

        MAKE_TENSOR(at::scaled_dot_product_attention(
            query->t,
            key->t,
            value->t,
            mask,
            dropout_p,
            static_cast<bool>(is_causal),
            scale_opt));
    ST_TRY_END
    return nullptr;
}

// =============================================================================
// Activation functions
// =============================================================================

ST_Tensor smile_torch_relu       (ST_Tensor x) { MAKE_TENSOR(torch::relu(x->t)); }
void      smile_torch_relu_      (ST_Tensor x) { if (x) x->t = torch::relu_(x->t); }
ST_Tensor smile_torch_gelu       (ST_Tensor x) { MAKE_TENSOR(torch::gelu(x->t)); }
void      smile_torch_gelu_      (ST_Tensor x) { if (x) x->t = torch::gelu(x->t); }
ST_Tensor smile_torch_glu        (ST_Tensor x) { MAKE_TENSOR(torch::glu(x->t)); }
ST_Tensor smile_torch_silu       (ST_Tensor x) { MAKE_TENSOR(torch::silu(x->t)); }
void      smile_torch_silu_      (ST_Tensor x) { if (x) torch::silu_(x->t); }
ST_Tensor smile_torch_sigmoid    (ST_Tensor x) { MAKE_TENSOR(torch::sigmoid(x->t)); }
void      smile_torch_sigmoid_   (ST_Tensor x) { if (x) torch::sigmoid_(x->t); }
ST_Tensor smile_torch_tanh       (ST_Tensor x) { MAKE_TENSOR(torch::tanh(x->t)); }
void      smile_torch_tanh_      (ST_Tensor x) { if (x) torch::tanh_(x->t); }
ST_Tensor smile_torch_leaky_relu (ST_Tensor x, double s) {
    MAKE_TENSOR(torch::leaky_relu(x->t, at::Scalar(s)));
}
void smile_torch_leaky_relu_     (ST_Tensor x, double s) {
    if (x) { ST_TRY_BEGIN torch::leaky_relu_(x->t, at::Scalar(s)); ST_TRY_END }
}
ST_Tensor smile_torch_elu  (ST_Tensor x, double alpha) {
    MAKE_TENSOR(torch::elu(x->t, at::Scalar(alpha)));
}
void smile_torch_elu_      (ST_Tensor x, double alpha, double scale, double is) {
    if (x) { ST_TRY_BEGIN
        torch::elu_(x->t, at::Scalar(alpha), at::Scalar(scale), at::Scalar(is));
    ST_TRY_END }
}
ST_Tensor smile_torch_softmax    (ST_Tensor x, int64_t dim) { MAKE_TENSOR(torch::softmax(x->t, dim)); }
ST_Tensor smile_torch_log_softmax(ST_Tensor x, int64_t dim) { MAKE_TENSOR(torch::log_softmax(x->t, dim)); }
ST_Tensor smile_torch_log_sigmoid(ST_Tensor x) { MAKE_TENSOR(torch::log_sigmoid(x->t)); }
ST_Tensor smile_torch_mish       (ST_Tensor x) { MAKE_TENSOR(torch::mish(x->t)); }
void      smile_torch_mish_      (ST_Tensor x) { if (x) torch::mish_(x->t); }
ST_Tensor smile_torch_hardswish  (ST_Tensor x) { MAKE_TENSOR(torch::hardswish(x->t)); }
void      smile_torch_hardswish_ (ST_Tensor x) { if (x) torch::hardswish_(x->t); }
ST_Tensor smile_torch_hardshrink (ST_Tensor x, double lam) {
    MAKE_TENSOR(torch::hardshrink(x->t, at::Scalar(lam)));
}
ST_Tensor smile_torch_softshrink (ST_Tensor x, double lam) {
    MAKE_TENSOR(torch::softshrink(x->t, at::Scalar(lam)));
}
ST_Tensor smile_torch_tanhshrink (ST_Tensor x) { MAKE_TENSOR(torch::nn::functional::tanhshrink(x->t)); }

// =============================================================================
// Loss functions
// =============================================================================

ST_Tensor smile_torch_l1_loss   (ST_Tensor i, ST_Tensor t) { MAKE_TENSOR(torch::l1_loss(i->t, t->t)); }
ST_Tensor smile_torch_mse_loss  (ST_Tensor i, ST_Tensor t) { MAKE_TENSOR(torch::mse_loss(i->t, t->t)); }
ST_Tensor smile_torch_nll_loss  (ST_Tensor i, ST_Tensor t) { MAKE_TENSOR(torch::nll_loss(i->t, t->t)); }

ST_Tensor smile_torch_cross_entropy(ST_Tensor input, ST_Tensor target,
                                     int64_t ignore_index, ST_Reduction reduction) {
    MAKE_TENSOR(torch::cross_entropy_loss(
        input->t, target->t,
        /*weight=*/{},
        /*reduction=*/static_cast<int64_t>(reduction),
        /*ignore_index=*/ignore_index,
        /*label_smoothing=*/0.0));
}
ST_Tensor smile_torch_hinge_embedding_loss       (ST_Tensor i, ST_Tensor t) {
    MAKE_TENSOR(torch::hinge_embedding_loss(i->t, t->t));
}
ST_Tensor smile_torch_binary_cross_entropy       (ST_Tensor i, ST_Tensor t) {
    MAKE_TENSOR(torch::binary_cross_entropy(i->t, t->t));
}
ST_Tensor smile_torch_binary_cross_entropy_logits(ST_Tensor i, ST_Tensor t) {
    MAKE_TENSOR(torch::binary_cross_entropy_with_logits(i->t, t->t));
}
ST_Tensor smile_torch_smooth_l1_loss(ST_Tensor i, ST_Tensor t) {
    MAKE_TENSOR(torch::smooth_l1_loss(i->t, t->t));
}
ST_Tensor smile_torch_huber_loss(ST_Tensor i, ST_Tensor t, double delta) {
    MAKE_TENSOR(torch::huber_loss(i->t, t->t,
                                  torch::Reduction::Mean, delta));
}
ST_Tensor smile_torch_kl_div              (ST_Tensor i, ST_Tensor t) { MAKE_TENSOR(torch::kl_div(i->t, t->t)); }
ST_Tensor smile_torch_margin_ranking_loss (ST_Tensor i1, ST_Tensor i2, ST_Tensor t) {
    MAKE_TENSOR(torch::margin_ranking_loss(i1->t, i2->t, t->t));
}
ST_Tensor smile_torch_triplet_margin_loss (ST_Tensor a, ST_Tensor p, ST_Tensor n) {
    MAKE_TENSOR(torch::triplet_margin_loss(a->t, p->t, n->t));
}

// =============================================================================
// NoGradGuard
// =============================================================================

ST_NoGradGuard smile_no_grad_guard_create(void) {
    ST_TRY_BEGIN return new ST_NoGradGuard_{}; ST_TRY_END return nullptr;
}
void smile_no_grad_guard_free(ST_NoGradGuard g) { delete g; }

// =============================================================================
// Module
// =============================================================================

ST_Module smile_module_create(const char *name) {
    ST_TRY_BEGIN
        auto m = std::make_shared<torch::nn::Module>(name ? name : "");
        return new ST_Module_{ m };
    ST_TRY_END
    return nullptr;
}
void smile_module_free(ST_Module m) { delete m; }

const char *smile_module_name(ST_Module m) {
    if (!m) return "";
    return m->m->name().c_str();
}

void smile_module_register_module(ST_Module m, const char *name, ST_Module child) {
    if (m && child && name) {
        ST_TRY_BEGIN m->m->register_module(name, child->m); ST_TRY_END
    }
}
void smile_module_unregister_module(ST_Module m, const char *name) {
    if (m && name) {
        ST_TRY_BEGIN m->m->unregister_module(name); ST_TRY_END
    }
}
void smile_module_register_buffer(ST_Module m, const char *name, ST_Tensor t) {
    if (m && t && name) {
        ST_TRY_BEGIN m->m->register_buffer(name, t->t); ST_TRY_END
    }
}
void smile_module_register_parameter(ST_Module m, const char *name, ST_Tensor t) {
    if (m && t && name) {
        ST_TRY_BEGIN m->m->register_parameter(name, t->t); ST_TRY_END
    }
}

ST_TensorVec smile_module_parameters(ST_Module m) {
    if (!m) return nullptr;
    ST_TRY_BEGIN
        auto *v = new ST_TensorVec_;
        for (auto &p : m->m->parameters()) v->vec.push_back(p);
        return v;
    ST_TRY_END
    return nullptr;
}

void smile_module_train(ST_Module m, int mode) {
    if (m) { ST_TRY_BEGIN m->m->train(static_cast<bool>(mode)); ST_TRY_END }
}
void smile_module_eval (ST_Module m) {
    if (m) { ST_TRY_BEGIN m->m->eval(); ST_TRY_END }
}
int smile_module_is_training(ST_Module m) {
    return m ? (m->m->is_training() ? 1 : 0) : 0;
}

void smile_module_set_requires_grad(ST_Module m, int requires_grad) {
    if (!m) return;
    ST_TRY_BEGIN
        const bool rg = requires_grad != 0;
        for (auto &p : m->m->parameters(/*recurse=*/true)) {
            p.set_requires_grad(rg);
        }
    ST_TRY_END
}

void smile_module_to_device(ST_Module m, ST_Device device, int non_blocking) {
    if (m && device) {
        ST_TRY_BEGIN m->m->to(device->d, static_cast<bool>(non_blocking)); ST_TRY_END
    }
}
void smile_module_to_dtype(ST_Module m, ST_Device device, ST_DType dtype, int non_blocking) {
    if (m && device) {
        ST_TRY_BEGIN
            m->m->to(device->d, to_scalar_type(dtype), static_cast<bool>(non_blocking));
        ST_TRY_END
    }
}

void smile_module_save(ST_Module m, ST_OutputArchive a) {
    if (m && a) { ST_TRY_BEGIN m->m->save(a->archive); ST_TRY_END }
}
void smile_module_load(ST_Module m, ST_InputArchive a) {
    if (m && a) { ST_TRY_BEGIN m->m->load(a->archive); ST_TRY_END }
}

int smile_module_load_state_dict(ST_Module m,
                                 const char **names,
                                 ST_Tensor *tensors,
                                 int64_t n,
                                 int strict) {
    if (!m || !names || !tensors || n < 0) {
        set_error("smile_module_load_state_dict: invalid arguments");
        return -1;
    }
    ST_TRY_BEGIN
        // Match torch.nn.Module.load_state_dict: copy_ into leaf parameters
        // that require grad must run under NoGradGuard, otherwise
        // check_inplace fails ("leaf Variable that requires grad...").
        torch::NoGradGuard no_grad;

        std::unordered_map<std::string, at::Tensor> state;
        state.reserve(static_cast<size_t>(n));
        for (int64_t i = 0; i < n; i++) {
            if (!names[i] || !tensors[i]) {
                set_error("smile_module_load_state_dict: null name or tensor at index "
                          + std::to_string(i));
                return -1;
            }
            state.emplace(names[i], tensors[i]->t);
        }

        auto params = m->m->named_parameters(/*recurse=*/true);
        std::unordered_set<std::string> used;
        used.reserve(params.size());

        for (auto &p : params) {
            auto it = state.find(p.key());
            if (it == state.end()) {
                if (strict) {
                    set_error("smile_module_load_state_dict: missing key '" + p.key() + "'");
                    return -1;
                }
                continue;
            }
            p.value().copy_(it->second);
            used.insert(p.key());
        }

        if (strict) {
            for (auto &kv : state) {
                if (!used.count(kv.first)) {
                    set_error("smile_module_load_state_dict: unexpected key '" + kv.first + "'");
                    return -1;
                }
            }
        }
        return 0;
    ST_TRY_END
    return -1;
}

// =============================================================================
// ModuleList
// =============================================================================

ST_ModuleList smile_module_list_create(void) {
    ST_TRY_BEGIN
        return new ST_ModuleList_{ std::make_shared<torch::nn::ModuleListImpl>() };
    ST_TRY_END
    return nullptr;
}
void    smile_module_list_free(ST_ModuleList ml) { delete ml; }
void    smile_module_list_push_back(ST_ModuleList ml, ST_Module m) {
    if (ml && m) { ST_TRY_BEGIN ml->ml->push_back(m->m); ST_TRY_END }
}
int64_t smile_module_list_size(ST_ModuleList ml) {
    return ml ? static_cast<int64_t>(ml->ml->size()) : 0;
}
ST_Module smile_module_list_get(ST_ModuleList ml, int64_t i) {
    if (!ml) return nullptr;
    ST_TRY_BEGIN
        return new ST_Module_{ ml->ml->ptr(i) };
    ST_TRY_END
    return nullptr;
}
ST_Module smile_module_list_as_module(ST_ModuleList ml) {
    if (!ml) return nullptr;
    ST_TRY_BEGIN
        return new ST_Module_{ std::static_pointer_cast<torch::nn::Module>(ml->ml) };
    ST_TRY_END
    return nullptr;
}

// =============================================================================
// Archive (checkpointing)
// =============================================================================

ST_InputArchive smile_input_archive_create(void) {
    ST_TRY_BEGIN return new ST_InputArchive_{}; ST_TRY_END return nullptr;
}
void smile_input_archive_free(ST_InputArchive a) { delete a; }

int smile_input_archive_load_from(ST_InputArchive a, const char *path, ST_Device device) {
    if (!a || !path) return -1;
    ST_TRY_BEGIN
        if (device)
            a->archive.load_from(path, device->d);
        else
            a->archive.load_from(path);
        return 0;
    ST_TRY_END
    return -1;
}

ST_OutputArchive smile_output_archive_create(void) {
    ST_TRY_BEGIN return new ST_OutputArchive_{ torch::serialize::OutputArchive() }; ST_TRY_END return nullptr;
}
void smile_output_archive_free(ST_OutputArchive a) { delete a; }

int smile_output_archive_save_to(ST_OutputArchive a, const char *path) {
    if (!a || !path) return -1;
    ST_TRY_BEGIN a->archive.save_to(path); return 0; ST_TRY_END
    return -1;
}

// =============================================================================
// Layer modules — helper macro
// =============================================================================

/** Wraps a layer module's nn::Module pointer as a borrowed ST_Module view.
 *  The returned handle shares ownership with the layer; do NOT call
 *  smile_module_free() on it. */
#define BORROW_MODULE(layer_ptr, holder_field) \
    ST_TRY_BEGIN \
        return new ST_Module_{ (layer_ptr)->holder_field.ptr() }; \
    ST_TRY_END \
    return nullptr

// =============================================================================
// Linear
// =============================================================================

ST_Linear smile_linear_create(int64_t in, int64_t out, int bias) {
    ST_TRY_BEGIN
        EmptyLinear linear(in, out, static_cast<bool>(bias), c10::Device(c10::kCPU));
        linear->reset_parameters();
        return new ST_Linear_{ std::move(linear) };
    ST_TRY_END
    return nullptr;
}

ST_Linear smile_linear_create_uninitialized(int64_t in, int64_t out, int bias,
                                            int device_type, int8_t device_index) {
    ST_TRY_BEGIN
        // torch::empty only — no Kaiming fill (weights overwritten on load).
        c10::Device device(to_device_type(device_type), device_index);
        return new ST_Linear_{ EmptyLinear(in, out, static_cast<bool>(bias), device) };
    ST_TRY_END
    return nullptr;
}

void      smile_linear_free  (ST_Linear l) { delete l; }
ST_Tensor smile_linear_forward(ST_Linear l, ST_Tensor input) {
    if (!l || !input) return nullptr;
    MAKE_TENSOR(l->mod->forward(input->t));
}
ST_Module smile_linear_as_module(ST_Linear l) {
    if (!l) return nullptr;
    BORROW_MODULE(l, mod);
}

// =============================================================================
// Conv2d
// =============================================================================

ST_Conv2d smile_conv2d_create(int64_t in, int64_t out,
                               const int64_t *kernel,
                               const int64_t *stride,
                               const int64_t *padding,
                               const int64_t *dilation,
                               int64_t groups, int bias,
                               ST_PaddingMode pad_mode) {
    ST_TRY_BEGIN
        auto ks  = param2(kernel,  1);
        auto st  = param2(stride,  1);
        auto pd  = param2(padding, 0);
        auto di  = param2(dilation,1);

        torch::nn::detail::conv_padding_mode_t pm;
        switch (pad_mode) {
            case ST_PAD_REFLECT:   pm = torch::kReflect;   break;
            case ST_PAD_REPLICATE: pm = torch::kReplicate; break;
            case ST_PAD_CIRCULAR:  pm = torch::kCircular;  break;
            default:               pm = torch::kZeros;     break;
        }

        auto opts = torch::nn::Conv2dOptions(in, out, ks)
                        .stride(st).padding(pd).dilation(di)
                        .groups(groups).bias(static_cast<bool>(bias))
                        .padding_mode(pm);
        return new ST_Conv2d_{ torch::nn::Conv2d(opts) };
    ST_TRY_END
    return nullptr;
}
void      smile_conv2d_free   (ST_Conv2d c) { delete c; }
ST_Tensor smile_conv2d_forward(ST_Conv2d c, ST_Tensor i) {
    if (!c||!i) return nullptr;
    MAKE_TENSOR(c->mod->forward(i->t));
}
ST_Module smile_conv2d_as_module(ST_Conv2d c) {
    if (!c) return nullptr; BORROW_MODULE(c, mod);
}

// =============================================================================
// BatchNorm1d / BatchNorm2d
// =============================================================================

ST_BatchNorm1d smile_batchnorm1d_create(int64_t ch, double eps, double mom, int affine) {
    ST_TRY_BEGIN
        auto opts = torch::nn::BatchNormOptions(ch).eps(eps).momentum(mom)
                                                   .affine(static_cast<bool>(affine));
        return new ST_BatchNorm1d_{ torch::nn::BatchNorm1d(opts) };
    ST_TRY_END return nullptr;
}
void      smile_batchnorm1d_free   (ST_BatchNorm1d b) { delete b; }
ST_Tensor smile_batchnorm1d_forward(ST_BatchNorm1d b, ST_Tensor i) {
    if (!b||!i) return nullptr; MAKE_TENSOR(b->mod->forward(i->t));
}
ST_Module smile_batchnorm1d_as_module(ST_BatchNorm1d b) {
    if (!b) return nullptr; BORROW_MODULE(b, mod);
}

ST_BatchNorm2d smile_batchnorm2d_create(int64_t ch, double eps, double mom, int affine) {
    ST_TRY_BEGIN
        auto opts = torch::nn::BatchNormOptions(ch).eps(eps).momentum(mom)
                                                   .affine(static_cast<bool>(affine));
        return new ST_BatchNorm2d_{ torch::nn::BatchNorm2d(opts) };
    ST_TRY_END return nullptr;
}
void      smile_batchnorm2d_free   (ST_BatchNorm2d b) { delete b; }
ST_Tensor smile_batchnorm2d_forward(ST_BatchNorm2d b, ST_Tensor i) {
    if (!b||!i) return nullptr; MAKE_TENSOR(b->mod->forward(i->t));
}
ST_Module smile_batchnorm2d_as_module(ST_BatchNorm2d b) {
    if (!b) return nullptr; BORROW_MODULE(b, mod);
}

// =============================================================================
// Dropout
// =============================================================================

ST_Dropout smile_dropout_create(double p, int inplace) {
    ST_TRY_BEGIN
        auto opts = torch::nn::DropoutOptions().p(p).inplace(static_cast<bool>(inplace));
        return new ST_Dropout_{ torch::nn::Dropout(opts) };
    ST_TRY_END return nullptr;
}
void      smile_dropout_free   (ST_Dropout d) { delete d; }
ST_Tensor smile_dropout_forward(ST_Dropout d, ST_Tensor i) {
    if (!d||!i) return nullptr; MAKE_TENSOR(d->mod->forward(i->t));
}
int       smile_dropout_is_training(ST_Dropout d) {
    return d ? (d->mod->is_training() ? 1 : 0) : 0;
}
ST_Module smile_dropout_as_module(ST_Dropout d) {
    if (!d) return nullptr; BORROW_MODULE(d, mod);
}

// =============================================================================
// Embedding
// =============================================================================

ST_Embedding smile_embedding_create(int64_t num, int64_t dim) {
    ST_TRY_BEGIN
        EmptyEmbedding emb(num, dim, c10::Device(c10::kCPU));
        emb->reset_parameters();
        return new ST_Embedding_{ std::move(emb) };
    ST_TRY_END
    return nullptr;
}

ST_Embedding smile_embedding_create_uninitialized(int64_t num, int64_t dim,
                                                  int device_type, int8_t device_index) {
    ST_TRY_BEGIN
        c10::Device device(to_device_type(device_type), device_index);
        return new ST_Embedding_{ EmptyEmbedding(num, dim, device) };
    ST_TRY_END
    return nullptr;
}

void      smile_embedding_free   (ST_Embedding e) { delete e; }
ST_Tensor smile_embedding_forward(ST_Embedding e, ST_Tensor i) {
    if (!e||!i) return nullptr; MAKE_TENSOR(e->mod->forward(i->t));
}
ST_Module smile_embedding_as_module(ST_Embedding e) {
    if (!e) return nullptr; BORROW_MODULE(e, mod);
}

// =============================================================================
// GroupNorm
// =============================================================================

ST_GroupNorm smile_groupnorm_create(int64_t groups, int64_t channels, double eps, int affine) {
    ST_TRY_BEGIN
        auto opts = torch::nn::GroupNormOptions(groups, channels)
                        .eps(eps).affine(static_cast<bool>(affine));
        return new ST_GroupNorm_{ torch::nn::GroupNorm(opts) };
    ST_TRY_END return nullptr;
}
void      smile_groupnorm_free   (ST_GroupNorm g) { delete g; }
ST_Tensor smile_groupnorm_forward(ST_GroupNorm g, ST_Tensor i) {
    if (!g||!i) return nullptr; MAKE_TENSOR(g->mod->forward(i->t));
}
ST_Module smile_groupnorm_as_module(ST_GroupNorm g) {
    if (!g) return nullptr; BORROW_MODULE(g, mod);
}

// =============================================================================
// MaxPool2d
// =============================================================================

ST_MaxPool2d smile_maxpool2d_create(const int64_t *kernel,
                                    const int64_t *stride,
                                    const int64_t *padding) {
    ST_TRY_BEGIN
        auto ks = param2(kernel,  2);
        auto st = stride  ? std::vector<int64_t>{stride[0],  stride[1]}  : ks;
        auto pd = param2(padding, 0);
        auto opts = torch::nn::MaxPool2dOptions(ks).stride(st).padding(pd);
        return new ST_MaxPool2d_{ torch::nn::MaxPool2d(opts) };
    ST_TRY_END return nullptr;
}
void      smile_maxpool2d_free   (ST_MaxPool2d p) { delete p; }
ST_Tensor smile_maxpool2d_forward(ST_MaxPool2d p, ST_Tensor i) {
    if (!p||!i) return nullptr; MAKE_TENSOR(p->mod->forward(i->t));
}
ST_Module smile_maxpool2d_as_module(ST_MaxPool2d p) {
    if (!p) return nullptr; BORROW_MODULE(p, mod);
}

// =============================================================================
// AvgPool2d
// =============================================================================

ST_AvgPool2d smile_avgpool2d_create(const int64_t *kernel,
                                    const int64_t *stride,
                                    const int64_t *padding) {
    ST_TRY_BEGIN
        auto ks = param2(kernel,  2);
        auto st = stride  ? std::vector<int64_t>{stride[0],  stride[1]}  : ks;
        auto pd = param2(padding, 0);
        auto opts = torch::nn::AvgPool2dOptions(ks).stride(st).padding(pd);
        return new ST_AvgPool2d_{ torch::nn::AvgPool2d(opts) };
    ST_TRY_END return nullptr;
}
void      smile_avgpool2d_free   (ST_AvgPool2d p) { delete p; }
ST_Tensor smile_avgpool2d_forward(ST_AvgPool2d p, ST_Tensor i) {
    if (!p||!i) return nullptr; MAKE_TENSOR(p->mod->forward(i->t));
}
ST_Module smile_avgpool2d_as_module(ST_AvgPool2d p) {
    if (!p) return nullptr; BORROW_MODULE(p, mod);
}

// =============================================================================
// AdaptiveAvgPool2d
// =============================================================================

ST_AdaptiveAvgPool2d smile_adaptive_avgpool2d_create(const int64_t *output_size) {
    ST_TRY_BEGIN
        using SIO = std::optional<int64_t>;
        SIO h = (output_size[0] < 0) ? SIO{} : SIO{output_size[0]};
        SIO w = (output_size[1] < 0) ? SIO{} : SIO{output_size[1]};
        torch::nn::AdaptiveAvgPool2dOptions opts({h, w});
        return new ST_AdaptiveAvgPool2d_{ torch::nn::AdaptiveAvgPool2d(opts) };
    ST_TRY_END return nullptr;
}
void      smile_adaptive_avgpool2d_free   (ST_AdaptiveAvgPool2d p) { delete p; }
ST_Tensor smile_adaptive_avgpool2d_forward(ST_AdaptiveAvgPool2d p, ST_Tensor i) {
    if (!p||!i) return nullptr; MAKE_TENSOR(p->mod->forward(i->t));
}
ST_Module smile_adaptive_avgpool2d_as_module(ST_AdaptiveAvgPool2d p) {
    if (!p) return nullptr; BORROW_MODULE(p, mod);
}

// =============================================================================
// SGD
// =============================================================================

ST_Optimizer smile_sgd_create(ST_TensorVec params,
                               double lr, double momentum,
                               double weight_decay, double dampening,
                               int nesterov) {
    ST_TRY_BEGIN
        auto opts = torch::optim::SGDOptions(lr)
                        .momentum(momentum).weight_decay(weight_decay)
                        .dampening(dampening).nesterov(static_cast<bool>(nesterov));
        auto *p = new ST_Optimizer_{};
        p->opt = std::make_unique<torch::optim::SGD>(extract_params(params), opts);
        return p;
    ST_TRY_END return nullptr;
}

// =============================================================================
// Adam
// =============================================================================

ST_Optimizer smile_adam_create(ST_TensorVec params,
                                double lr, double b1, double b2,
                                double eps, double wd, int amsgrad) {
    ST_TRY_BEGIN
        auto opts = torch::optim::AdamOptions(lr)
                        .betas({b1, b2}).eps(eps).weight_decay(wd)
                        .amsgrad(static_cast<bool>(amsgrad));
        auto *p = new ST_Optimizer_{};
        p->opt = std::make_unique<torch::optim::Adam>(extract_params(params), opts);
        return p;
    ST_TRY_END return nullptr;
}

// =============================================================================
// AdamW
// =============================================================================

ST_Optimizer smile_adamw_create(ST_TensorVec params,
                                 double lr, double b1, double b2,
                                 double eps, double wd, int amsgrad) {
    ST_TRY_BEGIN
        auto opts = torch::optim::AdamWOptions(lr)
                        .betas({b1, b2}).eps(eps).weight_decay(wd)
                        .amsgrad(static_cast<bool>(amsgrad));
        auto *p = new ST_Optimizer_{};
        p->opt = std::make_unique<torch::optim::AdamW>(extract_params(params), opts);
        return p;
    ST_TRY_END return nullptr;
}

// =============================================================================
// RMSprop
// =============================================================================

ST_Optimizer smile_rmsprop_create(ST_TensorVec params,
                                   double lr, double alpha, double eps,
                                   double wd, double momentum, int centered) {
    ST_TRY_BEGIN
        auto opts = torch::optim::RMSpropOptions(lr)
                        .alpha(alpha).eps(eps).weight_decay(wd)
                        .momentum(momentum).centered(static_cast<bool>(centered));
        auto *p = new ST_Optimizer_{};
        p->opt = std::make_unique<torch::optim::RMSprop>(extract_params(params), opts);
        return p;
    ST_TRY_END return nullptr;
}

// =============================================================================
// Optimizer common
// =============================================================================

void smile_optimizer_free     (ST_Optimizer opt) { delete opt; }
void smile_optimizer_zero_grad(ST_Optimizer opt) {
    if (opt) { ST_TRY_BEGIN opt->opt->zero_grad(); ST_TRY_END }
}
void smile_optimizer_step(ST_Optimizer opt) {
    if (opt) { ST_TRY_BEGIN opt->opt->step(); ST_TRY_END }
}
void smile_optimizer_set_lr(ST_Optimizer opt, double lr) {
    if (!opt) return;
    ST_TRY_BEGIN
        for (auto &group : opt->opt->param_groups()) {
            static_cast<torch::optim::OptimizerOptions &>(group.options()).set_lr(lr);
        }
    ST_TRY_END
}

// =============================================================================
// Version / build info
// =============================================================================

int smile_torch_version(char *buf, int buf_len) {
    if (!buf || buf_len <= 0) return -1;
    std::snprintf(buf, buf_len, "%s", TORCH_VERSION);
    return 0;
}

void smile_set_default_dtype(ST_DType dtype) {
    ST_TRY_BEGIN
        torch::set_default_dtype(c10::scalarTypeToTypeMeta(to_scalar_type(dtype)));
    ST_TRY_END
}

void smile_manual_seed(int64_t seed) {
    ST_TRY_BEGIN torch::manual_seed(static_cast<uint64_t>(seed)); ST_TRY_END
}

// =============================================================================
// Tensor parallelism collectives (NCCL primary, peer-copy fallback)
// =============================================================================

#ifdef USE_NCCL
struct ST_NcclComm_ {
    int nRanks = 0;
    ncclComm_t *comms = nullptr; // length nRanks
};
#endif

ST_NcclComm smile_nccl_comm_create(int n, const int *device_indices) {
#ifndef USE_NCCL
    (void)n; (void)device_indices;
    set_error("smile_torch was built without NCCL (USE_NCCL not enabled)");
    return nullptr;
#else
    if (n < 1 || !device_indices) {
        set_error("smile_nccl_comm_create: invalid args");
        return nullptr;
    }
    ST_TRY_BEGIN
        auto *c = new ST_NcclComm_();
        c->nRanks = n;
        c->comms = new ncclComm_t[static_cast<size_t>(n)];
        ncclResult_t rc = ncclCommInitAll(c->comms, n, device_indices);
        if (rc != ncclSuccess) {
            delete[] c->comms;
            delete c;
            set_error(std::string("ncclCommInitAll: ") + ncclGetErrorString(rc));
            return nullptr;
        }
        return c;
    ST_TRY_END
    return nullptr;
#endif
}

void smile_nccl_comm_free(ST_NcclComm comm) {
#ifdef USE_NCCL
    if (!comm) return;
    for (int i = 0; i < comm->nRanks; i++) {
        if (comm->comms[i]) {
            ncclCommDestroy(comm->comms[i]);
        }
    }
    delete[] comm->comms;
    delete comm;
#else
    (void)comm;
#endif
}

#ifdef USE_NCCL
static ncclDataType_t to_nccl_dtype(c10::ScalarType dt) {
    switch (dt) {
        case c10::ScalarType::Float: return ncclFloat32;
        case c10::ScalarType::Half: return ncclFloat16;
        case c10::ScalarType::BFloat16: return ncclBfloat16;
        case c10::ScalarType::Double: return ncclFloat64;
        case c10::ScalarType::Int: return ncclInt32;
        case c10::ScalarType::Long: return ncclInt64;
        default: return ncclFloat32;
    }
}
#endif

int smile_nccl_all_reduce_sum(ST_NcclComm comm, int rank, ST_Tensor local) {
#ifndef USE_NCCL
    (void)comm; (void)rank; (void)local;
    set_error("smile_torch was built without NCCL");
    return -1;
#else
    if (!comm || !local || !local->t.defined()) {
        set_error("smile_nccl_all_reduce_sum: null args");
        return -1;
    }
    if (rank < 0 || rank >= comm->nRanks) {
        set_error("smile_nccl_all_reduce_sum: rank out of range");
        return -1;
    }
    ST_TRY_BEGIN
        auto t = local->t;
        if (!t.is_cuda()) {
            set_error("smile_nccl_all_reduce_sum: tensor must be CUDA");
            return -1;
        }
        if (!t.is_contiguous()) {
            set_error("smile_nccl_all_reduce_sum: tensor must be contiguous");
            return -1;
        }
        c10::cuda::CUDAGuard guard(t.device());
        cudaStream_t stream = at::cuda::getCurrentCUDAStream(t.device().index()).stream();
        ncclDataType_t dt = to_nccl_dtype(t.scalar_type());
        size_t count = static_cast<size_t>(t.numel());
        ncclResult_t rc = ncclAllReduce(
                t.data_ptr(), t.data_ptr(), count, dt, ncclSum,
                comm->comms[rank], stream);
        if (rc != ncclSuccess) {
            set_error(std::string("ncclAllReduce: ") + ncclGetErrorString(rc));
            return -1;
        }
        return 0;
    ST_TRY_END
    return -1;
#endif
}

int smile_nccl_broadcast(ST_NcclComm comm, int rank, int root, ST_Tensor local) {
#ifndef USE_NCCL
    (void)comm; (void)rank; (void)root; (void)local;
    set_error("smile_torch was built without NCCL");
    return -1;
#else
    if (!comm || !local || !local->t.defined()) {
        set_error("smile_nccl_broadcast: null args");
        return -1;
    }
    if (rank < 0 || rank >= comm->nRanks || root < 0 || root >= comm->nRanks) {
        set_error("smile_nccl_broadcast: rank/root out of range");
        return -1;
    }
    ST_TRY_BEGIN
        auto t = local->t;
        if (!t.is_cuda() || !t.is_contiguous()) {
            set_error("smile_nccl_broadcast: tensor must be contiguous CUDA");
            return -1;
        }
        c10::cuda::CUDAGuard guard(t.device());
        cudaStream_t stream = at::cuda::getCurrentCUDAStream(t.device().index()).stream();
        ncclDataType_t dt = to_nccl_dtype(t.scalar_type());
        size_t count = static_cast<size_t>(t.numel());
        ncclResult_t rc = ncclBroadcast(
                t.data_ptr(), t.data_ptr(), count, dt, root,
                comm->comms[rank], stream);
        if (rc != ncclSuccess) {
            set_error(std::string("ncclBroadcast: ") + ncclGetErrorString(rc));
            return -1;
        }
        return 0;
    ST_TRY_END
    return -1;
#endif
}

int smile_tp_all_reduce_sum(ST_Tensor *tensors, int n) {
    if (n <= 1) return 0;
    if (!tensors) {
        set_error("smile_tp_all_reduce_sum: null tensors");
        return -1;
    }
    ST_TRY_BEGIN
        for (int i = 0; i < n; i++) {
            if (!tensors[i] || !tensors[i]->t.defined()) {
                set_error("smile_tp_all_reduce_sum: null/undefined tensor");
                return -1;
            }
        }
        auto ref = tensors[0]->t;
        for (int i = 1; i < n; i++) {
            if (tensors[i]->t.sizes() != ref.sizes() || tensors[i]->t.dtype() != ref.dtype()) {
                set_error("smile_tp_all_reduce_sum: shape/dtype mismatch");
                return -1;
            }
        }
        // Peer-copy fallback (used when NCCL path is not taken from Java).
        auto& acc = tensors[0]->t;
        for (int i = 1; i < n; i++) {
            acc.add_(tensors[i]->t.to(acc.device(), /*non_blocking=*/true));
        }
        for (int i = 1; i < n; i++) {
            tensors[i]->t.copy_(acc.to(tensors[i]->t.device(), /*non_blocking=*/true));
        }
#ifdef USE_CUDA
        if (acc.is_cuda()) {
            c10::cuda::CUDAGuard guard(acc.device());
            AT_CUDA_CHECK(cudaStreamSynchronize(
                    at::cuda::getCurrentCUDAStream(acc.device().index()).stream()));
        }
#endif
        return 0;
    ST_TRY_END
    return -1;
}

int smile_tp_broadcast(ST_Tensor *tensors, int n, int root) {
    if (n <= 1) return 0;
    if (!tensors) {
        set_error("smile_tp_broadcast: null tensors");
        return -1;
    }
    if (root < 0 || root >= n) {
        set_error("smile_tp_broadcast: root out of range");
        return -1;
    }
    ST_TRY_BEGIN
        if (!tensors[root] || !tensors[root]->t.defined()) {
            set_error("smile_tp_broadcast: null/undefined root tensor");
            return -1;
        }
        auto src = tensors[root]->t;
        for (int i = 0; i < n; i++) {
            if (i == root) continue;
            if (!tensors[i] || !tensors[i]->t.defined()) {
                set_error("smile_tp_broadcast: null/undefined tensor");
                return -1;
            }
            tensors[i]->t.copy_(src.to(tensors[i]->t.device(), /*non_blocking=*/true));
        }
#ifdef USE_CUDA
        if (src.is_cuda()) {
            c10::cuda::CUDAGuard guard(src.device());
            AT_CUDA_CHECK(cudaStreamSynchronize(
                    at::cuda::getCurrentCUDAStream(src.device().index()).stream()));
        }
#endif
        return 0;
    ST_TRY_END
    return -1;
}

// =============================================================================
// Gated DeltaNet fused recurrent rule
// =============================================================================

#ifdef USE_CUDA
namespace {
std::atomic<bool> g_gated_delta_libtorch_warned{false};

void warn_gated_delta_libtorch_once(const char *reason) {
    if (!g_gated_delta_libtorch_warned.exchange(true)) {
        fprintf(stderr,
                "WARN smile: GatedDeltaNet recurrent falling back from fused CUDA to "
                "libtorch GPU (%s); subsequent fallbacks suppressed\n",
                reason ? reason : "unknown");
        fflush(stderr);
    }
}
} // namespace
#endif

static torch::Tensor l2norm_last(const torch::Tensor &x) {
    auto s = x.mul(x).sum(-1, /*keepdim=*/true).add(1e-6).rsqrt();
    return x.mul(s);
}

static torch::Tensor gated_delta_recurrent_libtorch(
        torch::Tensor q, torch::Tensor k, torch::Tensor v,
        torch::Tensor g, torch::Tensor beta, torch::Tensor state,
        float scale) {
    // q/k/v/g/beta/state/out all float contiguous; layouts [B,H,S,*] / [B,H,K,V]
    q = q.mul(scale);
    auto B = q.size(0), H = q.size(1), S = q.size(2), V = v.size(3);
    auto out = torch::zeros({B, H, S, V}, q.options());
    for (int64_t t = 0; t < S; ++t) {
        auto q_t = q.select(2, t); // [B,H,K]
        auto k_t = k.select(2, t);
        auto v_t = v.select(2, t);
        auto g_t = g.select(2, t).exp().view({B, H, 1, 1});
        auto beta_t = beta.select(2, t).unsqueeze(-1); // [B,H,1]
        state.mul_(g_t);
        auto kv = at::matmul(k_t.unsqueeze(-2), state).squeeze(-2); // [B,H,V]
        auto delta = v_t.sub(kv).mul(beta_t);
        state.add_(k_t.unsqueeze(-1).mul(delta.unsqueeze(-2)));
        auto y = at::matmul(q_t.unsqueeze(-2), state).squeeze(-2);
        out.select(2, t).copy_(y);
    }
    return out;
}

ST_Tensor smile_recurrent_gated_delta_rule(
        ST_Tensor query, ST_Tensor key, ST_Tensor value,
        ST_Tensor g, ST_Tensor beta, ST_Tensor state,
        int qk_l2norm) {
    if (!query || !key || !value || !g || !beta || !state) {
        set_error("smile_recurrent_gated_delta_rule: null tensor");
        return nullptr;
    }
    ST_TRY_BEGIN
        auto q0 = query->t;
        auto k0 = key->t;
        auto v0 = value->t;
        auto g0 = g->t;
        auto beta0 = beta->t;
        auto st = state->t;
        if (q0.dim() != 4 || k0.dim() != 4 || v0.dim() != 4
                || g0.dim() != 3 || beta0.dim() != 3 || st.dim() != 4) {
            set_error("smile_recurrent_gated_delta_rule: unexpected ranks");
            return nullptr;
        }
        if (st.scalar_type() != c10::ScalarType::Float) {
            set_error("smile_recurrent_gated_delta_rule: state must be float32");
            return nullptr;
        }

        // [B,S,H,D] → [B,H,S,D] float contiguous
        auto q = q0.transpose(1, 2).contiguous().to(c10::ScalarType::Float);
        auto k = k0.transpose(1, 2).contiguous().to(c10::ScalarType::Float);
        auto v = v0.transpose(1, 2).contiguous().to(c10::ScalarType::Float);
        auto gf = g0.transpose(1, 2).contiguous().to(c10::ScalarType::Float);
        auto bf = beta0.transpose(1, 2).contiguous().to(c10::ScalarType::Float);
        if (qk_l2norm) {
            q = l2norm_last(q);
            k = l2norm_last(k);
        }
        const float scale = 1.0f / std::sqrt(static_cast<float>(k.size(3)));
        auto B = k.size(0), H = k.size(1), S = k.size(2), Vdim = v.size(3);
        auto out_f = torch::empty({B, H, S, Vdim}, q.options());

#ifdef USE_CUDA
        if (q.is_cuda()) {
            c10::cuda::CUDAGuard guard(q.device());
            cudaStream_t stream = at::cuda::getCurrentCUDAStream(q.device().index()).stream();
            int64_t K = k.size(3);
            int64_t Vdim = v.size(3);
            // Fused kernel keeps full [K,V] state in shared mem (see smile_gated_delta.cu).
            const size_t smem = static_cast<size_t>(
                    (K * Vdim + K + K + Vdim + Vdim + Vdim + Vdim) * sizeof(float));
            int dev = q.device().index();
            int smem_limit = 0;
            cudaDeviceGetAttribute(&smem_limit, cudaDevAttrMaxSharedMemoryPerBlockOptin, dev);
            if (smem_limit <= 0) {
                cudaDeviceGetAttribute(&smem_limit, cudaDevAttrMaxSharedMemoryPerBlock, dev);
            }
            bool fused_ok = false;
            if (smem <= static_cast<size_t>(smem_limit)) {
                // Kernel expects g already exponentiated; scale is folded into q.
                auto g_exp = gf.exp();
                auto q_scaled = q.mul(scale);
                int rc = smile_gated_delta_recurrent_cuda(
                        q_scaled.data_ptr<float>(),
                        k.data_ptr<float>(),
                        v.data_ptr<float>(),
                        g_exp.data_ptr<float>(),
                        bf.data_ptr<float>(),
                        st.data_ptr<float>(),
                        out_f.data_ptr<float>(),
                        B, H, S, K, Vdim,
                        /*scale already applied*/ 1.0f,
                        stream);
                fused_ok = (rc == 0);
            }
            if (!fused_ok) {
                char reason[256];
                if (smem > static_cast<size_t>(smem_limit)) {
                    snprintf(reason, sizeof(reason),
                             "fused kernel shared mem %zu bytes exceeds device limit %d",
                             smem, smem_limit);
                } else {
                    const char *err = smile_gated_delta_last_error();
                    snprintf(reason, sizeof(reason), "%s",
                             (err && err[0]) ? err : "fused kernel launch failed");
                }
                warn_gated_delta_libtorch_once(reason);
                out_f = gated_delta_recurrent_libtorch(q, k, v, gf, bf, st, scale);
            }
        } else
#endif
        {
            out_f = gated_delta_recurrent_libtorch(q, k, v, gf, bf, st, scale);
        }

        auto out = out_f.transpose(1, 2).contiguous().to(q0.scalar_type());
        return new ST_Tensor_{ out };
    ST_TRY_END
    return nullptr;
}

ST_Tensor smile_flashinfer_paged_attention(
        ST_Tensor query,
        ST_Tensor k_cache,
        ST_Tensor v_cache,
        ST_Tensor paged_kv_indptr,
        ST_Tensor paged_kv_indices,
        ST_Tensor paged_kv_last_page_len,
        int page_size,
        int num_kv_heads,
        int head_dim,
        int cache_len,
        double scale,
        float k_scale,
        float v_scale,
        int is_causal,
        ST_Tensor attn_mask,
        ST_FlashInferWorkspace workspace) {
#if defined(USE_CUDA) && defined(USE_FLASHINFER)
    if (!query || !k_cache || !v_cache || !paged_kv_indptr
            || !paged_kv_indices || !paged_kv_last_page_len || !workspace) {
        set_error("smile_flashinfer_paged_attention: null argument");
        return nullptr;
    }
    ST_TRY_BEGIN
        int dev = smile_flashinfer_workspace_device_index(workspace);
        c10::cuda::CUDAGuard guard(dev);
        auto q = query->t;
        float sc = scale > 0
                ? static_cast<float>(scale)
                : (1.0f / std::sqrt(static_cast<float>(head_dim > 0 ? head_dim : 1)));
        torch::Tensor out = torch::empty_like(q);
        std::string err;
        const torch::Tensor *mask_ptr = (attn_mask && attn_mask->t.defined())
                ? &attn_mask->t
                : nullptr;
        at::Tensor *float_ws = nullptr;
        at::Tensor *int_ws = nullptr;
        at::Tensor *pinned_ws = nullptr;
        if (smile_flashinfer_workspace_get_tensors(
                workspace, &float_ws, &int_ws, &pinned_ws) != 0) {
            set_error("smile_flashinfer_paged_attention: invalid workspace");
            return nullptr;
        }
        int rc = smile_flashinfer_paged_attention_cuda(
                q, k_cache->t, v_cache->t,
                paged_kv_indptr->t, paged_kv_indices->t, paged_kv_last_page_len->t,
                page_size, num_kv_heads, head_dim, cache_len,
                sc, k_scale, v_scale, is_causal, mask_ptr,
                float_ws, int_ws, pinned_ws,
                out, err);
        if (rc != 0) {
            set_error(err.empty() ? "flashinfer paged attention failed" : err);
            return nullptr;
        }
        return new ST_Tensor_{ out };
    ST_TRY_END
    return nullptr;
#else
    (void)query; (void)k_cache; (void)v_cache;
    (void)paged_kv_indptr; (void)paged_kv_indices; (void)paged_kv_last_page_len;
    (void)page_size; (void)num_kv_heads; (void)head_dim; (void)cache_len;
    (void)scale; (void)k_scale; (void)v_scale;
    (void)is_causal; (void)attn_mask; (void)workspace;
#  ifdef USE_CUDA
    set_error("smile_torch built without USE_FLASHINFER");
#  else
    set_error_no_cuda_build();
#  endif
    return nullptr;
#endif
}

ST_Tensor smile_flashinfer_ragged_attention(
        ST_Tensor query,
        ST_Tensor key,
        ST_Tensor value,
        ST_Tensor indptr,
        int num_kv_heads,
        int head_dim,
        double scale,
        int is_causal,
        ST_Tensor attn_mask) {
#if defined(USE_CUDA) && defined(USE_FLASHINFER)
    if (!query || !key || !value || !indptr) {
        set_error("smile_flashinfer_ragged_attention: null argument");
        return nullptr;
    }
    ST_TRY_BEGIN
        auto q = query->t;
        float sc = scale > 0
                ? static_cast<float>(scale)
                : (1.0f / std::sqrt(static_cast<float>(head_dim > 0 ? head_dim : 1)));
        torch::Tensor out = torch::empty_like(q);
        std::string err;
        const torch::Tensor *mask_ptr = (attn_mask && attn_mask->t.defined())
                ? &attn_mask->t
                : nullptr;
        int rc = smile_flashinfer_ragged_attention_cuda(
                q, key->t, value->t, indptr->t, num_kv_heads, head_dim,
                sc, is_causal, mask_ptr, out, err);
        if (rc != 0) {
            set_error(err.empty() ? "flashinfer ragged attention failed" : err);
            return nullptr;
        }
        return new ST_Tensor_{ out };
    ST_TRY_END
    return nullptr;
#else
    (void)query; (void)key; (void)value; (void)indptr;
    (void)num_kv_heads; (void)head_dim; (void)scale; (void)is_causal; (void)attn_mask;
#  ifdef USE_CUDA
    set_error("smile_torch built without USE_FLASHINFER");
#  else
    set_error_no_cuda_build();
#  endif
    return nullptr;
#endif
}

} // extern "C"

