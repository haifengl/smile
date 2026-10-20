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
 * Linear layer for MXFP8 weights on the native block-scaled FP8 GEMM of Hopper. The checkpoint's 1x32
 * E8M0 scales are folded into 128x128 float32 tile scales when the weight is installed
 * ({@link Native#mxfp8ToBlockFp8}), then every call is a W8A8 {@link Fp8BlockGemm}.
 *
 * <p>Unlike the Marlin path (FP8 weights, bfloat16 activations) the activations are rounded to FP8
 * here, so results differ slightly from W8A16 execution.
 *
 * @author Haifeng Li
 */
public class MxFp8BlockLinear implements MxFp8Layer {
    private final int inFeatures;
    private final int outFeatures;
    private final boolean emulate;
    private Tensor weight;
    private Tensor scaleInv;

    /**
     * Constructor.
     *
     * @param inFeatures  reduction size K; a multiple of 128.
     * @param outFeatures output size N; a multiple of 128.
     * @param emulate     {@code true} to emulate the FP8 tensor-core GEMM (runs on any CUDA GPU).
     */
    public MxFp8BlockLinear(int inFeatures, int outFeatures, boolean emulate) {
        if (inFeatures % 128 != 0 || outFeatures % 128 != 0) {
            throw new IllegalArgumentException("block FP8 needs K and N multiples of 128, got K="
                    + inFeatures + " N=" + outFeatures);
        }
        this.inFeatures = inFeatures;
        this.outFeatures = outFeatures;
        this.emulate = emulate;
    }

    @Override
    public void setWeight(Tensor w, Tensor scale) {
        long[] shape = w.shape();
        if (shape.length != 2 || shape[0] != outFeatures || shape[1] != inFeatures) {
            throw new IllegalArgumentException("expected weight [" + outFeatures + ", " + inFeatures
                    + "], got " + java.util.Arrays.toString(shape));
        }
        Tensor[] converted = Native.mxfp8ToBlockFp8(w, scale);
        close();
        this.weight = converted[0];
        this.scaleInv = converted[1];
    }

    @Override
    public boolean isLoaded() {
        return weight != null;
    }

    @Override
    public Tensor forward(Tensor input) {
        if (weight == null) {
            throw new IllegalStateException("MXFP8 weight has not been loaded");
        }
        long[] shape = input.shape();
        Tensor flat = input.reshape(-1, inFeatures);
        Tensor out = Fp8BlockGemm.mm(flat, weight, scaleInv, input.dtype(), emulate);
        long[] outShape = shape.clone();
        outShape[outShape.length - 1] = outFeatures;
        return out.reshape(outShape);
    }

    @Override
    public void close() {
        if (weight != null) {
            weight.close();
            weight = null;
        }
        if (scaleInv != null) {
            scaleInv.close();
            scaleInv = null;
        }
    }
}
