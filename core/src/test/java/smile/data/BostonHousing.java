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

import smile.data.formula.Formula;
import smile.io.Read;
import smile.util.Paths;

/**
 * @author ray
 */
public class BostonHousing {

    public static DataFrame data;
    public static Formula formula = Formula.lhs("class");

    public static double[][] x;
    public static double[] y;

    static {
        try {
            data = Read.arff(Paths.getTestData("weka/regression/housing.arff"));

            x = formula.x(data).toArray(false, CategoricalEncoder.DUMMY);
            y = formula.y(data).toDoubleArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'boston housing': " + ex);
            System.exit(-1);
        }
    }
}
