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
import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.util.Paths;

/**
 * Credit card default dataset. The dataset consists of 10000 individuals
 * and whether their credit card has defaulted or not.
 *
 * @author Haifeng Li
 */
public record Default(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public Default() throws IOException, ParseException {
        this(load(Paths.getTestData("classification/default.csv")), Formula.lhs("default"));
    }

    /**
     * Constructor.
     * @param first the path string or initial part of the path string.
     * @param more additional strings to be joined to form the path string.
     * @throws IOException when fails to read the file.
     */
    public Default(String first, String... more) throws IOException {
        this(load(first, more), Formula.lhs("default"));
    }

    private static DataFrame load(String first, String... more) throws IOException {
        return load(Path.of(first, more));
    }

    private static DataFrame load(Path path) throws IOException {
        CSVFormat format = CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).build();
        return Read.csv(path, format).factorize();
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
