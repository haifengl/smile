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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import smile.data.formula.Term;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.*;
import smile.data.vector.*;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;

/**
 * A data frame with a new index instead of the default [0, n) row index.
 *
 * @author Haifeng Li
 */
class IndexDataFrame implements DataFrame {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IndexDataFrame.class);

    /** The underlying data frame. */
    private DataFrame df;
    /** The row index. */
    private int[] index;

    /**
     * Constructor.
     * @param df The underlying data frame.
     * @param index The row index.
     */
    public IndexDataFrame(DataFrame df, int[] index) {
        this.df = df;
        this.index = index;
    }

    @Override
    public StructType schema() {
        return df.schema();
    }

    @Override
    public String toString() {
        return toString(10, true);
    }

    @Override
    public int columnIndex(String name) {
        return df.columnIndex(name);
    }

    @Override
    public int size() {
        return df.size();
    }

    @Override
    public int ncols() {
        return df.ncols();
    }

    @Override
    public Object get(int i, int j) {
        return df.get(i, j);
    }

    @Override
    public Stream<Tuple> stream() {
        return Arrays.stream(index).mapToObj(i -> df.get(i));
    }

    @Override
    public BaseVector column(int i) {
        return df.column(i);
    }

    @Override
    public <T> Vector<T> vector(int i) {
        return df.vector(i);
    }

    @Override
    public BooleanVector booleanVector(int i) {
        return df.booleanVector(i);
    }

    @Override
    public CharVector charVector(int i) {
        return df.charVector(i);
    }

    @Override
    public ByteVector byteVector(int i) {
        return df.byteVector(i);
    }

    @Override
    public ShortVector shortVector(int i) {
        return df.shortVector(i);
    }

    @Override
    public IntVector intVector(int i) {
        return df.intVector(i);
    }

    @Override
    public LongVector longVector(int i) {
        return df.longVector(i);
    }

    @Override
    public FloatVector floatVector(int i) {
        return df.floatVector(i);
    }

    @Override
    public DoubleVector doubleVector(int i) {
        return df.doubleVector(i);
    }

    @Override
    public DataFrame select(int... cols) {
        return new IndexDataFrame(df.select(cols), index);
    }

    @Override
    public DataFrame drop(int... cols) {
        return new IndexDataFrame(df.drop(cols), index);
    }

    /** Returns a new data frame with regular index. */
    private DataFrame rebase() {
        return DataFrame.of(stream().collect(Collectors.toList()));
    }

    @Override
    public DataFrame merge(DataFrame... dataframes) {
        for (DataFrame df : dataframes) {
            if (df.size() != size()) {
                throw new IllegalArgumentException("Merge data frames with different size: " + size() + " vs " + df.size());
            }
        }

        return rebase().merge(dataframes);
    }

    @Override
    public DataFrame merge(BaseVector... vectors) {
        for (BaseVector vector : vectors) {
            if (vector.size() != size()) {
                throw new IllegalArgumentException("Merge data frames with different size: " + size() + " vs " + vector.size());
            }
        }

        return rebase().merge(vectors);
    }

    @Override
    public DataFrame union(DataFrame... dataframes) {
        for (DataFrame df : dataframes) {
            if (!schema().equals(df.schema())) {
                throw new IllegalArgumentException("Union data frames with different schema: " + schema() + " vs " + df.schema());
            }
        }

        return rebase().union(dataframes);
    }

    @Override
    public Tuple get(int i) {
        return df.get(index[i]);
    }

    @Override
    public DenseMatrix toMatrix() {
        // Although clean, this is not optimal in term of performance.
        return rebase().toMatrix();
    }
}
