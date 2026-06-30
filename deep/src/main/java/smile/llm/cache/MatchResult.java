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

import smile.deep.tensor.Tensor;

/**
 * Result returned by {@link RadixCache#matchPrefix}.
 *
 * <p>This record implements {@link AutoCloseable} so callers can use
 * try-with-resources to release the {@link #indices()} tensor automatically:
 * <pre>{@code
 * try (var result = cache.matchPrefix(tokenIds)) {
 *     process(result.indices());
 * }
 * }</pre>
 *
 * <p>The caller <strong>owns</strong> the {@link #indices()} tensor. If
 * try-with-resources is not used, the caller must call {@link #close()}
 * (or {@code indices().close()}) explicitly.
 *
 * @param indices  a freshly-allocated 1-D {@code int64} tensor containing the
 *                 concatenated KV cache slot indices for the longest cached
 *                 prefix found. Its length is always a multiple of the cache's
 *                 {@code pageSize}. An empty tensor (length 0) means nothing
 *                 was found in the cache.
 * @param lastNode the deepest {@link TreeNode} reached during matching. Callers
 *                 should pass this node to {@link RadixCache#incLockRef} to
 *                 protect the matched prefix from eviction while the request
 *                 is in flight, and to {@link RadixCache#decLockRef} when done.
 *
 * @author Haifeng Li
 */
public record MatchResult(Tensor indices, TreeNode lastNode) implements AutoCloseable {

    /**
     * Returns the number of matched token slots (equal to {@code indices.length()}).
     * @return the number of matched tokens.
     */
    public int length() {
        return (int) indices.length();
    }

    /**
     * Releases the {@link #indices()} tensor. Called automatically when this
     * result is used in a try-with-resources statement.
     */
    @Override
    public void close() {
        indices.close();
    }
}
