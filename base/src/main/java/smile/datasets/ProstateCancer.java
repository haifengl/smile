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
import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.io.Paths;

/**
 * Prostate cancer dataset. This dataset examines the correlation between
 * the level of prostate-specific antigen and a number of clinical measures
 * in men who were about to receive a radical prostatectomy.
 *
 * @param train training data frame.
 * @param test testing data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record ProstateCancer(DataFrame train, DataFrame test, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public ProstateCancer() throws IOException {
        this(Paths.getTestData("regression/prostate-train.csv"),
             Paths.getTestData("regression/prostate-test.csv"));
    }

    /**
     * Constructor.
     * @param trainDataPath the path to training data file.
     * @param testDataPath the path to testing data file.
     * @throws IOException when fails to read the file.
     */
    public ProstateCancer(Path trainDataPath, Path testDataPath) throws IOException {
        this(load(trainDataPath), load(testDataPath), Formula.lhs("lpsa"));
    }

    private static DataFrame load(Path path) throws IOException {
        CSVFormat format = CSVFormat.Builder.create()
                .setDelimiter('\t')
                .setHeader().setSkipHeaderRecord(true)
                .get();
        return Read.csv(path, format);
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
     * Returns the test sample class labels.
     * @return the test sample class labels.
     */
    public double[] testy() {
        return formula.y(test).toDoubleArray();
    }
}
