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
import smile.validation.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class XMeansTest {
    
    public XMeansTest() {
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
     * Test of learn method, of class XMeans.
     */
    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = USPS.x;
        int[] y = USPS.y;
        double[][] testx = USPS.testx;
        int[] testy = USPS.testy;
            
        XMeans model = XMeans.fit(x, 10);
        System.out.println(model);

        double r = RandIndex.of(y, model.y);
        double r2 = AdjustedRandIndex.of(y, model.y);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8965, r, 1E-4);
        assertEquals(0.4564, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.y));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.y));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.y));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.y));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.y));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.y));

        int[] p = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            p[i] = model.predict(testx[i]);
        }
            
        r = RandIndex.of(testy, p);
        r2 = AdjustedRandIndex.of(testy, p);
        System.out.format("Testing rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8872, r, 1E-4);
        assertEquals(0.4076, r2, 1E-4);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}
