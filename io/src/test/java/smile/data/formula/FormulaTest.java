/*
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
 */
package smile.data.formula;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.Read;
import smile.util.Paths;

import static smile.data.formula.Terms.*;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class FormulaTest {

    DataFrame weather;

    public FormulaTest() {
        try {
            weather = Read.arff(Paths.getTestData("weka/weather.nominal.arff"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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

    @Test
    public void testInteraction() {
        System.out.println("interaction");

        Formula formula = Formula.rhs(interact("outlook", "temperature", "humidity"));
        assertEquals(" ~ outlook:temperature:humidity", formula.toString());

        DataFrame output = formula.frame(weather);
        System.out.println(output);

        StructType schema = DataTypes.struct(
                new StructField(
                        "outlook:temperature:humidity",
                        DataTypes.IntegerType,
                        new NominalScale(
                                "sunny:hot:high", "sunny:hot:normal", "sunny:mild:high", "sunny:mild:normal",
                                "sunny:cool:high", "sunny:cool:normal", "overcast:hot:high", "overcast:hot:normal",
                                "overcast:mild:high", "overcast:mild:normal", "overcast:cool:high", "overcast:cool:normal",
                                "rainy:hot:high", "rainy:hot:normal", "rainy:mild:high", "rainy:mild:normal",
                                "rainy:cool:high", "rainy:cool:normal")
                )
        );
        assertEquals(schema, output.schema());

        assertEquals("sunny:hot:high", output.getString(0, 0));
        assertEquals("sunny:hot:high", output.getString(1, 0));
        assertEquals("overcast:hot:high", output.getString(2, 0));
        assertEquals("rainy:mild:high", output.getString(3, 0));
        assertEquals("rainy:cool:normal", output.getString(4, 0));
        assertEquals("rainy:cool:normal", output.getString(5, 0));
        assertEquals("overcast:cool:normal", output.getString(6, 0));
        assertEquals("sunny:mild:high", output.getString(7, 0));
        assertEquals("sunny:cool:normal", output.getString(8, 0));
        assertEquals("rainy:mild:normal", output.getString(9, 0));
        assertEquals("sunny:mild:normal", output.getString(10, 0));
        assertEquals("overcast:mild:high", output.getString(11, 0));
        assertEquals("overcast:hot:normal", output.getString(12, 0));
        assertEquals("rainy:mild:high", output.getString(13, 0));
    }

    @Test
    public void testCrossing() {
        System.out.println("crossing");

        Formula formula = Formula.rhs(cross(2, "outlook", "temperature", "humidity"));
        assertEquals(" ~ (outlook x temperature x humidity)^2", formula.toString());

        DataFrame output = formula.frame(weather);
        System.out.println(output);

        StructType schema = DataTypes.struct(
                new StructField("outlook", DataTypes.ByteType, new NominalScale("sunny", "overcast", "rainy")),
                new StructField("temperature", DataTypes.ByteType, new NominalScale("hot", "mild", "cool")),
                new StructField("humidity", DataTypes.ByteType, new NominalScale("high", "normal")),
                new StructField("outlook:temperature", DataTypes.IntegerType, new NominalScale("sunny:hot", "sunny:mild", "sunny:cool", "overcast:hot", "overcast:mild", "overcast:cool", "rainy:hot", "rainy:mild", "rainy:cool")),
                new StructField("outlook:humidity", DataTypes.IntegerType, new NominalScale("sunny:high", "sunny:normal", "overcast:high", "overcast:normal", "rainy:high", "rainy:normal")),
                new StructField("temperature:humidity", DataTypes.IntegerType, new NominalScale("hot:high", "hot:normal", "mild:high", "mild:normal", "cool:high", "cool:normal"))
        );
        assertEquals(schema, output.schema());

        assertEquals("sunny:hot", output.getString(0, 3));
        assertEquals("sunny:hot", output.getString(1, 3));
        assertEquals("overcast:hot", output.getString(2, 3));
        assertEquals("rainy:mild", output.getString(3, 3));
        assertEquals("rainy:cool", output.getString(4, 3));
        assertEquals("rainy:cool", output.getString(5, 3));
        assertEquals("overcast:cool", output.getString(6, 3));
        assertEquals("sunny:mild", output.getString(7, 3));
        assertEquals("sunny:cool", output.getString(8, 3));
        assertEquals("rainy:mild", output.getString(9, 3));
        assertEquals("sunny:mild", output.getString(10, 3));
        assertEquals("overcast:mild", output.getString(11, 3));
        assertEquals("overcast:hot", output.getString(12, 3));
        assertEquals("rainy:mild", output.getString(13, 3));
    }
}