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
 * @author Haifeng Li
 */
class ChildKey {
    /** Optional namespace tag (e.g., LoRA adapter ID). {@code null} for the default namespace. */
    final String extraKey;
    /** The first {@code pageSize} token IDs on the tree edge. */
    final int[] tokens;

    /**
     * Constructor.
     * @param extraKey optional namespace tag, or {@code null}.
     * @param tokens   the first {@code pageSize} token IDs.
     */
    ChildKey(String extraKey, int[] tokens) {
        this.extraKey = extraKey;
        this.tokens = tokens;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChildKey other)) return false;
        return Objects.equals(extraKey, other.extraKey) && Arrays.equals(tokens, other.tokens);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(extraKey) + Arrays.hashCode(tokens);
    }

    @Override
    public String toString() {
        return "ChildKey{extraKey=" + extraKey + ", tokens=" + Arrays.toString(tokens) + "}";
    }
}
