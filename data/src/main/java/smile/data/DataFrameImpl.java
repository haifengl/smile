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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.*;
import smile.math.matrix.Matrix;

/**
 * A simple implementation of DataFrame that store columnar data in single machine's memory.
 *
 * @author Haifeng Li
 */
class DataFrameImpl implements DataFrame {
    /** DataFrame schema. */
    private StructType schema;
    /** The column vectors. */
    private List<BaseVector> columns;
    /** The number of rows. */
    private final int size;

    /**
     * Constructor.
     * @param columns The columns of data frame.
     */
    public DataFrameImpl(Collection<BaseVector> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Empty collection of columns");
        }

        this.columns = new ArrayList<>(columns);

        StructField[] fields = columns.stream()
                .map(v -> new StructField(v.name(), v.type()))
                .collect(Collectors.toList())
                .toArray(new StructField[columns.size()]);
        this.schema = new StructType(fields);

        Set<String> set = new HashSet<>();
        for (BaseVector v : columns) {
            if (!set.add(v.name())) {
                throw new IllegalArgumentException(String.format("Duplicated column name: %s", v.name()));
            }
        }

        BaseVector first = columns.iterator().next();
        this.size = first.size();
        for (BaseVector v : columns) {
            if (v.size() != first.size()) {
                throw new IllegalArgumentException(String.format("Column %s size %d != %d", v.name(), v.size(), first.size()));
            }
        }
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
        this.columns = new ArrayList<>();
        List<StructField> structFields = new ArrayList<>();

        Field[] fields = clazz.getFields();
        for (Field field : fields) {
            String name = field.getName();
            Class<?> type = field.getType();

            if (type == int.class) {
                int[] values = data.stream().mapToInt(o -> {
                    try {
                        return field.getInt(o);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray();
                IntVector vector = IntVector.of(name, values);
                columns.add(vector);
                structFields.add(new StructField(name, DataTypes.IntegerType));
            } else if (type == long.class) {
                long[] values = data.stream().mapToLong(o -> {
                    try {
                        return field.getLong(o);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray();
                LongVector vector = LongVector.of(name, values);
                columns.add(vector);
                structFields.add(new StructField(name, DataTypes.LongType));
            } else if (type == double.class) {
                double[] values = data.stream().mapToDouble(o -> {
                    try {
                        return field.getDouble(o);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray();
                DoubleVector vector = DoubleVector.of(name, values);
                columns.add(vector);
                structFields.add(new StructField(name, DataTypes.DoubleType));
            } else {
                T[] values = (T[]) data.stream().map(o -> {
                    try {
                        return (T) field.get(o);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray();
                Vector<T> vector = Vector.of(name, values);
                columns.add(vector);
                if (type == String.class) {
                    structFields.add(new StructField(name, DataTypes.StringType));
                } else if (type == LocalDate.class) {
                    structFields.add(new StructField(name, DataTypes.DateType));
                } else if (type == LocalDateTime.class) {
                    structFields.add(new StructField(name, DataTypes.DateTimeType));
                }
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
                Class<?> type = prop.getPropertyType();

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
                    columns.add(vector);
                    structFields.add(new StructField(name, DataTypes.IntegerType));
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
                    columns.add(vector);
                    structFields.add(new StructField(name, DataTypes.LongType));
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
                    columns.add(vector);
                    structFields.add(new StructField(name, DataTypes.DoubleType));
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
                    columns.add(vector);
                }
            }
        }
    }

    @Override
    public StructType schema() {
        return schema;
    }

    @Override
    public String toString() {
        return toString(10, true);
    }

    @Override
    public int columnIndex(String name) {
        return schema.fieldIndex(name);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int ncols() {
        return columns.size();
    }

    @Override
    public Stream<Tuple> stream() {
        Spliterator<Tuple> spliterator = new DatasetSpliterator<>(this, Spliterator.ORDERED);
        return java.util.stream.StreamSupport.stream(spliterator, true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Vector<T> column(int i) {
        return (Vector<T>) columns.get(i);
    }

    @Override
    public IntVector intColumn(int i) {
        return (IntVector) columns.get(i);
    }

    @Override
    public LongVector longColumn(int i) {
        return (LongVector) columns.get(i);
    }

    @Override
    public DoubleVector doubleColumn(int i) {
        return (DoubleVector) columns.get(i);
    }

    @Override
    public DataFrame select(int... cols) {
        List<BaseVector> sub = new ArrayList<>();
        for (int i = 0; i < cols.length; i++) {
            sub.add(columns.get(cols[i]));
        }
        return new DataFrameImpl(sub);
    }

    @Override
    public DataFrame drop(int... cols) {
        List<BaseVector> sub = new ArrayList<>(columns);
        List<BaseVector> drops = new ArrayList<>();
        for (int i = 0; i < cols.length; i++) {
            drops.add(columns.get(cols[i]));
        }
        sub.removeAll(drops);
        return new DataFrameImpl(sub);
    }

    @Override
    public DataFrame bind(DataFrame... dataframes) {
        List<BaseVector> all = new ArrayList<>(columns);
        for (DataFrame df : dataframes) {
            for (int i = 0; i < df.ncols(); i++) {
                all.add(df.column(i));
            }
        }
        return new DataFrameImpl(all);
    }

    @Override
    public DataFrame bind(BaseVector... vectors) {
        List<BaseVector> columns = new ArrayList<>(this.columns);
        Collections.addAll(columns, vectors);
        return new DataFrameImpl(columns);
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
        public StructType schema() {
            return schema;
        }

        @Override
        public int size() {
            return columns.size();
        }

        @Override
        public Object get(int j) {
            return columns.get(j).get(i);
        }

        @Override
        public boolean getBoolean(int j) {
            return ((BooleanVector) columns.get(j)).getBoolean(i);
        }

        @Override
        public char getChar(int j) {
            return ((CharVector) columns.get(j)).getChar(i);
        }

        @Override
        public byte getByte(int j) {
            return ((ByteVector) columns.get(j)).getByte(i);
        }

        @Override
        public short getShort(int j) {
            return ((ShortVector) columns.get(j)).getShort(i);
        }

        @Override
        public int getInt(int j) {
            return ((IntVector) columns.get(j)).getInt(i);
        }

        @Override
        public long getLong(int j) {
            return ((LongVector) columns.get(j)).getLong(i);
        }

        @Override
        public float getFloat(int j) {
            return ((FloatVector) columns.get(j)).getFloat(i);
        }

        @Override
        public double getDouble(int j) {
            return ((DoubleVector) columns.get(j)).getDouble(i);
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
