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
 * This data is from NHANES I with followup mortality data of the NHANES I Epidemiologic Followup
 * Study.
 *
 * <pre>https://wwwn.cdc.gov/nchs/nhanes/nhanes1</pre>
 *
 * <pre>https://wwwn.cdc.gov/nchs/nhanes/nhefs</pre>
 *
 * @author ray
 */
public class NHANES {

  public static DataFrame data;
  public static Formula formula = Formula.lhs("y");

  public static double[][] x;
  public static double[] y;

  static {
    List<StructField> fields = new ArrayList<StructField>();
    fields.add(new StructField("y", DataTypes.DoubleType));
    fields.add(new StructField("Age", DataTypes.DoubleType));
    fields.add(new StructField("Diastolic BP", DataTypes.DoubleType));
    fields.add(new StructField("Poverty index", DataTypes.DoubleType));
    fields.add(new StructField("Race", DataTypes.DoubleType));
    fields.add(new StructField("Red blood cells", DataTypes.DoubleType));
    fields.add(new StructField("Sedimentation rate", DataTypes.DoubleType));
    fields.add(new StructField("Serum Albumin", DataTypes.DoubleType));
    fields.add(new StructField("Serum Cholesterol", DataTypes.DoubleType));
    fields.add(new StructField("Serum Iron", DataTypes.DoubleType));
    fields.add(new StructField("Serum Magnesium", DataTypes.DoubleType));
    fields.add(new StructField("Serum Protein", DataTypes.DoubleType));
    fields.add(new StructField("Sex", DataTypes.DoubleType));
    fields.add(new StructField("Systolic BP", DataTypes.DoubleType));
    fields.add(new StructField("TIBC", DataTypes.DoubleType));
    fields.add(new StructField("TS", DataTypes.DoubleType));
    fields.add(new StructField("White blood cells", DataTypes.DoubleType));
    fields.add(new StructField("BMI", DataTypes.DoubleType));
    fields.add(new StructField("Pulse pressure", DataTypes.DoubleType));

    StructType schema = DataTypes.struct(fields);

    try {
      CSVFormat format = CSVFormat.DEFAULT;
      format = format.withSkipHeaderRecord();
      CSV csv = new CSV(format);
      csv.schema(schema);
      data = csv.read(Paths.getTestData("regression/NHANESI_subset.csv"));
      
      x = formula.x(data).toArray();
      y = formula.y(data).toDoubleArray();
    } catch (Exception ex) {
      System.err.println("Failed to load 'NHANES': " + ex);
      ex.printStackTrace();
      System.exit(-1);
    }
  }
}
