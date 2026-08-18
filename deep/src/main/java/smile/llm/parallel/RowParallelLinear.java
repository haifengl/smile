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

/**
 * Megatron-style row-parallel linear: shards {@code in_features} across TP
 * ranks. After the local matmul the caller must
 * {@link TensorParallelGroup#allReduceSumInPlace} so outputs are replicated.
 *
 * @author Haifeng Li
 */
public final class RowParallelLinear {
    private final LinearLayer linear;
    private final int tpRank;
    private final int tpSize;
    private final int globalInFeatures;

    /**
     * @param globalInFeatures full (unsharded) input size; must divide by tpSize.
     * @param outFeatures      shared output size.
     * @param bias             whether to use bias (typically false for TP row layers).
     * @param tpSize           tensor-parallel size.
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

    public LinearLayer linear() {
        return linear;
    }

    public MemorySegment module() {
        return linear.module();
    }

    public int localInFeatures() {
        return globalInFeatures / tpSize;
    }

    public int tpRank() {
        return tpRank;
    }

    public int tpSize() {
        return tpSize;
    }

    /**
     * Local matmul only. When {@code tpSize > 1}, the orchestrator must
     * all-reduce the returned tensors across ranks.
     */
    public Tensor forward(Tensor input) {
        return linear.forward(input);
    }
}
