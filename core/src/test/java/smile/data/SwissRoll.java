/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data;

import java.util.Arrays;
import smile.io.CSV;
import smile.math.MathEx;
import smile.util.Paths;
import org.apache.commons.csv.CSVFormat;

/**
 *
 * @author Haifeng
 */
public class SwissRoll {

    public static double[][] data;
    /** Pair wise distance of first 47 samples. */
    public static double[][] dist;

    static {
        try {
            CSV csv = new CSV(CSVFormat.DEFAULT.withDelimiter('\t'));
            data = csv.read(Paths.getTestData("manifold/swissroll.txt")).toArray(false, CategoricalEncoder.DUMMY);
            dist = MathEx.pdist(Arrays.copyOf(data, 47)).toArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'swissroll': " + ex);
            System.exit(-1);
        }
    }
}
