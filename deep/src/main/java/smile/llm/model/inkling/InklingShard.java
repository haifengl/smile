/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import smile.deep.tensor.Tensor;
import smile.llm.parallel.TensorParallelGroup;

/**
 * One rank's share of an Inkling-2 model that is split over {@code size} GPUs: attention is sharded by query head
 * (each rank also owns {@code numKvHeads / size} key/value heads), the routed experts by contiguous blocks of expert
 * ids, and everything else (AttnRes queries, norms, short convolutions of the residual stream, router, embedding and
 * head) is replicated. A sharded attention output and a sharded MoE output are partial sums, completed by
 * {@link #allReduce(Tensor)}.
 *
 * @param rank  this rank, {@code 0 <= rank < size}.
 * @param size  number of ranks.
 * @param group the communicator ({@code null} when {@code size == 1}).
 * @author Haifeng Li
 */
record InklingShard(int rank, int size, TensorParallelGroup group) {
    /** Timing experiments only: skips the all-reduces (the results are wrong). */
    private static final boolean SKIP_ALL_REDUCE = Boolean.getBoolean("smile.inkling.debug.skipallreduce");

    /** No sharding. */
    static final InklingShard SINGLE = new InklingShard(0, 1, null);

    InklingShard {
        if (size < 1 || rank < 0 || rank >= size || (size > 1 && group == null)) {
            throw new IllegalArgumentException("invalid shard " + rank + "/" + size);
        }
    }

    /**
     * The share of {@code total} items (heads, experts, ...).
     * @param total the item count, divisible by the number of ranks.
     * @return items per rank.
     */
    int part(int total) {
        if (total % size != 0) {
            throw new IllegalArgumentException(total + " is not divisible by " + size + " ranks");
        }
        return total / size;
    }

    /**
     * The first item of this rank.
     * @param total the item count.
     * @return the first item index.
     */
    int start(int total) {
        return rank * part(total);
    }

    /**
     * Completes a partial sum over the ranks, in place (no-op for one rank).
     * @param t contiguous CUDA tensor, the same shape on every rank.
     */
    void allReduce(Tensor t) {
        if (size > 1 && !SKIP_ALL_REDUCE) {
            group.allReduceSumInPlace(rank, t);
        }
    }
}
