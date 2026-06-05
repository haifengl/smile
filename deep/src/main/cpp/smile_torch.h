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
 * smile_torch.h — Stable C API (Hourglass pattern) over libtorch.
 *
 * All libtorch C++ types are hidden behind opaque handles.  Every function
 * that can fail stores a NUL-terminated error message retrievable via
 * smile_last_error() and returns NULL / a sentinel value.
 *
 * Thread-safety: each handle is NOT thread-safe on its own.  Share handles
 * across threads only under external locking, or use separate handle sets
 * per thread.
 *
 * Ownership: every "create" or "clone" function returns a new handle that the
 * caller owns and MUST release by calling the matching "free" function.
 * Functions that return a "view" or "borrow" (names contain "_borrow") do NOT
 * transfer ownership — do NOT free them.
 */

#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * ABI helpers
 * ========================================================================= */

#if defined(_WIN32) || defined(__CYGWIN__)
#  ifdef SMILE_TORCH_BUILD
#    define SMILE_API __declspec(dllexport)
#  else
#    define SMILE_API __declspec(dllimport)
#  endif
#else
#  define SMILE_API __attribute__((visibility("default")))
#endif

/* =========================================================================
 * Opaque handle types
 *
 * Each "ST_Xxx" is a pointer to an opaque C++ object.  All are typedef'd
 * to distinct struct pointers so the C compiler catches type mismatches.
 * ========================================================================= */

typedef struct ST_Tensor_        *ST_Tensor;
typedef struct ST_TensorOptions_ *ST_TensorOptions;
typedef struct ST_Scalar_        *ST_Scalar;
typedef struct ST_Device_        *ST_Device;
typedef struct ST_Module_        *ST_Module;
typedef struct ST_ModuleList_    *ST_ModuleList;
typedef struct ST_Linear_        *ST_Linear;
typedef struct ST_Conv2d_        *ST_Conv2d;
typedef struct ST_BatchNorm1d_   *ST_BatchNorm1d;
typedef struct ST_BatchNorm2d_   *ST_BatchNorm2d;
typedef struct ST_Dropout_       *ST_Dropout;
typedef struct ST_Embedding_     *ST_Embedding;
typedef struct ST_GroupNorm_     *ST_GroupNorm;
typedef struct ST_MaxPool2d_     *ST_MaxPool2d;
typedef struct ST_AvgPool2d_     *ST_AvgPool2d;
typedef struct ST_AdaptiveAvgPool2d_ *ST_AdaptiveAvgPool2d;
typedef struct ST_Optimizer_     *ST_Optimizer;
typedef struct ST_InputArchive_  *ST_InputArchive;
typedef struct ST_OutputArchive_ *ST_OutputArchive;
typedef struct ST_NoGradGuard_   *ST_NoGradGuard;
typedef struct ST_TensorIndex_   *ST_TensorIndex;
typedef struct ST_TensorIndexVec_ *ST_TensorIndexVec;
typedef struct ST_TensorVec_     *ST_TensorVec;
typedef struct ST_Slice_         *ST_Slice;

/* =========================================================================
 * Scalar type enum  (mirrors torch::ScalarType)
 * ========================================================================= */

typedef enum {
    ST_DTYPE_BOOL      =  11,
    ST_DTYPE_QUINT8    =  12,
    ST_DTYPE_QINT8     =  13,
    ST_DTYPE_BYTE      =   1,   /* uint8  */
    ST_DTYPE_SHORT     =   2,   /* int16  */
    ST_DTYPE_INT       =   3,   /* int32  */
    ST_DTYPE_LONG      =   4,   /* int64  */
    ST_DTYPE_BFLOAT16  =  15,
    ST_DTYPE_HALF      =   5,   /* float16 */
    ST_DTYPE_FLOAT     =   6,   /* float32 */
    ST_DTYPE_DOUBLE    =   7,   /* float64 */
    ST_DTYPE_UNDEFINED = 255
} ST_DType;

/* =========================================================================
 * Memory layout enum  (mirrors torch::Layout)
 * ========================================================================= */

typedef enum {
    ST_LAYOUT_STRIDED     = 0,
    ST_LAYOUT_SPARSE_COO  = 1,
    ST_LAYOUT_SPARSE_CSR  = 2,
    ST_LAYOUT_MKLDNN      = 3
} ST_Layout;

/* =========================================================================
 * Device type enum  (mirrors torch::DeviceType)
 * ========================================================================= */

typedef enum {
    ST_DEVICE_CPU  = 0,
    ST_DEVICE_CUDA = 1,
    ST_DEVICE_MPS  = 9
} ST_DeviceType;

/* =========================================================================
 * Reduction enum  (mirrors torch::Reduction::Reduction)
 * ========================================================================= */

