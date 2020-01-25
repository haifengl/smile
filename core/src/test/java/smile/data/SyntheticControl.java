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
import smile.io.Read;
import smile.util.Paths;

/**
 *
 * @author Haifeng
 */
public class SyntheticControl {

    public static DataFrame data;
    public static double[][] x;

    static {
        try {
            data = Read.csv(Paths.getTestData("uci/synthetic_control.data"), CSVFormat.DEFAULT.withDelimiter(' '));
            x = data.toArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'synthetic_control': " + ex);
            System.exit(-1);
        }
    }
}
