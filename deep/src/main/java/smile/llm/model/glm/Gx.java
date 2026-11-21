/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.glm;

import java.util.function.Supplier;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;
import smile.torch.Aten;
import smile.util.AutoScope;

/**
 * Small tensor helpers shared by the GLM layers (reference path: float32 on one device).
 *
 * @author Haifeng Li
 */
final class Gx {
    private Gx() { }

    private static volatile Device defaultDevice = Device.CPU();
    private static volatile ScalarType defaultWeightType = ScalarType.Float;
    private static final ThreadLocal<Device> DEVICE = ThreadLocal.withInitial(() -> defaultDevice);
    private static final ThreadLocal<ScalarType> WEIGHT_TYPE = ThreadLocal.withInitial(() -> defaultWeightType);
    private static final ThreadLocal<Ep> EP = new ThreadLocal<>();

    /**
     * Expert parallelism of the calling thread's model replica: this rank owns the routed experts
     * {@code [rank * n / world, (rank + 1) * n / world)} and the partial routed outputs are summed over the group.
     *
     * @param group the NCCL group.
     * @param rank  this thread's rank.
     * @param world the number of ranks.
     */
    record Ep(smile.llm.parallel.TensorParallelGroup group, int rank, int world) {
        boolean owns(int expert, int experts) {
            return expert >= (long) rank * experts / world && expert < (long) (rank + 1) * experts / world;
        }
    }

    /** Device of the calling thread's model tensors. */
    static Device device() {
        return DEVICE.get();
    }

    /** Sets the device for the calling thread, and for threads that have not asked yet. */
    static void setDevice(Device d) {
        defaultDevice = d;
        DEVICE.set(d);
    }

    /** Sets the device for the calling (rank) thread only. */
    static void setThreadDevice(Device d) {
        DEVICE.set(d);
    }

    /** Dtype of the dequantized matmul weights (float32 on CPU, bfloat16 on a GPU). */
    static ScalarType weightType() {
        return WEIGHT_TYPE.get();
    }

    static void setWeightType(ScalarType t) {
        defaultWeightType = t;
        WEIGHT_TYPE.set(t);
    }

    static void setThreadWeightType(ScalarType t) {
        WEIGHT_TYPE.set(t);
    }

    static Ep ep() {
        return EP.get();
    }

    /** Number of ranks sharing the heads and the dense matrices (1 without tensor parallelism). */
    static int tpWorld() {
        Ep ep = EP.get();
        return ep == null ? 1 : ep.world();
    }

    /** This thread's rank in the tensor-parallel group. */
    static int tpRank() {
        Ep ep = EP.get();
        return ep == null ? 0 : ep.rank();
    }

    /** Per-stage wall time with a device sync after each stage ({@code GLM_STAGE_TIMING=1}, eager execution only). */
    static final boolean STAGE_TIMING = "1".equals(System.getenv("GLM_STAGE_TIMING"));
    private static final java.util.Map<String, long[]> STAGES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<long[]> STAGE_T = ThreadLocal.withInitial(() -> new long[]{0});

    /** Marks the end of a stage: waits for the device and adds the elapsed time to {@code name}. */
    static void stage(String name, Tensor t) {
        if (!STAGE_TIMING) {
            return;
        }
        Aten.one("aten::sum", t).doubleValue();
        long now = System.nanoTime();
        long[] last = STAGE_T.get();
        if (last[0] != 0) {
            long[] acc = STAGES.computeIfAbsent(name, k -> new long[2]);
            synchronized (acc) {
                acc[0] += now - last[0];
                acc[1]++;
            }
        }
        last[0] = System.nanoTime();
    }

    /** Resets the stage clock (call before the first stage of a block). */
    static void stageStart(Tensor t) {
        if (STAGE_TIMING) {
            Aten.one("aten::sum", t).doubleValue();
            STAGE_T.get()[0] = System.nanoTime();
        }
    }

