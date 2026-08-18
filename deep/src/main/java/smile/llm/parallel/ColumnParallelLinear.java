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
 * Megatron-style column-parallel linear: shards {@code out_features} across TP
 * ranks. Input is replicated; output is the local shard (no gather).
 *
 * @author Haifeng Li
 */
public final class ColumnParallelLinear {
    private final LinearLayer linear;
    private final int tpRank;
    private final int tpSize;
    private final int globalOutFeatures;

    /**
     * @param inFeatures        shared input size.
     * @param globalOutFeatures full (unsharded) output size; must divide by tpSize.
     * @param bias              whether to use bias.
     * @param tpSize            tensor-parallel size.
     * @param tpRank            this rank.
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

    public LinearLayer linear() {
        return linear;
    }

    public MemorySegment module() {
        return linear.module();
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
