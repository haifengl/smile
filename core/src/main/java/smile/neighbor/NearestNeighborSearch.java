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
