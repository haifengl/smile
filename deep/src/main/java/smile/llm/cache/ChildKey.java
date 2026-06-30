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
package smile.llm.cache;

import java.util.Arrays;
import java.util.Objects;

/**
 * Composite key for child node lookup in the radix tree's children map.
 * Combines an optional namespace tag with the first {@code pageSize} token IDs
 * of a tree edge, providing O(1) child lookup while supporting namespace
 * isolation (e.g., different LoRA adapter IDs).
 *
 * <p>This is a {@code record} rather than a plain class because its only role
 * is to act as an immutable, value-based HashMap key. The default record
 * {@code equals} and {@code hashCode} are overridden because one component is
 * an {@code int[]}, whose identity-based {@code hashCode} would break map
 * semantics; {@link Arrays#equals} and {@link Arrays#hashCode} are used
 * instead.
 *
 * @param extraKey optional namespace tag (e.g., LoRA adapter ID);
 *                 {@code null} for the default namespace.
 * @param tokens   the first {@code pageSize} token IDs on the tree edge.
 *
 * @author Haifeng Li
 */
record ChildKey(String extraKey, int[] tokens) {

    /**
     * Value-based equality that uses {@link Arrays#equals} for the
     * {@code int[]} component. The record pattern
     * {@code ChildKey(var e, var t)} deconstructs the other instance without
     * an explicit cast.
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof ChildKey(var e, var t)
                && Objects.equals(extraKey, e)
                && Arrays.equals(tokens, t);
    }

    /**
     * Content-based hash code that uses {@link Arrays#hashCode} for the
     * {@code int[]} component so that structurally equal keys collide.
     */
    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(extraKey) + Arrays.hashCode(tokens);
    }

    @Override
    public String toString() {
        return "ChildKey[extraKey=%s, tokens=%s]".formatted(extraKey, Arrays.toString(tokens));
    }
}
