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
#include <optional>
#include <limits>

// ── CUDA introspection (compiled only when CUDA is present) ───────────────────
#ifdef USE_CUDA
#  include <cuda_runtime.h>
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
        case ST_DTYPE_BOOL:     return c10::ScalarType::Bool;
        case ST_DTYPE_QUINT8:   return c10::ScalarType::QUInt8;
        case ST_DTYPE_QINT8:    return c10::ScalarType::QInt8;
        case ST_DTYPE_BYTE:     return c10::ScalarType::Byte;
        case ST_DTYPE_SHORT:    return c10::ScalarType::Short;
        case ST_DTYPE_INT:      return c10::ScalarType::Int;
        case ST_DTYPE_LONG:     return c10::ScalarType::Long;
        case ST_DTYPE_BFLOAT16: return c10::ScalarType::BFloat16;
        case ST_DTYPE_HALF:     return c10::ScalarType::Half;
        case ST_DTYPE_FLOAT:    return c10::ScalarType::Float;
        case ST_DTYPE_DOUBLE:   return c10::ScalarType::Double;
        default: return c10::ScalarType::Undefined;
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
struct ST_Linear_        { torch::nn::Linear mod; };
struct ST_Conv2d_        { torch::nn::Conv2d mod; };
struct ST_BatchNorm1d_   { torch::nn::BatchNorm1d mod; };
struct ST_BatchNorm2d_   { torch::nn::BatchNorm2d mod; };
struct ST_Dropout_       { torch::nn::Dropout mod; };
struct ST_Embedding_     { torch::nn::Embedding mod; };
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

// =============================================================================
// Helpers: build TensorOptions from handle (NULL → default)
// =============================================================================

static at::TensorOptions get_opts(ST_TensorOptions opts) {
    return opts ? opts->opts : at::TensorOptions();
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
        cudaRuntimeGetVersion(&ver);
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
        cudaGetDeviceProperties(&prop, device_index);
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
        if (cudaGetDeviceProperties(&prop, device_index) == cudaSuccess)
            return static_cast<int64_t>(prop.totalGlobalMem);
    ST_TRY_END
#endif
    return -1;
}

void smile_cuda_empty_cache(void) {
    ST_TRY_BEGIN
        c10::cuda::CUDACachingAllocator::emptyCache();
    ST_TRY_END
}

int smile_cuda_is_bf16_supported(void) {
    ST_TRY_BEGIN
        int dev = at::cuda::current_device();
        cudaDeviceProp prop{};
        cudaGetDeviceProperties(&prop, dev);
        return (prop.major >= 8) ? 1 : 0;
    ST_TRY_END
    return 0;
}

int smile_mps_is_available(void) {
    ST_TRY_BEGIN
        return at::hasMPS() ? 1 : 0;
    ST_TRY_END
    return 0;
}

void smile_mps_empty_cache(void) {
#if defined(__APPLE__)
    ST_TRY_BEGIN
        at::mps::emptyCache();
    ST_TRY_END
#endif
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
    return d ? static_cast<int8_t>(d->d.index()) : -1;
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
        opts->opts = opts->opts.dtype(c10::nullopt);
    else
        opts->opts = opts->opts.dtype(to_scalar_type(dtype));
    return opts;
}

