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
                (container) -> Matrix.newInstance(container.toArray(new double[container.size()][]))
        );
    }
}
