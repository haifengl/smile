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

package smile.io;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDateTime;
import smile.data.DataFrame;
import smile.data.measure.NominalScale;
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

    @Test
    public void testParseWeather() throws Exception {
        System.out.println("weather");
        Arff arff = new Arff(Paths.getTestData("weka/weather.nominal.arff"));
        DataFrame weather = arff.read();
        System.out.println(weather);

        StructType schema = DataTypes.struct(
                new StructField("outlook", DataTypes.ByteType, new NominalScale("sunny", "overcast", "rainy")),
                new StructField("temperature", DataTypes.ByteType, new NominalScale("hot", "mild", "cool")),
                new StructField("humidity", DataTypes.ByteType, new NominalScale("high", "normal")),
                new StructField("windy", DataTypes.ByteType, new NominalScale("TRUE", "FALSE")),
                new StructField("play", DataTypes.ByteType, new NominalScale("yes", "no")));
        assertEquals(schema, weather.schema());

        assertEquals(14, weather.nrow());
        assertEquals(5, weather.ncol());
        assertEquals("no",    weather.getScale(0, "play"));
        assertEquals("no",    weather.getScale(1, "play"));
        assertEquals("yes",   weather.getScale(2, "play"));
        assertEquals("sunny", weather.getScale(0, 0));
        assertEquals("hot",   weather.getScale(0, 1));
        assertEquals("high",  weather.getScale(0, 2));
        assertEquals("FALSE", weather.getScale(0, 3));

        assertEquals("no",    weather.getScale(13, "play"));
        assertEquals("rainy", weather.getScale(13, 0));
        assertEquals("mild",  weather.getScale(13, 1));
        assertEquals("high",  weather.getScale(13, 2));
        assertEquals("TRUE",  weather.getScale(13, 3));
    }

    @Test
    public void testParseIris() throws Exception {
        System.out.println("iris");
        Arff arff = new Arff(Paths.getTestData("weka/iris.arff"));
        DataFrame iris = arff.read();

        StructType schema = DataTypes.struct(
                new StructField("sepallength", DataTypes.FloatType),
                new StructField("sepalwidth", DataTypes.FloatType),
                new StructField("petallength", DataTypes.FloatType),
                new StructField("petalwidth", DataTypes.FloatType),
                new StructField("class", DataTypes.ByteType, new NominalScale("Iris-setosa", "Iris-versicolor", "Iris-virginica")));
        assertEquals(schema, iris.schema());

        assertEquals(150, iris.nrow());
        assertEquals(5,   iris.ncol());
        assertEquals("Iris-setosa", iris.getScale(0, "class"));
        assertEquals("Iris-setosa", iris.getScale(1, "class"));
        assertEquals("Iris-setosa", iris.getScale(2, "class"));
        assertEquals(5.1, iris.getFloat(0, 0), 1E-7);
        assertEquals(3.5, iris.getFloat(0, 1), 1E-7);
        assertEquals(1.4, iris.getFloat(0, 2), 1E-7);
        assertEquals(0.2, iris.getFloat(0, 3), 1E-7);

        assertEquals("Iris-virginica", iris.getScale(149, "class"));
        assertEquals(5.9, iris.getFloat(149, 0), 1E-7);
        assertEquals(3.0, iris.getFloat(149, 1), 1E-7);
        assertEquals(5.1, iris.getFloat(149, 2), 1E-7);
        assertEquals(1.8, iris.getFloat(149, 3), 1E-7);
    }

    @Test
    public void testParseString() throws Exception {
        System.out.println("string");
        Arff arff = new Arff(Paths.getTestData("weka/string.arff"));
        DataFrame string = arff.read();

        StructType schema = DataTypes.struct(
                new StructField("LCC", DataTypes.StringType),
                new StructField("LCSH", DataTypes.StringType));
        assertEquals(schema, string.schema());

        System.out.println(string);
        System.out.println(string.schema());
        assertEquals(5, string.nrow());
        assertEquals(2, string.ncol());
        assertEquals("AG5", string.get(0).get(0));
        assertEquals("Encyclopedias and dictionaries.;Twentieth century.", string.get(0, 1));
        assertEquals("AS281", string.get(4, 0));
        assertEquals("Astronomy, Assyro-Babylonian.;Moon -- Tables.", string.get(4, 1));
    }

    @Test
    public void testParseDate() throws Exception {
        System.out.println("date");
        Arff arff = new Arff(Paths.getTestData("weka/date.arff"));
        DataFrame date = arff.read();
        System.out.println(date);

        StructType schema = DataTypes.struct(new StructField("timestamp", DataTypes.DateTimeType));
        assertEquals(schema, date.schema());

        assertEquals(2, date.nrow());
        assertEquals(1, date.ncol());
        assertEquals(LocalDateTime.parse("2001-04-03T12:12:12"), date.get(0, 0));
        assertEquals(LocalDateTime.parse("2001-05-03T12:59:55"), date.get(1, 0));
    }

    @Test
    public void testParseSparse() throws Exception {
        System.out.println("sparse");
        Arff arff = new Arff(Paths.getTestData("weka/sparse.arff"));
        DataFrame sparse = arff.read();

        StructType schema = DataTypes.struct(
                new StructField("V1", DataTypes.IntegerType),
                new StructField("V2", DataTypes.ByteType, new NominalScale("U", "W", "X", "Y")),
                new StructField("V3", DataTypes.ByteType, new NominalScale("U", "W", "X", "Y")),
                new StructField("V4", DataTypes.ByteType, new NominalScale("U", "W", "X", "Y")),
                new StructField("class", DataTypes.ByteType, new NominalScale("class A", "class B")));
        assertEquals(schema, sparse.schema());

        assertEquals(2, sparse.nrow());
        assertEquals(5, sparse.ncol());
            
        assertEquals(0.0, sparse.getDouble(0, 0), 1E-7);
        assertEquals(2.0, sparse.getDouble(0, 1), 1E-7);
        assertEquals(0.0, sparse.getDouble(0, 2), 1E-7);
        assertEquals(3.0, sparse.getDouble(0, 3), 1E-7);
        assertEquals(0.0, sparse.getDouble(0, 4), 1E-7);
            
        assertEquals(0.0, sparse.getDouble(1, 0), 1E-7);
        assertEquals(0.0, sparse.getDouble(1, 1), 1E-7);
        assertEquals(1.0, sparse.getDouble(1, 2), 1E-7);
        assertEquals(0.0, sparse.getDouble(1, 3), 1E-7);
        assertEquals(1.0, sparse.getDouble(1, 4), 1E-7);
    }
}