ST_TensorOptions smile_tensor_options_device(ST_TensorOptions opts, ST_Device device) {
    if (!opts) return nullptr;
    if (device)
        opts->opts = opts->opts.device(device->d);
    else
        opts->opts = opts->opts.device(c10::nullopt);
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
        opts->opts = opts->opts.requires_grad(c10::nullopt);
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
// Tensor — helper for shape vector
// =============================================================================

static std::vector<int64_t> to_shape(const int64_t *shape, int ndim) {
    return std::vector<int64_t>(shape, shape + ndim);
}

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
    MAKE_TENSOR(torch::eye(to_shape(shape, ndim), get_opts(opts)));
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
    MAKE_TENSOR(torch::from_blob(const_cast<uint8_t*>(data), to_shape(shape,ndim),
                                 at::dtype(at::kBool)).clone());
}
ST_Tensor smile_tensor_from_byte  (const int8_t  *data, const int64_t *shape, int ndim) {
    MAKE_TENSOR(torch::from_blob(const_cast<int8_t*>(data), to_shape(shape,ndim),
                                 at::dtype(at::kChar)).clone());
}
ST_Tensor smile_tensor_from_short (const int16_t *data, const int64_t *shape, int ndim) {
    MAKE_TENSOR(torch::from_blob(const_cast<int16_t*>(data), to_shape(shape,ndim),
                                 at::dtype(at::kShort)).clone());
}
ST_Tensor smile_tensor_from_int   (const int32_t *data, const int64_t *shape, int ndim) {
    MAKE_TENSOR(torch::from_blob(const_cast<int32_t*>(data), to_shape(shape,ndim),
                                 at::dtype(at::kInt)).clone());
}
ST_Tensor smile_tensor_from_long  (const int64_t *data, const int64_t *shape, int ndim) {
    MAKE_TENSOR(torch::from_blob(const_cast<int64_t*>(data), to_shape(shape,ndim),
                                 at::dtype(at::kLong)).clone());
}
ST_Tensor smile_tensor_from_float (const float   *data, const int64_t *shape, int ndim) {
    MAKE_TENSOR(torch::from_blob(const_cast<float*>(data), to_shape(shape,ndim),
                                 at::dtype(at::kFloat)).clone());
}
ST_Tensor smile_tensor_from_double(const double  *data, const int64_t *shape, int ndim) {
    MAKE_TENSOR(torch::from_blob(const_cast<double*>(data), to_shape(shape,ndim),
                                 at::dtype(at::kDouble)).clone());
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

uint8_t *smile_tensor_data_ptr_bool  (ST_Tensor t) { return t ? t->t.data_ptr<bool>()    : nullptr; }
int8_t  *smile_tensor_data_ptr_byte  (ST_Tensor t) { return t ? t->t.data_ptr<int8_t>()  : nullptr; }
int16_t *smile_tensor_data_ptr_short (ST_Tensor t) { return t ? t->t.data_ptr<int16_t>() : nullptr; }
int32_t *smile_tensor_data_ptr_int   (ST_Tensor t) { return t ? t->t.data_ptr<int32_t>() : nullptr; }
int64_t *smile_tensor_data_ptr_long  (ST_Tensor t) { return t ? t->t.data_ptr<int64_t>() : nullptr; }
float   *smile_tensor_data_ptr_float (ST_Tensor t) { return t ? t->t.data_ptr<float>()   : nullptr; }
double  *smile_tensor_data_ptr_double(ST_Tensor t) { return t ? t->t.data_ptr<double>()  : nullptr; }

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
    if (dtype == ST_DTYPE_UNDEFINED)
        MAKE_TENSOR(t->t.to(device->d));
    else
        MAKE_TENSOR(t->t.to(device->d, to_scalar_type(dtype)));
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
    if (dtype == ST_DTYPE_UNDEFINED)
        MAKE_TENSOR(t->t.sum(d, static_cast<bool>(keepdim)));
    else
        MAKE_TENSOR(t->t.sum(d, static_cast<bool>(keepdim), to_scalar_type(dtype)));
}

ST_Tensor smile_tensor_mean_dims(ST_Tensor t, const int64_t *dims, int ndim,
                                 int keepdim, ST_DType dtype) {
    auto d = to_shape(dims, ndim);
    if (dtype == ST_DTYPE_UNDEFINED)
        MAKE_TENSOR(t->t.mean(d, static_cast<bool>(keepdim)));
    else
        MAKE_TENSOR(t->t.mean(d, static_cast<bool>(keepdim), to_scalar_type(dtype)));
}