    static String stageReport() {
        StringBuilder sb = new StringBuilder();
        new java.util.TreeMap<>(STAGES).forEach((k, v) -> sb.append(String.format("  %-14s %7.1f us x %d%n", k, v[0] / 1e3 / Math.max(1, v[1]), v[1])));
        return sb.toString();
    }

    /** Profiling ablation switches (comma separated, env {@code GLM_PROF_SKIP}): moe, attn, ar. Changes the numbers: for timing only. */
    static final String PROF_SKIP = System.getenv().getOrDefault("GLM_PROF_SKIP", "");

    /** Sums a float32 tensor over the ranks in place (a row-parallel product's partial results); a no-op on one rank. */
    static Tensor allReduce(Tensor t) {
        if (PROF_SKIP.contains("ar")) {
            return t;
        }
        Ep ep = EP.get();
        if (ep != null) {
            ep.group().allReduceSumInPlace(ep.rank(), t);
        }
        return t;
    }

    static void setEp(Ep ep) {
        EP.set(ep);
    }

    static Tensor o(String op, Object... args) {
        return Aten.one(op, args);
    }

    static Tensor f32(Tensor x) {
        return x.dtype() == ScalarType.Float ? x : x.to(ScalarType.Float);
    }

    static Tensor narrow(Tensor t, long dim, long start, long length) {
        return o("aten::narrow", t, dim, start, length);
    }

    static Tensor cat(long dim, Tensor... ts) {
        return o("aten::cat", ts, dim);
    }

    static Tensor host(Tensor t) {
        return o("aten::clone", t).to(Device.CPU());
    }

    static Tensor zeros(ScalarType dtype, long... shape) {
        return Tensor.zeros(new Tensor.Options().dtype(dtype).device(device()), shape);
    }

    /** 3xTF32 tensor-core kernel for skinny fp32 products ({@code GLM_X3_GEMM=0} disables). */
    private static final boolean X3_GEMM = !"0".equals(System.getenv("GLM_X3_GEMM"));
    /** Widest fp32 product that takes the tensor-core path ({@code GLM_X3_MAX_N}). */
    private static final int X3_MAX_COLUMNS = Integer.parseInt(System.getenv().getOrDefault("GLM_X3_MAX_N", "512"));
    private static final java.util.Map<Tensor, Tensor> SPLITS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /**
     * The hi and lo bf16 parts of an fp32 weight {@code [N, K]} in mma fragment order {@code [N/8, K/16, 32, 8]} (per n8 tile,
     * k16 step and lane: hi b0, hi b1, lo b0, lo b1 as bf16 pairs), cached per weight.
     */
    static Tensor splitBf16(Tensor w) {
        return SPLITS.computeIfAbsent(w, k -> {
            long n = w.size(0);
            long kk = w.size(1);
            Tensor hi = o("aten::_to_copy", w, ScalarType.BFloat16);
            Tensor lo = o("aten::_to_copy", o("aten::sub.Tensor", w, hi.to(ScalarType.Float)), ScalarType.BFloat16);
            Tensor both = cat(0, unsqueeze(hi, 0), unsqueeze(lo, 0));                              // [hl, N, K]
            // [hl, nt, g, ks, half, t4, pair] -> [nt, ks, g, t4, hl, half, pair]
            Tensor v = o("aten::reshape", both, new long[]{2, n / 8, 8, kk / 16, 2, 4, 2});
            Tensor perm = o("aten::permute", v, new long[]{1, 3, 2, 5, 0, 4, 6});
            return keep(o("aten::reshape", o("aten::contiguous", perm), new long[]{n / 8, kk / 16, 32, 8}));
        });
    }

