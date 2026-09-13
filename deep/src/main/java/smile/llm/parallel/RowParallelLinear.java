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
 * Megatron-style row-parallel linear: shards {@code in_features} across TP
 * ranks. After the local matmul the caller must
 * {@link TensorParallelGroup#allReduceSumInPlace} so outputs are replicated.
 *
 * <p>Supports dense {@link LinearLayer} or quantized {@link LinearOp}. Quantized
 * weights must be sharded then packed before wrapping.
 *
 * @author Haifeng Li
 */
public final class RowParallelLinear {
    private final LinearOp linear;
    private final int tpRank;
    private final int tpSize;
    private final int globalInFeatures;

    /**
     * Creates a dense row-parallel linear layer.
     *
     * @param globalInFeatures global input features (must be divisible by {@code tpSize}).
     * @param outFeatures      output features.
     * @param bias             whether to include bias.
     * @param tpSize           tensor-parallel world size.
     * @param tpRank           this rank.
     */
    public RowParallelLinear(int globalInFeatures, int outFeatures, boolean bias,
                             int tpSize, int tpRank) {
        if (globalInFeatures % tpSize != 0) {
            throw new IllegalArgumentException(
                    "globalInFeatures=" + globalInFeatures + " not divisible by tpSize=" + tpSize);
        }
        this.tpSize = tpSize;
        this.tpRank = tpRank;
        this.globalInFeatures = globalInFeatures;
        this.linear = new LinearLayer(globalInFeatures / tpSize, outFeatures, bias);
    }

    /**
     * Wraps an already-sharded (and packed, if quantized) local linear op.
     *
     * @param local            local linear op.
     * @param globalInFeatures global input features.
     * @param tpSize           tensor-parallel world size.
     * @param tpRank           this rank.
     */
    public RowParallelLinear(LinearOp local, int globalInFeatures, int tpSize, int tpRank) {
        if (local == null) {
            throw new IllegalArgumentException("local linear required");
        }
        if (globalInFeatures % tpSize != 0) {
            throw new IllegalArgumentException(
                    "globalInFeatures=" + globalInFeatures + " not divisible by tpSize=" + tpSize);
        }
        this.linear = local;
        this.globalInFeatures = globalInFeatures;
        this.tpSize = tpSize;
        this.tpRank = tpRank;
    }

    /**
     * Returns the underlying linear op.
     *
     * @return linear op.
     */
    public LinearOp linearOp() {
        return linear;
    }

    /**
     * Returns the dense linear layer when present.
     *
     * @return dense {@link LinearLayer}.
     */
    public LinearLayer linear() {
        if (linear instanceof LinearLayer ll) {
            return ll;
        }
        throw new IllegalStateException("RowParallelLinear holds quantized LinearOp, not LinearLayer");
    }

    /**
     * Returns the dense module handle.
     *
     * @return module handle.
     */
    public MemorySegment module() {
        return linear().module();
    }

    /**
     * Returns local input features ({@code globalInFeatures / tpSize}).
     *
     * @return local input features.
     */
    public int localInFeatures() {
        return globalInFeatures / tpSize;
    }

    /**
     * Returns this TP rank.
     *
     * @return TP rank.
     */
    public int tpRank() {
        return tpRank;
    }

    /**
     * Returns the TP world size.
     *
     * @return TP world size.
     */
    public int tpSize() {
        return tpSize;
    }

    /**
     * Local matmul; caller must all-reduce for replicated outputs.
     *
     * @param input activation tensor.
     * @return local output.
     */
    public Tensor forward(Tensor input) {
        return linear.forward(input);
    }
}
