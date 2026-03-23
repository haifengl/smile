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

import smile.util.Strings;

/**
 * The immutable object encapsulates the results of nearest neighbor search.
 * A returned neighbor for nearest neighbor search contains the key of object
 * (e.g. the weight vector of a neuron) and the object itself (e.g. a neuron,
 * which also contains other information beyond weight vector), an index of
 * object in the dataset, which is often useful, and the distance between
 * the query key to the object key.
 *
 * @param key the key of neighbor.
 * @param value the value of neighbor.
 * @param index the index of neighbor object in the dataset.
 * @param distance the distance between the query and the neighbor.
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 * 
 * @author Haifeng Li
 */
public record Neighbor<K, V>(K key, V value, int index, double distance) implements Comparable<Neighbor<K,V>> {
    @Override
    public int compareTo(Neighbor<K,V> o) {
        int d = Double.compare(distance, o.distance);
        // Sometime, the dataset contains duplicate samples.
        // If the distances are same, we sort by the sample index.
        return d == 0 ? Integer.compare(index, o.index) : d;
    }

    @Override
    public String toString() {
        return String.format("Neighbor(%s[%d]: %s)", key, index, Strings.format(distance));
    }

    /**
     * Creates a neighbor object, of which key and object are the same.
     *
     * @param key      the query key.
     * @param index    the index of object.
     * @param distance the distance between query key and neighbor.
     * @param <T>      the data type of key and object.
     * @return the neighbor object.
     */
    public static <T> Neighbor<T, T> of(T key, int index, double distance) {
        return new Neighbor<>(key, key, index, distance);
    }
}
