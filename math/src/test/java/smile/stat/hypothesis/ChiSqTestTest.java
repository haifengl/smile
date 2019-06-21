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
public class ChiSqTestTest {

    public ChiSqTestTest() {
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
     * Test of test method, of class ChiSqTest.
     */
    @Test
    public void testTest() {
        System.out.println("one sample test");
        int[] bins = {20, 22, 13, 22, 10, 13};
        double[] prob = {1.0/6, 1.0/6, 1.0/6, 1.0/6, 1.0/6, 1.0/6};
        ChiSqTest result = ChiSqTest.test(bins, prob);
        assertEquals(8.36, result.chisq, 1E-2);
        assertEquals(5, result.df, 1E-10);
        assertEquals(0.1375, result.pvalue, 1E-4);
    }

    /**
     * Test of test method, of class ChiSqTest.
     */
    @Test
    public void testTest2() {
        System.out.println("two sample test");
        int[] bins1 = {8, 13, 16, 10, 3};
        int[] bins2 = {4,  9, 14, 16, 7};
        ChiSqTest result = ChiSqTest.test(bins1, bins2);
        assertEquals(5.179, result.chisq, 1E-2);
        assertEquals(4, result.df, 1E-10);
        assertEquals(0.2695, result.pvalue, 1E-4);
    }

    /**
     * Test of test method, of class Chisq.
     */
    @Test
    public void testPearsonChisqTest() {
        System.out.println("pearson");
        int[][] x = {{12, 7}, {5, 7}};
        ChiSqTest result = ChiSqTest.test(x);
        assertEquals(1, result.df, 1E-7);
        assertEquals(0.6411, result.chisq, 1E-4);
        assertEquals(0.4233, result.pvalue, 1E-4);
    }

    /**
     * Test of test method, of class Chisq.
     */
    @Test
    public void testPearsonChisqTest2() {
        System.out.println("pearson 2");
        int[][] y = {
                {8, 13, 16, 10, 3},
                {4, 9, 14, 16, 7}
        };

        ChiSqTest result = ChiSqTest.test(y);
        assertEquals(4, result.df, 1E-7);
        assertEquals(5.179, result.chisq, 1E-3);
        assertEquals(0.2695, result.pvalue, 1E-4);
    }
}