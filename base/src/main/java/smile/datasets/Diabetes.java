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
import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.io.Paths;

/**
 * Diabetes dataset. This dataset has 10 baseline variables, age, sex, body
 * mass index, average blood pressure, and six blood serum measurements were
 * obtained for each of 442 diabetes patients, as well as the response of
 * interest, a quantitative measure of disease progression one year after
 * baseline. This dataset was originally used in "Least Angle Regression"
 * by Efron et al., 2004, in Annals of Statistics.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record Diabetes(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public Diabetes() throws IOException {
        this(Paths.getTestData("regression/diabetes.csv"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     */
    public Diabetes(Path path) throws IOException {
        this(load(path), Formula.lhs("y"));
    }

    private static DataFrame load(Path path) throws IOException {
        CSVFormat format = CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).get();
        return Read.csv(path, format);
    }

    /**
     * Returns the sample features.
     * @return the sample features.
     */
    public double[][] x() {
        return formula.x(data).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the target values.
     * @return the target values.
     */
    public double[] y() {
        return formula.y(data).toDoubleArray();
    }
}
