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

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

/**
 * Block-scaled FP8 linear for GPUs without native FP8 (Ampere, Ada): DeepSeek / Qwen fine-grained checkpoints
 * ({@code weight_block_size=[128,128]} with float32 {@code weight_scale_inv}) keep their FP8 weights in device memory
 * and run through the weight-only Marlin kernel with bfloat16 activations. Each 128x128 scale is expanded over its 128
 * output columns, which is the kernel's group-128 layout, so the result equals dequantizing the weights (up to the
 * rounding of the scales to bfloat16) and multiplying.
 *
 * <p>The layer takes ownership of the tensors passed to the constructor and replaces them by the repacked weight.
 *
 * @author Haifeng Li
 */
public final class Fp8BlockMarlinLinear implements LinearOp, AutoCloseable {
    private Tensor qweight;
    private Tensor scales;
    private final Tensor bias;
    private final int inFeatures;
    private final int outFeatures;

    /**
     * Repacks a block-FP8 weight.
     *
     * @param weight         FP8 E4M3 {@code [out, in]} on CUDA ({@code in % 128 == 0}, {@code out % 128 == 0}).
     * @param weightScaleInv float32 {@code [out/128, in/128]}, {@code W = fp8 * scale} per tile.
     * @param bias           optional bias, or {@code null}.
     * @throws IllegalStateException    if the native library lacks the Marlin FP8 kernels.
     * @throws IllegalArgumentException on shapes the kernel cannot take.
     */
    public Fp8BlockMarlinLinear(Tensor weight, Tensor weightScaleInv, Tensor bias) {
        if (!Native.marlinFp8Available() || !Native.marlinFp8BlockScalesAvailable()) {
            throw new IllegalStateException(
                    "Marlin block-FP8 kernels unavailable; rebuild libsmile_torch (USE_MARLIN_FP8=ON, CUDA)");
        }
        long[] shape = weight.shape();
        if (shape.length != 2 || !weight.device().isCUDA()) {
            throw new IllegalArgumentException("weight must be a 2-D CUDA tensor [out, in]");
        }
        this.outFeatures = (int) shape[0];
        this.inFeatures = (int) shape[1];
        if (outFeatures % 128 != 0 || inFeatures % 128 != 0) {
            throw new IllegalArgumentException("Marlin block FP8 needs out and in divisible by 128, got ["
                    + outFeatures + ", " + inFeatures + "]");
        }
        long[] s = weightScaleInv.shape();
        if (s.length != 2 || s[0] != outFeatures / 128 || s[1] != inFeatures / 128) {
            throw new IllegalArgumentException("weightScaleInv " + java.util.Arrays.toString(s)
                    + " does not match weight [" + outFeatures + ", " + inFeatures + "]");
        }
        Tensor packed = Native.marlinFp8RepackWeight(weight);
        Tensor permuted = Native.marlinFp8RepackBlockScales(weightScaleInv);
        weight.close();
        weightScaleInv.close();
        this.qweight = packed;
        this.scales = permuted;
        this.bias = bias;
    }

    /**
     * Returns the number of input features.
     *
     * @return input features.
     */
    public int inFeatures() {
        return inFeatures;
    }

    /**
     * Returns the number of output features.
     *
     * @return output features.
     */
    public int outFeatures() {
        return outFeatures;
    }

    @Override
    public Tensor forward(Tensor input) {
        if (input.dtype() != ScalarType.BFloat16) {
            throw new IllegalArgumentException("Marlin block FP8 needs bfloat16 activations, got " + input.dtype());
        }
        long[] shape = input.shape();
        if (shape[shape.length - 1] != inFeatures) {
            throw new IllegalArgumentException(
                    "input last dim " + shape[shape.length - 1] + " != inFeatures " + inFeatures);
        }
        Tensor flat = input.reshape(-1, inFeatures);
        Tensor out = Native.marlinFp8Gemm(flat, qweight, scales);
        if (bias != null) {
            Tensor withBias = out.add(bias);
            out.close();
            out = withBias;
        }
        long[] outShape = shape.clone();
        outShape[outShape.length - 1] = outFeatures;
        return out.reshape(outShape);
    }

    @Override
    public void close() {
        if (qweight != null) {
            qweight.close();
            qweight = null;
        }
        if (scales != null) {
            scales.close();
            scales = null;
        }
        if (bias != null) {
            bias.close();
        }
    }
}
