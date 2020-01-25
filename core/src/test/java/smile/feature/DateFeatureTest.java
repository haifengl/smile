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

package smile.feature;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Optional;
import smile.data.DataFrame;
import smile.data.Date;
import smile.data.formula.Formula;
import smile.data.formula.DateFeature;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructType;
import static org.junit.Assert.*;
import static smile.data.formula.Terms.date;

/**
 *
 * @author Haifeng Li
 */
public class DateFeatureTest {
    
    public DateFeatureTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of attributes method, of class DateFeature.
     */
    @Test
    public void testDateFeatures() {
        System.out.println("date");

        double[][] result = {
                {2001.0, 4.0, 3.0, 2.0, 12.0, 12.0, 12.0},
                {2001.0, 5.0, 3.0, 4.0, 12.0, 59.0, 55.0},
        };

        Formula formula = Formula.rhs(date("timestamp", DateFeature.YEAR, DateFeature.MONTH, DateFeature.DAY_OF_MONTH, DateFeature.DAY_OF_WEEK, DateFeature.HOURS, DateFeature.MINUTES, DateFeature.SECONDS));
        DataFrame output = formula.apply(Date.data);
        assertEquals(output.ncols(), 7);

        StructType schema = output.schema();
        System.out.println(schema);
        System.out.println(output);

        for (int i = 0; i < output.ncols(); i++) {
            assertEquals(DataTypes.IntegerType, schema.field(i).type);
            if (i == 1 || i == 3) {
                assertTrue(schema.field(i).measure instanceof NominalScale);
            } else {
                assertEquals(null, schema.field(i).measure);
            }
        }

        for (int i = 0; i < output.nrows(); i++) {
            for (int j = 0; j < output.ncols(); j++) {
                assertEquals(result[i][j], output.getDouble(i, j), 1E-7);
            }
        }

        assertEquals("APRIL", output.getScale(0, 1));
        assertEquals("MAY", output.getScale(1, 1));
        assertEquals("TUESDAY", output.getScale(0, 3));
        assertEquals("THURSDAY", output.getScale(1, 3));
    }
}
