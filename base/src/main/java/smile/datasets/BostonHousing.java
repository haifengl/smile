/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.datasets;

import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.util.Paths;

/**
 * Boston housing dataset. This dataset is a derived from information collected
 * by the U.S. Census Service concerning housing in the area of Boston MA.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record BostonHousing(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public BostonHousing() throws IOException, ParseException {
        this(load(Paths.getTestData("weka/regression/housing.arff")), Formula.lhs("class"));
    }

    /**
     * Constructor.
     * @param first the path string or initial part of the path string.
     * @param more additional strings to be joined to form the path string.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public BostonHousing(String first, String... more) throws IOException, ParseException {
        this(load(first, more), Formula.lhs("class"));
    }

    private static DataFrame load(String first, String... more) throws IOException, ParseException {
        return load(Path.of(first, more));
    }

    private static DataFrame load(Path path) throws IOException, ParseException {
        return Read.arff(path);
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
