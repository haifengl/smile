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

import org.apache.commons.csv.CSVFormat;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.util.Paths;

import java.util.Arrays;

/**
 *
 * @author Haifeng
 */
public class LibrasMovement {

    public static DataFrame data;
    public static Formula formula = Formula.lhs("V91");
    public static double[][] x;
    public static int[] y;

    static {
        try {
            data = Read.csv(Paths.getTestData("classification/movement_libras.data"), CSVFormat.DEFAULT);
            x = formula.x(data).toArray(false, CategoricalEncoder.DUMMY);
            y = Arrays.stream(formula.y(data).toIntArray()).map(yi -> yi - 1).toArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'movement_libras': " + ex);
            System.exit(-1);
        }
    }
}
