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
import smile.data.formula.Formula;
import smile.io.CSV;
import smile.util.Paths;

/**
 *
 * @author Haifeng
 */
public class Prostate {

    public static DataFrame train;
    public static DataFrame test;
    public static Formula formula = Formula.lhs("lpsa");

    public static double[][] x;
    public static int[] y;
    public static double[][] testx;
    public static int[] testy;

    static {
        CSV csv = new CSV(CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter('\t'));

        try {
            train = csv.read(Paths.getTestData("regression/prostate-train.csv"));
            test = csv.read(Paths.getTestData("regression/prostate-test.csv"));

            x = formula.frame(train).toArray();
            y = formula.response(train).toIntArray();
            testx = formula.frame(test).toArray();
            testy = formula.response(test).toIntArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'prostate': " + ex);
            System.exit(-1);
        }
    }
}
