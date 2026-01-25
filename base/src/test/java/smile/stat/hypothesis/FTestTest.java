/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.stat.hypothesis;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class FTestTest {

    public FTestTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() {
        System.out.println("F-test");
        double[] x = {0.48074284, -0.52975023, 1.28590721, 0.63456079, -0.41761197, 2.76072411,
            1.30321095, -1.16454533, 2.27210509, 1.46394553, -0.31713164, 1.26247543,
            2.65886430, 0.40773450, 1.18055440, -0.39611251, 2.13557687, 0.40878860,
            1.28461394, -0.02906355};

        double[] y = {1.7495879, 1.9359727, 3.1294928, 0.0861894, 2.1643415, 0.1913219,
            -0.3947444, 1.6910837, 1.1548294, 0.2763955, 0.4794719, 3.1805501,
            1.5700497, 2.6860190, -0.4410879, 1.8900183, 1.3422381, -0.1701592};

        FTest test = FTest.test(x, y);
        assertEquals(17, test.df1(), 1E-10);
        assertEquals(19, test.df2(), 1E-10);
        assertEquals(1/0.9126, test.f(), 1E-4);
        assertEquals(0.8415, test.pvalue(), 1E-4);

        double[] z = {0.6621329, 0.4688975, -0.1553013, 0.4564548, 2.2776146, 2.1543678,
            2.8555142, 1.5852899, 0.9091290, 1.6060025, 1.0111968, 1.2479493,
            0.9407034, 1.7167572, 0.5380608, 2.1290007, 1.8695506, 1.2139096};

        test = FTest.test(x, z);
        assertEquals(19, test.df1(), 1E-10);
        assertEquals(17, test.df2(), 1E-10);
        assertEquals(2.046, test.f(), 1E-3);
        assertEquals(0.1438, test.pvalue(), 1E-4);
    }

    @Test
    public void testANOVA() {
        System.out.println("ANOVA");

        int[] x = {1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3};
        double[] y = {6, 8, 4, 5, 3, 4, 8, 12, 9, 11, 6, 8, 13, 9, 11, 8, 7, 12};

        FTest test = FTest.test(x, y);
        assertEquals(2, test.df1(), 1E-10);
        assertEquals(15, test.df2(), 1E-10);
        assertEquals(9.2647, test.f(), 1E-4);
        assertEquals(0.002399, test.pvalue(), 1E-6);
    }
}