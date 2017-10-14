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

import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class SparseOneHotEncoderTest {
    
    public SparseOneHotEncoderTest() {
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
     * Test of feature method, of class SparseOneHotEncoder.
     */
    @Test
    public void testFeature() {
        System.out.println("feature");
        int[][] result = {
            {0, 3, 6, 9},
            {0, 3, 6, 8},
            {1, 3, 6, 9},
            {2, 4, 6, 9},
            {2, 5, 7, 9},
            {2, 5, 7, 8},
            {1, 5, 7, 8},
            {0, 4, 6, 9},
            {0, 5, 7, 9},
            {2, 4, 7, 9},
            {0, 4, 7, 8},
            {1, 4, 6, 8},
            {1, 3, 7, 9},
            {2, 4, 6, 8}
        };
        
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);

            SparseOneHotEncoder n2sb = new SparseOneHotEncoder(weather.attributes());
            for (int i = 0; i < x.length; i++) {
                int[] y = n2sb.feature(x[i]);
                assertEquals(result[i].length, y.length);
                for (int j = 0; j < y.length; j++) {
                    assertEquals(result[i][j], y[j]);
                }
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
