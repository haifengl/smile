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

package smile.io;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;

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
