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
package smile.datasets;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.Read;
import smile.io.Paths;

/**
 * Predicting the age of abalone from physical measurements. The age of
 * abalone is determined by cutting the shell through the cone, staining
 * it, and counting the number of rings through a microscope.
 * <p>
 * Other measurements, which are easier to obtain, are used to predict
 * the age. Further information, such as weather patterns and location
 * (hence food availability) may be required to solve the problem.
 * <p>
 * From the original data examples with missing values were removed
 * (the majority having the predicted value missing), and the ranges
 * of the continuous values have been scaled for use with an ANN
 * (by dividing by 200).
 *
 * @param train training data frame.
 * @param test testing data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record Abalone(DataFrame train, DataFrame test, Formula formula) {
    /** Data schema. */
    private static final StructType schema = new StructType(
            new StructField("sex", DataTypes.ByteType, new NominalScale("F", "M", "I")),
            new StructField("length", DataTypes.DoubleType),
            new StructField("diameter", DataTypes.DoubleType),
            new StructField("height", DataTypes.DoubleType),
            new StructField("whole weight", DataTypes.DoubleType),
            new StructField("shucked weight", DataTypes.DoubleType),
            new StructField("viscera weight", DataTypes.DoubleType),
            new StructField("shell weight", DataTypes.DoubleType),
            new StructField("rings", DataTypes.DoubleType));

    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public Abalone() throws IOException {
        this(Paths.getTestData("regression/abalone-train.data"),
             Paths.getTestData("regression/abalone-test.data"));
    }

    /**
     * Constructor.
     * @param trainDataPath the path to training data file.
     * @param testDataPath the path to testing data file.
     * @throws IOException when fails to read the file.
     */
    public Abalone(Path trainDataPath, Path testDataPath) throws IOException {
        this(load(trainDataPath), load(testDataPath), Formula.lhs("rings"));
    }

    private static DataFrame load(Path path) throws IOException {
        return Read.csv(path, CSVFormat.DEFAULT, schema);
    }

    /**
     * Returns the train sample features.
     * @return the train sample features.
     */
    public double[][] x() {
        return formula.x(train).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the train sample target values.
     * @return the train sample target values.
     */
    public double[] y() {
        return formula.y(train).toDoubleArray();
    }

    /**
     * Returns the test sample features.
     * @return the test sample features.
     */
    public double[][] testx() {
        return formula.x(test).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the test sample target values.
     * @return the test sample target values.
     */
    public double[] testy() {
        return formula.y(test).toDoubleArray();
    }
}
