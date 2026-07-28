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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.stat.hypothesis;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class CorTestTest {

    public CorTestTest() {
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

    /**
     * Test of pearson method, of class CorTest.
     */
    @Test
    public void testPearson() {
        System.out.println("pearson");
        double[] x = {44.4, 45.9, 41.9, 53.3, 44.7, 44.1, 50.7, 45.2, 60.1};
        double[] y  = {2.6,  3.1,  2.5,  5.0,  3.6,  4.0,  5.2,  2.8,  3.8};

        CorTest test = CorTest.pearson(x, y);
        assertEquals(0.5711816, test.cor(), 1E-7);
        assertEquals(7, test.df(), 1E-10);
        assertEquals(1.8411, test.t(), 1E-4);
        assertEquals(0.1082, test.pvalue(), 1E-4);
    }

    /**
     * Test of spearman method, of class CorTest.
     */
    @Test
    public void testSpearman() {
        System.out.println("spearman");
        double[] x = {44.4, 45.9, 41.9, 53.3, 44.7, 44.1, 50.7, 45.2, 60.1};
        double[] y  = {2.6,  3.1,  2.5,  5.0,  3.6,  4.0,  5.2,  2.8,  3.8};

        CorTest test = CorTest.spearman(x, y);
        assertEquals(0.6, test.cor(), 1E-7);
        assertEquals(0.08762, test.pvalue(), 1E-5);
    }

    /**
     * Test of kendall method, of class CorTest.
     */
    @Test
    public void testKendall() {
        System.out.println("kendall");
        double[] x = {44.4, 45.9, 41.9, 53.3, 44.7, 44.1, 50.7, 45.2, 60.1};
        double[] y  = {2.6,  3.1,  2.5,  5.0,  3.6,  4.0,  5.2,  2.8,  3.8};

        CorTest test = CorTest.kendall(x, y);
        assertEquals(0.4444444, test.cor(), 1E-7);
        assertEquals(0.0953, test.pvalue(), 1E-4);
    }

    /**
     * Test of spearman on a large sample. The n<sup>3</sup>-n rank
     * normalization must be evaluated in double; computed in int it
     * overflows for n >= 1291, pushing the coefficient outside [-1, 1].
     */
    @Test
    public void testSpearmanLargeSample() {
        System.out.println("spearman large sample");
        // Strictly anti-correlated ranks (no ties): true Spearman rho is -1.
        for (int n : new int[]{1290, 1291, 2000}) {
            double[] x = new double[n];
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = i;
                y[i] = n - 1 - i;
            }
            double rho = CorTest.spearman(x, y).cor();
            assertTrue(rho >= -1.0 && rho <= 1.0, "rho out of range at n=" + n + ": " + rho);
            assertEquals(-1.0, rho, 1E-10, "n=" + n);
        }
    }

    /**
     * Test of kendall on a large sample. The concordant/discordant pair
     * counters reach n(n-1)/2 and must be long; computed in int they
     * overflow for n >= 65537, yielding NaN from the root of a negative count.
     */
    @Test
    public void testKendallLargeSample() {
        System.out.println("kendall large sample");
        // Perfectly concordant ranks (no ties): true Kendall tau is +1.
        int n = 65537;
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = i;
            y[i] = i;
        }
        double tau = CorTest.kendall(x, y).cor();
        assertTrue(tau >= -1.0 && tau <= 1.0, "tau out of range: " + tau);
        assertEquals(1.0, tau, 1E-10);
    }
}