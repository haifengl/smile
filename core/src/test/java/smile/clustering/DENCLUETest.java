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

import smile.data.GaussianMixture;
import smile.math.MathEx;
import smile.stat.distribution.MultivariateGaussianDistribution;
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
public class DENCLUETest {
    
    public DENCLUETest() {
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
    
    @Test
    public void testGaussianMixture() {
        System.out.println("Gaussian Mixture");

        double[][] x = GaussianMixture.x;
        int[] y = GaussianMixture.y;

        MathEx.setSeed(19650218); // to get repeatable results.
        DENCLUE denclue = DENCLUE.fit(x, 0.85, 100);
        System.out.println(denclue);

        double r = RandIndex.of(y, denclue.y);
        double r2 = AdjustedRandIndex.of(y, denclue.y);
        System.out.println("The number of clusters: " + denclue.k);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.6080, r, 1E-4);
        assertEquals(0.2460, r2, 1E-4);
    }
}
