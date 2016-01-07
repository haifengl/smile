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
public class SignalNoiseRatioTest {
    
    public SignalNoiseRatioTest() {
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
     * Test of rank method, of class SignalNoiseRatio.
     */
    @Test
    public void testRank() {
        System.out.println("rank");
        try {
            ArffParser arffParser = new ArffParser();
            arffParser.setResponseIndex(4);
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);
            
            for (int i = 0; i < y.length; i++) {
                if (y[i] < 2) y[i] = 0;
                else y[i] = 1;
            }

            SignalNoiseRatio s2n = new SignalNoiseRatio();
            double[] ratio = s2n.rank(x, y);
            assertEquals(4, ratio.length);
            assertEquals(0.8743107, ratio[0], 1E-7);
            assertEquals(0.1502717, ratio[1], 1E-7);   
            assertEquals(1.3446912, ratio[2], 1E-7);   
            assertEquals(1.4757334, ratio[3], 1E-7); 
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
