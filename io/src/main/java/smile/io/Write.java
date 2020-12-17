/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.io;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;

/**
 * Writes data to external storage systems.
 *
 * @author Haifeng Li
 */
public interface Write {
    /**
     * Writes a CSV file.
     *
     * @param data the data frame.
     * @param path the output file path.
     * @throws IOException when fails to write the file.
     */
    static void csv(DataFrame data, Path path) throws IOException {
        csv(data, path, CSVFormat.DEFAULT);
    }

    /**
     * Writes a CSV file.
     *
     * @param data the data frame.
     * @param path the output file path.
     * @param format the CSV file format.
     * @throws IOException when fails to write the file.
     */
    static void csv(DataFrame data, Path path, CSVFormat format) throws IOException {
        CSV csv = new CSV(format);
        csv.write(data, path);
    }

    /**
     * Writes an Apache Arrow file.
     * Apache Arrow is a cross-language development platform for in-memory data.
     * It specifies a standardized language-independent columnar memory format
     * for flat and hierarchical data, organized for efficient analytic
     * operations on modern hardware.
     *
     * @param data the data frame.
     * @param path the output file path.
     * @throws IOException when fails to write the file.
     */
    static void arrow(DataFrame data, Path path) throws IOException {
        Arrow arrow = new Arrow();
        arrow.write(data, path);
    }

    /**
     * Writes the data frame to an ARFF file.
     *
     * @param data the data frame.
     * @param path the output file path.
     * @param relation the relation name of ARFF.
     * @throws IOException when fails to write the file.
     */
    static void arff(DataFrame data, Path path, String relation) throws IOException {
        Arff.write(data, path, relation);
    }
}
