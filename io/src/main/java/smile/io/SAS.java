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

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.epam.parso.Column;
import com.epam.parso.SasFileProperties;
import com.epam.parso.SasFileReader;
import com.epam.parso.impl.SasFileReaderImpl;
import smile.data.DataFrame;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.data.vector.Vector;

/**
 * Reads SAS7BDAT datasets. SAS7BDAT is currently the main format
 * used for storing SAS datasets across all platforms.
 *
 * Leveraging the lightweight Java library Parso (https//github.com/epam/parso),
 * no proprietary SAS software is required.
 *
 * @author Haifeng Li
 */
public class SAS {
    /**
     * Constructor.
     */
    public SAS() {

    }

    /**
     * Reads a SAS7BDAT file.
     *
     * @param path a SAS7BDAT file path.
     */
    public DataFrame read(Path path) throws IOException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from a SAS7BDAT file.
     *
     * @param path a SAS7BDAT file path.
     * @param limit reads a limited number of records.
     */
    public DataFrame read(Path path, int limit) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            SasFileReader reader = new SasFileReaderImpl(input);
            SasFileProperties properties = reader.getSasFileProperties();
            List<Column> columns = reader.getColumns();
            int nrows = (int) properties.getRowCount();
            int ncols = (int) properties.getColumnsCount();

            Object[][] rows = new Object[Math.min(nrows, limit)][];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = reader.readNext();
            }

            BaseVector[] vectors = new BaseVector[ncols];
            for (int j = 0; j < ncols; j++) {
                Column column = columns.get(j);
                if (column.getType() == String.class) {
                    String[] vector = new String[nrows];
                    for (int i = 0; i < rows.length; i++) {
                        vector[i] = (String) rows[i][j];
                    }
                    vectors[j] = Vector.of(column.getName(), DataTypes.StringType, vector);
                } else {
                    double[] vector = new double[nrows];
                    for (int i = 0; i < rows.length; i++) {
                        vector[i] = ((Number) rows[i][j]).doubleValue();
                    }
                    vectors[j] = DoubleVector.of(column.getName(), vector);
                }
            }

            return DataFrame.of(vectors);
        }
    }
}
