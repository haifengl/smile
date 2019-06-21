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

package smile.data;

import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * An immutable collection of data objects.
 *
 * @param <T> the type of data objects.
 * 
 * @author Haifeng Li
 */
public interface Dataset<T> {
    /**
     * Returns true if the dataset is distributed over multiple machines.
     */
    default boolean distributed() {
        return false;
    }

    /**
     * Returns the number of elements in this collection.
     */
    int size();

    /** Returns true if the dataset is empty. */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the element at the specified position in this dataset.
     * @param i the index of the element to be returned.
     */
    T get(int i);

    /**
     * Returns the element at the specified position in this dataset.
     * @param i the index of the element to be returned.
     */
    default T apply(int i) {
        return get(i);
    }

    /**
     * Returns a (possibly parallel) Stream with this collection as its source.
     *
     * @return a (possibly parallel) Stream with this collection as its source.
     */
    Stream<T> stream();

    /**
     * Returns the string representation of the row.
     * @param numRows Number of rows to show
     */
    default String toString(int numRows) {
        StringBuilder sb = new StringBuilder();
        String top = stream().limit(numRows).map(Object::toString).collect(java.util.stream.Collectors.joining("\n"));
        sb.append(top);

        int rest = size() - numRows;
        if (rest > 0) {
            String rowsString = (rest == 1) ? "row" : "rows";
            sb.append(String.format("\n%d more %s...\n", rest, rowsString));
        }

        return sb.toString();
    }

    /** Returns a default implementation of Dataset from a collection. */
    static <T> Dataset<T> of(Collection<T> data) {
        return new DatasetImpl<>(data);
    }

    /**
     * Returns a stream collector that accumulates elements into a Dataset.
     *
     * @param <T> the type of input elements to the reduction operation
     */
    static <T> Collector<T, List<T>, Dataset<T>> toDataset() {
        return Collector.of(
                // supplier
                () -> new ArrayList<T>(),
                // accumulator
                (container, t) -> container.add(t),
                // combiner
                (c1, c2) -> { c1.addAll(c2); return c1; },
                // finisher
                (container) -> Dataset.of(container)
        );
    }

    /**
     * Returns a stream collector that accumulates elements into a Matrix.
     */
    static <T> Collector<double[], List<double[]>, DenseMatrix> toMatrix() {
        return Collector.of(
                // supplier
                () -> new ArrayList<double[]>(),
                // accumulator
                (container, t) -> container.add(t),
                // combiner
                (c1, c2) -> { c1.addAll(c2); return c1; },
                // finisher
                (container) -> Matrix.of(container.toArray(new double[container.size()][]))
        );
    }
}
