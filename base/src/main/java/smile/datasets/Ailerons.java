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
import java.text.ParseException;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.io.Paths;

/**
 * Ailerons benchmark data for regression on numerical features. This dataset
 * addresses a control problem, namely flying an F16 aircraft. The attributes
 * describe the status of the aeroplane, while the goal is to predict the
 * control action on the ailerons of the aircraft.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record Ailerons(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public Ailerons() throws IOException, ParseException {
        this(Paths.getTestData("weka/regression/ailerons.arff"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public Ailerons(Path path) throws IOException, ParseException {
        this(Read.arff(path), Formula.lhs("goal"));
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
