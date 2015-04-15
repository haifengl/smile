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

package smile.sort;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.Math;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class ShellSortTest {

    double[] big = new double[1000000];
    public ShellSortTest() {
        for (int i = 0; i < big.length; i++)
            big[i] = Math.random();
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
     * Test of sort method, of class ShellSort.
     */
    @Test
    public void testSortInt() {
        System.out.println("sort int");
        int[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        int[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ShellSort.sort(data1);
        assertTrue(Math.equals(data, data1));
        int[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        ShellSort.sort(data2);
        assertTrue(Math.equals(data, data2));
        int[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        ShellSort.sort(data3);
        assertTrue(Math.equals(data, data3));
        int[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        ShellSort.sort(data4);
        assertTrue(Math.equals(data, data4));
    }

    /**
     * Test of sort method, of class ShellSort.
     */
    @Test
    public void testSortFloat() {
        System.out.println("sort float");
        float[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        float[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ShellSort.sort(data1);
        assertTrue(Math.equals(data, data1));
        float[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        ShellSort.sort(data2);
        assertTrue(Math.equals(data, data2));
        float[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        ShellSort.sort(data3);
        assertTrue(Math.equals(data, data3));
        float[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        ShellSort.sort(data4);
        assertTrue(Math.equals(data, data4));
    }

    /**
     * Test of sort method, of class ShellSort.
     */
    @Test
    public void testSortDouble() {
        System.out.println("sort double");
        double[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        double[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ShellSort.sort(data1);
        assertTrue(Math.equals(data, data1));
        double[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        ShellSort.sort(data2);
        assertTrue(Math.equals(data, data2));
        double[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        ShellSort.sort(data3);
        assertTrue(Math.equals(data, data3));
        double[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        ShellSort.sort(data4);
        assertTrue(Math.equals(data, data4));
    }

    /**
     * Test of sort method, of class ShellSort.
     */
    @Test
    public void testSortObject() {
        System.out.println("sort object");
        Integer[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        Integer[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ShellSort.sort(data1);
        assertTrue(Math.equals(data, data1));
        Integer[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        ShellSort.sort(data2);
        assertTrue(Math.equals(data, data2));
        Integer[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        ShellSort.sort(data3);
        assertTrue(Math.equals(data, data3));
        Integer[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        ShellSort.sort(data4);
        assertTrue(Math.equals(data, data4));
    }

    /**
     * Test of sort method, of class ShellSort.
     */
    @Test
    public void testSortBig() {
        System.out.println("sort big array");
        ShellSort.sort(big);
    }
}