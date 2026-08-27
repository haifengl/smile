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
package smile.llm.parallel;

import java.lang.foreign.MemorySegment;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Tensor;
import smile.llm.quant.LinearOp;

/**
 * Megatron-style column-parallel linear: shards {@code out_features} across TP
 * ranks. Input is replicated; output is the local shard (no gather).
 *
 * <p>Supports dense {@link LinearLayer} or quantized {@link LinearOp} (FP8 /
 * NVFP4 / Marlin). Quantized weights must be <em>sharded then packed</em> before
 * constructing this wrapper.
 *
 * @author Haifeng Li
 */
public final class ColumnParallelLinear {
    private final LinearOp linear;
    private final int tpRank;
    private final int tpSize;
    private final int globalOutFeatures;

    /**
     * Creates a dense column-parallel linear layer for the given TP rank.
     */
    public ColumnParallelLinear(int inFeatures, int globalOutFeatures, boolean bias,
                                int tpSize, int tpRank) {
        if (globalOutFeatures % tpSize != 0) {
            throw new IllegalArgumentException(
                    "globalOutFeatures=" + globalOutFeatures + " not divisible by tpSize=" + tpSize);
        }
        this.tpSize = tpSize;
        this.tpRank = tpRank;
        this.globalOutFeatures = globalOutFeatures;
        this.linear = new LinearLayer(inFeatures, globalOutFeatures / tpSize, bias);
    }

    /**
     * Wraps an already-sharded (and packed, if quantized) local linear op.
     */
    public ColumnParallelLinear(LinearOp local, int globalOutFeatures, int tpSize, int tpRank) {
        if (local == null) {
            throw new IllegalArgumentException("local linear required");
        }
        if (globalOutFeatures % tpSize != 0) {
            throw new IllegalArgumentException(
                    "globalOutFeatures=" + globalOutFeatures + " not divisible by tpSize=" + tpSize);
        }
        this.linear = local;
        this.globalOutFeatures = globalOutFeatures;
        this.tpSize = tpSize;
        this.tpRank = tpRank;
    }

    /** @return local {@link LinearOp} (dense or quantized). */
    public LinearOp linearOp() {
        return linear;
    }

    /**
     * Returns the underlying dense linear layer.
     * @throws IllegalStateException if this wrapper holds a quantized op.
     */
    public LinearLayer linear() {
        if (linear instanceof LinearLayer ll) {
            return ll;
        }
        throw new IllegalStateException("ColumnParallelLinear holds quantized LinearOp, not LinearLayer");
    }

    /**
     * Returns the native module handle for weight registration (dense only).
     */
    public MemorySegment module() {
        return linear().module();
    }

    public int localOutFeatures() {
        return globalOutFeatures / tpSize;
    }

    public int tpRank() {
        return tpRank;
    }

    public int tpSize() {
        return tpSize;
    }

    public Tensor forward(Tensor input) {
        return linear.forward(input);
    }
}
