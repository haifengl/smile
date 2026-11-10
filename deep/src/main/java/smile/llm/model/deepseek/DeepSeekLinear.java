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
package smile.llm.model.deepseek;

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.quant.MxFp8MarlinLinear;
import smile.torch.Aten;

/**
 * A bias-free linear layer of the checkpoint. On CUDA with {@link Dsv#marlin} the FP8 (32x32 blocks) weights stay packed
 * in the Marlin tile layout (one byte per element) and run through the weight-only Marlin FP8 kernel, which needs no FP8
 * tensor cores; otherwise FP8 and FP4 weights are dequantized at load time. Either way the activations are
 * fp8-fake-quantized first (the reference's fp8/fp4 GEMMs quantize their input); bf16 and float32 weights are used as
 * they are.
 *
 * <p>The Marlin path computes the same products as the dequantized one (an fp8 value times a power of two is exact in
 * bf16, the kernel accumulates in fp32), so the two differ only by summation order.
 *
 * @author Haifeng Li
 */
final class DeepSeekLinear {
    /** Packed weights for the kernel, or {@code null}. */
    private MxFp8MarlinLinear marlin;
    /** Dense weight {@code [out, in]}, or {@code null} when packed. */
    private Tensor weight;
    private final boolean quantized;

    /**
     * Loads {@code prefix.weight} (and {@code prefix.scale} for quantized weights).
     *
     * @param ck     the checkpoint.
     * @param prefix the tensor name prefix.
     * @param dtype  dtype to cast an unquantized weight to, or {@code null} to keep it.
     */
    DeepSeekLinear(DeepSeekCheckpoint ck, String prefix, ScalarType dtype) {
        boolean fp4 = ck.isInt8(prefix + ".weight");
        boolean fp8 = !fp4 && ck.has(prefix + ".scale");
        quantized = fp4 || fp8;
        if (fp8 && useMarlin()) {
            long[] shape = ck.shape(prefix + ".weight");
            if (shape[0] % 64 == 0 && shape[1] % 128 == 0) {
                marlin = Dsv.scopedLayer(() -> {
                    MxFp8MarlinLinear m = new MxFp8MarlinLinear((int) shape[1], (int) shape[0]);
                    // the checkpoint's scale is per 32x32 block {@code [N/32, K/32]}; the kernel wants one per row
                    Tensor scale = Dsv.o("aten::repeat_interleave.self_int", ck.dev(prefix + ".scale"), 32L, 0L);
                    m.setWeight(ck.dev(prefix + ".weight"), scale);
                    m.detachFromScopes();
                    return m;
                });
                return;
            }
        }
        weight = Dsv.scoped(() -> {
            Tensor w = ck.dev(prefix + ".weight");
            if (fp4) {
                return DeepSeekOps.dequantFp4(w, ck.dev(prefix + ".scale")).to(gemmType());
            } else if (fp8) {
                return DeepSeekOps.dequantFp8(w, ck.dev(prefix + ".scale")).to(gemmType());
            }
            return dtype == null ? w : w.to(dtype);
        });
    }

    private DeepSeekLinear(MxFp8MarlinLinear marlin, Tensor weight) {
        this.marlin = marlin;
        this.weight = weight;
        this.quantized = true;
    }

    /** Whether the weight stays packed for the Marlin kernel. */
    boolean packed() {
        return marlin != null;
    }

    private static boolean useMarlin() {
        return Dsv.marlin && Dsv.device().isCUDA() && !"0".equals(System.getenv("DEEPSEEK_DENSE_MARLIN"))
                && smile.torch.Native.marlinFp8Available();
    }

    /**
     * Fuses layers that read the same input into one layer whose output is their outputs side by side, so one kernel
     * (and one activation quantization) serves all of them. The parts are closed.
     *
     * @param parts the layers; all packed, or all dense, with equal input size.
     * @return the fused layer.
     */
    static DeepSeekLinear fuse(DeepSeekLinear... parts) {
        boolean packed = parts[0].marlin != null;
        for (DeepSeekLinear p : parts) {
            if ((p.marlin != null) != packed || !p.quantized) {
                throw new IllegalArgumentException("fuse needs layers of one kind");
            }
        }
        if (packed) {
            MxFp8MarlinLinear[] m = new MxFp8MarlinLinear[parts.length];
            for (int i = 0; i < m.length; i++) {
                m[i] = parts[i].marlin;
                parts[i].marlin = null;
            }
            return new DeepSeekLinear(MxFp8MarlinLinear.fuse(m), null);
        }
        Tensor[] w = new Tensor[parts.length];
        for (int i = 0; i < w.length; i++) {
            w[i] = parts[i].weight;
        }
        return new DeepSeekLinear(null, Dsv.scoped(() -> Dsv.cat(0, w)));
    }

    private static ScalarType gemmType() {
        return Dsv.device().isCUDA() && !"1".equals(System.getenv("DEEPSEEK_GEMM_F32")) ? ScalarType.BFloat16 : ScalarType.Float;
    }

    /**
     * Applies the layer.
     *
     * @param x activations {@code [..., in]}.
     * @return {@code [..., out]} in bf16 for quantized layers, otherwise in the dtype of the product.
     */
    Tensor forward(Tensor x) {
        if (marlin != null) {
            return marlin.forward(DeepSeekOps.fakeQuantFp8(x, 32).to(ScalarType.BFloat16));
        }
        if (quantized) {
            // on CUDA the weight is bf16 (exact: fp8 values times powers of two) and the GEMM accumulates in fp32
            Tensor q = DeepSeekOps.fakeQuantFp8(x, 32).to(weight.dtype());
            return Aten.one("aten::linear", q, weight).to(ScalarType.BFloat16);
        }
        return Aten.one("aten::linear", x, weight);
    }
}
