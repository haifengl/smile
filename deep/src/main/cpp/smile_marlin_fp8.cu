/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * MXFP8 Marlin entry points; see smile_marlin_fp8.h. The launcher is vendored from vLLM (see the
 * header of smile_marlin_fp8_launcher.cuh for the exact provenance).
 */
#include "smile_marlin_fp8_launcher.cuh"

namespace {

constexpr int kScaleGroup = 32;

void set_err(const std::string& msg) { smile_torch_set_error(msg.c_str()); }

/** Marlin scale permutation of one 64-element row segment (vLLM get_scale_perms().scale_perm). */
at::Tensor scale_perm(const at::Device& device) {
    std::vector<int64_t> perm;
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            perm.push_back(i + 8 * j);
        }
    }
    return torch::tensor(perm, torch::dtype(torch::kLong)).to(device);
}


/** Repacks one [N, K] MXFP8 weight; throws std::runtime_error on bad input. */
at::Tensor repack_weight_impl(const at::Tensor& t) {
    if (!t.is_cuda() || t.dim() != 2) {
        throw std::runtime_error("weight must be a 2-D CUDA tensor");
    }
    if (t.scalar_type() != at::kFloat8_e4m3fn && t.scalar_type() != at::kByte) {
        throw std::runtime_error("weight must be float8_e4m3fn or uint8");
    }
    const int64_t n = t.size(0);
    const int64_t k = t.size(1);
    if (n % marlin::tile_n_size != 0 || k % 128 != 0) {
        throw std::runtime_error("need N % 64 == 0 and K % 128 == 0, got N=" + std::to_string(n)
                                 + " K=" + std::to_string(k));
    }
    const int dev = t.get_device();
    c10::cuda::CUDAGuard guard(dev);
    // [N, K] fp8 -> [N, K/4] int32 -> [K/4, N] (GPTQ layout with 4 K-values per word)
    at::Tensor packed = t.contiguous().view(at::kInt).t().contiguous();
    at::Tensor out = torch::empty({k / marlin::tile_size, n * marlin::tile_size / 4},
                                  torch::dtype(torch::kInt).device(t.device()));
    launch_gptq_marlin_repack(packed.data_ptr(), out.data_ptr(), (int) k, (int) n, 8, false, dev,
                              at::cuda::getCurrentCUDAStream(dev));
    cudaError_t st = cudaGetLastError();
    if (st != cudaSuccess) {
        throw std::runtime_error(std::string("CUDA error: ") + cudaGetErrorString(st));
    }
    return out;
}

/** Permutes one [N, K/32] uint8 scale matrix into the Marlin layout [K/32, N]. */
at::Tensor repack_scales_impl(const at::Tensor& t) {
    if (t.dim() != 2 || t.scalar_type() != at::kByte) {
        throw std::runtime_error("scales must be a 2-D uint8 tensor");
    }
    const int64_t n = t.size(0);
    // vLLM: s.T -> marlin_permute_scales(group_size=32) -> view(-1,4)[:, [0,2,1,3]]. Pure data
    // movement, so it is done on the E8M0 bytes directly.
    at::Tensor g = t.t().contiguous();                                   // [K/32, N]
    at::Tensor perm = scale_perm(t.device());
    g = g.reshape({-1, 64}).index_select(1, perm).reshape({-1, n}).contiguous();
    at::Tensor order = torch::tensor(std::vector<int64_t>{0, 2, 1, 3}, torch::dtype(torch::kLong))
                           .to(t.device());
    const int64_t rows = g.size(0);
    return g.reshape({-1, 4}).index_select(1, order).reshape({rows, -1}).contiguous();
}

/** Stacks per-expert results of {@code fn} over the leading dimension of a 3-D tensor. */
template <typename F>
at::Tensor stack_impl(const at::Tensor& t, F fn) {
    if (t.dim() != 3) {
        throw std::runtime_error("expected a 3-D [experts, N, K] tensor");
    }
    std::vector<at::Tensor> parts;
    parts.reserve(t.size(0));
    for (int64_t e = 0; e < t.size(0); e++) {
        parts.push_back(fn(t.select(0, e)));
    }
    return at::stack(parts, 0);
}

}  // namespace

extern "C" int smile_marlin_fp8_available(void) { return 1; }

#define SMILE_GUARD(name, expr)                                              \
    try {                                                                    \
        smile_torch_clear_error();                                           \
        return new ST_Tensor_{ expr };                                       \
    } catch (const std::exception& ex) {                                     \
        smile_torch_set_error((std::string(name) + ": " + ex.what()).c_str()); \
        return nullptr;                                                      \
    }

