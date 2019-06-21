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
 * The object encapsulates the results of nearest neighbor search. A returned
 * neighbor for nearest neighbor search contains the key of object (say weight
 * vector of a neuron) and the object itself (say a neuron in neural network,
 * which also contains other information beyond weight vector), an index of
 * object in the dataset, which is often useful, and the distance between
 * the query key to the object key.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 * 
 * @author Haifeng Li
 */
public class Neighbor<K, V> implements Comparable<Neighbor<K,V>> {
    /**
     * The key of neighbor.
     */
    public K key;
    /**
     * The data object of neighbor. It may be same as the key object.
     */
    public V value;
    /**
     * The index of neighbor object in the dataset.
     */
    public int index;
    /**
     * The distance between the query and the neighbor.
     */
    public double distance;

    /**
     * Constructor.
     * @param object the neighbor object.
     * @param index the index of neighbor object in the dataset.
     * @param distance the distance between the query and the neighbor.
     */
    public Neighbor(K key, V object, int index, double distance) {
        this.key = key;
        this.value = object;
        this.index = index;
        this.distance = distance;
    }

    @Override
    public int compareTo(Neighbor<K,V> o) {
        int d = (int) Math.signum(distance - o.distance);
        // Sometime, the dataset contains duplicate samples.
        // If the distances are same, we sort by the sample index.
        if (d == 0)
            return index - o.index;
        else
            return d;
    }
}
