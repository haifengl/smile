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
 * Nearest neighbor search, also known as proximity search, similarity search
 * or closest point search, is an optimization problem for finding closest
 * points in metric spaces. The problem is: given a set S of points in a metric
 * space M and a query point q &isin; M, find the closest point in S to q.
 * 
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public interface NearestNeighborSearch<K, V> {
    /**
     * Search the nearest neighbor to the given sample.
     *
     * @param q the query key.
     * @return the nearest neighbor
     */
    public Neighbor<K, V> nearest(K q);
}
