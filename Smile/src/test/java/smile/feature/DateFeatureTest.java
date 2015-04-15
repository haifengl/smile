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
    public void testAttributes() {
        System.out.println("attributes");
        try {
            ArffParser parser = new ArffParser();
            AttributeDataset data = parser.parse(this.getClass().getResourceAsStream("/smile/data/weka/date.arff"));
            
            DateFeature.Type[] features = {DateFeature.Type.YEAR, DateFeature.Type.MONTH, DateFeature.Type.DAY_OF_MONTH, DateFeature.Type.DAY_OF_WEEK, DateFeature.Type.HOURS, DateFeature.Type.MINUTES, DateFeature.Type.SECONDS};
            DateFeature df = new DateFeature(data.attributes(), features);
            Attribute[] attributes = df.attributes();
            assertEquals(features.length, attributes.length);
            for (int i = 0; i < attributes.length; i++) {
                System.out.println(attributes[i]);
                assertEquals(Attribute.Type.NUMERIC, attributes[i].type);
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of f method, of class DateFeature.
     */
    @Test
    public void testF() {
        System.out.println("f");
        double[][] result = {
            {2001.0, 3.0, 3.0, 2.0, 12.0, 12.0, 12.0},
            {2001.0, 4.0, 3.0, 4.0, 12.0, 59.0, 55.0},
        };

        try {
            ArffParser parser = new ArffParser();
            AttributeDataset data = parser.parse(this.getClass().getResourceAsStream("/smile/data/weka/date.arff"));
            double[][] x = data.toArray(new double[data.size()][]);
            
            DateFeature.Type[] features = {DateFeature.Type.YEAR, DateFeature.Type.MONTH, DateFeature.Type.DAY_OF_MONTH, DateFeature.Type.DAY_OF_WEEK, DateFeature.Type.HOURS, DateFeature.Type.MINUTES, DateFeature.Type.SECONDS};
            DateFeature df = new DateFeature(data.attributes(), features);
            Attribute[] attributes = df.attributes();
            assertEquals(features.length, attributes.length);
            for (int i = 0; i < x.length; i++) {
                double[] y = new double[attributes.length];
                for (int j = 0; j < y.length; j++) {
                    y[j] = df.f(x[i], j);
                    assertEquals(result[i][j], y[j], 1E-7);
                }
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
