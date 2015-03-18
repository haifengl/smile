/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    /**
     * Test of chisq method, of class CorTest.
     */
    @Test
    public void testChisqTest() {
        System.out.println("chisq");
        int[][] x = {{12, 7}, {5, 7}};
        CorTest result = CorTest.chisq(x);
        assertEquals(0.1438, result.cor, 1E-4);
        assertEquals(1, result.df, 1E-7);
        assertEquals(0.6411, result.t, 1E-4);
        assertEquals(0.4233, result.pvalue, 1E-4);

        int[][] y = {
            {8, 13, 16, 10, 3},
            {4,  9, 14, 16, 7}
        };
        result = CorTest.chisq(y);
        assertEquals(0.2275644, result.cor, 1E-7);
        assertEquals(4, result.df, 1E-7);
        assertEquals(5.179, result.t, 1E-3);
        assertEquals(0.2695, result.pvalue, 1E-4);
    }
}