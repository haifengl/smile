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

import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import smile.data.CategoricalEncoder;
import smile.io.Read;
import smile.math.MathEx;
import smile.io.Paths;

/**
 * Swiss roll dataset. It is commonly used demonstrating nonlinear
 * dimensionality reduction algorithms. It consists of a set of points
 * in three dimensions, arranged in a "roll" shape, such that the points
 * on the roll are mapped to a two-dimensional plane in a nonlinear fashion.
 *
 * @param data the data points.
 * @author Haifeng Li
 */
public record SwissRoll(double[][] data) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public SwissRoll() throws IOException {
        this(Paths.getTestData("manifold/swissroll.txt"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     */
    public SwissRoll(Path path) throws IOException {
        this(load(path));
    }

    private static double[][] load(Path path) throws IOException {
        CSVFormat format = CSVFormat.Builder.create().setDelimiter('\t').get();
        var data = Read.csv(path, format);
        return data.toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the pairwise distance matrix.
     * @param size the number of samples to calculate pair wise distance.
     * @return the pairwise distance matrix.
     */
    public double[][] distance(int size) {
        return MathEx.pdist(Arrays.copyOf(data, size)).toArray();
    }
}
