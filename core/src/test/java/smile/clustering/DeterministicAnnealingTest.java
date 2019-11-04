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

package smile.clustering;

import smile.data.USPS;
import smile.math.MathEx;
import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
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
public class DeterministicAnnealingTest {
    
    public DeterministicAnnealingTest() {
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
     * Test of learn method, of class DeterministicAnnealing.
     */
    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = USPS.x;
        int[] y = USPS.y;
        double[][] testx = USPS.testx;
        int[] testy = USPS.testy;

        DeterministicAnnealing annealing = DeterministicAnnealing.fit(x, 10, 0.8, 100, 1E-4, 1E-2);
            
        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
        double r = rand.measure(y, annealing.y);
        double r2 = ari.measure(y, annealing.y);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8975, r, 1E-4);
        assertEquals(0.4701, r2, 1E-4);
            
        int[] p = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            p[i] = annealing.predict(testx[i]);
        }
            
        r = rand.measure(testy, p);
        r2 = ari.measure(testy, p);
        System.out.format("Testing rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8995, r, 1E-4);
        assertEquals(0.4745, r2, 1E-4);
    }
}
