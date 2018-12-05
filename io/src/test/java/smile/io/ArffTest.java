/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.io;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDateTime;
import smile.data.DataFrame;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.util.Paths;

/**
 *
 * @author Haifeng Li
 */
public class ArffTest {
    
    public ArffTest() {
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
     * Test of read method, of class Arff.
     */
    @Test(expected = Test.None.class)
    public void testParseWeather() throws Exception {
        System.out.println("weather");
        Arff arff = new Arff(Paths.getTestData("weka/weather.nominal.arff"));
        DataFrame weather = arff.read();

        StructType schema = DataTypes.struct(
                new StructField("outlook", DataTypes.IntegerType),
                new StructField("temperature", DataTypes.IntegerType),
                new StructField("humidity", DataTypes.IntegerType),
                new StructField("windy", DataTypes.IntegerType),
                new StructField("play", DataTypes.IntegerType));
        assertEquals(schema, weather.schema());

        assertEquals(14, weather.nrows());
        assertEquals(4, weather.ncols());
        assertEquals("no",    weather.get(0).get("play"));
        assertEquals("no",    weather.get(1).get("play"));
        assertEquals("yes",   weather.get(2).get("play"));
        assertEquals("sunny", weather.get(0).get(0));
        assertEquals("hot",   weather.get(0).get(1));
        assertEquals("high",  weather.get(0).get(2));
        assertEquals("FALSE", weather.get(0).get(3));

        assertEquals("no",    weather.get(13).get("play"));
        assertEquals("rainy", weather.get(13).get(0));
        assertEquals("mild",  weather.get(13).get(1));
        assertEquals("high",  weather.get(13).get(2));
        assertEquals("TRUE",  weather.get(13).get(3));
    }

    /**
     * Test of read method, of class Arff.
     */
    @Test(expected = Test.None.class)
    public void testParseIris() throws Exception {
        System.out.println("iris");
        Arff arff = new Arff(Paths.getTestData("weka/iris.arff"));
        DataFrame iris = arff.read();

        StructType schema = DataTypes.struct(
                new StructField("sepallength", DataTypes.FloatType),
                new StructField("sepalwidth", DataTypes.FloatType),
                new StructField("petallength", DataTypes.FloatType),
                new StructField("petalwidth", DataTypes.FloatType),
                new StructField("class", DataTypes.IntegerType));
        assertEquals(schema, iris.schema());

        assertEquals(150, iris.nrows());
        assertEquals(4,   iris.ncols());
        assertEquals("Iris-setosa", iris.get(0).get("class"));
        assertEquals("Iris-setosa", iris.get(1).get("class"));
        assertEquals("Iris-setosa", iris.get(2).get("class"));
        assertEquals(5.1, iris.get(0).getFloat(0), 1E-7);
        assertEquals(3.5, iris.get(0).getFloat(1), 1E-7);
        assertEquals(1.4, iris.get(0).getFloat(2), 1E-7);
        assertEquals(0.2, iris.get(0).getFloat(3), 1E-7);

        assertEquals("Iris-virginica", iris.get(149).get("class"));
        assertEquals(5.9, iris.get(149).getFloat(0), 1E-7);
        assertEquals(3.0, iris.get(149).getFloat(1), 1E-7);
        assertEquals(5.1, iris.get(149).getFloat(2), 1E-7);
        assertEquals(1.8, iris.get(149).getFloat(3), 1E-7);
    }

    /**
     * Test of read method, of class Arff.
     */
    @Test(expected = Test.None.class)
    public void testParseString() throws Exception {
        System.out.println("string");
        Arff arff = new Arff(Paths.getTestData("weka/string.arff"));
        DataFrame string = arff.read();

        StructType schema = DataTypes.struct(
                new StructField("LCC", DataTypes.StringType),
                new StructField("LCSH", DataTypes.StringType));
        assertEquals(schema, string.schema());

        assertEquals(5, string.nrows());
        assertEquals(2, string.ncols());
        assertEquals("AG5", string.get(0).get(0));
        assertEquals("Encyclopedias and dictionaries.;Twentieth century.", string.get(0).get(1));
        assertEquals("AS281", string.get(4).get(0));
        assertEquals("Astronomy, Assyro-Babylonian.;Moon -- Tables.", string.get(4).get(1));
    }

    /**
     * Test of read method, of class Arff.
     */
    @Test(expected = Test.None.class)
    public void testParseDate() throws Exception {
        System.out.println("date");
        Arff arff = new Arff(Paths.getTestData("weka/date.arff"));
        DataFrame date = arff.read();

        StructType schema = DataTypes.struct(new StructField("timestamp", DataTypes.DateTimeType));
        assertEquals(schema, date.schema());

        assertEquals(2, date.nrows());
        assertEquals(1, date.ncols());
        assertEquals(LocalDateTime.parse("2001-04-03 12:12:12"), date.get(0).get(0));
        assertEquals(LocalDateTime.parse("2001-05-03 12:59:55"), date.get(0).get(1));
    }

    /**
     * Test of read method, of class Arff.
     */
    @Test(expected = Test.None.class)
    public void testParseSparse() throws Exception {
        System.out.println("sparse");
        Arff arff = new Arff(Paths.getTestData("weka/sparse.arff"));
        DataFrame sparse = arff.read();

        StructType schema = DataTypes.struct(
                new StructField("V1", DataTypes.IntegerType),
                new StructField("V2", DataTypes.IntegerType),
                new StructField("V3", DataTypes.IntegerType),
                new StructField("V4", DataTypes.IntegerType),
                new StructField("class", DataTypes.IntegerType));
        assertEquals(schema, sparse.schema());

        assertEquals(2, sparse.nrows());
        assertEquals(5, sparse.ncols());
            
        assertEquals(0.0, sparse.get(0).getDouble(0), 1E-7);
        assertEquals(2.0, sparse.get(0).getDouble(1), 1E-7);
        assertEquals(0.0, sparse.get(0).getDouble(2), 1E-7);
        assertEquals(3.0, sparse.get(0).getDouble(3), 1E-7);
        assertEquals(0.0, sparse.get(0).getDouble(4), 1E-7);
            
        assertEquals(0.0, sparse.get(1).getDouble(0), 1E-7);
        assertEquals(0.0, sparse.get(1).getDouble(1), 1E-7);
        assertEquals(1.0, sparse.get(1).getDouble(2), 1E-7);
        assertEquals(0.0, sparse.get(1).getDouble(3), 1E-7);
        assertEquals(1.0, sparse.get(1).getDouble(4), 1E-7);
    }
}
