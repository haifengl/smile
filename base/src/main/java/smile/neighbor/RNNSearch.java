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

import java.util.List;

/**
 * Retrieves the nearest neighbors to a query in a radius. It is a natural
 * generalization of point and continuous nearest neighbor queries and has
 * many applications.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public interface RNNSearch<K, V> {
    /**
     * Retrieves the neighbors in a fixed radius of query object, i.e.
     * {@code d(q, v) <= radius}.
     *
     * @param q the query key.
     * @param radius the radius of search range from target.
     * @param neighbors the list to store found neighbors on output.
     */
    void search(K q, double radius, List<Neighbor<K,V>> neighbors);
}
