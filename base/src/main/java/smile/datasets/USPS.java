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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
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
 * USPS handwritten text recognition dataset. USPS is a digit dataset scanned
 * from envelopes by the US Postal Service containing a total of 9,298 16×16
 * pixel grayscale samples; the images are centered, normalized and show a
 * broad range of font styles.
 *
 * @param train training data frame.
 * @param test testing data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record USPS(DataFrame train, DataFrame test, Formula formula) {
    private static final StructType schema;
    static {
        ArrayList<StructField> fields = new ArrayList<>();
        fields.add(new StructField("class", DataTypes.ByteType));
        IntStream.range(1, 257).forEach(i -> fields.add(new StructField("V" + i, DataTypes.FloatType)));
        schema = new StructType(fields);
    }

    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public USPS() throws IOException {
        this(Paths.getTestData("usps/zip.train"),
             Paths.getTestData("usps/zip.test"));
    }

    /**
     * Constructor.
     * @param trainDataPath the path to training data file.
     * @param testDataPath the path to testing data file.
     * @throws IOException when fails to read the file.
     */
    public USPS(Path trainDataPath, Path testDataPath) throws IOException {
        this(load(trainDataPath), load(testDataPath), Formula.lhs("class"));
    }
    private static DataFrame load(Path path) throws IOException {
        CSVFormat format = CSVFormat.Builder.create().setDelimiter(' ').get();
        return Read.csv(path, format, schema);
    }

    /**
     * Returns the train sample features.
     * @return the train sample features.
     */
    public double[][] x() {
        return formula.x(train).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the train sample class labels.
     * @return the train sample class labels.
     */
    public int[] y() {
        return formula.y(train).toIntArray();
    }

    /**
     * Returns the test sample features.
     * @return the test sample features.
     */
    public double[][] testx() {
        return formula.x(test).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the test sample class labels.
     * @return the test sample class labels.
     */
    public int[] testy() {
        return formula.y(test).toIntArray();
    }
}
