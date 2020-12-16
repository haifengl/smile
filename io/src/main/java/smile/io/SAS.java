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

import java.io.InputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.epam.parso.Column;
import com.epam.parso.SasFileProperties;
import com.epam.parso.SasFileReader;
import com.epam.parso.impl.SasFileReaderImpl;
import smile.data.DataFrame;
import smile.data.type.DataTypes;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.data.vector.Vector;

/**
 * Reads SAS7BDAT datasets. SAS7BDAT is currently the main format
 * used for storing SAS datasets across all platforms.
 * <p>
 * Leveraging the lightweight Java library Parso (https//github.com/epam/parso),
 * no proprietary SAS software is required.
 *
 * @author Haifeng Li
 */
public interface SAS {
    /**
     * Reads a SAS7BDAT file.
     *
     * @param path the input file path.
     * @throws IOException when fails to write the file.
     * @return the data frame.
     */
    static DataFrame read(Path path) throws IOException {
        return read(Files.newInputStream(path), Integer.MAX_VALUE);
    }

    /**
     * Reads a SAS7BDAT file.
     *
     * @param path the input file path.
     * @throws IOException when fails to write the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    static DataFrame read(String path) throws IOException, URISyntaxException {
        return read(Input.stream(path), Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from a SAS7BDAT file.
     *
     * @param input a SAS7BDAT file input stream.
     * @param limit the number number of records to read.
     * @throws IOException when fails to write the file.
     * @return the data frame.
     */
    static DataFrame read(InputStream input, int limit) throws IOException {
        try {
            SasFileReader reader = new SasFileReaderImpl(input);
            SasFileProperties properties = reader.getSasFileProperties();
            List<Column> columns = reader.getColumns();
            int nrow = (int) properties.getRowCount();
            int ncol = (int) properties.getColumnsCount();

            Object[][] rows = new Object[Math.min(nrow, limit)][];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = reader.readNext();
            }

            BaseVector[] vectors = new BaseVector[ncol];
            for (int j = 0; j < ncol; j++) {
                Column column = columns.get(j);
                if (column.getType() == String.class) {
                    String[] vector = new String[nrow];
                    for (int i = 0; i < rows.length; i++) {
                        vector[i] = (String) rows[i][j];
                    }
                    vectors[j] = Vector.of(column.getName(), DataTypes.StringType, vector);
                } else {
                    double[] vector = new double[nrow];
                    for (int i = 0; i < rows.length; i++) {
                        vector[i] = ((Number) rows[i][j]).doubleValue();
                    }
                    vectors[j] = DoubleVector.of(column.getName(), vector);
                }
            }

            return DataFrame.of(vectors);
        } finally {
            input.close();
        }
    }
}
