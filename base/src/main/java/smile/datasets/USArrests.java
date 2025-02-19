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
import smile.data.DataFrame;
import smile.io.Read;
import smile.io.Paths;

/**
 * Violent crime rates by US state. This dataset contains statistics,
 * in arrests per 100,000 residents for assault, murder, and rape in
 * each of the 50 US states in 1973. Also given is the percent of the
 * population living in urban areas.
 *
 * @param data data frame.
 * @author Haifeng Li
 */
public record USArrests(DataFrame data) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public USArrests() throws IOException {
        this(Paths.getTestData("clustering/USArrests.txt"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     */
    public USArrests(Path path) throws IOException {
        this(load(path));
    }

    private static DataFrame load(Path path) throws IOException {
        CSVFormat format = CSVFormat.Builder.create()
                .setDelimiter('\t')
                .setHeader().setSkipHeaderRecord(true)
                .get();
        return Read.csv(path, format);
    }

    /**
     * Returns the sample features.
     * @return the sample features.
     */
    public double[][] x() {
        return data.drop(0).toArray();
    }
}
