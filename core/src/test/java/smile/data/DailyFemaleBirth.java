/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.data;

import java.util.ArrayList;

import org.apache.commons.csv.CSVFormat;

import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.io.CSV;
import smile.util.Paths;

/**
 * A time series dataset depicting the total number of female births recording
 * in California, USA during the year of 1959.
 * 
 * <pre>
 *   "date","births"
 *   "1959-01-01",35
 *   "1959-01-02",32
 *   ...
 * </pre>
 * 
 * @see https://www.kaggle.com/dougcresswell/daily-total-female-births-in-california-1959
 * 
 * @author rayeaster
 */
public class DailyFemaleBirth {

    public static DataFrame data;
    public static double[] timeseries;

    private static final String DATA_COL_NAME = "births";
    static {
//        ArrayList<StructField> fields = new ArrayList<>();
//        fields.add(new StructField("date", DataTypes.StringType));
//        fields.add(new StructField(DATA_COL_NAME, DataTypes.IntegerType));
//        StructType schema = DataTypes.struct(fields);

        // csv parser will figure out the schema automatically
        CSVFormat format = CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim();
        CSV csv = new CSV(format);
//        csv.schema(schema);

        try {
            data = csv.read(Paths.getTestData("timeseries/daily-total-female-births-CA.csv"));

            BaseVector birthVec = data.column(DATA_COL_NAME);
            timeseries = birthVec.toDoubleArray();

        } catch (Exception ex) {
            System.err.println("Failed to load 'DailyFemaleBirth': " + ex);
            System.exit(-1);
        }
    }
}
