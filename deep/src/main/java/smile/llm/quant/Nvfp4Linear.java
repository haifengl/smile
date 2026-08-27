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

/**
 * NVFP4 weight-only linear for Blackwell (sm_100+).
 *
 * <p>Wraps LibTorch / cuBLASLt NVFP4 GEMM when available. Until the linked
 * LibTorch exposes a stable NVFP4 scaled-mm entry point, {@link #forward}
 * throws a clear error (no Marlin fallback on Blackwell).
 *
 * @author Haifeng Li
 */
public final class Nvfp4Linear implements LinearOp, AutoCloseable {
    private final Tensor weight;
    private final Tensor weightScale;
    private final Tensor bias;
    private final int inFeatures;
    private final int outFeatures;

    public Nvfp4Linear(Tensor weight, Tensor weightScale, Tensor bias) {
        if (weight == null || weightScale == null) {
            throw new IllegalArgumentException("weight and weightScale required");
        }
        long[] shape = weight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("weight must be 2D [out,in] (logical)");
        }
        this.outFeatures = (int) shape[0];
        this.inFeatures = (int) shape[1];
        this.weight = weight;
        this.weightScale = weightScale;
        this.bias = bias;
    }

    public int inFeatures() { return inFeatures; }
    public int outFeatures() { return outFeatures; }

    @Override
    public Tensor forward(Tensor input) {
        throw new UnsupportedOperationException(
                "Nvfp4Linear GEMM requires LibTorch NVFP4 / cuBLASLt support in this "
                        + "libsmile_torch build. FP8 weights + FP8 KV remain the Hopper path; "
                        + "do not fall back to Marlin on Blackwell.");
    }

    @Override
    public void close() {
        weight.close();
        weightScale.close();
        if (bias != null) {
            bias.close();
        }
    }
}
