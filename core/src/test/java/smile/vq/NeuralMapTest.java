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

package smile.vq;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.USPS;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.CSV;
import smile.util.Paths;
import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.stream.IntStream;

/**
 *
 * @author Haifeng
 */
public class NeuralMapTest {
    
    public NeuralMapTest() {
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
     * Test of learn method, of class NeuralMap.
     */
    @Test(expected = Test.None.class)
    public void testUSPS() {
        System.out.println("USPS");

        double[][] x = USPS.x;
        int[] y = USPS.y;
        double[][] testx = USPS.testx;
        int[] testy = USPS.testy;

        NeuralMap cortex = new NeuralMap(x[0].length, 8.0, 0.05, 0.0006, 5, 3);

        for (int i = 0; i < 5; i++) {
            for (double[] xi : x) {
                cortex.update(xi);
            }
        }

        cortex.purge(16);
        cortex.partition(10);
            
        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();

        int[] p = new int[x.length];
        for (int i = 0; i < x.length; i++) {
            p[i] = cortex.predict(x[i]);
        }
            
        double r = rand.measure(y, p);
        double r2 = ari.measure(y, p);
        System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        //assertTrue(r > 0.65);
        //assertTrue(r2 > 0.18);
            
        p = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            p[i] = cortex.predict(testx[i]);
        }
            
        r = rand.measure(testy, p);
        r2 = ari.measure(testy, p);
        System.out.format("Testing rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        //assertTrue(r > 0.65);
        //assertTrue(r2 > 0.18);
    }
}
