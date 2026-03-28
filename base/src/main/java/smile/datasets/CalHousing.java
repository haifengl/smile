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
import java.text.ParseException;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.io.Paths;

/**
 * California housing dataset. The target variable is the median house value
 * for California districts, expressed in hundreds of thousands of dollars
 * ($100,000). This dataset was derived from the 1990 U.S. census, using one
 * row per census block group. A block group is the smallest geographical unit
 * for which the U.S. Census Bureau publishes sample data (a block group
 * typically has a population of 600 to 3,000 people).
 * <p>
 * A household is a group of people residing within a home. Since the average
 * number of rooms and bedrooms in this dataset are provided per household, these
 * columns may take surprisingly large values for block groups with few households
 * and many empty houses, such as vacation resorts.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record CalHousing(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public CalHousing() throws IOException, ParseException {
        this(Paths.getTestData("weka/regression/cal_housing.arff"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public CalHousing(Path path) throws IOException, ParseException {
        this(Read.arff(path), Formula.lhs("medianHouseValue"));
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
