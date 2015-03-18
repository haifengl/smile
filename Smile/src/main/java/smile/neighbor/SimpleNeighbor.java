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
 * The simple neighbor object, in which key and object are the same.
 *
 * @param <T> the type of key/object.
 *
 * @author Haifeng Li
 */
public class SimpleNeighbor<T> extends Neighbor<T,T> {
    /**
     * Constructor.
     * @param key the neighbor object.
     * @param index the index of neighbor object in the dataset.
     * @param distance the distance between the query and the neighbor.
     */
    public SimpleNeighbor(T key, int index, double distance) {
        super(key, key, index, distance);
    }
}
