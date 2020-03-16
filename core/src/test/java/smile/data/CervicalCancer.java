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
import smile.io.CSV;
import smile.util.Paths;

/**
 * The dataset was collected at 'Hospital Universitario de Caracas' in Caracas, Venezuela. 
 * <p>
 * The dataset comprises demographic information, habits, and historic medical records of 858 patients. 
 * <p>
 * <pre>https://archive.ics.uci.edu/ml/datasets/Cervical+cancer+%28Risk+Factors%29#</pre>
 * 
 * In the modified dataset <i>risk_factors_cervical_cancer.csv</i>, we replace all missing value simply with 0 and 
 * remove 3 variables: Hinselmann/Schiller/Citology to keep only <b>Biopsy</b> as the final response.
 * <p>
 * Original data file is <i>risk_factors_cervical_cancer_original.csv</i>
 * 
 * @author ray
 */
public class CervicalCancer {

    public static DataFrame data;
    public static Formula formula = Formula.lhs("Biopsy");

    public static double[][] x;
    public static double[] y;

    static {
        StructType schema = DataTypes.struct(
                new StructField("Age", DataTypes.DoubleType),
                new StructField("Number of sexual partners", DataTypes.DoubleType),
                new StructField("First sexual intercourse (age)", DataTypes.DoubleType),
                new StructField("Num of pregnancies", DataTypes.DoubleType),
                new StructField("Smokes", DataTypes.DoubleType),
                new StructField("Smokes (years)", DataTypes.DoubleType),
                new StructField("Smokes (packs/year) ", DataTypes.DoubleType),
                new StructField("Hormonal Contraceptives", DataTypes.DoubleType),
                new StructField("Hormonal Contraceptives (years)", DataTypes.DoubleType),
                new StructField("IUD", DataTypes.DoubleType),
                new StructField("IUD (years)", DataTypes.DoubleType),
                new StructField("STDs", DataTypes.DoubleType),
                new StructField("STDs (number)", DataTypes.DoubleType),
                new StructField("STDs:condylomatosis", DataTypes.DoubleType),
                new StructField("STDs:cervical condylomatosis", DataTypes.DoubleType),
                new StructField("STDs:vaginal condylomatosis", DataTypes.DoubleType),
                new StructField("STDs:vulvo-perineal condylomatosis", DataTypes.DoubleType),
                new StructField("STDs:syphilis", DataTypes.DoubleType),
                new StructField("STDs:pelvic inflammatory disease", DataTypes.DoubleType),
                new StructField("STDs:genital herpes", DataTypes.DoubleType),
                new StructField("STDs:molluscum contagiosum", DataTypes.DoubleType),
                new StructField("STDs:AIDS", DataTypes.DoubleType),
                new StructField("STDs:HIV", DataTypes.DoubleType),
                new StructField("STDs:Hepatitis B", DataTypes.DoubleType),
                new StructField("STDs:HPV", DataTypes.DoubleType),
                new StructField("STDs: Number of diagnosis", DataTypes.DoubleType),
                new StructField("STDs: Time since first diagnosis", DataTypes.DoubleType),
                new StructField("STDs: Time since last diagnosis", DataTypes.DoubleType),
                new StructField("Dx:Cancer", DataTypes.DoubleType),
                new StructField("Dx:CIN", DataTypes.DoubleType),
                new StructField("Dx:HPV", DataTypes.DoubleType),
                new StructField("Dx", DataTypes.DoubleType),
                new StructField("Biopsy", DataTypes.DoubleType)
        );

        CSVFormat format = CSVFormat.DEFAULT;
        //format = format.withNullString("?");
        
        CSV csv = new CSV(format);
        csv.schema(schema);

        try {
            data = csv.read(Paths.getTestData("uci/risk_factors_cervical_cancer.csv"));
            
            x = formula.x(data).toArray();            
            y = formula.y(data).toDoubleArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'Cervical Cancer': " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }
    }
}
