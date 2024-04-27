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

package smile.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Stream;
import smile.math.MathEx;

/**
 * An immutable collection of data objects.
 *
 * @param <D> the data type.
 * @param <T> the target type.
 * 
 * @author Haifeng Li
 */
public interface Dataset<D, T> extends Iterable<SampleInstance<D, T>> {
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
     * Returns the instance at the specified index.
     * @param i the index of the instance to be returned.
     * @return the i-th instance.
     */
    SampleInstance<D, T> get(int i);

    /**
     * Returns the index at the specified index.  For Scala's convenience.
     * @param i the index of the instance to be returned.
     * @return the i-th instance.
     */
    default SampleInstance<D, T> apply(int i) {
        return get(i);
    }

    /**
     * Returns a (possibly parallel) Stream with this collection as its source.
     *
     * @return a (possibly parallel) Stream with this collection as its source.
     */
    Stream<SampleInstance<D, T>> stream();

    /**
     * Returns an iterator of mini-batches.
     * @param size the batch size.
     * @return an iterator of mini-batches.
     */
    default Iterator<List<SampleInstance<D, T>>> batch(int size) {
        return new Iterator<>() {
            final int[] permutation = MathEx.permutate(size());
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < size();
            }

            @Override
            public List<SampleInstance<D, T>> next() {
                int length = Math.min(size, size() - i);
                ArrayList<SampleInstance<D, T>> batch = new ArrayList<>(length);
                for (int j = 0; j < length; j++, i++) {
                    batch.add(get(permutation[i]));
                }
                return batch;
            }
        };
    }

    /**
     * Returns the <code>List</code> of data items.
     * @return the <code>List</code> of data items.
     */
    default List<SampleInstance<D, T>> toList() {
        return stream().collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns the string representation of the dataset.
     * @param numRows the number of rows to show.
     * @return the string representation of the dataset.
     */
    default String toString(int numRows) {
        StringBuilder sb = new StringBuilder();
        String top = stream().limit(numRows).map(Object::toString).collect(java.util.stream.Collectors.joining(System.lineSeparator()));
        sb.append(top);

        int rest = size() - numRows;
        if (rest > 0) {
            String rowsString = (rest == 1) ? "row" : "rows";
            sb.append(String.format("%n%d more %s...%n", rest, rowsString));
        }

        return sb.toString();
    }

    /**
     * Returns a default implementation of Dataset from a collection.
     * @param instances the sample instances.
     * @param <D> the data type.
     * @param <T> the target type.
     * @return the dataset.
     */
    static <D, T> Dataset<D, T> of(Collection<SampleInstance<D, T>> instances) {
        return new DatasetImpl<>(instances);
    }

    /**
     * Returns a default implementation of Dataset from a collection.
     * @param data the sample data.
     * @param target the sample targets.
     * @param <D> the data type.
     * @param <T> the target type.
     * @return the dataset.
     */
    static <D, T> Dataset<D, T> of(List<D> data, List<T> target) {
        List<SampleInstance<D, T>> instances = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            instances.add(new SampleInstance<>(data.get(i), target.get(i)));
        }
        return new DatasetImpl<>(instances);
    }

    /**
     * Returns a default implementation of Dataset from a collection.
     * @param data the sample data.
     * @param target the sample targets.
     * @param <D> the data type.
     * @param <T> the target type.
     * @return the dataset.
     */
    static <D, T> Dataset<D, T> of(D[] data, T[] target) {
        List<SampleInstance<D, T>> instances = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            instances.add(new SampleInstance<>(data[i], target[i]));
        }
        return new DatasetImpl<>(instances);
    }

    /**
     * Returns a default implementation of Dataset from a collection.
     * @param data the sample data.
     * @param target the sample targets.
     * @param <D> the data type.
     * @return the dataset.
     */
    static <D> Dataset<D, Integer> of(D[] data, int[] target) {
        List<SampleInstance<D, Integer>> instances = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            instances.add(new SampleInstance<>(data[i], target[i]));
        }
        return new DatasetImpl<>(instances);
    }

    /**
     * Returns a default implementation of Dataset from a collection.
     * @param data the sample data.
     * @param target the sample targets.
     * @param <D> the data type.
     * @return the dataset.
     */
    static <D> Dataset<D, Float> of(D[] data, float[] target) {
        List<SampleInstance<D, Float>> instances = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            instances.add(new SampleInstance<>(data[i], target[i]));
        }
        return new DatasetImpl<>(instances);
    }

    /**
     * Returns a default implementation of Dataset from a collection.
     * @param data the sample data.
     * @param target the sample targets.
     * @param <D> the data type.
     * @return the dataset.
     */
    static <D> Dataset<D, Double> of(D[] data, double[] target) {
        List<SampleInstance<D, Double>> instances = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            instances.add(new SampleInstance<>(data[i], target[i]));
        }
        return new DatasetImpl<>(instances);
    }

    /**
     * Returns a stream collector that accumulates elements into a Dataset.
     *
     * @param <D> the data type.
     * @param <T> the target type.
     * @return the stream collector.
     */
    static <D, T> Collector<SampleInstance<D, T>, List<SampleInstance<D, T>>, Dataset<D, T>> collector() {
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
}