extern "C" ST_Tensor smile_marlin_fp8_repack_weight(ST_Tensor w) {
    SMILE_GUARD("smile_marlin_fp8_repack_weight", repack_weight_impl(w->t))
}

extern "C" ST_Tensor smile_marlin_fp8_repack_scales(ST_Tensor s) {
    SMILE_GUARD("smile_marlin_fp8_repack_scales", repack_scales_impl(s->t))
}

extern "C" ST_Tensor smile_marlin_fp8_repack_weight_stack(ST_Tensor w) {
    SMILE_GUARD("smile_marlin_fp8_repack_weight_stack", stack_impl(w->t, repack_weight_impl))
}

extern "C" ST_Tensor smile_marlin_fp8_repack_scales_stack(ST_Tensor s) {
    SMILE_GUARD("smile_marlin_fp8_repack_scales_stack", stack_impl(s->t, repack_scales_impl))
}

extern "C" ST_Tensor smile_marlin_fp8_gemm(ST_Tensor a, ST_Tensor qweight, ST_Tensor scales) {
    try {
        smile_torch_clear_error();
        if (!a || !qweight || !scales) {
            set_err("smile_marlin_fp8_gemm: null tensor");
            return nullptr;
        }
        const at::Tensor& A = a->t;
        const at::Tensor& B = qweight->t;
        const at::Tensor& S = scales->t;
        if (!A.is_cuda() || A.dim() != 2 || A.scalar_type() != at::kBFloat16) {
            set_err("smile_marlin_fp8_gemm: activations must be a 2-D bfloat16 CUDA tensor");
            return nullptr;
        }
        if (A.stride(1) != 1 || A.stride(0) % 8 != 0 || ((uintptr_t) A.data_ptr()) % 16 != 0) {
            set_err("smile_marlin_fp8_gemm: activations must be row-major with 16-byte alignment");
            return nullptr;
        }
        const int64_t size_m = A.size(0);
        const int64_t size_k = A.size(1);
        const int64_t size_n = B.size(1) / marlin::tile_size * 4;
        if (B.scalar_type() != at::kInt || !B.is_contiguous() || B.size(0) != size_k / marlin::tile_size) {
            set_err("smile_marlin_fp8_gemm: qweight is not a Marlin-packed int32 [K/16, N*4]");
            return nullptr;
        }
        if (S.scalar_type() != at::kByte || !S.is_contiguous() || S.size(0) != size_k / kScaleGroup
                || S.size(1) != size_n) {
            set_err("smile_marlin_fp8_gemm: scales must be uint8 [K/32, N] from smile_marlin_fp8_repack_scales");
            return nullptr;
        }
        const int dev = A.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor C = torch::empty({size_m, size_n}, A.options());
        if (size_m == 0) {
            return new ST_Tensor_{ C };
        }
        int sms = 0;
        cudaDeviceGetAttribute(&sms, cudaDevAttrMultiProcessorCount, dev);

        const bool use_fp32_reduce = true;
        int64_t max_m_block = std::min<int64_t>((size_m + 15) / 16 * 16, 64);
        at::Tensor c_tmp = torch::empty({sms * max_m_block * marlin::max_thread_n},
                                        torch::dtype(torch::kFloat).device(A.device()));
        at::Tensor workspace = torch::zeros({sms}, torch::dtype(torch::kInt).device(A.device()));
        at::Tensor empty_f = torch::empty({0}, torch::dtype(torch::kFloat).device(A.device()));
        at::Tensor empty_b = torch::empty({0}, A.options());

        (void) cudaGetLastError();
        marlin::marlin_mm(A.data_ptr(), B.data_ptr(), C.data_ptr(), c_tmp.data_ptr(),
                          empty_b.data_ptr(), empty_f.data_ptr(), S.data_ptr(), empty_f.data_ptr(),
                          empty_b.data_ptr(), (int) size_m, (int) size_n, (int) size_k, (int) A.stride(0),
                          workspace.data_ptr(), vllm::kBFloat16, vllm::kFE4M3fn, vllm::kBFloat16,
                          vllm::kFE8M0fnu, /*has_bias=*/false, /*has_zp=*/false,
                          /*num_groups=*/(int) S.size(0), /*group_size=*/kScaleGroup, dev,
                          at::cuda::getCurrentCUDAStream(dev), /*thread_k=*/-1, /*thread_n=*/-1, sms,
                          /*use_atomic_add=*/false, use_fp32_reduce, /*is_zp_float=*/false);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_marlin_fp8_gemm: CUDA launch error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ C };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}