typedef enum {
    ST_REDUCTION_NONE  = 0,
    ST_REDUCTION_MEAN  = 1,
    ST_REDUCTION_SUM   = 2
} ST_Reduction;

/* =========================================================================
 * Padding-mode enum  (mirrors torch::nn::detail::conv_padding_mode_t)
 * ========================================================================= */

typedef enum {
    ST_PAD_ZEROS     = 0,
    ST_PAD_REFLECT   = 1,
    ST_PAD_REPLICATE = 2,
    ST_PAD_CIRCULAR  = 3
} ST_PaddingMode;

/* =========================================================================
 * Error handling
 * ========================================================================= */

/** Returns a NUL-terminated description of the last error, or "" if none. */
SMILE_API const char *smile_last_error(void);

/* =========================================================================
 * CUDA / Device utilities
 * ========================================================================= */

/** Returns 1 if a CUDA device is available, 0 otherwise. */
SMILE_API int smile_cuda_is_available(void);

/** Returns the number of available CUDA devices (0 if none). */
SMILE_API int smile_cuda_device_count(void);

/** Returns the CUDA-runtime version string into buf (at most buf_len bytes).
 *  Returns 0 on success, -1 on failure. */
SMILE_API int smile_cuda_runtime_version(char *buf, int buf_len);

/** Returns the name of CUDA device at index into buf. */
SMILE_API int smile_cuda_device_name(int device_index, char *buf, int buf_len);

/** Returns the total memory (bytes) of CUDA device at index, or -1 on error. */
SMILE_API int64_t smile_cuda_total_memory(int device_index);

/** Frees cached CUDA memory held by the allocator. */
SMILE_API void smile_cuda_empty_cache(void);

/** Returns 1 if BF16 is supported on current CUDA device. */
SMILE_API int smile_cuda_is_bf16_supported(void);

/** Returns 1 if MPS (Apple Silicon GPU) is available. */
SMILE_API int smile_mps_is_available(void);

/** Frees cached MPS memory. */
SMILE_API void smile_mps_empty_cache(void);

/** Returns the number of CPU threads used by PyTorch. */
SMILE_API int smile_get_num_threads(void);

/** Sets the number of CPU threads used by PyTorch. */
SMILE_API void smile_set_num_threads(int n);

/* ---- Device handle ---- */

/** Creates a Device handle.  device_type is ST_DeviceType; index is -1 for default. */
SMILE_API ST_Device smile_device_create(int device_type, int8_t index);

SMILE_API void smile_device_free(ST_Device d);

/** Returns 1 if device is CPU. */
SMILE_API int smile_device_is_cpu(ST_Device d);

/** Returns 1 if device is CUDA. */
SMILE_API int smile_device_is_cuda(ST_Device d);

/** Returns 1 if device is MPS. */
SMILE_API int smile_device_is_mps(ST_Device d);

/** Returns the device index (-1 for CPU or single-device). */
SMILE_API int8_t smile_device_index(ST_Device d);

/** Copies the device string (e.g. "cpu", "cuda:0") into buf. */
SMILE_API int smile_device_str(ST_Device d, char *buf, int buf_len);

/* =========================================================================
 * TensorOptions
 * ========================================================================= */

SMILE_API ST_TensorOptions smile_tensor_options_create(void);
SMILE_API void              smile_tensor_options_free(ST_TensorOptions opts);

/** Sets the dtype; pass ST_DTYPE_UNDEFINED to clear. */
SMILE_API ST_TensorOptions smile_tensor_options_dtype(ST_TensorOptions opts, ST_DType dtype);

/** Sets the device; pass NULL to clear. */
SMILE_API ST_TensorOptions smile_tensor_options_device(ST_TensorOptions opts, ST_Device device);

/** Sets the memory layout. */
SMILE_API ST_TensorOptions smile_tensor_options_layout(ST_TensorOptions opts, ST_Layout layout);

/** Sets requires_grad; pass -1 to clear. */
SMILE_API ST_TensorOptions smile_tensor_options_requires_grad(ST_TensorOptions opts, int requires_grad);

/* =========================================================================
 * Scalar
 * ========================================================================= */

SMILE_API ST_Scalar smile_scalar_from_int(int64_t value);
SMILE_API ST_Scalar smile_scalar_from_float(double value);
SMILE_API void      smile_scalar_free(ST_Scalar s);

/* =========================================================================
 * Tensor — Construction
 * ========================================================================= */

SMILE_API void smile_tensor_free(ST_Tensor t);

/** Returns a deep copy of the tensor. */
SMILE_API ST_Tensor smile_tensor_clone(ST_Tensor t);

