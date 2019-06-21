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
public class CorTestTest {

    public CorTestTest() {
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
     * Test of pearson method, of class CorTest.
     */
    @Test
    public void testPearson() {
        System.out.println("pearson");
        double[] x = {44.4, 45.9, 41.9, 53.3, 44.7, 44.1, 50.7, 45.2, 60.1};
        double[] y  = {2.6,  3.1,  2.5,  5.0,  3.6,  4.0,  5.2,  2.8,  3.8};

        CorTest result = CorTest.pearson(x, y);
        assertEquals(0.5711816, result.cor, 1E-7);
        assertEquals(7, result.df, 1E-10);
        assertEquals(1.8411, result.t, 1E-4);
        assertEquals(0.1082, result.pvalue, 1E-4);
    }

    /**
     * Test of spearman method, of class CorTest.
     */
    @Test
    public void testSpearman() {
        System.out.println("spearman");
        double[] x = {44.4, 45.9, 41.9, 53.3, 44.7, 44.1, 50.7, 45.2, 60.1};
        double[] y  = {2.6,  3.1,  2.5,  5.0,  3.6,  4.0,  5.2,  2.8,  3.8};

        CorTest result = CorTest.spearman(x, y);
        assertEquals(0.6, result.cor, 1E-7);
        assertEquals(0.08762, result.pvalue, 1E-5);
    }

    /**
     * Test of kendall method, of class CorTest.
     */
    @Test
    public void testKendall() {
        System.out.println("kendall");
        double[] x = {44.4, 45.9, 41.9, 53.3, 44.7, 44.1, 50.7, 45.2, 60.1};
        double[] y  = {2.6,  3.1,  2.5,  5.0,  3.6,  4.0,  5.2,  2.8,  3.8};

        CorTest result = CorTest.kendall(x, y);
        assertEquals(0.4444444, result.cor, 1E-7);
        assertEquals(0.0953, result.pvalue, 1E-4);
    }
}