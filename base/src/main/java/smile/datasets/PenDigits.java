/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.datasets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.IntStream;
import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.Read;
import smile.io.Paths;

/**
 * Pen-based recognition of handwritten digits dataset. This dataset
 * was collected from 44 writers. The writers are asked write 250
 * digits in random order.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record PenDigits(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public PenDigits() throws IOException {
        this(Paths.getTestData("classification/pendigits.txt"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     */
    public PenDigits(Path path) throws IOException {
        this(load(path), Formula.lhs("class"));
    }

    private static DataFrame load(Path path) throws IOException {
        ArrayList<StructField> fields = new ArrayList<>();
        IntStream.range(1, 17).forEach(i -> fields.add(new StructField("V"+i, DataTypes.DoubleType)));
        fields.add(new StructField("class", DataTypes.ByteType));
        StructType schema = new StructType(fields);

        CSVFormat format = CSVFormat.Builder.create().setDelimiter('\t').get();
        return Read.csv(path, format, schema);
    }

    /**
     * Returns the sample features.
     * @return the sample features.
     */
    public double[][] x() {
        return formula.x(data).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the class labels.
     * @return the class labels.
     */
    public int[] y() {
        return formula.y(data).toIntArray();
    }
}
