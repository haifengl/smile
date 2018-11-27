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
package smile.data.arrow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.ipc.message.ArrowBlock;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import smile.data.DataFrame;

/**
 * An immutable collection of data organized into (potentially heterogeneous) named columns.
 * A <code>DataFrame</code> is equivalent to a relational table.
 *
 * @author Haifeng Li
 */
public class ArrowDataFrame implements DataFrame {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ArrowDataFrame.class);

    /** The holder for a set of vectors to be loaded/unloaded. */
    private VectorSchemaRoot root;
    /** Arrow schema. */
    private Schema schema;
    /**
     * When a field is dictionary encoded, the values are represented
     * by an array of Int32 representing the index of the value in the
     * dictionary. The Dictionary is received as one or more
     * DictionaryBatches with the id referenced by a dictionary attribute
     * defined in the metadata (Message.fbs) in the Field table.
     * The dictionary has the same layout as the type of the field would
     * dictate. Each entry in the dictionary can be accessed by its index
     * in the DictionaryBatches. When a Schema references a Dictionary id,
     * it must send at least one DictionaryBatch for this id.
     */
    private DictionaryProvider provider;
    /** The fields in the schema. */
    private List<Field> fields;
    /** The columns in the schema. */
    private List<FieldVector> columns;
    /**
     * The maximum amount of memory in bytes that can be
     * allocated in this Accountant.
     */
    private long limit = Integer.MAX_VALUE;

    /**
     * Constructor.
     * @param file the path name of an Apache Arrow file.
     */
    public ArrowDataFrame(Schema schema) {
        this.schema = schema;
        root = VectorSchemaRoot.create(schema, new RootAllocator(limit));
    }

    /**
     * Constructor.
     * @param file an Apache Arrow file.
     */
    public ArrowDataFrame(File file) throws IOException {
        this(file, Integer.MAX_VALUE);
    }

    /**
     * Constructor.
     * @param file an Apache Arrow file.
     * @param limit the maximum amount of memory in bytes that can be allocated.
     */
    public ArrowDataFrame(File file, long limit) throws IOException {
        this.limit = limit;
        read(file);
    }

    /** Reads an Arrow file. */
    private void read(File file) throws IOException {
        try (RootAllocator allocator = new RootAllocator(limit);
             FileInputStream input = new FileInputStream(file);
             ArrowFileReader reader = new ArrowFileReader(input.getChannel(), allocator)) {

            root = reader.getVectorSchemaRoot();
            schema = root.getSchema();
            fields = schema.getFields();
            columns = root.getFieldVectors();

            List<ArrowBlock> blocks = reader.getRecordBlocks();
            for (ArrowBlock block : blocks) {
                if (!reader.loadRecordBatch(block)) {
                    throw new IOException("Expected to read record batch");
                }

                System.out.println("\trow count for this block is " + root.getRowCount());
                List<FieldVector> fieldVector = root.getFieldVectors();
                System.out.println("\tnumber of fieldVectors (corresponding to columns) : " + fieldVector.size());
            }
        }
    }

    /** Writes the DataFrame to a file. */
    public void write(File file) throws IOException {
        DictionaryProvider provider = new DictionaryProvider.MapDictionaryProvider();
        try (FileOutputStream output = new FileOutputStream(file);
             ArrowFileWriter writer = new ArrowFileWriter(root, provider, output.getChannel())) {
            writer.start();
            writer.writeBatch();
            writer.end();
            writer.close();
            output.flush();
            output.close();
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
        List<BaseVector> columns = new ArrayList<>(this.columns);
        Collections.addAll(columns, vectors);
        return new DataFrameImpl(columns);
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
            if (type == DataTypes.DoubleType) {
                DoubleVector v = doubleVector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getDouble(i));
            } else if (type == DataTypes.IntegerType) {
                IntVector v = intVector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getInt(i));
            } else if (type == DataTypes.LongType) {
                LongVector v = longVector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getLong(i));
            } else if (type == DataTypes.FloatType) {
                FloatVector v = floatVector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getFloat(i));
            } else if (type == DataTypes.ShortType) {
                ShortVector v = shortVector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getShort(i));
            } else if (type == DataTypes.ByteType) {
                ByteVector v = byteVector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getByte(i));
            } else if (type == DataTypes.CharType) {
                CharVector v = charVector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getChar(i));
            } else if (type == DataTypes.BooleanType) {
                BooleanVector v = booleanVector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getDouble(i));
            } else if (type == DataTypes.BooleanObjectType) {
                Vector<Boolean> v = vector(j);
                for (int i = 0; i < nrows; i++) {
                    Boolean b = v.get(i);
                    if (b != null)
                        m.set(i, j, b.booleanValue() ? 1 : 0);
                    else
                        m.set(i, j, Double.NaN);
                }
            } else if (type == DataTypes.DoubleObjectType ||
                    type == DataTypes.IntegerObjectType ||
                    type == DataTypes.FloatObjectType ||
                    type == DataTypes.LongObjectType ||
                    type == DataTypes.ByteObjectType ||
                    type == DataTypes.ShortObjectType ||
                    type == DataTypes.CharObjectType) {
                Vector<?> v = vector(j);
                for (int i = 0; i < nrows; i++) m.set(i, j, v.getDouble(i));
            } else {
                throw new UnsupportedOperationException(String.format("DataFrame.toMatrix() doesn't support type %s", type));
            }
        }

        return m;
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
        public int fieldIndex(String name) {
            return columnIndex(name);
        }

        @Override
        public String toString() {
            return toString(",");
        }
    }
}
