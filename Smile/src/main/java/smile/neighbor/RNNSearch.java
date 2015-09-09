/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.neighbor;

import java.util.List;

/**
 * A range nearest neighbor search retrieves the nearest neighbors to a query
 * in a range. It is a natural generalization of point and continuous nearest
 * neighbor queries and has many applications.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public interface RNNSearch<K, V> {
    /**
     * Search the neighbors in the given radius of query object, i.e.
     * d(q, v) &le; radius.
     *
     * @param q the query key.
     * @param radius the radius of search range from target.
     * @param neighbors the list to store found neighbors in the given range on output.
     */
    public void range(K q, double radius, List<Neighbor<K,V>> neighbors);
}
