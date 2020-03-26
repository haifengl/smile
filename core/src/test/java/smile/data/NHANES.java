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
 * This data is from NHANES I with followup mortality data of the NHANES I Epidemiologic Followup Study.
 * 
 * <pre>https://wwwn.cdc.gov/nchs/nhanes/nhanes1</pre> 
 * <pre>https://wwwn.cdc.gov/nchs/nhanes/nhefs</pre>
 * 
 * @author ray
 */
public class NHANES {

    public static DataFrame data;
    public static DataFrame xdata;
    public static DataFrame ydata;
    public static Formula formula = Formula.lhs("y");

    public static double[][] x;
    public static double[] y;

    static {
    	List<StructField> xfields = new ArrayList<StructField>(); 
    	xfields.add(new StructField("Row", DataTypes.IntegerType));
    	xfields.add(new StructField("Age", DataTypes.DoubleType));
    	xfields.add(new StructField("Diastolic BP", DataTypes.DoubleType));
    	xfields.add(new StructField("Poverty index", DataTypes.DoubleType));
    	xfields.add(new StructField("Race", DataTypes.DoubleType));
    	xfields.add(new StructField("Red blood cells", DataTypes.DoubleType));
    	xfields.add(new StructField("Sedimentation rate", DataTypes.DoubleType));
    	xfields.add(new StructField("Serum Albumin", DataTypes.DoubleType));
    	xfields.add(new StructField("Serum Cholesterol", DataTypes.DoubleType));
    	xfields.add(new StructField("Serum Iron", DataTypes.DoubleType));
    	xfields.add(new StructField("Serum Magnesium", DataTypes.DoubleType));
    	xfields.add(new StructField("Serum Protein", DataTypes.DoubleType));
    	xfields.add(new StructField("Sex", DataTypes.DoubleType));
    	xfields.add(new StructField("Systolic BP", DataTypes.DoubleType));
    	xfields.add(new StructField("TIBC", DataTypes.DoubleType));
    	xfields.add(new StructField("TS", DataTypes.DoubleType));
    	xfields.add(new StructField("White blood cells", DataTypes.DoubleType));
    	xfields.add(new StructField("BMI", DataTypes.DoubleType));
    	xfields.add(new StructField("Pulse pressure", DataTypes.DoubleType));
    	   	
    	List<StructField> yfields = new ArrayList<StructField>();
    	yfields.add(new StructField("Row", DataTypes.IntegerType));
    	yfields.add(new StructField("y", DataTypes.DoubleType));
    	
    	List<StructField> fields = new ArrayList<StructField>();    	
    	fields.addAll(xfields);
    	fields.addAll(yfields);
    	
        StructType xschema = DataTypes.struct(xfields);
        StructType yschema = DataTypes.struct(yfields);
        
        try {
            CSVFormat format = CSVFormat.DEFAULT;
            format = format.withSkipHeaderRecord();            
            CSV csv = new CSV(format);
            csv.schema(xschema);
            xdata = csv.read(Paths.getTestData("regression/NHANESI_subset_X.csv"));
            xdata = xdata.drop("Row");

            CSVFormat yformat = CSVFormat.DEFAULT;
            yformat = yformat.withSkipHeaderRecord();            
            CSV ycsv = new CSV(yformat);
            ycsv.schema(yschema);
            ydata = ycsv.read(Paths.getTestData("regression/NHANESI_subset_y.csv"));
            ydata = ydata.drop("Row");
            
            data = xdata.merge(ydata);
                        
            x = formula.x(data).toArray();            
            y = formula.y(data).toDoubleArray();
        } catch (Exception ex) {
            System.err.println("Failed to load 'NHANES': " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }
    }
}
