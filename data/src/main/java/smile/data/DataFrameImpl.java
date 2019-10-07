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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import smile.data.measure.NominalScale;
import smile.data.type.*;
import smile.data.vector.*;
import smile.data.vector.Vector;
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
                .map(column -> column.field())
                .toArray(StructField[]::new);
        this.schema = DataTypes.struct(fields);

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
            StructField[] fields = Arrays.stream(props)
                    .filter(prop -> !prop.getName().equals("class"))
                    .map(this::field)
                    .toArray(StructField[]::new);

            this.schema = DataTypes.struct(fields);
            for (PropertyDescriptor prop : props) {
                if (!prop.getName().equals("class")) {
                    String name = prop.getName();
                    Class<?> type = prop.getPropertyType();
                    Method read = prop.getReadMethod();
                    StructField field = Arrays.stream(fields).filter(f -> f.name.equals(name)).findFirst().get();

                    if (type == int.class) {
                        int[] values = new int[size];
                        for (int i = 0; i < size; i++) values[i] = (int) read.invoke(data.get(i));
                        IntVector vector = IntVector.of(field, values);
                        columns.add(vector);
                    } else if (type == double.class) {
                        double[] values = new double[size];
                        for (int i = 0; i < size; i++) values[i] = (double) read.invoke(data.get(i));
                        DoubleVector vector = DoubleVector.of(field, values);
                        columns.add(vector);
                    } else if (type == boolean.class) {
                        boolean[] values = new boolean[size];
                        for (int i = 0; i < size; i++) values[i] = (boolean) read.invoke(data.get(i));
                        BooleanVector vector = BooleanVector.of(field, values);
                        columns.add(vector);
                    } else if (type == short.class) {
                        short[] values = new short[size];
                        for (int i = 0; i < size; i++) values[i] = (short) read.invoke(data.get(i));
                        ShortVector vector = ShortVector.of(field, values);
                        columns.add(vector);
                    } else if (type == long.class) {
                        long[] values = new long[size];
                        for (int i = 0; i < size; i++) values[i] = (long) read.invoke(data.get(i));
                        LongVector vector = LongVector.of(field, values);
                        columns.add(vector);
                    } else if (type == float.class) {
                        float[] values = new float[size];
                        for (int i = 0; i < size; i++) values[i] = (float) read.invoke(data.get(i));
                        FloatVector vector = FloatVector.of(field, values);
                        columns.add(vector);
                    } else if (type == byte.class) {
                        byte[] values = new byte[size];
                        for (int i = 0; i < size; i++) values[i] = (byte) read.invoke(data.get(i));
                        ByteVector vector = ByteVector.of(field, values);
                        columns.add(vector);
                    } else if (type == char.class) {
                        char[] values = new char[size];
                        for (int i = 0; i < size; i++) values[i] = (char) read.invoke(data.get(i));
                        CharVector vector = CharVector.of(field, values);
                        columns.add(vector);
                    } else if (type == String.class) {
                        String[] values = new String[size];
                        for (int i = 0; i < size; i++) values[i] = (String) read.invoke(data.get(i));
                        StringVector vector = StringVector.of(field, values);
                        columns.add(vector);
                    } else if (type.isEnum()) {
                        Object[] levels = type.getEnumConstants();
                        if (levels.length < Byte.MAX_VALUE + 1) {
                            byte[] values = new byte[size];
                            for (int i = 0; i < size; i++) values[i] = (byte) ((Enum) read.invoke(data.get(i))).ordinal();
                            ByteVector vector = ByteVector.of(field, values);
                            columns.add(vector);
                        } else if (levels.length < Short.MAX_VALUE + 1) {
                            short[] values = new short[size];
                            for (int i = 0; i < size; i++) values[i] = (short) ((Enum) read.invoke(data.get(i))).ordinal();
                            ShortVector vector = ShortVector.of(field, values);
                            columns.add(vector);
                        } else {
                            int[] values = new int[size];
                            for (int i = 0; i < size; i++) values[i] = ((Enum) read.invoke(data.get(i))).ordinal();
                            IntVector vector = IntVector.of(field, values);
                            columns.add(vector);
                        }
                    } else {
                        Object[] values = new Object[size];
                        for (int i = 0; i < size; i++) values[i] = read.invoke(data.get(i));
                        Vector<?> vector = Vector.of(field, values);
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
    }

    /** Returns the struct field of a property. */
    private StructField field(PropertyDescriptor prop) {
        Class<?> clazz = prop.getPropertyType();

        DataType type = DataType.of(clazz);
        NominalScale scale = null;

        if (clazz.isEnum()) {
            Object[] levels = clazz.getEnumConstants();
            scale = new NominalScale(Arrays.stream(levels).map(Object::toString).toArray(String[]::new));
        }

        return new StructField(prop.getName(), type, scale);
    }

    /**
     * Constructor.
     * @param data The data stream.
     */
    public DataFrameImpl(Stream<Tuple> data) {
        this(data.collect(Collectors.toList()));
    }

    /**
     * Constructor.
     * @param data The data stream.
     */
    public DataFrameImpl(Stream<Tuple> data, StructType schema) {
        this(data.collect(Collectors.toList()), schema);
    }
    /**
     * Constructor.
     * @param data The data collection.
     */
    public DataFrameImpl(List<Tuple> data) {
        this(data, data.get(0).schema());
    }

    /**
     * Constructor.
     * @param data The data collection.
     */
    public DataFrameImpl(List<Tuple> data, StructType schema) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty tuple collections");
        }

        this.size = data.size();
        this.schema = schema;
        StructField[] fields = schema.fields();
        this.columns = new ArrayList<>(fields.length);

        for (int j = 0; j < fields.length; j++) {
            StructField field = fields[j];
            switch (field.type.id()) {
                case Integer: {
                    int[] values = new int[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getInt(j);
                    IntVector vector = IntVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                case Long: {
                    long[] values = new long[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getLong(j);
                    LongVector vector = LongVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                case Double: {
                    double[] values = new double[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getDouble(j);
                    DoubleVector vector = DoubleVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                case Float: {
                    float[] values = new float[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getFloat(j);
                    FloatVector vector = FloatVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                case Boolean: {
                    boolean[] values = new boolean[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getBoolean(j);
                    BooleanVector vector = BooleanVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                case Byte: {
                    byte[] values = new byte[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getByte(j);
                    ByteVector vector = ByteVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                case Short: {
                    short[] values = new short[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getShort(j);
                    ShortVector vector = ShortVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                case Char: {
                    char[] values = new char[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getChar(j);
                    CharVector vector = CharVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                case String: {
                    String[] values = new String[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).getString(j);
                    StringVector vector = StringVector.of(field, values);
                    columns.add(vector);
                    break;
                }

                default: {
                    Object[] values = new Object[size];
                    for (int i = 0; i < size; i++) values[i] = data.get(i).get(j);
                    Vector vector = Vector.of(field, values);
                    columns.add(vector);
                }
            }
        }
    }

    /**
     * Constructor.
     * @param vectors The column vectors.
     */
    public DataFrameImpl(BaseVector... vectors) {
        this(Arrays.asList(vectors));
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
    public Iterator<BaseVector> iterator() {
        return columns.iterator();
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
    public StringVector stringVector(int i) {
        return (StringVector) columns.get(i);
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
    public DataFrame merge(DataFrame... dataframes) {
        for (DataFrame df : dataframes) {
            if (df.size() != size()) {
                throw new IllegalArgumentException("Merge data frames with different size: " + size() + " vs " + df.size());
            }
        }

        List<BaseVector> all = new ArrayList<>(columns);
        for (DataFrame df : dataframes) {
            for (int i = 0; i < df.ncols(); i++) {
                all.add(df.column(i));
            }
        }

        return new DataFrameImpl(all);
    }

    @Override
    public DataFrame merge(BaseVector... vectors) {
        for (BaseVector vector : vectors) {
            if (vector.size() != size()) {
                throw new IllegalArgumentException("Merge data frames with different size: " + size() + " vs " + vector.size());
            }
        }

        List<BaseVector> columns = new ArrayList<>(this.columns);
        Collections.addAll(columns, vectors);
        return new DataFrameImpl(columns);
    }

    @Override
    public DataFrame union(DataFrame... dataframes) {
        for (DataFrame df : dataframes) {
            if (!schema.equals(df.schema())) {
                throw new IllegalArgumentException("Union data frames with different schema: " + schema + " vs " + df.schema());
            }
        }

        int nrows = nrows();
        for (DataFrame df : dataframes) {
            nrows += df.nrows();
        }

        // Single line solution
        // Stream.of(a, b).flatMap(Stream::of).toArray(Object[]::new)
        // It doesn't work for boolean, byte, char, short though.
        Object[] vectors = new Object[ncols()];
        for (int i = 0; i < vectors.length; i++) {
            BaseVector column = columns.get(i);
            switch (column.type().id()) {
                case Boolean:
                    vectors[i] = new boolean[nrows];
                    break;
                case Char:
                    vectors[i] = new char[nrows];
                    break;
                case Byte:
                    vectors[i] = new byte[nrows];
                    break;
                case Short:
                    vectors[i] = new short[nrows];
                    break;
                case Integer:
                    vectors[i] = new int[nrows];
                    break;
                case Long:
                    vectors[i] = new long[nrows];
                    break;
                case Float:
                    vectors[i] = new float[nrows];
                    break;
                case Double:
                    vectors[i] = new double[nrows];
                    break;
                default:
                    vectors[i] = new Object[nrows];
            }
            System.arraycopy(column.array(), 0, vectors[i], 0, nrows());
        }

        int destPos = nrows();
        for (DataFrame df : dataframes) {
            for (int i = 0; i < vectors.length; i++) {
                System.arraycopy(df.column(i).array(), 0, vectors[i], destPos, df.nrows());
            }
            destPos += df.nrows();
        }

        List<BaseVector> data = new ArrayList<>();
        for (int i = 0; i < vectors.length; i++) {
            BaseVector column = columns.get(i);
            switch (column.type().id()) {
                case Boolean:
                    data.add(BooleanVector.of(column.name(), (boolean[]) vectors[i]));
                    break;
                case Char:
                    data.add(CharVector.of(column.name(), (char[]) vectors[i]));
                    break;
                case Byte:
                    data.add(ByteVector.of(column.name(), (byte[]) vectors[i]));
                    break;
                case Short:
                    data.add(ShortVector.of(column.name(), (short[]) vectors[i]));
                    break;
                case Integer:
                    data.add(IntVector.of(column.name(), (int[]) vectors[i]));
                    break;
                case Long:
                    data.add(LongVector.of(column.name(), (long[]) vectors[i]));
                    break;
                case Float:
                    data.add(FloatVector.of(column.name(), (float[]) vectors[i]));
                    break;
                case Double:
                    data.add(DoubleVector.of(column.name(), (double[]) vectors[i]));
                    break;
                default:
                    data.add(Vector.of(column.name(), column.type(), (Object[]) vectors[i]));
            }
        }

        return new DataFrameImpl(data);
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
                    StringVector v = stringVector(j);
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

    @Override
    public double[][] toArray() {
        int nrows = nrows();
        int ncols = ncols();
        DataType[] types = types();

        double[][] m = new double[nrows][ncols];
        for (int j = 0; j < ncols; j++) {
            DataType type = types[j];
            switch (type.id()) {
                case Double: {
                    DoubleVector v = doubleVector(j);
                    for (int i = 0; i < nrows; i++) m[i][j] = v.getDouble(i);
                    break;
                }

                case Integer: {
                    IntVector v = intVector(j);
                    for (int i = 0; i < nrows; i++) m[i][j] = v.getInt(i);
                    break;
                }

                case Float: {
                    FloatVector v = floatVector(j);
                    for (int i = 0; i < nrows; i++) m[i][j] = v.getFloat(i);
                    break;
                }

                case Long: {
                    LongVector v = longVector(j);
                    for (int i = 0; i < nrows; i++) m[i][j] = v.getLong(i);
                    break;
                }

                case Boolean: {
                    BooleanVector v = booleanVector(j);
                    for (int i = 0; i < nrows; i++) m[i][j] = v.getDouble(i);
                    break;
                }

                case Byte: {
                    ByteVector v = byteVector(j);
                    for (int i = 0; i < nrows; i++) m[i][j] = v.getByte(i);
                    break;
                }

                case Short: {
                    ShortVector v = shortVector(j);
                    for (int i = 0; i < nrows; i++) m[i][j] = v.getShort(i);
                    break;
                }

                case Char: {
                    CharVector v = charVector(j);
                    for (int i = 0; i < nrows; i++) m[i][j] = v.getChar(i);
                    break;
                }

                case String: {
                    StringVector v = stringVector(j);
                    for (int i = 0; i < nrows; i++) {
                        String s = v.get(i);
                        m[i][j] = s == null ? Double.NaN : Double.valueOf(s);
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
                                m[i][j] = b.booleanValue() ? 1 : 0;
                            else
                                m[i][j] = Double.NaN;
                        }
                    } else if (Number.class.isAssignableFrom(clazz)) {
                        Vector<?> v = vector(j);
                        for (int i = 0; i < nrows; i++) m[i][j] = v.getDouble(i);
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
