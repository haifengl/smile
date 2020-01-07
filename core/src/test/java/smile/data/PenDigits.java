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
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.Read;
import smile.util.Paths;

import java.util.ArrayList;
import java.util.stream.IntStream;

/**
 *
 * @author Haifeng
 */
public class PenDigits {

    public static DataFrame data;
    public static Formula formula = Formula.lhs("class");
    public static double[][] x;
    public static int[] y;

    static {
        ArrayList<StructField> fields = new ArrayList<>();
        IntStream.range(1, 17).forEach(i -> fields.add(new StructField("V"+i, DataTypes.DoubleType)));
        fields.add(new StructField("class", DataTypes.ByteType));
        StructType schema = DataTypes.struct(fields);

        try {
            data = Read.csv(Paths.getTestData("classification/pendigits.txt"), CSVFormat.DEFAULT.withDelimiter('\t'), schema);
            x = formula.x(data).toArray();
            y = formula.y(data).toIntArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'pendigitis': " + ex);
            System.exit(-1);
        }
    }
}
