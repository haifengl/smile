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
package smile.vq;

import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
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
public class NeuralGasTest {
    
    public NeuralGasTest() {
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
     * Test of learn method, of class NeuralGas.
     */
    @Test
    public void testUSPS() {
        System.out.println("USPS");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            NeuralGas gas = new NeuralGas(x, 10);
            
            AdjustedRandIndex ari = new AdjustedRandIndex();
            RandIndex rand = new RandIndex();
            double r = rand.measure(y, gas.getClusterLabel());
            double r2 = ari.measure(y, gas.getClusterLabel());
            System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.88);
            assertTrue(r2 > 0.45);
            
            int[] p = new int[testx.length];
            for (int i = 0; i < testx.length; i++) {
                p[i] = gas.predict(testx[i]);
            }
            
            r = rand.measure(testy, p);
            r2 = ari.measure(testy, p);
            System.out.format("Testing rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.88);
            assertTrue(r2 > 0.45);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
