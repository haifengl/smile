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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import smile.data.vector.*;
import smile.math.matrix.Matrix;

/**
 * A simple implementation of DataFrame that store columnar data in single machine's memory.
 *
 * @author Haifeng Li
 */
class DataFrameImpl implements DataFrame {
    /** The column vectors. */
    private List<BaseVector> vectors;
    /** The column names. */
    private List<String> names;
    /** The column types. */
    private List<Class> types;
    /** The column name -> index map. */
    private Map<String, Integer> columnIndex;
    /** The number of rows. */
    private final int size;

    /**
     * Constructor.
     * @param data The underlying data collection.
     */
    public DataFrameImpl(Collection<BaseVector> data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty collection of columns");
        }

        this.vectors = new ArrayList<>(data);
        this.names = data.stream().map(v -> v.name()).collect(Collectors.toList());
        this.types = data.stream().map(v -> v.type()).collect(Collectors.toList());

        Set<String> set = new HashSet<>();
        for (BaseVector v : data) {
            if (!set.add(v.name())) {
                throw new IllegalArgumentException(String.format("Duplicated column name: %s", v.name()));
            }
        }

        BaseVector first = data.iterator().next();
        this.size = first.size();
        for (BaseVector v : data) {
            if (v.size() != first.size()) {
                throw new IllegalArgumentException(String.format("Column %s size %d != %d", v.name(), v.size(), first.size()));
            }
        }

