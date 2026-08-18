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

/**
 * Local shard sizes for a dense decoder under tensor parallelism.
 *
 * @param tpSize              tensor-parallel world size.
 * @param tpRank              this rank.
 * @param numHeads            local query heads ({@code global / tpSize}).
 * @param numKvHeads          local KV heads.
 * @param intermediateSize    local FFN intermediate size.
 * @param linearNumKeyHeads   local DeltaNet key heads (may equal global when not sharded).
 * @param linearNumValueHeads local DeltaNet value heads.
 *
 * @author Haifeng Li
 */
public record TensorShardSpec(
        int tpSize,
        int tpRank,
        int numHeads,
        int numKvHeads,
        int intermediateSize,
        int linearNumKeyHeads,
        int linearNumValueHeads) {

    public static TensorShardSpec single(int numHeads, int numKvHeads, int intermediateSize,
                                         int linearNumKeyHeads, int linearNumValueHeads) {
        return new TensorShardSpec(1, 0, numHeads, numKvHeads, intermediateSize,
                linearNumKeyHeads, linearNumValueHeads);
    }

    /**
     * Builds a per-rank shard spec. Requires {@code numHeads}, {@code numKvHeads},
     * and {@code intermediateSize} to be divisible by {@code tpSize}. Linear-attn
     * head counts must also divide when {@code > 0}.
     */
    public static TensorShardSpec forRank(int tpSize, int tpRank,
                                          int numHeads, int numKvHeads, int intermediateSize,
                                          int linearNumKeyHeads, int linearNumValueHeads) {
        requireDivisible("numHeads", numHeads, tpSize);
        requireDivisible("numKvHeads", numKvHeads, tpSize);
        requireDivisible("intermediateSize", intermediateSize, tpSize);
        if (linearNumKeyHeads > 0) {
            requireDivisible("linearNumKeyHeads", linearNumKeyHeads, tpSize);
        }
        if (linearNumValueHeads > 0) {
            requireDivisible("linearNumValueHeads", linearNumValueHeads, tpSize);
        }
        return new TensorShardSpec(
                tpSize,
                tpRank,
                numHeads / tpSize,
                numKvHeads / tpSize,
                intermediateSize / tpSize,
                linearNumKeyHeads > 0 ? linearNumKeyHeads / tpSize : 0,
                linearNumValueHeads > 0 ? linearNumValueHeads / tpSize : 0);
    }

    private static void requireDivisible(String name, int value, int tp) {
        if (value % tp != 0) {
            throw new IllegalArgumentException(name + "=" + value + " must be divisible by tpSize=" + tp);
        }
    }
}
