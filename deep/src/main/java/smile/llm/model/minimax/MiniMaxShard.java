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
 * every MoE layer and allocates their weights directly on {@code device}. By default attention, dense
 * FFNs, the router, the shared expert, the embedding and the output head are replicated on every rank;
 * with {@code tensorParallel} they are split across the ranks instead (attention by heads, with one KV head
 * serving several ranks when there are fewer KV heads than ranks; FFNs by intermediate width; embedding and
 * head by vocabulary), which is what lets a checkpoint whose replicated part is large fit.
 *
 * @param rank           this rank, in {@code [0, size)}.
 * @param size           number of ranks.
 * @param device         the rank's device; expert weights are allocated here.
 * @param tensorParallel whether the non-expert weights are split across the ranks.
 *
 * @author Haifeng Li
 */
public record MiniMaxShard(int rank, int size, Device device, boolean tensorParallel) {
    /**
     * Expert parallelism only (everything else replicated).
     * @param rank   this rank.
     * @param size   number of ranks.
     * @param device the rank's device.
     */
    public MiniMaxShard(int rank, int size, Device device) {
        this(rank, size, device, false);
    }

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

    /**
     * Returns whether non-expert weights are split across ranks.
     * @return {@code true} with {@code tensorParallel} and more than one rank.
     */
    public boolean tp() {
        return tensorParallel && size > 1;
    }

    /**
     * Returns this rank's share of a dimension that is split evenly, or all of it without tensor parallelism.
     *
     * @param total the full extent (heads, rows, vocabulary...).
     * @return the local extent.
     */
    public int part(int total) {
        if (!tp()) {
            return total;
        }
        if (total % size != 0) {
            throw new IllegalArgumentException(total + " is not divisible by " + size + " tensor-parallel ranks");
        }
        return total / size;
    }

    /**
     * Returns the first element of this rank's share of an evenly split dimension.
     *
     * @param total the full extent.
     * @return the local start.
     */
    public int partStart(int total) {
        return tp() ? rank * part(total) : 0;
    }

    /**
     * Returns the number of KV heads this rank works with: its share, or one when there are fewer KV heads than
     * ranks (several ranks then hold the same KV head and split its query group).
     *
     * @param numKvHeads KV heads of the model.
     * @return the local KV head count.
     */
    public int kvHeads(int numKvHeads) {
        if (!tp()) {
            return numKvHeads;
        }
        if (numKvHeads >= size) {
            return part(numKvHeads);
        }
        if (size % numKvHeads != 0) {
            throw new IllegalArgumentException(size + " tensor-parallel ranks do not map onto " + numKvHeads + " KV heads");
        }
        return 1;
    }

    /**
     * Returns the first KV head this rank works with.
     *
     * @param numKvHeads KV heads of the model.
     * @return the global index of the first local KV head.
     */
    public int kvStart(int numKvHeads) {
        if (!tp()) {
            return 0;
        }
        return numKvHeads >= size ? rank * (numKvHeads / size) : rank / (size / numKvHeads);
    }
}