        initColumnIndex();
    }

    /**
     * Constructor.
     * @param data The data collection.
     * @param clazz The class type of elements.
     * @param <T> The type of elements.
     */
    @SuppressWarnings("unchecked")
    public <T> DataFrameImpl(Collection<T> data, Class<T> clazz) {
        this.size = data.size();
        this.vectors = new ArrayList<>();
        this.names = new ArrayList<>();
        this.types = new ArrayList<>();

        Field[] fields = clazz.getFields();
        for (Field field : fields) {
            String name = field.getName();
            names.add(name);

            Class<?> type = field.getType();
            types.add(type);

            if (type == int.class) {
                int[] values = data.stream().mapToInt(o -> {
                    try {
                        return field.getInt(o);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray();
                IntVector vector = IntVector.of(name, values);
                vectors.add(vector);
            } else if (type == long.class) {
                long[] values = data.stream().mapToLong(o -> {
                    try {
                        return field.getLong(o);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray();
                LongVector vector = LongVector.of(name, values);
                vectors.add(vector);
            } else if (type == double.class) {
                double[] values = data.stream().mapToDouble(o -> {
                    try {
                        return field.getDouble(o);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray();
                DoubleVector vector = DoubleVector.of(name, values);
                vectors.add(vector);
            } else {
                T[] values = (T[]) data.stream().map(o -> {
                    try {
                        return (T) field.get(o);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray();
                Vector<T> vector = Vector.of(name, values);
                vectors.add(vector);
            }
        }

        BeanInfo info;
        try {
            info = Introspector.getBeanInfo(clazz);
        } catch (java.beans.IntrospectionException ex) {
            throw new RuntimeException(ex);
        }

        PropertyDescriptor[] props = info.getPropertyDescriptors();

        for (PropertyDescriptor prop : props) {
            if (!prop.getName().equals("class")) {
                String name = prop.getName();
                names.add(name);

                Class<?> type = prop.getPropertyType();
                types.add(type);

                if (type == int.class) {
                    Method read = prop.getReadMethod();
                    int[] values = data.stream().mapToInt(o -> {
                        try {
                            return (Integer) read.invoke(o);
                        } catch (ReflectiveOperationException ex) {
                            throw new RuntimeException(ex);
                        }
                    }).toArray();
                    IntVector vector = IntVector.of(name, values);
                    vectors.add(vector);
                } else if (type == long.class) {
                    Method read = prop.getReadMethod();
                    long[] values = data.stream().mapToLong(o -> {
                        try {
                            return (Long) read.invoke(o);
                        } catch (ReflectiveOperationException ex) {
                            throw new RuntimeException(ex);
                        }
                    }).toArray();
                    LongVector vector = LongVector.of(name, values);
                    vectors.add(vector);
                } else if (type == double.class) {
                    Method read = prop.getReadMethod();
                    double[] values = data.stream().mapToDouble(o -> {
                        try {
                            return (Double) read.invoke(o);
                        } catch (ReflectiveOperationException ex) {
                            throw new RuntimeException(ex);
                        }
                    }).toArray();
                    DoubleVector vector = DoubleVector.of(name, values);
                    vectors.add(vector);
                } else {
                    Method read = prop.getReadMethod();
                    T[] values = (T[]) data.stream().map(o -> {
                        try {
                            return (T) read.invoke(o);
                        } catch (ReflectiveOperationException ex) {
                            throw new RuntimeException(ex);
                        }
                    }).toArray();
                    Vector<T> vector = Vector.of(name, values);
                    vectors.add(vector);
                }
            }
        }

        initColumnIndex();
    }

    /** Initialize column index. */
    private void initColumnIndex() {
        columnIndex = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            columnIndex.put(names.get(i), i);
        }
    }

    @Override
    public String toString() {
        return toString(10, true);
    }

    @Override
    public String[] names() {
        return names.toArray(new String[names.size()]);
    }

    @Override
    public Class[] types() {
        return types.toArray(new Class[types.size()]);
    }

    @Override
    public int columnIndex(String name) {
        return columnIndex.get(name);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int ncols() {
        return names.size();
    }

    @Override
    public Stream<Tuple> stream() {
        Spliterator<Tuple> spliterator = new DatasetSpliterator<>(this, Spliterator.ORDERED);
        return java.util.stream.StreamSupport.stream(spliterator, true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Vector<T> column(int i) {
        return (Vector<T>) vectors.get(i);
    }

    @Override
    public IntVector intColumn(int i) {
        return (IntVector) vectors.get(i);
    }

    @Override
    public LongVector longColumn(int i) {
        return (LongVector) vectors.get(i);
    }

    @Override
    public DoubleVector doubleColumn(int i) {
        return (DoubleVector) vectors.get(i);
    }

    @Override
    public DataFrame select(int... cols) {
        List<BaseVector> sub = new ArrayList<>();
        for (int i = 0; i < cols.length; i++) {
            sub.add(vectors.get(cols[i]));
        }
        return new DataFrameImpl(sub);
    }

    @Override
    public DataFrame drop(int... cols) {
        List<BaseVector> sub = new ArrayList<>(vectors);
        List<BaseVector> drops = new ArrayList<>();
        for (int i = 0; i < cols.length; i++) {
            drops.add(vectors.get(cols[i]));
        }
        sub.removeAll(drops);
        return new DataFrameImpl(sub);
    }

    @Override
    public DataFrame bind(DataFrame... dataframes) {
        List<BaseVector> all = new ArrayList<>(vectors);
        for (DataFrame df : dataframes) {
            for (int i = 0; i < df.ncols(); i++) {
                all.add(df.column(i));
            }
        }
        return new DataFrameImpl(all);
    }

    @Override
    public DataFrame bind(BaseVector... vectors) {
        List<BaseVector> all = new ArrayList<>(this.vectors);
        Collections.addAll(all, vectors);
        return new DataFrameImpl(all);
    }

    @Override
    public Tuple get(int i) {
        return new DataFrameRow(i);
    }

    @Override
    public Matrix toMatrix() {
        throw new UnsupportedOperationException();
    }

    class DataFrameRow implements Tuple {
        /** Row index. */
        int i;

        DataFrameRow(int i) {
            this.i = i;
        }

        @Override
        public int size() {
            return vectors.size();
        }

        @Override
        public Object get(int j) {
            return vectors.get(j).get(i);
        }

        @Override
        public int getInt(int j) {
            return ((IntVector) vectors.get(j)).getInt(i);
        }

        @Override
        public long getLong(int j) {
            return ((LongVector) vectors.get(j)).getLong(i);
        }

        @Override
        public double getDouble(int j) {
            return ((DoubleVector) vectors.get(j)).getDouble(i);
        }

        @Override
        public int fieldIndex(String name) {
            return columnIndex(name);
        }

        @Override
        public String toString() {
            return toString(",");
        }
    }
}
