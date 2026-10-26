/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * MXFP8 Marlin entry points; see smile_marlin_fp8.h. The launcher is vendored from vLLM (see the
 * header of smile_marlin_fp8_launcher.cuh for the exact provenance).
 */
#include <map>
#include "smile_marlin_fp8_launcher.cuh"

namespace {

constexpr int kScaleGroup = 32;
constexpr int kBlockGroup = 128;

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

/**
 * Block-FP8 scales (DeepSeek/Qwen layout, one float32 per 128x128 tile, [N/128, K/128], W = fp8 * scale) to the
 * Marlin group-128 layout: expand every tile scale over its 128 output columns -> [K/128, N], cast to bfloat16,
 * apply the Marlin scale permutation, and fold the FP8->bfloat16 exponent bias (2^(8-1) - 2^(4-1) = 2^120) into the
 * scales, as vLLM does for FP8 Marlin (the kernel converts E4M3 bits to bfloat16 without rescaling).
 */
at::Tensor repack_block_scales_impl(const at::Tensor& t) {
    if (t.dim() != 2 || !t.is_cuda()) {
        throw std::runtime_error("block scales must be a 2-D CUDA tensor [N/128, K/128]");
    }
    at::Tensor g = t.to(at::kFloat).t().contiguous();                      // [K/128, N/128]
    g = g.repeat_interleave(kBlockGroup, /*dim=*/1).to(at::kBFloat16);       // [K/128, N]
    const int64_t n = g.size(1);
    if (n % 64 != 0) {
        throw std::runtime_error("N must be a multiple of 64, got " + std::to_string(n));
    }
    at::Tensor perm = scale_perm(t.device());
    g = g.reshape({-1, 64}).index_select(1, perm).reshape({-1, n}).contiguous();
    // 2^120 only shifts the exponent: exact unless a scale is >= 2^8 (never for quantization scales)
    if (g.abs().max().item<float>() >= 256.0f) {
        throw std::runtime_error("block scale too large for the fused exponent bias");
    }
    return (g.to(at::kFloat) * std::ldexp(1.0f, 120)).to(at::kBFloat16).contiguous();
}

/**
 * Repacks one NVFP4 weight, {@code [N, K/2]} uint8 (two E2M1 nibbles per byte, low nibble first), into the Marlin
 * tile layout {@code [K/16, N*2]} int32.
 */
at::Tensor repack_nvfp4_weight_impl(const at::Tensor& t) {
    if (!t.is_cuda() || t.dim() != 2 || t.scalar_type() != at::kByte) {
        throw std::runtime_error("NVFP4 weight must be a 2-D uint8 CUDA tensor");
    }
    const int64_t n = t.size(0);
    const int64_t k = t.size(1) * 2;
    if (n % marlin::tile_n_size != 0 || k % 128 != 0) {
        throw std::runtime_error("need N % 64 == 0 and K % 128 == 0, got N=" + std::to_string(n)
                                 + " K=" + std::to_string(k));
    }
    const int dev = t.get_device();
    c10::cuda::CUDAGuard guard(dev);
    at::Tensor packed = t.contiguous().view(at::kInt).t().contiguous();  // [K/8, N]
    at::Tensor out = torch::empty({k / marlin::tile_size, n * marlin::tile_size / 8},
                                  torch::dtype(torch::kInt).device(t.device()));
    launch_gptq_marlin_repack(packed.data_ptr(), out.data_ptr(), (int) k, (int) n, 4, false, dev,
                              at::cuda::getCurrentCUDAStream(dev));
    cudaError_t st = cudaGetLastError();
    if (st != cudaSuccess) {
        throw std::runtime_error(std::string("CUDA error: ") + cudaGetErrorString(st));
    }
    return out;
}

/**
 * NVFP4 scales and global scales of all experts of one projection group to the Marlin layout, following vLLM's
 * {@code prepare_nvfp4_moe_layer_for_marlin}. {@code s} is {@code [E, N, K/16]} E4M3 (float8 or its bytes);
 * {@code global} is the checkpoint's {@code weight_global_scale} {@code [E]} float32 (the quantizer divided by it,
 * so the dequantization multiplies by its inverse). The E4M3 scale is re-expressed as S0E5M3 (x 2^7, shifted so that
 * the dequantizer can splice the bits into a bfloat16 exponent), after scaling every value by a common power of two
 * so the most significant mantissa bit is set; the kernel's per-expert float32 scale absorbs that factor, the FP4 to
 * bfloat16 exponent bias (2^119) and the checkpoint's global scale. Returns uint8 {@code [E, K/16, N]}; the new
 * global scales go to {@code global_out}.
 */
at::Tensor repack_nvfp4_scales_impl(const at::Tensor& s, const at::Tensor& global, at::Tensor* global_out) {
    if (!s.is_cuda() || s.dim() != 3 || (s.scalar_type() != at::kByte && s.scalar_type() != at::kFloat8_e4m3fn)) {
        throw std::runtime_error("NVFP4 scales must be a 3-D float8_e4m3fn or uint8 CUDA tensor");
    }
    if (global.scalar_type() != at::kFloat || global.numel() != s.size(0)) {
        throw std::runtime_error("global scales must be float32 with one entry per expert");
    }
    const int64_t e = s.size(0);
    const int64_t n = s.size(1);
    const int64_t g = s.size(2);
    if (n % 64 != 0) {
        throw std::runtime_error("N must be a multiple of 64, got " + std::to_string(n));
    }
    at::Tensor h = s.contiguous().view(at::kFloat8_e4m3fn).to(at::kHalf);  // exact: E4M3 fits in half
    if ((h < 0).any().item<bool>()) {
        throw std::runtime_error("NVFP4 Marlin needs non-negative scales");
    }
    // Common power-of-two factor so that every non-zero scale * 2^7 is >= 2 (see vLLM _nvfp4_compute_scale_factor).
    double factor = 1.0;
    at::Tensor scaled = h.to(at::kFloat) * 128.0f;
    at::Tensor positive = scaled.masked_select(scaled > 0);
    if (positive.numel() > 0) {
        const double max_val = positive.max().item<double>();
        if (max_val < 448.0 * 128.0) {
            factor = std::exp2(std::floor(std::log2(448.0 * 128.0 / max_val)));
        }
    }
    at::Tensor x = h.transpose(1, 2).contiguous();                               // [E, K/16, N]
    at::Tensor perm = scale_perm(s.device());
    x = x.reshape({-1, 64}).index_select(1, perm).reshape({e, g, n});
    at::Tensor order = torch::tensor(std::vector<int64_t>{0, 2, 1, 3}, torch::dtype(torch::kLong)).to(s.device());
    x = x.reshape({-1, 4}).index_select(1, order).reshape({e, g, n}).contiguous();
    if (factor > 1.0) {
        x = (x.to(at::kFloat) * (float) factor).to(at::kHalf);
    }
    x = x * 128.0f;
    x = at::where(x < 2.0f, at::zeros_like(x), x);
    at::Tensor bits = (x.contiguous().view(at::kShort) * 2).contiguous().view(at::kByte).reshape({e, g, n, 2});
    at::Tensor out = bits.select(3, 1).contiguous();                              // high bytes: the S0E5M3 scale
    // 2^(8 - 1) - 2^(2 - 1) = 126 is the FP4 -> bfloat16 exponent bias; the 7 is the 2^7 folded into the scales.
    *global_out = (global.to(at::kFloat).reciprocal() * (float) std::exp2(126.0 - 7.0) / (float) factor).contiguous();
    return out;
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

extern "C" ST_Tensor smile_marlin_fp8_repack_block_scales(ST_Tensor s) {
    SMILE_GUARD("smile_marlin_fp8_repack_block_scales", repack_block_scales_impl(s->t))
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
        // uint8 E8M0 scales per 32 K (MXFP8) or bfloat16 scales per 128 K (block FP8, exponent bias fused in)
        const bool block = S.scalar_type() == at::kBFloat16;
        const int group = block ? kBlockGroup : kScaleGroup;
        if ((!block && S.scalar_type() != at::kByte) || !S.is_contiguous() || S.size(0) != size_k / group
                || S.size(1) != size_n) {
            set_err("smile_marlin_fp8_gemm: scales must be uint8 [K/32, N] (smile_marlin_fp8_repack_scales) or "
                    "bfloat16 [K/128, N] (smile_marlin_fp8_repack_block_scales)");
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
        // The kernel leaves its lock array zeroed, so one workspace per (device, stream) is reused (as vLLM does)
        // instead of paying a fill kernel per GEMM. Calls on one stream are serialized, so they never share a lock live.
        static thread_local std::map<std::pair<int, void *>, at::Tensor> workspaces;
        cudaStream_t cur_stream = at::cuda::getCurrentCUDAStream(dev);
        at::Tensor &workspace = workspaces[{dev, (void *) cur_stream}];
        if (!workspace.defined() || workspace.numel() < sms) {
            workspace = torch::zeros({sms}, torch::dtype(torch::kInt).device(A.device()));
        }
        at::Tensor empty_f = torch::empty({0}, torch::dtype(torch::kFloat).device(A.device()));
        at::Tensor empty_b = torch::empty({0}, A.options());

        (void) cudaGetLastError();
        marlin::marlin_mm(A.data_ptr(), B.data_ptr(), C.data_ptr(), c_tmp.data_ptr(),
                          empty_b.data_ptr(), empty_f.data_ptr(), S.data_ptr(), empty_f.data_ptr(),
                          empty_b.data_ptr(), (int) size_m, (int) size_n, (int) size_k, (int) A.stride(0),
                          workspace.data_ptr(), vllm::kBFloat16, vllm::kFE4M3fn, vllm::kBFloat16,
                          block ? vllm::kBFloat16 : vllm::kFE8M0fnu, /*has_bias=*/false, /*has_zp=*/false,
                          /*num_groups=*/(int) S.size(0), /*group_size=*/group, dev,
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

extern "C" ST_Tensor smile_marlin_nvfp4_repack_weight_stack(ST_Tensor w) {
    SMILE_GUARD("smile_marlin_nvfp4_repack_weight_stack", stack_impl(w->t, repack_nvfp4_weight_impl))
}

extern "C" ST_Tensor smile_marlin_nvfp4_repack_scales_stack(ST_Tensor s, ST_Tensor global, ST_Tensor* global_out) {
    try {
        smile_torch_clear_error();
        if (!s || !global || !global_out) {
            throw std::runtime_error("null argument");
        }
        at::Tensor g;
        at::Tensor out = repack_nvfp4_scales_impl(s->t, global->t, &g);
        *global_out = new ST_Tensor_{ g };
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error((std::string("smile_marlin_nvfp4_repack_scales_stack: ") + ex.what()).c_str());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_nvfp4_dequantize(ST_Tensor packed, ST_Tensor scale, float global_scale) {
    try {
        smile_torch_clear_error();
        const at::Tensor& p = packed->t;
        const at::Tensor& sc = scale->t;
        if (!p.is_cuda() || p.scalar_type() != at::kByte || p.dim() < 2) {
            throw std::runtime_error("packed must be a uint8 CUDA tensor [.., N, K/2]");
        }
        if (sc.numel() * 8 != p.numel() || (sc.scalar_type() != at::kByte && sc.scalar_type() != at::kFloat8_e4m3fn)) {
            throw std::runtime_error("scale must be E4M3 with one entry per 16 weights");
        }
        c10::cuda::CUDAGuard guard(p.get_device());
        // E2M1 magnitudes 0, .5, 1, 1.5, 2, 3, 4, 6; bit 3 is the sign. The low nibble is the even element.
        at::Tensor table = torch::tensor(std::vector<float>{0, .5f, 1, 1.5f, 2, 3, 4, 6, -0.f, -.5f, -1, -1.5f, -2, -3, -4, -6},
                                         torch::dtype(torch::kFloat)).to(p.device());
        at::Tensor lo = table.index_select(0, (p & 0xF).to(at::kLong).reshape({-1}));
        at::Tensor hi = table.index_select(0, at::bitwise_right_shift(p, 4).to(at::kLong).reshape({-1}));
        std::vector<int64_t> shape(p.sizes().begin(), p.sizes().end());
        shape.back() *= 2;
        at::Tensor vals = at::stack({lo, hi}, 1).reshape(shape);
        at::Tensor scales = sc.contiguous().view(at::kFloat8_e4m3fn).to(at::kFloat).repeat_interleave(16, -1);
        return new ST_Tensor_{ (vals * scales / global_scale).to(at::kBFloat16) };
    } catch (const std::exception& ex) {
        smile_torch_set_error((std::string("smile_nvfp4_dequantize: ") + ex.what()).c_str());
        return nullptr;
    }
}
