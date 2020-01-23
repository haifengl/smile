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

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

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
 * @param <T> the type of data objects.
 *
 * @author Haifeng Li
 */
public class LinearSearch<T> implements NearestNeighborSearch<T,T>, KNNSearch<T,T>, RNNSearch<T,T>, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The dataset of search space.
     */
    private T[] data;
    /**
     * The distance function used to determine nearest neighbors.
     */
    private Distance<T> distance;

    /**
     * Constructor. By default, query object self will be excluded from search.
     */
    public LinearSearch(T[] dataset, Distance<T> distance) {
        this.data = dataset;
        this.distance = distance;
    }

    @Override
    public String toString() {
        return String.format("Linear Search (%s)", distance);
    }

    @Override
    public Neighbor<T, T> nearest(T q) {
        // avoid Stream.reduce as we will create a lot of temporary Neighbor objects.
        double[] dist = Arrays.stream(data).parallel().mapToDouble(x -> distance.d(q, x)).toArray();

        int index = -1;
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < dist.length; i++) {
            if (dist[i] < nearest && q != data[i]) {
                index = i;
                nearest = dist[i];
            }
        }

        return Neighbor.of(data[index], index, nearest);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Neighbor<T, T>[] knn(T q, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (k > data.length) {
            throw new IllegalArgumentException("Neighbor array length is larger than the dataset size");
        }

        double[] dist = Arrays.stream(data).parallel().mapToDouble(x -> distance.d(q, x)).toArray();
        HeapSelect<NeighborBuilder<T,T>> heap = new HeapSelect<>(k);
        for (int i = 0; i < k; i++) {
            heap.add(new NeighborBuilder<>());
        }

        for (int i = 0; i < dist.length; i++) {
            NeighborBuilder<T,T> datum = heap.peek();
            if (dist[i] < datum.distance && q != data[i]) {
                datum.distance = dist[i];
                datum.index = i;
                datum.key = data[i];
                datum.value = data[i];
                heap.heapify();
            }
        }

        heap.sort();
        return Arrays.stream(heap.toArray()).map(NeighborBuilder::toNeighbor).toArray(Neighbor[]::new);
    }

    @Override
    public void range(T q, double radius, List<Neighbor<T, T>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        double[] dist = Arrays.stream(data).parallel().mapToDouble(x -> distance.d(q, x)).toArray();
        for (int i = 0; i < data.length; i++) {
            if (dist[i] <= radius && q != data[i]) {
                neighbors.add(Neighbor.of(data[i], i, dist[i]));
            }
        }
    }
}
