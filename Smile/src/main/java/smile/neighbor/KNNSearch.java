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
     * @param k	the number of nearest neighbors to search for.
     */
    public Neighbor<K,V>[] knn(K q, int k);
}
