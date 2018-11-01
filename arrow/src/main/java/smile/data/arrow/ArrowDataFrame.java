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
public abstract class ArrowDataFrame implements DataFrame {

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
    /** A vector corresponding to a Field in the schema. */
    private List<Field> fields;
    /** A vector corresponding to a Field in the schema. */
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
}
