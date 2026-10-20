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
package smile.llm.model.minimax;

import smile.deep.tensor.Device;

/**
 * One rank of expert parallelism: this rank owns a contiguous range of the routed experts of
 * every MoE layer and allocates their weights directly on {@code device}. Attention, dense FFNs,
 * the router and the shared expert are replicated on every rank.
 *
 * @param rank   this rank, in {@code [0, size)}.
 * @param size   number of ranks.
 * @param device the rank's device; expert weights are allocated here.
 *
 * @author Haifeng Li
 */
public record MiniMaxShard(int rank, int size, Device device) {
    /** Validates the rank. */
    public MiniMaxShard {
        if (size < 1 || rank < 0 || rank >= size) {
            throw new IllegalArgumentException("invalid shard " + rank + "/" + size);
        }
    }

    /**
     * Returns the unsharded layout.
     * @return a single rank on the CPU (the model is moved with {@code to(device)}).
     */
    public static MiniMaxShard single() {
        return new MiniMaxShard(0, 1, Device.CPU());
    }

    /**
     * Returns the number of experts owned by this rank.
     * @param numExperts total routed experts.
     * @return local expert count.
     */
    public int localExperts(int numExperts) {
        if (numExperts % size != 0) {
            throw new IllegalArgumentException(numExperts + " experts are not divisible by " + size + " ranks");
        }
        return numExperts / size;
    }

    /**
     * Returns the first expert owned by this rank.
     * @param numExperts total routed experts.
     * @return global index of the first local expert.
     */
    public int expertStart(int numExperts) {
        return rank * localExperts(numExperts);
    }
}
