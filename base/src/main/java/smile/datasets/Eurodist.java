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
import smile.data.DataFrame;
import org.apache.commons.csv.CSVFormat;
import smile.io.Read;
import smile.io.Paths;

/**
 * Distances between European cities.
 *
 * @param data data frame.
 * @author Haifeng Li
 */
public record Eurodist(DataFrame data) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public Eurodist() throws IOException {
        this(Paths.getTestData("mds/eurodist.txt"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     */
    public Eurodist(Path path) throws IOException {
        this(load(path));
    }

    private static DataFrame load(Path path) throws IOException {
        CSVFormat format = CSVFormat.Builder.create().setDelimiter('\t').get();
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
