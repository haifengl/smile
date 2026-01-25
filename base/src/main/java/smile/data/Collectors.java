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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import smile.data.type.StructType;
import smile.tensor.DenseMatrix;
import smile.tensor.Matrix;
import static smile.tensor.ScalarType.*;


/** Stream collectors for Dataset, DataFrame, and Matrix. */
public interface Collectors {
    /**
     * Returns a stream collector that accumulates elements into a Dataset.
     *
     * @param <D> the data type.
     * @param <T> the target type.
     * @return the stream collector.
     */
    static <D, T> Collector<SampleInstance<D, T>, List<SampleInstance<D, T>>, Dataset<D, T>> toDataset() {
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
     * Returns a stream collector that accumulates objects into a DataFrame.
     *
     * @param <T>   the type of input elements to the reduction operation
     * @param clazz The class type of elements.
     * @return the stream collector.
     */
    static <T> Collector<T, List<T>, DataFrame> toDataFrame(Class<T> clazz) {
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
                (container) -> DataFrame.of(clazz, container)
        );
    }

    /**
     * Returns a stream collector that accumulates tuples into a DataFrame.
     *
     * @param schema the schema of data frame.
     * @return the stream collector.
     */
    static Collector<Tuple, List<Tuple>, DataFrame> toDataFrame(StructType schema) {
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
                (container) -> DataFrame.of(schema, container)
        );
    }

    /**
     * Returns a stream collector that accumulates tuples into a Matrix.
     *
     * @return the stream collector.
     */
    static Collector<Tuple, List<Tuple>, Matrix> toMatrix() {
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
                (container) -> {
                    if (container.isEmpty()) {
                        throw new IllegalArgumentException("Empty list of tuples");
                    }
                    int nrow = container.size();
                    int ncol = container.getFirst().length();
                    Matrix m = DenseMatrix.zeros(Float64, nrow, ncol);
                    for (int i = 0; i < nrow; i++) {
                        for (int j = 0; j < ncol; j++) {
                            m.set(i, j, container.get(i).getDouble(j));
                        }
                    }
                    return m;
                }
        );
    }
}
