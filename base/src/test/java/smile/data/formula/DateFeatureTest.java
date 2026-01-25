/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.formula;

import org.junit.jupiter.api.*;
import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructType;
import smile.io.Read;
import smile.io.Paths;
import static org.junit.jupiter.api.Assertions.*;
import static smile.data.formula.Terms.date;

/**
 *
 * @author Haifeng Li
 */
public class DateFeatureTest {
    
    public DateFeatureTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of attributes method, of class DateFeature.
     */
    @Test
    public void testDateFeatures() throws Exception {
        System.out.println("date");

        double[][] result = {
                {2001.0, 4.0, 3.0, 2.0, 12.0, 12.0, 12.0},
                {2001.0, 5.0, 3.0, 4.0, 12.0, 59.0, 55.0},
        };

        Formula formula = Formula.rhs(date("timestamp",
                DateFeature.YEAR, DateFeature.MONTH, DateFeature.DAY_OF_MONTH,
                DateFeature.DAY_OF_WEEK, DateFeature.HOUR, DateFeature.MINUTE,
                DateFeature.SECOND));
        var data = Read.arff(Paths.getTestData("weka/date.arff"));
        DataFrame output = formula.frame(data);
        assertEquals(7, output.columns().size());

        StructType schema = output.schema();
        System.out.println(schema);
        System.out.println(output);

        for (int i = 0; i < output.columns().size(); i++) {
            assertEquals(DataTypes.IntType, schema.field(i).dtype());
            if (i == 1 || i == 3) {
                assertTrue(schema.field(i).measure() instanceof NominalScale);
            } else {
                assertNull(schema.field(i).measure());
            }
        }

        for (int i = 0; i < output.size(); i++) {
            for (int j = 0; j < output.ncol(); j++) {
                assertEquals(result[i][j], output.getDouble(i, j), 1E-7);
            }
        }

        assertEquals("APRIL", output.getScale(0, 1));
        assertEquals("MAY", output.getScale(1, 1));
        assertEquals("TUESDAY", output.getScale(0, 3));
        assertEquals("THURSDAY", output.getScale(1, 3));
    }
}
