/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.neighbor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import smile.math.distance.Distance;
import smile.sort.HeapSelect;

/**
 * Brute force linear nearest neighbor search. This simplest solution computes
 * the distance from the query point to every other point in the database,
 * keeping track of the "best so far". There are no search data structures to
 * maintain, so linear search has no space complexity beyond the storage of
 * the database. Although it is very simple, naive search outperforms space
 * partitioning approaches (e.g. K-D trees) on higher dimensional spaces.
 * <p>
 * By default, the query object (reference equality) is excluded from the neighborhood.
 * Note that you may observe weird behavior with String objects. JVM will pool the string
 * literal objects. So the below variables
 * <code>
 *     String a = "ABC";
 *     String b = "ABC";
 *     String c = "AB" + "C";
 * </code>
 * are actually equal in reference test <code>a == b == c</code>. With toy data that you
 * type explicitly in the code, this will cause problems. Fortunately, the data would be
 * read from secondary storage in production.
 * </p>
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public class LinearSearch<K, V> implements KNNSearch<K, V>, RNNSearch<K, V>, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The object keys.
     */
    private final List<K> keys;
    /**
     * The data objects.
     */
    private final List<V> data;
    /**
     * The distance function used to determine nearest neighbors.
     */
    private final Distance<K> distance;

    /**
     * Constructor.
     * @param keys the data keys.
     * @param data the data objects.
     * @param distance the distance function.
     */
    public LinearSearch(K[] keys, V[] data, Distance<K> distance) {
        this(Arrays.asList(keys), Arrays.asList(data), distance);
    }

    /**
     * Constructor.
     * @param keys the data keys.
     * @param data the data objects.
     * @param distance the distance function.
     */
    public LinearSearch(List<K> keys, List<V> data, Distance<K> distance) {
        if (keys.size() != data.size()) {
            throw new IllegalArgumentException("Different size of keys and data objects");
        }

        this.keys = keys;
        this.data = data;
        this.distance = distance;
    }

    /**
     * Constructor.
     * @param data the data objects.
     * @param distance the distance function.
     * @param key the lambda to extra the key from data object.
     */
    public LinearSearch(V[] data, Distance<K> distance, Function<V, K> key) {
        this(Arrays.asList(data), distance, key);
    }

    /**
     * Constructor.
     * @param data the data objects.
     * @param distance the distance function.
     * @param key the lambda to extra the key from data object.
     */
    public LinearSearch(List<V> data, Distance<K> distance, Function<V, K> key) {
        this.data = data;
        this.keys = data.stream().map(key).collect(Collectors.toList());
        this.distance = distance;
    }

    /**
     * Return linear nearest neighbor search.
     * @param data the data objects, which are also used as key.
     * @param distance the distance function.
     */
    public static <T> LinearSearch<T, T> of(T[] data, Distance<T> distance) {
        return new LinearSearch<>(data, data, distance);
    }

    /**
     * Return linear nearest neighbor search.
     * @param data the data objects, which are also used as key.
     * @param distance the distance function.
     */
    public static <T> LinearSearch<T, T> of(List<T> data, Distance<T> distance) {
        return new LinearSearch<>(data, data, distance);
    }

    @Override
    public String toString() {
        return String.format("Linear Search (%s)", distance);
    }

    /** Returns a neighbor object. */
    private Neighbor<K, V> neighbor(int i, double distance) {
        return new Neighbor<>(keys.get(i), data.get(i), i, distance);
    }

    @Override
    public Neighbor<K, V> nearest(K q) {
        // avoid Stream.reduce as we will create a lot of temporary Neighbor objects.
        double[] dist = keys.stream().parallel().mapToDouble(x -> distance.d(x, q)).toArray();

        int index = -1;
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < dist.length; i++) {
            if (dist[i] < nearest && q != keys.get(i)) {
                index = i;
                nearest = dist[i];
            }
        }

        return neighbor(index, nearest);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Neighbor<K, V>[] search(K q, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (k > data.size()) {
            throw new IllegalArgumentException("Neighbor array length is larger than the data size");
        }

        double[] dist = keys.stream().parallel().mapToDouble(x -> distance.d(x, q)).toArray();
        HeapSelect<NeighborBuilder<K, V>> heap = new HeapSelect<>(NeighborBuilder.class, k);
        for (int i = 0; i < k; i++) {
            heap.add(new NeighborBuilder<>());
        }

        for (int i = 0; i < dist.length; i++) {
            NeighborBuilder<K, V> datum = heap.peek();
            if (dist[i] < datum.distance && q != keys.get(i)) {
                datum.distance = dist[i];
                datum.index = i;
                datum.key = keys.get(i);
                datum.value = data.get(i);
                heap.heapify();
            }
        }

        heap.sort();
        return Arrays.stream(heap.toArray()).map(NeighborBuilder::toNeighbor).toArray(Neighbor[]::new);
    }

    @Override
    public void search(K q, double radius, List<Neighbor<K, V>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        double[] dist = keys.stream().parallel().mapToDouble(x -> distance.d(x, q)).toArray();
        for (int i = 0; i < dist.length; i++) {
            if (dist[i] <= radius && q != keys.get(i)) {
                neighbors.add(neighbor(i, dist[i]));
            }
        }
    }
}
