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

import smile.data.formula.Formula;
import smile.io.Arff;
import smile.util.Paths;

/**
 *
 * @author Haifeng
 */
public class Segment {

    public static DataFrame train;
    public static DataFrame test;
    public static Formula formula = Formula.lhs("class");

    public static double[][] x;
    public static int[] y;
    public static double[][] testx;
    public static int[] testy;

    static {
        try {
            Arff arff = new Arff(Paths.getTestData("weka/segment-challenge.arff"));
            train = arff.read();

            x = formula.frame(train).toArray();
            y = formula.response(train).toIntArray();

            arff = new Arff(Paths.getTestData("weka/segment-test.arff"));
            test = arff.read();

            testx = formula.frame(test).toArray();
            testy = formula.response(test).toIntArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'segment': " + ex);
            System.exit(-1);
        }
    }
}
