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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.quant;

import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

/**
 * Factory for {@link LinearOp} backends from checkpoint tensors + selected backend.
 *
 * @author Haifeng Li
 */
public final class QuantLinearFactory {
    private QuantLinearFactory() {}

    /**
     * Builds a dense {@link LinearLayer} wrapped as {@link LinearOp}.
     *
     * @param inFeatures  input features.
     * @param outFeatures output features.
     * @param bias        whether to include bias.
     * @return dense linear op.
     */
    public static LinearOp dense(int inFeatures, int outFeatures, boolean bias) {
        return new LinearLayer(inFeatures, outFeatures, bias);
    }

    /**
     * Builds an FP8 linear from native FP8 weight + tensor scale.
     *
     * @param weightFp8   FP8 weight.
     * @param weightScale float scale.
     * @param bias        optional bias, or {@code null}.
     * @param outDtype    output dtype.
     * @return FP8 linear op.
     */
    public static LinearOp fp8(Tensor weightFp8, Tensor weightScale, Tensor bias, ScalarType outDtype) {
        return new Fp8Linear(weightFp8, weightScale, bias, outDtype);
    }

    /**
     * Builds a block-scaled FP8 linear (DeepSeek / Qwen block-128 layout).
     *
     * @param weightFp8      FP8 weight.
     * @param weightScaleInv block inverse scales.
     * @param bias           optional bias, or {@code null}.
     * @param outDtype       output dtype.
     * @return block FP8 linear op.
     */
    public static LinearOp fp8Block(Tensor weightFp8, Tensor weightScaleInv, Tensor bias,
                                    ScalarType outDtype) {
        return new Fp8BlockLinear(weightFp8, weightScaleInv, bias, outDtype);
    }

    /**
     * Builds an NVFP4 linear (may throw at forward until LibTorch NVFP4 is wired).
     *
     * @param weight      NVFP4 weight.
     * @param weightScale weight scale.
     * @param bias        optional bias, or {@code null}.
     * @return NVFP4 linear op.
     */
    public static LinearOp nvfp4(Tensor weight, Tensor weightScale, Tensor bias) {
        return new Nvfp4Linear(weight, weightScale, bias);
    }

    /**
     * Packs GPTQ tensors and builds {@link MarlinLinear} (Ampere/Ada only).
     * Uses direct GPTQ→Marlin pack (act-order checkpoints fail fast).
     *
     * @param qweight   GPTQ packed weights.
     * @param scales    group scales.
     * @param qzeros    packed zeros, or {@code null}.
     * @param gIdx      GPTQ {@code g_idx}, or {@code null}.
     * @param groupSize quantization group size.
     * @param device    destination device.
     * @return Marlin linear op.
     */
    public static LinearOp marlinFromGptq(Tensor qweight, Tensor scales, Tensor qzeros, Tensor gIdx,
                                          int groupSize, Device device) {
        var packed = MarlinWeightPacker.packGptqDirect(qweight, scales, qzeros, gIdx, groupSize, device);
        return new MarlinLinear(packed.qweight(), packed.scales(), null,
                packed.inFeatures(), packed.outFeatures(), packed.groupSize());
    }

    /**
     * Packs AWQ tensors and builds {@link MarlinLinear} via direct AWQ→Marlin pack.
     *
     * @param qweight   AWQ packed weights.
     * @param scales    group scales.
     * @param qzeros    packed zeros, or {@code null}.
     * @param groupSize quantization group size.
     * @param device    destination device.
     * @return Marlin linear op.
     */
    public static LinearOp marlinFromAwq(Tensor qweight, Tensor scales, Tensor qzeros,
                                         int groupSize, Device device) {
        var packed = MarlinWeightPacker.packAwqDirect(qweight, scales, qzeros, groupSize, device);
        return new MarlinLinear(packed.qweight(), packed.scales(), null,
                packed.inFeatures(), packed.outFeatures(), packed.groupSize());
    }
}
