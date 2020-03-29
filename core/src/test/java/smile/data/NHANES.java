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

import org.apache.commons.csv.CSVFormat;

import smile.data.formula.Formula;
import smile.io.Read;
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
    try {
      data = Read.csv(
              Paths.getTestData("regression/NHANESI_subset.csv"),
              CSVFormat.DEFAULT.withFirstRecordAsHeader());

      x = formula.x(data).toArray();
      y = formula.y(data).toDoubleArray();
    } catch (Exception ex) {
      System.err.println("Failed to load 'NHANES': " + ex);
      ex.printStackTrace();
      System.exit(-1);
    }
  }
}
