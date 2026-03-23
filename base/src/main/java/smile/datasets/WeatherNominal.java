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
 * Toy weather dataset, of which all attributes are nominal.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record WeatherNominal(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public WeatherNominal() throws IOException, ParseException {
        this(Paths.getTestData("weka/weather.nominal.arff"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public WeatherNominal(Path path) throws IOException, ParseException {
        this(Read.arff(path), Formula.lhs("play"));
    }

    /**
     * Returns the sample features in level encoding.
     * @return the sample features in level encoding.
     */
    public double[][] level() {
        return formula.x(data).toArray(false, CategoricalEncoder.LEVEL);
    }

    /**
     * Returns the sample features in dummy encoding.
     * @return the sample features in dummy encoding.
     */
    public double[][] dummy() {
        return formula.x(data).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the sample features in one-hot encoding.
     * @return the sample features in one-hot encoding.
     */
    public double[][] onehot() {
        return formula.x(data).toArray(false, CategoricalEncoder.ONE_HOT);
    }

    /**
     * Returns the class labels.
     * @return the class labels.
     */
    public int[] y() {
        return formula.y(data).toIntArray();
    }
}
