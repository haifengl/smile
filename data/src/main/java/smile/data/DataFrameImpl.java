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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import smile.data.type.*;
import smile.data.vector.*;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;

/**
 * A simple implementation of DataFrame that store columnar data in single machine's memory.
 *
 * @author Haifeng Li
 */
class DataFrameImpl implements DataFrame {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DataFrameImpl.class);

    /** DataFrame schema. */
    private StructType schema;
    /** The column vectors. */
    private List<BaseVector> columns;
    /** The number of rows. */
    private final int size;
    /** The lambda to retrieve a field value. */
    private final Getter[] getter;

    /** The lambda to retrieve a field value. */
    interface Getter {
        /** Returns the field of row i. */
        Object apply(int i);
    }

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
        this.schema = DataTypes.struct(fields);
        this.getter = IntStream.of(fields.length)
                .<Getter>mapToObj(j -> (i -> get(i, j)))
                .toArray(Getter[]::new);

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
    public <T> DataFrameImpl(List<T> data, Class<T> clazz) {
        this.size = data.size();
        this.columns = new ArrayList<>();

        try {
            BeanInfo info = Introspector.getBeanInfo(clazz);
            PropertyDescriptor[] props = info.getPropertyDescriptors();
            List<StructField> fields = Arrays.stream(props)
                    .filter(prop -> !prop.getName().equals("class"))
                    .map(this::field)
                    .collect(Collectors.toList());

            this.schema = DataTypes.struct(fields);
            for (PropertyDescriptor prop : props) {
                if (!prop.getName().equals("class")) {
                    String name = prop.getName();
                    Class<?> type = prop.getPropertyType();
                    Method read = prop.getReadMethod();

                    if (type == int.class) {
                        int[] values = new int[size];
                        for (int i = 0; i < size; i++) values[i] = (int) read.invoke(data.get(i));
                        IntVector vector = IntVector.of(name, values);
                        columns.add(vector);
                    } else if (type == double.class) {
                        double[] values = new double[size];
                        for (int i = 0; i < size; i++) values[i] = (double) read.invoke(data.get(i));
                        DoubleVector vector = DoubleVector.of(name, values);
                        columns.add(vector);
                    } else if (type == boolean.class) {
                        boolean[] values = new boolean[size];
                        for (int i = 0; i < size; i++) values[i] = (boolean) read.invoke(data.get(i));
                        BooleanVector vector = BooleanVector.of(name, values);
                        columns.add(vector);
                    } else if (type == short.class) {
                        short[] values = new short[size];
                        for (int i = 0; i < size; i++) values[i] = (short) read.invoke(data.get(i));
                        ShortVector vector = ShortVector.of(name, values);
                        columns.add(vector);
                    } else if (type == long.class) {
                        long[] values = new long[size];
                        for (int i = 0; i < size; i++) values[i] = (long) read.invoke(data.get(i));
                        LongVector vector = LongVector.of(name, values);
                        columns.add(vector);
                    } else if (type == float.class) {
                        float[] values = new float[size];
                        for (int i = 0; i < size; i++) values[i] = (float) read.invoke(data.get(i));
                        FloatVector vector = FloatVector.of(name, values);
                        columns.add(vector);
                    } else if (type == byte.class) {
                        byte[] values = new byte[size];
                        for (int i = 0; i < size; i++) values[i] = (byte) read.invoke(data.get(i));
                        ByteVector vector = ByteVector.of(name, values);
                        columns.add(vector);
                    } else if (type == char.class) {
                        char[] values = new char[size];
                        for (int i = 0; i < size; i++) values[i] = (char) read.invoke(data.get(i));
                        CharVector vector = CharVector.of(name, values);
                        columns.add(vector);
                    } else if (type.isEnum()) {
                        Object[] levels = type.getEnumConstants();
                        NominalScale scale = new NominalScale(Arrays.stream(levels).map(Object::toString).toArray(String[]::new));
                        schema.measure().put(name, scale);
                        if (levels.length < Byte.MAX_VALUE + 1) {
                            byte[] values = new byte[size];
                            for (int i = 0; i < size; i++) values[i] = (byte) ((Enum) read.invoke(data.get(i))).ordinal();
                            ByteVector vector = ByteVector.of(name, values);
                            columns.add(vector);
                        } else if (levels.length < Short.MAX_VALUE + 1) {
                            short[] values = new short[size];
                            for (int i = 0; i < size; i++) values[i] = (short) ((Enum) read.invoke(data.get(i))).ordinal();
                            ShortVector vector = ShortVector.of(name, values);
                            columns.add(vector);
                        } else {
                            int[] values = new int[size];
                            for (int i = 0; i < size; i++) values[i] = ((Enum) read.invoke(data.get(i))).ordinal();
                            IntVector vector = IntVector.of(name, values);
                            columns.add(vector);
                        }
                    } else {
                        Object[] values = new Object[size];
                        for (int i = 0; i < size; i++) values[i] = read.invoke(data.get(i));
                        Vector<?> vector = Vector.of(name, values);
                        columns.add(vector);
                    }
                }
            }
        } catch (java.beans.IntrospectionException ex) {
            logger.error("Failed to introspect a bean: ", ex);
            throw new RuntimeException(ex);
        } catch (ReflectiveOperationException ex) {
            logger.error("Failed to call property read method: ", ex);
            throw new RuntimeException(ex);
        }

        this.getter = IntStream.of(schema.length()).<Getter>mapToObj(j -> (i -> get(i, j))).toArray(Getter[]::new);
    }

    /** Returns the struct field of a property. */
    private StructField field(PropertyDescriptor prop) {
        return new StructField(prop.getName(), DataType.of(prop.getPropertyType()));
    }

    /**
     * Constructor.
     * @param data The data collection.
     */
    public DataFrameImpl(List<Tuple> data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty tuple collections");
        }

        this.size = data.size();
        this.schema = data.get(0).schema();
        StructField[] fields = schema.fields();
        this.columns = new ArrayList<>(fields.length);
        this.getter = IntStream.of(fields.length).<Getter>mapToObj(j -> (i -> columns.get(j).get(i))).toArray(Getter[]::new);

        for (int j = 0; j < fields.length; j++) {
            StructField field = fields[j];
            switch (field.type.id()) {
                case Integer: {
                    int[] values = new int[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getInt(j);
                    IntVector vector = IntVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Long: {
                    long[] values = new long[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getLong(j);
                    LongVector vector = LongVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Double: {
                    double[] values = new double[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getDouble(j);
                    DoubleVector vector = DoubleVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Float: {
                    float[] values = new float[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getFloat(j);
                    FloatVector vector = FloatVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Boolean: {
                    boolean[] values = new boolean[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getBoolean(j);
                    BooleanVector vector = BooleanVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Byte: {
                    byte[] values = new byte[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getByte(j);
                    ByteVector vector = ByteVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Short: {
                    short[] values = new short[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getShort(j);
                    ShortVector vector = ShortVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Char: {
                    char[] values = new char[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getChar(j);
                    CharVector vector = CharVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                default: {
                    Object[] values = new Object[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).get(j);
                    Vector vector = Vector.of(field.name, values);
                    columns.add(vector);
                }
            }
        }
    }

    /**
     * Constructor.
     * @param df The input DataFrame.
     * @param formula The formula that transforms from the input DataFrame.
     */
    public DataFrameImpl(DataFrame df, smile.data.formula.Formula formula) {
        this.size = df.size();
        this.schema = formula.bind(df.schema());
        StructField[] fields = schema.fields();
        this.columns = new ArrayList<>(fields.length);
        this.getter = IntStream.of(fields.length).<Getter>mapToObj(j -> (i -> get(i, j))).toArray(Getter[]::new);

        smile.data.formula.Factor[] factors = formula.factors();
        for (int j = 0; j < fields.length; j++) {
            StructField field = fields[j];
            if (factors[j].isColumn()) {
                columns.add(df.column(field.name));
            }
        }

        // We are done if all factors are raw columns.
        if (columns.size() == factors.length) return;

        List<Tuple> data = df.stream().map(formula::apply).collect(Collectors.toList());
        for (int j = 0; j < fields.length; j++) {
            StructField field = fields[j];
            if (formula.factors()[j].isColumn()) {
                continue;
            }

            switch (field.type.id()) {
                case Integer: {
                    int[] values = new int[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getInt(j);
                    IntVector vector = IntVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Long: {
                    long[] values = new long[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getLong(j);
                    LongVector vector = LongVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Double: {
                    double[] values = new double[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getDouble(j);
                    DoubleVector vector = DoubleVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Float: {
                    float[] values = new float[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getFloat(j);
                    FloatVector vector = FloatVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Boolean: {
                    boolean[] values = new boolean[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getBoolean(j);
                    BooleanVector vector = BooleanVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Byte: {
                    byte[] values = new byte[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getByte(j);
                    ByteVector vector = ByteVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Short: {
                    short[] values = new short[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getShort(j);
                    ShortVector vector = ShortVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                case Char: {
                    char[] values = new char[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getChar(j);
                    CharVector vector = CharVector.of(field.name, values);
                    columns.add(vector);
                    break;
                }

                default: {
                    Object[] values = new Object[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).get(j);
                    Vector vector = Vector.of(field.name, values);
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
    public Object get(int i, int j) {
        return columns.get(j).get(i);
    }

    @Override
    public Stream<Tuple> stream() {
        Spliterator<Tuple> spliterator = new DatasetSpliterator<>(this, Spliterator.ORDERED);
        return java.util.stream.StreamSupport.stream(spliterator, true);
    }

    @Override
    public BaseVector column(int i) {
        return columns.get(i);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Vector<T> vector(int i) {
        return (Vector<T>) columns.get(i);
    }

    @Override
    public BooleanVector booleanVector(int i) {
        return (BooleanVector) columns.get(i);
    }

    @Override
    public CharVector charVector(int i) {
        return (CharVector) columns.get(i);
    }

    @Override
    public ByteVector byteVector(int i) {
        return (ByteVector) columns.get(i);
    }

    @Override
    public ShortVector shortVector(int i) {
        return (ShortVector) columns.get(i);
    }

    @Override
    public IntVector intVector(int i) {
        return (IntVector) columns.get(i);
    }

    @Override
    public LongVector longVector(int i) {
        return (LongVector) columns.get(i);
    }

    @Override
    public FloatVector floatVector(int i) {
        return (FloatVector) columns.get(i);
    }

    @Override
    public DoubleVector doubleVector(int i) {
        return (DoubleVector) columns.get(i);
    }

    @Override
    public DataFrame select(int... cols) {
        List<BaseVector> sub = new ArrayList<>();
        for (int i = 0; i < cols.length; i++) {
            sub.add(columns.get(cols[i]));
        }

        DataFrameImpl df = new DataFrameImpl(sub);
        for (StructField field : df.schema.fields()) {
            Measure measure = schema.measure().get(field.name);
            if (measure != null) {
                df.schema.measure().put(field.name, measure);
            }
        }

        return df;
    }

    @Override
    public DataFrame drop(int... cols) {
        List<BaseVector> sub = new ArrayList<>(columns);
        List<BaseVector> drops = new ArrayList<>();
        for (int i = 0; i < cols.length; i++) {
            drops.add(columns.get(cols[i]));
        }
        sub.removeAll(drops);

        DataFrameImpl df = new DataFrameImpl(sub);
        for (StructField field : df.schema.fields()) {
            Measure measure = schema.measure().get(field.name);
            if (measure != null) {
                df.schema.measure().put(field.name, measure);
            }
        }

        return df;
    }

    @Override
    public DataFrame merge(DataFrame... dataframes) {
        List<BaseVector> all = new ArrayList<>(columns);
        for (DataFrame df : dataframes) {
            for (int i = 0; i < df.ncols(); i++) {
                all.add(df.column(i));
            }
        }

        DataFrameImpl df = new DataFrameImpl(all);
        df.schema.measure().putAll(schema.measure());
        for (DataFrame dataframe : dataframes) {
            df.schema.measure().putAll(dataframe.schema().measure());
        }

        return df;
    }

    @Override
    public DataFrame merge(BaseVector... vectors) {
        List<BaseVector> columns = new ArrayList<>(this.columns);
        Collections.addAll(columns, vectors);
        DataFrameImpl df = new DataFrameImpl(columns);
        df.schema.measure().putAll(schema.measure());
        return df;
    }

    @Override
    public Tuple get(int i) {
        return new DataFrameRow(i);
    }

    @Override
    public DenseMatrix toMatrix() {
        int nrows = nrows();
        int ncols = ncols();
        DataType[] types = types();

        DenseMatrix m = Matrix.of(nrows, ncols, 0);
        for (int j = 0; j < ncols; j++) {
            DataType type = types[j];
            switch (type.id()) {
                case Double: {
                    DoubleVector v = doubleVector(j);
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getDouble(i));
                    break;
                }

                case Integer: {
                    IntVector v = intVector(j);
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getInt(i));
                    break;
                }

                case Float: {
                    FloatVector v = floatVector(j);
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getFloat(i));
                    break;
                }

                case Long: {
                    LongVector v = longVector(j);
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getLong(i));
                    break;
                }

                case Boolean: {
                    BooleanVector v = booleanVector(j);
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getDouble(i));
                    break;
                }

                case Byte: {
                    ByteVector v = byteVector(j);
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getByte(i));
                    break;
                }

                case Short: {
                    ShortVector v = shortVector(j);
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getShort(i));
                    break;
                }

                case Char: {
                    CharVector v = charVector(j);
                    for (int i = 0; i < nrows; i++) m.set(i, j, v.getChar(i));
                    break;
                }

                case String: {
                    Vector<String> v = vector(j);
                    for (int i = 0; i < nrows; i++) {
                        String s = v.get(i);
                        m.set(i, j, s == null ? Double.NaN : Double.valueOf(s));
                    }
                    break;
                }

                case Object: {
                    Class clazz = ((ObjectType) type).getObjectClass();
                    if (clazz == Boolean.class) {
                        Vector<Boolean> v = vector(j);
                        for (int i = 0; i < nrows; i++) {
                            Boolean b = v.get(i);
                            if (b != null)
                                m.set(i, j, b.booleanValue() ? 1 : 0);
                            else
                                m.set(i, j, Double.NaN);
                        }
                    } else if (Number.class.isAssignableFrom(clazz)) {
                        Vector<?> v = vector(j);
                        for (int i = 0; i < nrows; i++) m.set(i, j, v.getDouble(i));
                    } else {
                        throw new UnsupportedOperationException(String.format("DataFrame.toMatrix() doesn't support type %s", type));
                    }
                    break;
                }

                default:
                    throw new UnsupportedOperationException(String.format("DataFrame.toMatrix() doesn't support type %s", type));
            }
        }

        return m;
    }

    class DataFrameRow implements Tuple {
        /** Row index. */
        private int i;

        DataFrameRow(int i) {
            this.i = i;
        }

        @Override
        public StructType schema() {
            return schema;
        }

        @Override
        public Object get(int j) {
            return DataFrameImpl.this.get(i, j);
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
            return columns.get(j).getByte(i);
        }

        @Override
        public short getShort(int j) {
            return columns.get(j).getShort(i);
        }

        @Override
        public int getInt(int j) {
            return columns.get(j).getInt(i);
        }

        @Override
        public long getLong(int j) {
            return columns.get(j).getLong(i);
        }

        @Override
        public float getFloat(int j) {
            return columns.get(j).getFloat(i);
        }

        @Override
        public double getDouble(int j) {
            return columns.get(j).getDouble(i);
        }

        @Override
        public String toString() {
            return schema.toString(this);
        }
    }
}
