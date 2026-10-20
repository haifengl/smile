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
 * Linear layer whose weight stays in MXFP8 (FP8 E4M3 elements, E8M0 scale per 32 elements) in the
 * Marlin tile layout and is multiplied with bfloat16 activations by the weight-only Marlin kernel.
 * It works on GPUs without native FP8 (Ampere). The weight takes one byte per element plus 1/32 byte
 * of scale.
 *
 * <p>The layer is created empty and receives its weight from the checkpoint through
 * {@link #setWeight}; it owns no module parameters, so {@code LayerBlock.to(device, dtype)} never
 * converts it.
 *
 * @author Haifeng Li
 */
public class MxFp8MarlinLinear implements MxFp8Layer {
    private final int inFeatures;
    private final int outFeatures;
    private Tensor qweight;
    private Tensor scales;

    /**
     * Constructor.
     *
     * @param inFeatures  reduction size K; a multiple of 128.
     * @param outFeatures output size N; a multiple of 64.
     */
    public MxFp8MarlinLinear(int inFeatures, int outFeatures) {
        if (inFeatures % 128 != 0 || outFeatures % 64 != 0) {
            throw new IllegalArgumentException("Marlin MXFP8 needs K % 128 == 0 and N % 64 == 0, got K="
                    + inFeatures + " N=" + outFeatures);
        }
        this.inFeatures = inFeatures;
        this.outFeatures = outFeatures;
    }

    /**
     * Installs the weight from checkpoint tensors, repacking them for the kernel.
     *
     * @param weight FP8 E4M3 {@code [N, K]} on the target CUDA device.
     * @param scale  E8M0 scales as uint8 {@code [N, K / 32]} on the same device.
     */
    @Override
    public void setWeight(Tensor weight, Tensor scale) {
        long[] w = weight.shape();
        if (w.length != 2 || w[0] != outFeatures || w[1] != inFeatures) {
            throw new IllegalArgumentException("expected weight [" + outFeatures + ", " + inFeatures
                    + "], got " + java.util.Arrays.toString(w));
        }
        Tensor packed = Native.marlinFp8RepackWeight(weight);
        Tensor permuted = Native.marlinFp8RepackScales(scale);
        close();
        this.qweight = packed;
        this.scales = permuted;
    }

    /**
     * Returns whether the weight has been installed.
     * @return {@code true} once {@link #setWeight} has run.
     */
    @Override
    public boolean isLoaded() {
        return qweight != null;
    }

    @Override
    public Tensor forward(Tensor input) {
        if (qweight == null) {
            throw new IllegalStateException("MXFP8 weight has not been loaded");
        }
        if (input.dtype() != ScalarType.BFloat16) {
            throw new IllegalArgumentException("Marlin MXFP8 needs bfloat16 activations, got " + input.dtype());
        }
        long[] shape = input.shape();
        Tensor flat = input.reshape(-1, inFeatures);
        Tensor out = Native.marlinFp8Gemm(flat, qweight, scales);
        long[] outShape = shape.clone();
        outShape[outShape.length - 1] = outFeatures;
        Tensor result = out.reshape(outShape);
        return result;
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
    }
}
