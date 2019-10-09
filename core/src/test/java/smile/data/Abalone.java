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
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.CSV;
import smile.util.Paths;

/**
 *
 * @author Haifeng
 */
public class Abalone {

    public static DataFrame train;
    public static DataFrame test;
    public static Formula formula = Formula.lhs("rings");

    public static double[][] x;
    public static double[] y;
    public static double[][] testx;
    public static double[] testy;

    static {
        StructType schema = DataTypes.struct(
                new StructField("sex", DataTypes.ByteType, new NominalScale("F", "M", "I")),
                new StructField("length", DataTypes.DoubleType),
                new StructField("diameter", DataTypes.DoubleType),
                new StructField("height", DataTypes.DoubleType),
                new StructField("whole weight", DataTypes.DoubleType),
                new StructField("shucked weight", DataTypes.DoubleType),
                new StructField("viscera weight", DataTypes.DoubleType),
                new StructField("shell weight", DataTypes.DoubleType),
                new StructField("rings", DataTypes.DoubleType)
        );

        CSV csv = new CSV(CSVFormat.DEFAULT);
        csv.schema(schema);

        try {
            train = csv.read(Paths.getTestData("regression/abalone-train.data"));
            test = csv.read(Paths.getTestData("regression/abalone-test.data"));

            x = formula.x(train).toArray();
            y = formula.y(train).toDoubleArray();
            testx = formula.x(test).toArray();
            testy = formula.y(test).toDoubleArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'abalone': " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }
    }
}
