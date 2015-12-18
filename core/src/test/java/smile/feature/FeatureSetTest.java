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
public class FeatureSetTest {
    
    public FeatureSetTest() {
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
     * Test of attributes method, of class FeatureSet.
     */
    @Test
    public void testAttributes() {
        System.out.println("attributes");
        try {
            ArffParser parser = new ArffParser();
            AttributeDataset data = parser.parse(this.getClass().getResourceAsStream("/smile/data/weka/regression/abalone.arff"));
            double[][] x = data.toArray(new double[data.size()][]);
            
            FeatureSet<double[]> features = new FeatureSet<double[]>();
            features.add(new Nominal2Binary(data.attributes()));
            features.add(new NumericAttributeFeature(data.attributes(), 0.05, 0.95, x));
            Attribute[] attributes = features.attributes();
            assertEquals(11, attributes.length);
            for (int i = 0; i < attributes.length; i++) {
                System.out.println(attributes[i]);
                assertEquals(Attribute.Type.NUMERIC, attributes[i].type);
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of f method, of class FeatureSet.
     */
    @Test
    public void testF() {
        System.out.println("f");
        try {
            ArffParser parser = new ArffParser();
            AttributeDataset data = parser.parse(this.getClass().getResourceAsStream("/smile/data/weka/regression/abalone.arff"));
            double[][] x = data.toArray(new double[data.size()][]);
            
            FeatureSet<double[]> features = new FeatureSet<double[]>();
            features.add(new Nominal2Binary(data.attributes()));
            features.add(new NumericAttributeFeature(data.attributes(), 0.05, 0.95, x));
            
            AttributeDataset dataset = features.f(data);
            assertEquals(data.size(), dataset.size());
            assertEquals(data.getName(), dataset.getName());
            assertEquals(data.getDescription(), dataset.getDescription());
            
            Attribute[] attributes = features.attributes();
            for (int i = 0; i < attributes.length; i++) {
                assertEquals(attributes[i].name, dataset.attributes()[i].name);
                assertEquals(attributes[i].type, dataset.attributes()[i].type);
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
