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

package smile.stat.hypothesis;

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
public class FTestTest {

    public FTestTest() {
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
     * Test of test method, of class FTest.
     */
    @Test
    public void testTest() {
        System.out.println("test");
        double[] x = {0.48074284, -0.52975023, 1.28590721, 0.63456079, -0.41761197, 2.76072411,
            1.30321095, -1.16454533, 2.27210509, 1.46394553, -0.31713164, 1.26247543,
            2.65886430, 0.40773450, 1.18055440, -0.39611251, 2.13557687, 0.40878860,
            1.28461394, -0.02906355};

        double[] y = {1.7495879, 1.9359727, 3.1294928, 0.0861894, 2.1643415, 0.1913219,
            -0.3947444, 1.6910837, 1.1548294, 0.2763955, 0.4794719, 3.1805501,
            1.5700497, 2.6860190, -0.4410879, 1.8900183, 1.3422381, -0.1701592};

        FTest result = FTest.test(x, y);
        assertEquals(17, result.df1, 1E-10);
        assertEquals(19, result.df2, 1E-10);
        assertEquals(1/0.9126, result.f, 1E-4);
        assertEquals(0.8415, result.pvalue, 1E-4);

        double[] z = {0.6621329, 0.4688975, -0.1553013, 0.4564548, 2.2776146, 2.1543678,
            2.8555142, 1.5852899, 0.9091290, 1.6060025, 1.0111968, 1.2479493,
            0.9407034, 1.7167572, 0.5380608, 2.1290007, 1.8695506, 1.2139096};

        result = FTest.test(x, z);
        assertEquals(19, result.df1, 1E-10);
        assertEquals(17, result.df2, 1E-10);
        assertEquals(2.046, result.f, 1E-3);
        assertEquals(0.1438, result.pvalue, 1E-4);
    }

}