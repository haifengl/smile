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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

/**
 * Retrieves the top k nearest neighbors to the query. This technique is
 * commonly used in predictive analytics to estimate or classify a point
 * based on the consensus of its neighbors. K-nearest neighbor graphs are
 * graphs in which every point is connected to its k nearest neighbors.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public interface KNNSearch<K, V> {
    /**
     * Returns the nearest neighbor. In machine learning, we often build
     * a nearest neighbor search data structure, and then search with object
     * in the same dataset. The object itself is of course the nearest one
     * with distance 0. Since this is generally useless, we check
     * the reference during the search and excludes the query object from the
     * results.
     *
     * @param q the query key.
     * @return the nearest neighbor
     */
    default Neighbor<K, V> nearest(K q) {
        return search(q, 1)[0];
    }

    /**
     * Retrieves the k nearest neighbors to the query key.
     *
     * @param q the query key.
     * @param k the number of nearest neighbors to search for.
     * @return the k nearest neighbors
     */
    Neighbor<K, V>[] search(K q, int k);
}
