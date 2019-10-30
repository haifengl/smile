/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.neighbor;

/**
 * K-nearest neighbor search identifies the top k nearest neighbors to the
 * query. This technique is commonly used in predictive analytics to
 * estimate or classify a point based on the consensus of its neighbors.
 * K-nearest neighbor graphs are graphs in which every point is connected
 * to its k nearest neighbors.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public interface KNNSearch<K, V> {
    /**
     * Search the k nearest neighbors to the query.
     *
     * @param q the query key.
     * @param k the number of nearest neighbors to search for.
     */
    Neighbor<K,V>[] knn(K q, int k);
}
