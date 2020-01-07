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
import smile.io.Read;
import smile.util.Paths;

/**
 *
 * @author Haifeng
 */
public class BreastCancer {

    public static DataFrame data;
    public static Formula formula = Formula.lhs("diagnosis");
    public static double[][] x;
    public static int[] y;

    static {
        StructType schema = DataTypes.struct(
                new StructField("id", DataTypes.IntegerType),
                new StructField("diagnosis", DataTypes.ByteType, new NominalScale("M", "B")),
                new StructField("radius_mean", DataTypes.DoubleType),
                new StructField("texture_mean", DataTypes.DoubleType),
                new StructField("perimeter_mean", DataTypes.DoubleType),
                new StructField("area_mean", DataTypes.DoubleType),
                new StructField("smoothness_mean", DataTypes.DoubleType),
                new StructField("compactness_mean", DataTypes.DoubleType),
                new StructField("concavity_mean", DataTypes.DoubleType),
                new StructField("concave points_mean", DataTypes.DoubleType),
                new StructField("symmetry_mean", DataTypes.DoubleType),
                new StructField("fractal_dimension_mean", DataTypes.DoubleType),
                new StructField("radius_se", DataTypes.DoubleType),
                new StructField("texture_se", DataTypes.DoubleType),
                new StructField("perimeter_se", DataTypes.DoubleType),
                new StructField("area_se", DataTypes.DoubleType),
                new StructField("smoothness_se", DataTypes.DoubleType),
                new StructField("compactness_se", DataTypes.DoubleType),
                new StructField("concavity_se", DataTypes.DoubleType),
                new StructField("concave points_se", DataTypes.DoubleType),
                new StructField("symmetry_se", DataTypes.DoubleType),
                new StructField("fractal_dimension_se", DataTypes.DoubleType),
                new StructField("radius_worst", DataTypes.DoubleType),
                new StructField("texture_worst", DataTypes.DoubleType),
                new StructField("perimeter_worst", DataTypes.DoubleType),
                new StructField("area_worst", DataTypes.DoubleType),
                new StructField("smoothness_worst", DataTypes.DoubleType),
                new StructField("compactness_worst", DataTypes.DoubleType),
                new StructField("concavity_worst", DataTypes.DoubleType),
                new StructField("concave points_worst", DataTypes.DoubleType),
                new StructField("symmetry_worst", DataTypes.DoubleType),
                new StructField("fractal_dimension_worst", DataTypes.DoubleType)
        );

        try {
            data = Read.csv(Paths.getTestData("classification/breastcancer.csv"), CSVFormat.DEFAULT.withFirstRecordAsHeader(), schema);
            data = data.drop("id");
            x = formula.x(data).toArray();
            y = formula.y(data).toIntArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'breast cancer': " + ex);
            System.exit(-1);
        }
    }
}
