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

package smile.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.Dataset;
import smile.data.Instance;
import smile.data.type.StructType;
import smile.math.SparseArray;

/**
 * Interface to write a Dataset to external storage systems.
 *
 * @author Haifeng Li
 */
public class DatasetWriter {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DatasetWriter.class);

    /** CSV format. */
    private CSVFormat format = CSVFormat.DEFAULT;

    /**
     * Constructor.
     */
    public DatasetWriter() {

    }

    /**
     * Sets the CSV format.
     */
    public void format(CSVFormat format) {
        this.format = format;
    }

    /** Writes a CSV file. */
    public void csv(DataFrame df, Path path) throws IOException {
        CSV csv = new CSV(format);
        csv.write(df, path);
    }

    /**
     * Writes an Apache Arrow file.
     * Apache Arrow is a cross-language development platform for in-memory data.
     * It specifies a standardized language-independent columnar memory format
     * for flat and hierarchical data, organized for efficient analytic
     * operations on modern hardware.
     */
    public void arrow(DataFrame df, Path path) throws IOException {
        Arrow arrow = new Arrow();
        arrow.write(df, path);
    }
}
