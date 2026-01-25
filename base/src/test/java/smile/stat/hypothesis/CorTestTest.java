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
}