/* Factory functions — opts may be NULL for defaults */
SMILE_API ST_Tensor smile_tensor_eye(const int64_t *shape, int ndim, ST_TensorOptions opts);
SMILE_API ST_Tensor smile_tensor_full(const int64_t *shape, int ndim, ST_Scalar value, ST_TensorOptions opts);
SMILE_API ST_Tensor smile_tensor_empty(const int64_t *shape, int ndim, ST_TensorOptions opts);
SMILE_API ST_Tensor smile_tensor_zeros(const int64_t *shape, int ndim, ST_TensorOptions opts);
SMILE_API ST_Tensor smile_tensor_ones(const int64_t *shape, int ndim, ST_TensorOptions opts);
SMILE_API ST_Tensor smile_tensor_rand(const int64_t *shape, int ndim, ST_TensorOptions opts);
SMILE_API ST_Tensor smile_tensor_randn(const int64_t *shape, int ndim, ST_TensorOptions opts);

/** arange(start, end, step) with optional dtype */
SMILE_API ST_Tensor smile_tensor_arange(double start, double end, double step, ST_TensorOptions opts);

/* From raw host data (data is copied into the tensor).
 * dtype must match the element type of data. */
SMILE_API ST_Tensor smile_tensor_from_bool  (const uint8_t  *data, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_from_byte  (const int8_t   *data, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_from_short (const int16_t  *data, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_from_int   (const int32_t  *data, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_from_long  (const int64_t  *data, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_from_float (const float    *data, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_from_double(const double   *data, const int64_t *shape, int ndim);

/* =========================================================================
 * Tensor — Metadata
 * ========================================================================= */

SMILE_API int     smile_tensor_is_null(ST_Tensor t);
SMILE_API int     smile_tensor_is_view(ST_Tensor t);
SMILE_API int     smile_tensor_dim(ST_Tensor t);
SMILE_API int64_t smile_tensor_size(ST_Tensor t, int64_t dim);

/** Copies the shape into shape[0..ndim-1].  Returns the number of dims. */
SMILE_API int smile_tensor_shape(ST_Tensor t, int64_t *shape, int max_dims);

SMILE_API ST_DType smile_tensor_dtype(ST_Tensor t);
SMILE_API int      smile_tensor_requires_grad(ST_Tensor t);
SMILE_API void     smile_tensor_set_requires_grad(ST_Tensor t, int requires_grad);
SMILE_API int      smile_tensor_is_training(ST_Tensor t);

/* =========================================================================
 * Tensor — Data Pointers
 * ========================================================================= */

SMILE_API uint8_t  *smile_tensor_data_ptr_bool  (ST_Tensor t);
SMILE_API int8_t   *smile_tensor_data_ptr_byte  (ST_Tensor t);
SMILE_API int16_t  *smile_tensor_data_ptr_short (ST_Tensor t);
SMILE_API int32_t  *smile_tensor_data_ptr_int   (ST_Tensor t);
SMILE_API int64_t  *smile_tensor_data_ptr_long  (ST_Tensor t);
SMILE_API float    *smile_tensor_data_ptr_float (ST_Tensor t);
SMILE_API double   *smile_tensor_data_ptr_double(ST_Tensor t);

/* =========================================================================
 * Tensor — Item (scalar extraction)
 * ========================================================================= */

SMILE_API uint8_t  smile_tensor_item_bool  (ST_Tensor t);
SMILE_API int8_t   smile_tensor_item_byte  (ST_Tensor t);
SMILE_API int16_t  smile_tensor_item_short (ST_Tensor t);
SMILE_API int32_t  smile_tensor_item_int   (ST_Tensor t);
SMILE_API int64_t  smile_tensor_item_long  (ST_Tensor t);
SMILE_API float    smile_tensor_item_float (ST_Tensor t);
SMILE_API double   smile_tensor_item_double(ST_Tensor t);

/* =========================================================================
 * Tensor — Type / Device casting
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_to_dtype (ST_Tensor t, ST_DType dtype);
SMILE_API ST_Tensor smile_tensor_to_device(ST_Tensor t, ST_Device device, ST_DType dtype);

/* =========================================================================
 * Tensor — Shape manipulation
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_reshape   (ST_Tensor t, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_view      (ST_Tensor t, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_flatten   (ST_Tensor t, int64_t start_dim, int64_t end_dim);
SMILE_API ST_Tensor smile_tensor_expand    (ST_Tensor t, const int64_t *size, int ndim);
SMILE_API ST_Tensor smile_tensor_unsqueeze (ST_Tensor t, int64_t dim);
SMILE_API ST_Tensor smile_tensor_permute   (ST_Tensor t, const int64_t *dims, int ndim);
SMILE_API ST_Tensor smile_tensor_transpose (ST_Tensor t, int64_t dim0, int64_t dim1);
SMILE_API ST_Tensor smile_tensor_contiguous(ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_triu      (ST_Tensor t, int64_t diagonal);
SMILE_API void      smile_tensor_triu_     (ST_Tensor t, int64_t diagonal);

/* =========================================================================
 * Tensor — Autograd
 * ========================================================================= */

SMILE_API void      smile_tensor_backward(ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_detach  (ST_Tensor t);

/* =========================================================================
 * Tensor — Reductions
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_sum (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_mean(ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_min (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_max (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_all (ST_Tensor t);

/** sum over dims[0..ndim-1], keepdim, optional dtype (ST_DTYPE_UNDEFINED = no cast). */
SMILE_API ST_Tensor smile_tensor_sum_dims (ST_Tensor t, const int64_t *dims, int ndim, int keepdim, ST_DType dtype);
SMILE_API ST_Tensor smile_tensor_mean_dims(ST_Tensor t, const int64_t *dims, int ndim, int keepdim, ST_DType dtype);

/** argmax; dim < 0 means over entire tensor. */
SMILE_API ST_Tensor smile_tensor_argmax(ST_Tensor t, int64_t dim, int keepdim, int has_dim);

/** topk; returns (values, indices) into *values_out and *indices_out. */
SMILE_API int smile_tensor_topk(ST_Tensor t, int64_t k,
                                int64_t dim, int largest, int sorted,
                                ST_Tensor *values_out, ST_Tensor *indices_out);

/* =========================================================================
 * Tensor — Arithmetic (out-of-place)
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_neg     (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_add_s   (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_add_t   (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_add_t_s (ST_Tensor a, ST_Tensor b, ST_Scalar alpha);
SMILE_API ST_Tensor smile_tensor_sub_s   (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_sub_t   (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_sub_t_s (ST_Tensor a, ST_Tensor b, ST_Scalar alpha);
SMILE_API ST_Tensor smile_tensor_mul_s   (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_mul_t   (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_div_s   (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_div_t   (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_pow_s   (ST_Tensor t, ST_Scalar exponent);

/* =========================================================================
 * Tensor — Arithmetic (in-place)
 * ========================================================================= */

SMILE_API void smile_tensor_neg_      (ST_Tensor t);
SMILE_API void smile_tensor_add_s_    (ST_Tensor t, ST_Scalar s);
SMILE_API void smile_tensor_add_t_    (ST_Tensor a, ST_Tensor b);
SMILE_API void smile_tensor_add_t_s_  (ST_Tensor a, ST_Tensor b, ST_Scalar alpha);
SMILE_API void smile_tensor_sub_s_    (ST_Tensor t, ST_Scalar s);
SMILE_API void smile_tensor_sub_t_    (ST_Tensor a, ST_Tensor b);
SMILE_API void smile_tensor_sub_t_s_  (ST_Tensor a, ST_Tensor b, ST_Scalar alpha);
SMILE_API void smile_tensor_mul_s_    (ST_Tensor t, ST_Scalar s);
SMILE_API void smile_tensor_mul_t_    (ST_Tensor a, ST_Tensor b);
SMILE_API void smile_tensor_div_s_    (ST_Tensor t, ST_Scalar s);
SMILE_API void smile_tensor_div_t_    (ST_Tensor a, ST_Tensor b);
SMILE_API void smile_tensor_pow_s_    (ST_Tensor t, ST_Scalar exponent);
SMILE_API void smile_tensor_fill_     (ST_Tensor t, ST_Scalar value);
SMILE_API void smile_tensor_bernoulli_(ST_Tensor t, double p);

/* =========================================================================
 * Tensor — Element-wise math
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_abs  (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_log  (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_exp  (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_rsqrt(ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_cos  (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_sin  (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_acos (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_asin (ST_Tensor t);

/** clamp; pass NaN for has_min / has_max to omit a bound. */
SMILE_API ST_Tensor smile_tensor_clamp   (ST_Tensor t, int has_min, ST_Scalar min,
                                                        int has_max, ST_Scalar max);
SMILE_API void      smile_tensor_clamp_  (ST_Tensor t, int has_min, ST_Scalar min,
                                                        int has_max, ST_Scalar max);

/* in-place math */
SMILE_API void smile_tensor_abs_  (ST_Tensor t);
SMILE_API void smile_tensor_log_  (ST_Tensor t);
SMILE_API void smile_tensor_exp_  (ST_Tensor t);
SMILE_API void smile_tensor_rsqrt_(ST_Tensor t);
SMILE_API void smile_tensor_cos_  (ST_Tensor t);
SMILE_API void smile_tensor_sin_  (ST_Tensor t);
SMILE_API void smile_tensor_acos_ (ST_Tensor t);
SMILE_API void smile_tensor_asin_ (ST_Tensor t);
SMILE_API void smile_tensor_mul_scalar_(ST_Tensor t, double s);

/* =========================================================================
 * Tensor — Comparison
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_eq_s (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_eq_t (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_ne_s (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_ne_t (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_lt_s (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_lt_t (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_le_s (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_le_t (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_gt_s (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_gt_t (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_ge_s (ST_Tensor t, ST_Scalar s);
SMILE_API ST_Tensor smile_tensor_ge_t (ST_Tensor a, ST_Tensor b);

/* =========================================================================
 * Tensor — Logical
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_logical_not  (ST_Tensor t);
SMILE_API ST_Tensor smile_tensor_logical_and  (ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_logical_or   (ST_Tensor a, ST_Tensor b);
SMILE_API void      smile_tensor_logical_not_ (ST_Tensor t);
SMILE_API void      smile_tensor_logical_and_ (ST_Tensor a, ST_Tensor b);
SMILE_API void      smile_tensor_logical_or_  (ST_Tensor a, ST_Tensor b);

/* =========================================================================
 * Tensor — Linear algebra
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_matmul(ST_Tensor a, ST_Tensor b);
SMILE_API ST_Tensor smile_tensor_outer (ST_Tensor a, ST_Tensor b);

/* =========================================================================
 * Tensor — New-tensor creators from existing tensor
 * ========================================================================= */

SMILE_API ST_Tensor smile_tensor_new_zeros(ST_Tensor t, const int64_t *shape, int ndim);
SMILE_API ST_Tensor smile_tensor_new_ones (ST_Tensor t, const int64_t *shape, int ndim);

/* =========================================================================
 * Tensor — Indexing helpers
 * ========================================================================= */

/** Creates a TensorIndex from an integer. */
SMILE_API ST_TensorIndex smile_tensor_index_from_int (int64_t value);
/** Creates a boolean-mask TensorIndex. */
SMILE_API ST_TensorIndex smile_tensor_index_from_bool(int value);
/** Creates a TensorIndex wrapping a tensor. */
SMILE_API ST_TensorIndex smile_tensor_index_from_tensor(ST_Tensor t);
/** Creates an Ellipsis TensorIndex. */
SMILE_API ST_TensorIndex smile_tensor_index_ellipsis(void);
/** Creates a Slice TensorIndex.  Pass INT64_MIN for "not set". */
SMILE_API ST_TensorIndex smile_tensor_index_slice(int64_t start, int64_t stop, int64_t step);
SMILE_API void smile_tensor_index_free(ST_TensorIndex idx);

/** Creates an empty TensorIndexVector. */
SMILE_API ST_TensorIndexVec smile_tensor_index_vec_create(void);
SMILE_API void               smile_tensor_index_vec_push(ST_TensorIndexVec v, ST_TensorIndex idx);
SMILE_API void               smile_tensor_index_vec_free(ST_TensorIndexVec v);

/** Advanced indexing: t[indices]. */
SMILE_API ST_Tensor smile_tensor_index(ST_Tensor t, ST_TensorIndexVec indices);

/** In-place scatter: t[indices] = src. */
SMILE_API void smile_tensor_index_put_(ST_Tensor t, ST_TensorIndexVec indices, ST_Tensor src);
SMILE_API void smile_tensor_index_put_scalar_(ST_Tensor t, ST_TensorIndexVec indices, ST_Scalar s);

/** Creates a TensorVector. */
SMILE_API ST_TensorVec smile_tensor_vec_create(void);
SMILE_API void          smile_tensor_vec_push(ST_TensorVec v, ST_Tensor t);
SMILE_API void          smile_tensor_vec_free(ST_TensorVec v);

/* =========================================================================
 * Tensor — Global torch functions
 * ========================================================================= */

SMILE_API ST_Tensor smile_torch_view_as_complex(ST_Tensor t);
SMILE_API ST_Tensor smile_torch_view_as_real   (ST_Tensor t);
SMILE_API ST_Tensor smile_torch_polar          (ST_Tensor abs, ST_Tensor angle);
SMILE_API ST_Tensor smile_torch_hstack         (ST_TensorVec tensors);
SMILE_API ST_Tensor smile_torch_vstack         (ST_TensorVec tensors);
SMILE_API ST_Tensor smile_torch_cumsum         (ST_Tensor t, int64_t dim);
SMILE_API ST_Tensor smile_torch_multinomial    (ST_Tensor t, int64_t num_samples);
SMILE_API ST_Tensor smile_torch_gather         (ST_Tensor t, int64_t dim, ST_Tensor index);
SMILE_API ST_Tensor smile_torch_isin           (ST_Tensor elements, ST_Tensor test_elements);
SMILE_API ST_Tensor smile_torch_dropout        (ST_Tensor t, double p, int training);
SMILE_API void      smile_torch_print          (ST_Tensor t);

/** sort; returns (sorted_values, indices). Caller owns both. */
SMILE_API int smile_torch_sort(ST_Tensor t, int64_t dim, int descending,
                               ST_Tensor *sorted_out, ST_Tensor *indices_out);

/** where(condition, input, other) — both Tensor variants. */
SMILE_API ST_Tensor smile_torch_where_tt(ST_Tensor cond, ST_Tensor input, ST_Tensor other);
SMILE_API ST_Tensor smile_torch_where_ts(ST_Tensor cond, ST_Tensor input, ST_Scalar other);

/* =========================================================================
 * Activation functions (global)
 * ========================================================================= */

SMILE_API ST_Tensor smile_torch_relu        (ST_Tensor x);
SMILE_API void      smile_torch_relu_       (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_gelu        (ST_Tensor x);
SMILE_API void      smile_torch_gelu_       (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_glu         (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_silu        (ST_Tensor x);
SMILE_API void      smile_torch_silu_       (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_sigmoid     (ST_Tensor x);
SMILE_API void      smile_torch_sigmoid_    (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_tanh        (ST_Tensor x);
SMILE_API void      smile_torch_tanh_       (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_leaky_relu  (ST_Tensor x, double negative_slope);
SMILE_API void      smile_torch_leaky_relu_ (ST_Tensor x, double negative_slope);
SMILE_API ST_Tensor smile_torch_elu         (ST_Tensor x, double alpha);
SMILE_API void      smile_torch_elu_        (ST_Tensor x, double alpha, double scale,
                                              double input_scale);
SMILE_API ST_Tensor smile_torch_softmax     (ST_Tensor x, int64_t dim);
SMILE_API ST_Tensor smile_torch_log_softmax (ST_Tensor x, int64_t dim);
SMILE_API ST_Tensor smile_torch_log_sigmoid (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_mish        (ST_Tensor x);
SMILE_API void      smile_torch_mish_       (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_hardswish   (ST_Tensor x);
SMILE_API void      smile_torch_hardswish_  (ST_Tensor x);
SMILE_API ST_Tensor smile_torch_hardshrink  (ST_Tensor x, double lambda);
SMILE_API ST_Tensor smile_torch_softshrink  (ST_Tensor x, double lambda);
SMILE_API ST_Tensor smile_torch_tanhshrink  (ST_Tensor x);

/* =========================================================================
 * Loss functions (global)
 * ========================================================================= */

SMILE_API ST_Tensor smile_torch_l1_loss        (ST_Tensor input, ST_Tensor target);
SMILE_API ST_Tensor smile_torch_mse_loss       (ST_Tensor input, ST_Tensor target);
SMILE_API ST_Tensor smile_torch_nll_loss       (ST_Tensor input, ST_Tensor target);
SMILE_API ST_Tensor smile_torch_cross_entropy  (ST_Tensor input, ST_Tensor target,
                                                 int64_t ignore_index, ST_Reduction reduction);
SMILE_API ST_Tensor smile_torch_hinge_embedding_loss       (ST_Tensor input, ST_Tensor target);
SMILE_API ST_Tensor smile_torch_binary_cross_entropy       (ST_Tensor input, ST_Tensor target);
SMILE_API ST_Tensor smile_torch_binary_cross_entropy_logits(ST_Tensor input, ST_Tensor target);
SMILE_API ST_Tensor smile_torch_smooth_l1_loss (ST_Tensor input, ST_Tensor target);
SMILE_API ST_Tensor smile_torch_huber_loss     (ST_Tensor input, ST_Tensor target, double delta);
SMILE_API ST_Tensor smile_torch_kl_div         (ST_Tensor input, ST_Tensor target);
SMILE_API ST_Tensor smile_torch_margin_ranking_loss(ST_Tensor input1, ST_Tensor input2,
                                                     ST_Tensor target);
SMILE_API ST_Tensor smile_torch_triplet_margin_loss(ST_Tensor anchor,
                                                     ST_Tensor positive, ST_Tensor negative);

/* =========================================================================
 * NoGradGuard (disables autograd in a scope)
 * ========================================================================= */

SMILE_API ST_NoGradGuard smile_no_grad_guard_create(void);
SMILE_API void            smile_no_grad_guard_free  (ST_NoGradGuard g);

/* =========================================================================
 * Module (base class)
 * ========================================================================= */

/** Creates a generic (custom) Module with an optional name (may be NULL). */
SMILE_API ST_Module smile_module_create(const char *name);
SMILE_API void      smile_module_free  (ST_Module m);

SMILE_API const char *smile_module_name(ST_Module m);

/** Registers a child module under name (non-owning view of child is returned). */
SMILE_API void smile_module_register_module   (ST_Module m, const char *name, ST_Module child);
SMILE_API void smile_module_register_parameter(ST_Module m, const char *name, ST_Tensor t);

/** Returns a TensorVec of all learnable parameters (caller owns the vector). */
SMILE_API ST_TensorVec smile_module_parameters(ST_Module m);

SMILE_API void smile_module_train(ST_Module m, int mode);
SMILE_API void smile_module_eval (ST_Module m);
SMILE_API int  smile_module_is_training(ST_Module m);

SMILE_API void smile_module_to_device(ST_Module m, ST_Device device, int non_blocking);
SMILE_API void smile_module_to_dtype (ST_Module m, ST_Device device, ST_DType dtype,
                                      int non_blocking);

SMILE_API void smile_module_save(ST_Module m, ST_OutputArchive archive);
SMILE_API void smile_module_load(ST_Module m, ST_InputArchive  archive);

/* =========================================================================
 * ModuleList
 * ========================================================================= */

SMILE_API ST_ModuleList smile_module_list_create(void);
SMILE_API void          smile_module_list_free  (ST_ModuleList ml);
SMILE_API void          smile_module_list_push_back(ST_ModuleList ml, ST_Module m);
SMILE_API int64_t       smile_module_list_size  (ST_ModuleList ml);
SMILE_API ST_Module     smile_module_list_get   (ST_ModuleList ml, int64_t index);

/* =========================================================================
 * Archive (checkpointing)
 * ========================================================================= */

SMILE_API ST_InputArchive  smile_input_archive_create (void);
SMILE_API void              smile_input_archive_free   (ST_InputArchive a);
SMILE_API int               smile_input_archive_load_from(ST_InputArchive a,
                                                           const char *path,
                                                           ST_Device device);  /* device may be NULL */

SMILE_API ST_OutputArchive smile_output_archive_create(void);
SMILE_API void              smile_output_archive_free  (ST_OutputArchive a);
SMILE_API int               smile_output_archive_save_to(ST_OutputArchive a, const char *path);

/* =========================================================================
 * Layer modules
 * ========================================================================= */

/* ---- Linear ---- */

SMILE_API ST_Linear smile_linear_create(int64_t in_features, int64_t out_features, int bias);
SMILE_API void      smile_linear_free  (ST_Linear l);
SMILE_API ST_Tensor smile_linear_forward(ST_Linear l, ST_Tensor input);
/** Returns a borrowed (non-owning) ST_Module view. */
SMILE_API ST_Module smile_linear_as_module(ST_Linear l);

/* ---- Conv2d ---- */

/**
 * @param kernel  two-element array [kH, kW]
 * @param stride  two-element array [sH, sW]  (NULL → {1,1})
 * @param padding two-element array [pH, pW]  (NULL → {0,0})
 * @param dilation two-element array [dH, dW] (NULL → {1,1})
 * @param groups   number of blocked connections
 * @param bias     1 to include bias, 0 to omit
 * @param pad_mode ST_PaddingMode value
 */
SMILE_API ST_Conv2d smile_conv2d_create(int64_t in_channels, int64_t out_channels,
                                        const int64_t *kernel,
                                        const int64_t *stride,
                                        const int64_t *padding,
                                        const int64_t *dilation,
                                        int64_t groups, int bias,
                                        ST_PaddingMode pad_mode);
SMILE_API void      smile_conv2d_free   (ST_Conv2d c);
SMILE_API ST_Tensor smile_conv2d_forward(ST_Conv2d c, ST_Tensor input);
SMILE_API ST_Module smile_conv2d_as_module(ST_Conv2d c);

/* ---- BatchNorm1d ---- */

SMILE_API ST_BatchNorm1d smile_batchnorm1d_create(int64_t num_features,
                                                   double eps, double momentum,
                                                   int affine);
SMILE_API void            smile_batchnorm1d_free   (ST_BatchNorm1d b);
SMILE_API ST_Tensor       smile_batchnorm1d_forward(ST_BatchNorm1d b, ST_Tensor input);
SMILE_API ST_Module       smile_batchnorm1d_as_module(ST_BatchNorm1d b);

/* ---- BatchNorm2d ---- */

SMILE_API ST_BatchNorm2d smile_batchnorm2d_create(int64_t num_features,
                                                   double eps, double momentum,
                                                   int affine);
SMILE_API void            smile_batchnorm2d_free   (ST_BatchNorm2d b);
SMILE_API ST_Tensor       smile_batchnorm2d_forward(ST_BatchNorm2d b, ST_Tensor input);
SMILE_API ST_Module       smile_batchnorm2d_as_module(ST_BatchNorm2d b);

/* ---- Dropout ---- */

SMILE_API ST_Dropout smile_dropout_create (double p, int inplace);
SMILE_API void       smile_dropout_free   (ST_Dropout d);
SMILE_API ST_Tensor  smile_dropout_forward(ST_Dropout d, ST_Tensor input);
SMILE_API int        smile_dropout_is_training(ST_Dropout d);
SMILE_API ST_Module  smile_dropout_as_module(ST_Dropout d);

/* ---- Embedding ---- */

SMILE_API ST_Embedding smile_embedding_create (int64_t num_embeddings, int64_t embedding_dim);
SMILE_API void          smile_embedding_free   (ST_Embedding e);
SMILE_API ST_Tensor     smile_embedding_forward(ST_Embedding e, ST_Tensor input);
SMILE_API ST_Module     smile_embedding_as_module(ST_Embedding e);

/* ---- GroupNorm ---- */

SMILE_API ST_GroupNorm smile_groupnorm_create (int64_t num_groups, int64_t num_channels,
                                                double eps, int affine);
SMILE_API void          smile_groupnorm_free   (ST_GroupNorm g);
SMILE_API ST_Tensor     smile_groupnorm_forward(ST_GroupNorm g, ST_Tensor input);
SMILE_API ST_Module     smile_groupnorm_as_module(ST_GroupNorm g);

/* ---- MaxPool2d ---- */

/**
 * @param kernel   two-element array [kH, kW]
 * @param stride   two-element array [sH, sW]  (NULL → same as kernel)
 * @param padding  two-element array [pH, pW]  (NULL → {0,0})
 */
SMILE_API ST_MaxPool2d smile_maxpool2d_create (const int64_t *kernel,
                                                const int64_t *stride,
                                                const int64_t *padding);
SMILE_API void          smile_maxpool2d_free   (ST_MaxPool2d p);
SMILE_API ST_Tensor     smile_maxpool2d_forward(ST_MaxPool2d p, ST_Tensor input);
SMILE_API ST_Module     smile_maxpool2d_as_module(ST_MaxPool2d p);

/* ---- AvgPool2d ---- */

SMILE_API ST_AvgPool2d smile_avgpool2d_create (const int64_t *kernel,
                                                const int64_t *stride,
                                                const int64_t *padding);
SMILE_API void          smile_avgpool2d_free   (ST_AvgPool2d p);
SMILE_API ST_Tensor     smile_avgpool2d_forward(ST_AvgPool2d p, ST_Tensor input);
SMILE_API ST_Module     smile_avgpool2d_as_module(ST_AvgPool2d p);

/* ---- AdaptiveAvgPool2d ---- */

/**
 * @param output_size  two-element array [oH, oW]; use -1 for "none" in either dimension.
 */
SMILE_API ST_AdaptiveAvgPool2d smile_adaptive_avgpool2d_create (const int64_t *output_size);
SMILE_API void                  smile_adaptive_avgpool2d_free   (ST_AdaptiveAvgPool2d p);
SMILE_API ST_Tensor             smile_adaptive_avgpool2d_forward(ST_AdaptiveAvgPool2d p,
                                                                  ST_Tensor input);
SMILE_API ST_Module             smile_adaptive_avgpool2d_as_module(ST_AdaptiveAvgPool2d p);

/* =========================================================================
 * Optimizers
 *
 * All optimizer constructors accept the parameter tensor vector returned by
 * smile_module_parameters().  They do NOT take ownership of the vector — the
 * caller should free it after creating the optimizer.
 * ========================================================================= */

/** SGD optimizer.
 *  @param nesterov 1 to enable Nesterov momentum, 0 to disable. */
SMILE_API ST_Optimizer smile_sgd_create(ST_TensorVec params,
                                        double lr, double momentum,
                                        double weight_decay, double dampening,
                                        int nesterov);

/** Adam optimizer.
 *  @param beta1 first moment coefficient  (default 0.9)
 *  @param beta2 second moment coefficient (default 0.999)
 *  @param amsgrad 1 to use AMSGrad variant */
SMILE_API ST_Optimizer smile_adam_create(ST_TensorVec params,
                                         double lr, double beta1, double beta2,
                                         double eps, double weight_decay,
                                         int amsgrad);

/** AdamW optimizer. */
SMILE_API ST_Optimizer smile_adamw_create(ST_TensorVec params,
                                           double lr, double beta1, double beta2,
                                           double eps, double weight_decay,
                                           int amsgrad);

/** RMSprop optimizer. */
SMILE_API ST_Optimizer smile_rmsprop_create(ST_TensorVec params,
                                             double lr, double alpha, double eps,
                                             double weight_decay, double momentum,
                                             int centered);

SMILE_API void smile_optimizer_free      (ST_Optimizer opt);
SMILE_API void smile_optimizer_zero_grad (ST_Optimizer opt);
SMILE_API void smile_optimizer_step      (ST_Optimizer opt);

/** Updates the learning rate of every param group. */
SMILE_API void smile_optimizer_set_lr(ST_Optimizer opt, double lr);

/* =========================================================================
 * Version / build info
 * ========================================================================= */

/** Writes the libtorch version string into buf. */
SMILE_API int smile_torch_version(char *buf, int buf_len);

#ifdef __cplusplus
} /* extern "C" */
#endif

