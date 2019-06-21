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

import static org.junit.Assert.*;
import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;

/**
 *
 * @author Haifeng
 */
public class OneHotEncoderTest {
    
    public OneHotEncoderTest() {
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
     * Test of attributes method, of class OneHotEncoder.
     */
    @SuppressWarnings("unused")
    @Test
    public void testAttributes() {
        System.out.println("attributes");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(smile.util.Paths.getTestData("weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);
            
            OneHotEncoder n2b = new OneHotEncoder(weather.attributes());
            Attribute[] attributes = n2b.attributes();
            assertEquals(10, attributes.length);
            for (int i = 0; i < attributes.length; i++) {
                System.out.println(attributes[i]);
                assertEquals(Attribute.Type.NUMERIC, attributes[i].getType());
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of f method, of class OneHotEncoder.
     */
    @Test
    public void testFeature() {
        System.out.println("feature");
        double[][] result = {
            {1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0},
            {1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0},
            {0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0},
            {0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0},
            {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0},
            {1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0},
            {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0},
            {0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0},
            {0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0}
        };

        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(smile.util.Paths.getTestData("weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);

            OneHotEncoder n2b = new OneHotEncoder(weather.attributes());
            for (int i = 0; i < x.length; i++) {
                double[] y = n2b.feature(x[i]);
                for (int j = 0; j < y.length; j++) {
                    assertEquals(result[i][j], y[j], 1E-7);
                }
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