    /**
     * {@code x w^T}; a lower-precision weight rounds the input to its dtype, the product is accumulated and returned in
     * float32 (no intermediate rounding of the output, which matters for row-parallel partial sums).
     */
    static Tensor linear(Tensor x, Tensor w) {
        if (w.dtype() == ScalarType.Float) {
            if (X3_GEMM && x.dtype() == ScalarType.Float && w.dim() == 2 && x.dim() == 2 && x.size(0) >= 256
                    && w.size(0) <= X3_MAX_COLUMNS && w.size(0) % 8 == 0 && w.size(1) % 16 == 0 && x.device().isCUDA()
                    && smile.torch.Native.f32GemmBf16x3Available()) {
                // skinny fp32 products of prefill (HC projection N = 24, router N = 288) on bf16 tensor cores with hi/lo
                // splits of both operands (the weight's split is made once)
                return smile.torch.Native.f32GemmBf16x3(o("aten::contiguous", x), splitBf16(w));
            }
            return o("aten::linear", x, w);
        }
        Tensor xb = x.dtype() == w.dtype() ? x : x.to(w.dtype());
        long[] shape = x.shape();
        if (shape.length == 2) {
            return o("aten::mm.dtype", xb, o("aten::t", w), ScalarType.Float);
        }
        long[] out = shape.clone();
        out[out.length - 1] = w.size(0);
        Tensor flat = o("aten::reshape", xb, new long[]{-1L, shape[shape.length - 1]});
        return o("aten::reshape", o("aten::mm.dtype", flat, o("aten::t", w), ScalarType.Float), out);
    }

    static Tensor sigmoid(Tensor x) {
        return o("aten::sigmoid", x);
    }

    static Tensor mul(Tensor a, Tensor b) {
        return o("aten::mul.Tensor", a, b);
    }

    static Tensor add(Tensor a, Tensor b) {
        return o("aten::add.Tensor", a, b);
    }

    static Tensor sumDim(Tensor x, long dim, boolean keep) {
        return o("aten::sum.dim_IntList", x, new long[]{dim}, keep);
    }

    static Tensor unsqueeze(Tensor x, long dim) {
        return o("aten::unsqueeze", x, dim);
    }

    /** Keeps a long-lived tensor out of every scope. */
    static Tensor keep(Tensor t) {
        t.detachFromScopes();
        return t;
    }

