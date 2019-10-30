/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.data;

import org.apache.commons.csv.CSVFormat;
import smile.io.CSV;
import smile.util.Paths;

/**
 *
 * @author Haifeng
 */
public class SwissRoll {

    public static double[][] data;

    static {
        try {
            CSV csv = new CSV(CSVFormat.DEFAULT.withDelimiter('\t'));
            data = csv.read(Paths.getTestData("manifold/swissroll.txt")).toArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'index.noun': " + ex);
            System.exit(-1);
        }
    }
}
