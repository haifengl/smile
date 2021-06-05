/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Stream;
import smile.math.matrix.Matrix;

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
     * @return true if the dataset is distributed over multiple machines.
     */
    default boolean distributed() {
        return false;
    }

    /**
     * Returns the number of elements in this collection.
     * @return the number of elements in this collection.
     */
    int size();

    /**
     * Returns true if the dataset is empty.
     * @return true if the dataset is empty.
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the element at the specified position in this dataset.
     * @param i the index of the element to be returned.
     * @return the i-th element.
     */
    T get(int i);

    /**
     * Returns the element at the specified position in this dataset.
     * @param i the index of the element to be returned.
     * @return the i-th element.
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
     * Returns the <code>List</code> of data items.
     * @return the <code>List</code> of data items.
     */
    default List<T> toList() {
        return stream().collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns the string representation of the dataset.
     * @param numRows the number of rows to show.
     * @return the string representation of the dataset.
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

    /**
     * Returns a default implementation of Dataset from a collection.
     * @param data the data collection.
     * @param <T> the type of input elements.
     * @return the dataset.
     */
    static <T> Dataset<T> of(Collection<T> data) {
        return new DatasetImpl<>(data);
    }

    /** Stream collectors. */
    interface Collectors {
        /**
         * Returns a stream collector that accumulates elements into a Dataset.
         *
         * @param <T> the type of input elements.
         * @return the stream collector.
         */
        static <T> Collector<T, List<T>, Dataset<T>> toDataset() {
            return Collector.of(
                    // supplier
                    ArrayList::new,
                    // accumulator
                    List::add,
                    // combiner
                    (c1, c2) -> {
                        c1.addAll(c2);
                        return c1;
                    },
                    // finisher
                    Dataset::of
            );
        }

        /**
         * Returns a stream collector that accumulates elements into a Matrix.
         *
         * @return the stream collector.
         */
        static Collector<double[], List<double[]>, Matrix> toMatrix() {
            return Collector.of(
                    // supplier
                    ArrayList::new,
                    // accumulator
                    List::add,
                    // combiner
                    (c1, c2) -> {
                        c1.addAll(c2);
                        return c1;
                    },
                    // finisher
                    (container) -> Matrix.of(container.toArray(new double[container.size()][]))
            );
        }
    }
}
