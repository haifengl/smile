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
 * The dataset was collected at 'Hospital Universitario de Caracas' in Caracas, Venezuela.
 *
 * <p>The dataset comprises demographic information, habits, and historic medical records of 858 patients.
 *
 * <pre>https://archive.ics.uci.edu/ml/datasets/Cervical+cancer+%28Risk+Factors%29#</pre>
 *
 * In the modified dataset <i>risk_factors_cervical_cancer.csv</i>, we replace all missing value
 * simply with 0 and remove 3 variables: Hinselmann/Schiller/Citology to keep only <b>Biopsy</b> as
 * the final response.
 *
 * <p>
 *
 * @author ray
 */
public class CervicalCancer {

  public static DataFrame data;
  public static Formula formula = Formula.lhs("Biopsy");

  public static double[][] x;
  public static double[] y;

  static {
    try {
      data = Read.csv(
              Paths.getTestData("uci/risk_factors_cervical_cancer.csv"),
              CSVFormat.DEFAULT.withFirstRecordAsHeader());

      x = formula.x(data).toArray();
      y = formula.y(data).toDoubleArray();
    } catch (Exception ex) {
      System.err.println("Failed to load 'Cervical Cancer': " + ex);
      ex.printStackTrace();
      System.exit(-1);
    }
  }
}
