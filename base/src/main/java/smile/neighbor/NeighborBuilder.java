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
package smile.neighbor;

/**
 * The mutable object as a template to create a Neighbor object.
 * This helps reduce the creation of a lot of temporary objects
 * as we can update this object's values in the heap.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
class NeighborBuilder<K, V> implements Comparable<NeighborBuilder<K,V>> {
    /**
     * The key of neighbor.
     */
    K key;
    /**
     * The data object of neighbor. It may be same as the key object.
     */
    V value;
    /**
     * The index of neighbor object in the dataset.
     */
    int index;
    /**
     * The distance between the query and the neighbor.
     */
    double distance;

    /**
     * Constructor.
     */
    public NeighborBuilder() {
        this.index = -1;
        this.distance = Double.MAX_VALUE;
    }

    /**
     * Constructor.
     * @param key the key of neighbor.
     * @param value the value of neighbor.
     * @param index the index of neighbor object in the dataset.
     * @param distance the distance between the query and the neighbor.
     */
    public NeighborBuilder(K key, V value, int index, double distance) {
        this.key = key;
        this.value = value;
        this.index = index;
        this.distance = distance;
    }

    /** Creates a neighbor object. */
    public Neighbor<K, V> toNeighbor() {
        return new Neighbor<>(key, value, index, distance);
    }

    @Override
    public int compareTo(NeighborBuilder<K,V> o) {
        int d = Double.compare(distance, o.distance);
        // Sometime, the dataset contains duplicate samples.
        // If the distances are same, we sort by the sample index.
        return d == 0 ? index - o.index : d;
    }
}