ST_Tensor smile_tensor_argmax(ST_Tensor t, int64_t dim, int keepdim, int has_dim) {
    if (!has_dim)
        MAKE_TENSOR(t->t.argmax());
    else
        MAKE_TENSOR(t->t.argmax(dim, static_cast<bool>(keepdim)));
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

void smile_tensor_neg_      (ST_Tensor t)                               { if (t) t->t.neg_(); }
void smile_tensor_add_s_    (ST_Tensor t, ST_Scalar s)                  { if (t&&s) t->t.add_(s->s); }
void smile_tensor_add_t_    (ST_Tensor a, ST_Tensor b)                  { if (a&&b) a->t.add_(b->t); }
void smile_tensor_add_t_s_  (ST_Tensor a, ST_Tensor b, ST_Scalar alpha) { if (a&&b&&alpha) a->t.add_(b->t, alpha->s); }
void smile_tensor_sub_s_    (ST_Tensor t, ST_Scalar s)                  { if (t&&s) t->t.sub_(s->s); }
void smile_tensor_sub_t_    (ST_Tensor a, ST_Tensor b)                  { if (a&&b) a->t.sub_(b->t); }
void smile_tensor_sub_t_s_  (ST_Tensor a, ST_Tensor b, ST_Scalar alpha) { if (a&&b&&alpha) a->t.sub_(b->t, alpha->s); }
void smile_tensor_mul_s_    (ST_Tensor t, ST_Scalar s)                  { if (t&&s) t->t.mul_(s->s); }
void smile_tensor_mul_t_    (ST_Tensor a, ST_Tensor b)                  { if (a&&b) a->t.mul_(b->t); }
void smile_tensor_div_s_    (ST_Tensor t, ST_Scalar s)                  { if (t&&s) t->t.div_(s->s); }
void smile_tensor_div_t_    (ST_Tensor a, ST_Tensor b)                  { if (a&&b) a->t.div_(b->t); }
void smile_tensor_pow_s_    (ST_Tensor t, ST_Scalar e)                  { if (t&&e) t->t.pow_(e->s); }
void smile_tensor_fill_     (ST_Tensor t, ST_Scalar v)                  { if (t&&v) t->t.fill_(v->s); }
void smile_tensor_bernoulli_ (ST_Tensor t, double p)                    { if (t) t->t.bernoulli_(p); }
void smile_tensor_mul_scalar_(ST_Tensor t, double s)                    { if (t) t->t.mul_(at::Scalar(s)); }

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

void smile_tensor_abs_  (ST_Tensor t) { if (t) t->t.abs_(); }
void smile_tensor_log_  (ST_Tensor t) { if (t) t->t.log_(); }
void smile_tensor_exp_  (ST_Tensor t) { if (t) t->t.exp_(); }
void smile_tensor_rsqrt_(ST_Tensor t) { if (t) t->t.rsqrt_(); }
void smile_tensor_cos_  (ST_Tensor t) { if (t) t->t.cos_(); }
void smile_tensor_sin_  (ST_Tensor t) { if (t) t->t.sin_(); }
void smile_tensor_acos_ (ST_Tensor t) { if (t) t->t.acos_(); }
void smile_tensor_asin_ (ST_Tensor t) { if (t) t->t.asin_(); }

static std::optional<at::Scalar> maybe_scalar(int has, ST_Scalar s) {
    if (has && s) return s->s;
    return std::nullopt;
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
ST_Tensor smile_tensor_outer  (ST_Tensor a, ST_Tensor b) { MAKE_TENSOR(at::outer(a->t, b->t)); }

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
ST_Tensor smile_torch_tanhshrink (ST_Tensor x) { MAKE_TENSOR(torch::tanhshrink(x->t)); }

// =============================================================================
// Loss functions
// =============================================================================

ST_Tensor smile_torch_l1_loss   (ST_Tensor i, ST_Tensor t) { MAKE_TENSOR(torch::l1_loss(i->t, t->t)); }
ST_Tensor smile_torch_mse_loss  (ST_Tensor i, ST_Tensor t) { MAKE_TENSOR(torch::mse_loss(i->t, t->t)); }
ST_Tensor smile_torch_nll_loss  (ST_Tensor i, ST_Tensor t) { MAKE_TENSOR(torch::nll_loss(i->t, t->t)); }

ST_Tensor smile_torch_cross_entropy(ST_Tensor input, ST_Tensor target,
                                     int64_t ignore_index, ST_Reduction reduction) {
    auto opts = torch::nn::CrossEntropyLossOptions()
                    .ignore_index(ignore_index)
                    .reduction(static_cast<torch::Reduction::Reduction>(reduction));
    MAKE_TENSOR(torch::cross_entropy_loss(input->t, target->t,
                                          {}, opts.reduction(), opts.ignore_index(),
                                          opts.label_smoothing()));
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
    ST_TRY_BEGIN return new ST_OutputArchive_{}; ST_TRY_END return nullptr;
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
        auto opts = torch::nn::LinearOptions(in, out).bias(static_cast<bool>(bias));
        return new ST_Linear_{ torch::nn::Linear(opts) };
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

static std::vector<int64_t> param2(const int64_t *p, int64_t def) {
    if (p) return {p[0], p[1]};
    return {def, def};
}

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
    ST_TRY_BEGIN return new ST_Embedding_{ torch::nn::Embedding(num, dim) }; ST_TRY_END return nullptr;
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
// Optimizers — helpers
// =============================================================================

static std::vector<at::Tensor> extract_params(ST_TensorVec v) {
    return v ? v->vec : std::vector<at::Tensor>{};
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

} // extern "C"

