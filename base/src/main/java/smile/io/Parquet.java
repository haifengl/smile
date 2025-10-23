/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.io;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.dataset.file.FileFormat;
import org.apache.arrow.dataset.file.FileSystemDatasetFactory;
import org.apache.arrow.dataset.jni.NativeMemoryPool;
import org.apache.arrow.dataset.scanner.Scanner;
import org.apache.arrow.dataset.scanner.ScanOptions;
import org.apache.arrow.dataset.source.Dataset;
import org.apache.arrow.dataset.source.DatasetFactory;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.types.pojo.Schema;
import smile.data.DataFrame;
import smile.data.type.*;

/**
 * Apache Parquet is a columnar storage format that supports
 * nested data structures. It uses the record shredding and
 * assembly algorithm described in the Dremel paper.
 *
 * @author Haifeng Li
 */
public class Parquet {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Parquet.class);

    /** Private constructor to prevent object creation. */
    private Parquet() {

    }

    /**
     * Reads a parquet file.
     * @param path the input file path.
     * @throws IOException when fails to write the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    public static DataFrame read(String path) throws Exception {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from a parquet file.
     * @param path the input file path.
     * @param limit the number of records to read.
     * @throws IOException when fails to write the file.
     * @return the data frame.
     */
    public static DataFrame read(String path, int limit) throws Exception {
        ScanOptions options = new ScanOptions(32768); // batch size
        String uri = "file://" + path;
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);
             DatasetFactory factory = new FileSystemDatasetFactory(allocator, NativeMemoryPool.getDefault(), FileFormat.PARQUET, uri);
             Dataset dataset = factory.finish();
             Scanner scanner = dataset.newScan(options);
             ArrowReader reader = scanner.scanBatches()) {

            int rowCount = 0;
            Schema schema = factory.inspect();
            StructType struct = Arrow.toStructType(schema);
            List<DataFrame> frames = new ArrayList<>();
            while (reader.loadNextBatch() && rowCount < limit) {
                try (VectorSchemaRoot root = reader.getVectorSchemaRoot()) {
                    DataFrame frame = Arrow.read(root);
                    frames.add(frame);
                    rowCount += frames.size();
                }
            }

            if (frames.isEmpty()) {
                throw new IllegalStateException("No record batch");
            } else if (frames.size() == 1) {
                return frames.getFirst();
            } else {
                DataFrame df = frames.getFirst();
                return df.concat(frames.subList(1, frames.size()).toArray(new DataFrame[frames.size() - 1]));
            }
        }
    }
}
