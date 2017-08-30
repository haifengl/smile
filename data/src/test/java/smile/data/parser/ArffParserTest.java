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
package smile.data.parser;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.Attribute;
import smile.data.AttributeDataset;

/**
 *
 * @author Haifeng Li
 */
public class ArffParserTest {
    
    public ArffParserTest() {
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
     * Test of parse method, of class ArffParser.
     */
    @Test
    public void testParseWeather() throws Exception {
        System.out.println("weather");
        try {
            ArffParser arffParser = new ArffParser();
            arffParser.setResponseIndex(4);
            AttributeDataset weather = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);
            int[] y = weather.toArray(new int[weather.size()]);
            
            assertEquals(Attribute.Type.NOMINAL, weather.responseAttribute().getType());
            for (Attribute attribute : weather.attributes()) {
                assertEquals(Attribute.Type.NOMINAL, attribute.getType());
            }
            
            assertEquals(14, weather.size());
            assertEquals(4, weather.attributes().length);
            assertEquals("no", weather.responseAttribute().toString(y[0]));
            assertEquals("no", weather.responseAttribute().toString(y[1]));
            assertEquals("yes", weather.responseAttribute().toString(y[2]));
            assertEquals("sunny", weather.attributes()[0].toString(x[0][0]));
            assertEquals("hot", weather.attributes()[1].toString(x[0][1]));
            assertEquals("high", weather.attributes()[2].toString(x[0][2]));
            assertEquals("FALSE", weather.attributes()[3].toString(x[0][3]));

            assertEquals("no", weather.responseAttribute().toString(y[13]));
            assertEquals("rainy", weather.attributes()[0].toString(x[13][0]));
            assertEquals("mild", weather.attributes()[1].toString(x[13][1]));
            assertEquals("high", weather.attributes()[2].toString(x[13][2]));
            assertEquals("TRUE", weather.attributes()[3].toString(x[13][3]));
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of parse method, of class ArffParser.
     */
    @Test
    public void testParseIris() throws Exception {
        System.out.println("iris");
        try {
            ArffParser arffParser = new ArffParser();
            arffParser.setResponseIndex(4);
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);
            
            assertEquals(Attribute.Type.NOMINAL, iris.responseAttribute().getType());
            for (Attribute attribute : iris.attributes()) {
                assertEquals(Attribute.Type.NUMERIC, attribute.getType());
            }
            
            assertEquals(150, iris.size());
            assertEquals(4, iris.attributes().length);
            assertEquals("Iris-setosa", iris.responseAttribute().toString(y[0]));
            assertEquals("Iris-setosa", iris.responseAttribute().toString(y[1]));
            assertEquals("Iris-setosa", iris.responseAttribute().toString(y[2]));
            assertEquals(5.1, x[0][0], 1E-7);
            assertEquals(3.5, x[0][1], 1E-7);
            assertEquals(1.4, x[0][2], 1E-7);
            assertEquals(0.2, x[0][3], 1E-7);

            assertEquals("Iris-virginica", iris.responseAttribute().toString(y[149]));
            assertEquals(5.9, x[149][0], 1E-7);
            assertEquals(3.0, x[149][1], 1E-7);
            assertEquals(5.1, x[149][2], 1E-7);
            assertEquals(1.8, x[149][3], 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of parse method, of class ArffParser.
     */
    @Test
    public void testParseString() throws Exception {
        System.out.println("string");
        try {
            ArffParser arffParser = new ArffParser();
            AttributeDataset string = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/string.arff"));
            double[][] x = string.toArray(new double[string.size()][]);
            
            for (Attribute attribute : string.attributes()) {
                assertEquals(Attribute.Type.STRING, attribute.getType());
            }
            
            Attribute[] attributes = string.attributes();
            assertEquals(5, string.size());
            assertEquals(2, attributes.length);
            assertEquals("AG5", attributes[0].toString(x[0][0]));
            assertEquals("Encyclopedias and dictionaries.;Twentieth century.", attributes[1].toString(x[0][1]));
            assertEquals("AS281", attributes[0].toString(x[4][0]));
            assertEquals("Astronomy, Assyro-Babylonian.;Moon -- Tables.", attributes[1].toString(x[4][1]));
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of parse method, of class ArffParser.
     */
    @Test
    public void testParseSparse() throws Exception {
        System.out.println("sparse");
        try {
            ArffParser arffParser = new ArffParser();
            AttributeDataset sparse = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/sparse.arff"));
            double[][] x = sparse.toArray(new double[sparse.size()][]);
                        
            assertEquals(2, sparse.size());
            assertEquals(5, sparse.attributes().length);
            
            assertEquals(0.0, x[0][0], 1E-7);
            assertEquals(2.0, x[0][1], 1E-7);
            assertEquals(0.0, x[0][2], 1E-7);
            assertEquals(3.0, x[0][3], 1E-7);
            assertEquals(0.0, x[0][4], 1E-7);
            
            assertEquals(0.0, x[1][0], 1E-7);
            assertEquals(0.0, x[1][1], 1E-7);
            assertEquals(1.0, x[1][2], 1E-7);
            assertEquals(0.0, x[1][3], 1E-7);
            assertEquals(1.0, x[1][4], 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
