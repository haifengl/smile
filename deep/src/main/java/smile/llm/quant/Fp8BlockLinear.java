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

import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

/**
 * Block-scaled FP8 linear for DeepSeek / Qwen fine-grained FP8 checkpoints
 * ({@code weight_block_size=[128,128]} + {@code weight_scale_inv}).
 *
 * <p>Uses LibTorch {@code at::_scaled_mm_v2} with activation BlockWise1x128 and
 * weight BlockWise128x128 scales.
 *
 * @author Haifeng Li
 */
public final class Fp8BlockLinear implements LinearOp, AutoCloseable {
    private final Tensor weight;
    private final Tensor weightScaleInv;
    private final Tensor bias;
    private final ScalarType outDtype;
    private final int inFeatures;
    private final int outFeatures;

    /**
     * @param weight          FP8 weight {@code [outFeatures, inFeatures]}.
     * @param weightScaleInv  float32 block inverse scales
     *                        {@code [ceil(out/128), ceil(in/128)]}.
     * @param bias            optional bias, or {@code null}.
     * @param outDtype        output dtype (BF16 or FP16).
     */
    public Fp8BlockLinear(Tensor weight, Tensor weightScaleInv, Tensor bias, ScalarType outDtype) {
        if (weight == null || weightScaleInv == null) {
            throw new IllegalArgumentException("weight and weightScaleInv required");
        }
        if (!Native.scaledMmV2Available() || !Native.fp8Quant1x128Available()) {
            throw new IllegalStateException(
                    "block FP8 GEMM unavailable; rebuild libsmile_torch with CUDA / LibTorch 2.12+");
        }
        Device dev = weight.device();
        if (!dev.isCUDA()) {
            throw new IllegalArgumentException("Fp8BlockLinear requires CUDA weights");
        }
        int[] cc = Native.cudaComputeCapability(dev.index());
        if (cc[0] < 9) {
            throw new IllegalArgumentException(
                    "block FP8 GEMM requires Hopper+ (sm_90+), got sm_" + cc[0] + cc[1]);
        }
        long[] shape = weight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("weight must be 2D [out,in]");
        }
        this.outFeatures = (int) shape[0];
        this.inFeatures = (int) shape[1];
        if (!Fp8BlockDequant.isBlockScale(weight, weightScaleInv)) {
            throw new IllegalArgumentException(
                    "weightScaleInv layout mismatch for weight ["
                            + outFeatures + "," + inFeatures + "]");
        }
        if (inFeatures % Fp8BlockDequant.BLOCK != 0) {
            throw new IllegalArgumentException(
                    "inFeatures " + inFeatures + " must be divisible by "
                            + Fp8BlockDequant.BLOCK + " for block FP8");
        }
        this.weight = weight;
        this.weightScaleInv = weightScaleInv;
        this.bias = bias;
        this.outDtype = outDtype == null ? ScalarType.BFloat16 : outDtype;
    }

    public int inFeatures() { return inFeatures; }
    public int outFeatures() { return outFeatures; }
    public Tensor weight() { return weight; }
    public Tensor weightScaleInv() { return weightScaleInv; }

    @Override
    public Tensor forward(Tensor input) {
        long[] inShape = input.shape();
        if (inShape.length < 1) {
            throw new IllegalArgumentException("input must be at least 1D");
        }
        long k = inShape[inShape.length - 1];
        if (k != inFeatures) {
            throw new IllegalArgumentException(
                    "input last dim " + k + " != inFeatures " + inFeatures);
        }
        long m = 1;
        for (int i = 0; i < inShape.length - 1; i++) {
            m *= inShape[i];
        }

        Tensor flat = input.reshape(m, k);
        Tensor[] quant = Native.fp8Quant1x128(flat);
        if (quant == null) {
            throw new IllegalStateException(
                    "smile_fp8_quant_1x128 unavailable; rebuild libsmile_torch with CUDA");
        }
        Tensor aFp8 = quant[0];
        Tensor scaleA = quant[1];
        int outCode = outDtype.code();
        Tensor outFlat = Native.scaledMmV2(
                aFp8, weight, scaleA, weightScaleInv,
                Native.SCALING_BLOCK_WISE_1X128,
                Native.SCALING_BLOCK_WISE_128X128,
                outCode);
        aFp8.close();
        scaleA.close();
        if (outFlat == null) {
            throw new IllegalStateException(
                    "smile_scaled_mm_v2 failed; rebuild libsmile_torch with CUDA / Hopper FP8");
        }
        if (bias != null) {
            Tensor withBias = outFlat.add(bias);
            outFlat.close();
            outFlat = withBias;
        }

        long[] outShape = new long[inShape.length];
        System.arraycopy(inShape, 0, outShape, 0, inShape.length - 1);
        outShape[outShape.length - 1] = outFeatures;
        Tensor out = outFlat.reshape(outShape);
        if (out != outFlat) {
            outFlat.close();
        }
        flat.close();
        return out;
    }

    @Override
    public void close() {
        weight.close();
        weightScaleInv.close();
        if (bias != null) {
            bias.close();
        }
    }
}