    /** Builds a weight, freeing the temporaries (dequantization intermediates) at once. */
    static Tensor scoped(Supplier<Tensor> build) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor t = build.get();
            t.promoteToParent();
            return keep(t);
        } finally {
            Tensor.pop();
        }
    }

    /** Runs a layer in its own scope; the result (and nothing else) survives. */
    static Tensor layerScoped(Supplier<Tensor> layer) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor t = layer.get();
            t.promoteToParent();
            return t;
        } finally {
            Tensor.pop();
        }
    }

    /** Runs a step in its own scope; the returned tensors (and nothing else) survive into the caller's scope. */
    static Tensor[] scopedAll(Supplier<Tensor[]> step) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor[] r = step.get();
            for (Tensor t : r) {
                t.promoteToParent();
            }
            return r;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Dequantizes an FP8 weight with 128x128 block scales ({@code weight_scale_inv}) to float32.
     *
     * @param w     {@code [N, K]} float8 e4m3.
     * @param scale {@code [ceil(N/128), ceil(K/128)]} float32 (the value is multiplied: "inverse" of the quantization scale).
     * @return {@code [N, K]} float32.
     */
    static Tensor dequantBlockFp8(Tensor w, Tensor scale) {
        long n = w.size(0);
        long k = w.size(1);
        Tensor s = o("aten::repeat_interleave.self_int", scale, 128L, 0L);
        s = o("aten::repeat_interleave.self_int", narrow(s, 0, 0, n), 128L, 1L);
        s = narrow(s, 1, 0, k);
        return mul(f32(w), s);
    }

    /**
     * Loads {@code prefix.weight} as float32, dequantizing when {@code prefix.weight_scale_inv} exists.
     *
     * @param ck     the checkpoint.
     * @param prefix tensor name without {@code .weight}.
     * @return a long-lived float32 tensor on the model device.
     */
    static Tensor weight(DeepSeekCheckpoint ck, String prefix) {
        return scoped(() -> rawWeight(ck, prefix));
    }

    /**
     * The calling rank's slice of a weight along {@code dim} (0: rows, 1: columns), dequantized like {@link #rawWeight};
     * only the slice is moved to the device and dequantized. FP8 weights need 128-aligned slices.
     */
    static Tensor rawShard(DeepSeekCheckpoint ck, String prefix, int dim) {
        int world = tpWorld();
        if (world == 1) {
            return rawWeight(ck, prefix);
        }
        Tensor w = ck.get(prefix + ".weight");
        long per = w.size(dim) / world;
        long off = per * tpRank();
        if (per * world != w.size(dim)) {
            throw new IllegalArgumentException(prefix + ": " + w.size(dim) + " is not divisible by " + world + " ranks");
        }
        Tensor part = o("aten::contiguous", narrow(w, dim, off, per)).to(device());
        if (ck.has(prefix + ".weight_scale_inv")) {
            if (off % 128 != 0 || per % 128 != 0) {
                throw new IllegalArgumentException(prefix + ": slice " + off + "+" + per + " is not 128-aligned");
            }
            Tensor sc = ck.get(prefix + ".weight_scale_inv");
            Tensor sp = o("aten::contiguous", narrow(sc, dim, off / 128, per / 128)).to(device());
            return dequantBlockFp8(part, sp).to(weightType());
        }
        return f32(part).to(weightType());
    }

    /** {@link #rawShard}, kept. */
    static Tensor shard(DeepSeekCheckpoint ck, String prefix, int dim) {
        return scoped(() -> rawShard(ck, prefix, dim));
    }

    /** The rank's slice of a vector tensor (float32), not kept. */
    static Tensor rawVectorShard(DeepSeekCheckpoint ck, String name) {
        Tensor v = ck.get(name);
        int world = tpWorld();
        if (world == 1) {
            return f32(v).to(device());
        }
        long per = v.size(0) / world;
        return o("aten::contiguous", narrow(f32(v), 0, per * tpRank(), per)).to(device());
    }

    /** {@link #rawVectorShard}, kept. */
    static Tensor vectorShard(DeepSeekCheckpoint ck, String name) {
        return scoped(() -> rawVectorShard(ck, name));
    }

    /** Like {@link #weight} but not kept: for building a larger weight inside {@link #scoped}. */
    static Tensor rawWeight(DeepSeekCheckpoint ck, String prefix) {
        Tensor w = ck.get(prefix + ".weight").to(device());
        if (ck.has(prefix + ".weight_scale_inv")) {
            return dequantBlockFp8(w, ck.get(prefix + ".weight_scale_inv").to(device())).to(weightType());
        }
        return f32(w).to(weightType());
    }

    /** Loads a plain tensor as float32 on the model device (long-lived). */
    static Tensor vector(DeepSeekCheckpoint ck, String name) {
        return scoped(() -> f32(ck.get(name)).to(device()));
    }

    /** {@code x / sqrt(sum(x^2) + eps)} over the last dimension. */
    static Tensor l2norm(Tensor x, double eps) {
        Tensor ss = o("aten::sum.dim_IntList", mul(x, x), new long[]{-1}, true);
        return o("aten::div.Tensor", x, o("aten::sqrt", o("aten::add.Scalar", ss, eps)));
    }

    /** Plain RMS norm over the last dimension in float32 with an optional weight. */
    static Tensor rms(Tensor x, Tensor weight, double eps) {
        Tensor xf = f32(x);
        if (weight != null) {
            Tensor fused = smile.torch.Native.dsRmsNorm(xf, weight, eps);       // one launch; null when unavailable or unsupported
            if (fused != null) {
                return fused;
            }
        }
        Tensor var = o("aten::mean.dim", mul(xf, xf), new long[]{-1}, true);
        Tensor y = mul(xf, o("aten::rsqrt", o("aten::add.Scalar", var, eps)));
        return weight == null ? y : mul(weight, y);
    }
}
