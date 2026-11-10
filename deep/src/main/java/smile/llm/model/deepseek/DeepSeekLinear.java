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
import smile.torch.Aten;

/**
 * A bias-free linear layer of the checkpoint. FP8 (32x32 blocks) and FP4 weights are dequantized to float32 at load
 * time and applied to fp8-fake-quantized activations (as the reference's fp8/fp4 GEMMs quantize their input); bf16
 * and float32 weights are used as they are.
 *
 * <p>This is the validation implementation: it keeps float32 weights. A serving implementation will keep the packed
 * weights and use a fused kernel.
 *
 * @author Haifeng Li
 */
final class DeepSeekLinear {
    private final Tensor weight;
    private final boolean quantized;

    /**
     * Loads {@code prefix.weight} (and {@code prefix.scale} for quantized weights).
     *
     * @param ck     the checkpoint.
     * @param prefix the tensor name prefix.
     * @param dtype  dtype to cast an unquantized weight to, or {@code null} to keep it.
     */
    DeepSeekLinear(DeepSeekCheckpoint ck, String prefix, ScalarType dtype) {
        boolean fp4 = ck.get(prefix + ".weight").dtype() == ScalarType.Int8;
        boolean fp8 = !fp4 && ck.has(prefix + ".scale");
        quantized = fp4 || fp8;
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
        if (quantized) {
            // on CUDA the weight is bf16 (exact: fp8 values times powers of two) and the GEMM accumulates in fp32
            Tensor q = DeepSeekOps.fakeQuantFp8(x, 32).to(weight.dtype());
            return Aten.one("aten::linear", q, weight).to(ScalarType.BFloat16);
        }
        return Aten.one("aten::linear", x, weight);
    }

    /**
     * The (dequantized) weight.
     *
     * @return the weight {@code [out, in]}.
     */
    Tensor weight() {
        return weight;
    }
}
