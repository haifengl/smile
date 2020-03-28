/**
 * ***************************************************************************** Copyright (c)
 * 2010-2019 Haifeng Li
 *
 * <p>Smile is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * <p>Smile is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with Smile. If
 * not, see <https://www.gnu.org/licenses/>.
 * *****************************************************************************
 */
package smile.data;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;

import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.CSV;
import smile.util.Paths;

/**
 * The Boston housing data was collected in 1978 
 * and each of the 506 entries represent aggregated data about 14 features 
 * for homes from various suburbs in Boston, Massachusetts.
 *
 * <pre>https://archive.ics.uci.edu/ml/machine-learning-databases/housing/</pre>
 *
 * @author ray
 */
public class BostonHousing {

  public static DataFrame data;
  public static Formula formula = Formula.lhs("MEDV");

  public static double[][] x;
  public static double[] y;

  static {
    List<StructField> fields = new ArrayList<StructField>();
    fields.add(new StructField("CRIM", DataTypes.DoubleType));
    fields.add(new StructField("ZN", DataTypes.DoubleType));
    fields.add(new StructField("INDUS", DataTypes.DoubleType));
    fields.add(new StructField("CHAS", DataTypes.DoubleType));
    fields.add(new StructField("NOX", DataTypes.DoubleType));
    fields.add(new StructField("RM", DataTypes.DoubleType));
    fields.add(new StructField("AGE", DataTypes.DoubleType));
    fields.add(new StructField("DIS", DataTypes.DoubleType));
    fields.add(new StructField("RAD", DataTypes.DoubleType));
    fields.add(new StructField("TAX", DataTypes.DoubleType));
    fields.add(new StructField("PTRATIO", DataTypes.DoubleType));
    fields.add(new StructField("B", DataTypes.DoubleType));
    fields.add(new StructField("LSTAT", DataTypes.DoubleType));
    fields.add(new StructField("MEDV", DataTypes.DoubleType));

    StructType schema = DataTypes.struct(fields);

    try {
      CSVFormat format = CSVFormat.DEFAULT;
      format = format.withSkipHeaderRecord();
      CSV csv = new CSV(format);
      csv.schema(schema);
      data = csv.read(Paths.getTestData("uci/housing.data"));
      
      x = formula.x(data).toArray();
      y = formula.y(data).toDoubleArray();
    } catch (Exception ex) {
      System.err.println("Failed to load 'Boston Housing': " + ex);
      ex.printStackTrace();
      System.exit(-1);
    }
  }
}
