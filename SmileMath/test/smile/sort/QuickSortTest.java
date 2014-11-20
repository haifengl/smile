/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

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
public class QuickSortTest {

    double[] big = new double[1000000];
    public QuickSortTest() {
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
     * Test of sort method, of class QuickSort.
     */
    @Test
    public void testSortInt() {
        System.out.println("sort int");
        int[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] order1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertTrue(Math.equals(order1, QuickSort.sort(data1)));
        int[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] order2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        assertTrue(Math.equals(order2, QuickSort.sort(data2)));
        int[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        int[] order3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        assertTrue(Math.equals(order3, QuickSort.sort(data3)));
        int[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        int[] order4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        assertTrue(Math.equals(order4, QuickSort.sort(data4)));
    }

    /**
     * Test of sort method, of class QuickSort.
     */
    @Test
    public void testSortFloat() {
        System.out.println("sort float");
        float[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] order1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertTrue(Math.equals(order1, QuickSort.sort(data1)));
        float[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] order2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        assertTrue(Math.equals(order2, QuickSort.sort(data2)));
        float[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        int[] order3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        assertTrue(Math.equals(order3, QuickSort.sort(data3)));
        float[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        int[] order4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        assertTrue(Math.equals(order4, QuickSort.sort(data4)));
    }

    /**
     * Test of sort method, of class QuickSort.
     */
    @Test
    public void testSortDouble() {
        System.out.println("sort double");
        double[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] order1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertTrue(Math.equals(order1, QuickSort.sort(data1)));
        double[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] order2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        assertTrue(Math.equals(order2, QuickSort.sort(data2)));
        double[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        int[] order3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        assertTrue(Math.equals(order3, QuickSort.sort(data3)));
        double[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        int[] order4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        assertTrue(Math.equals(order4, QuickSort.sort(data4)));
    }

    /**
     * Test of sort method, of class QuickSort.
     */
    @Test
    public void testSortObject() {
        System.out.println("sort object");
        Integer[] data1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] order1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertTrue(Math.equals(order1, QuickSort.sort(data1)));
        Integer[] data2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        int[] order2 = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        assertTrue(Math.equals(order2, QuickSort.sort(data2)));
        Integer[] data3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        int[] order3 = {0, 1, 2, 3, 5, 4, 6, 7, 8, 9};
        assertTrue(Math.equals(order3, QuickSort.sort(data3)));
        Integer[] data4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        int[] order4 = {4, 1, 2, 3, 0, 5, 6, 7, 8, 9};
        assertTrue(Math.equals(order4, QuickSort.sort(data4)));
    }

    /**
     * Test of sort method, of class QuickSort.
     */
    @Test
    public void testSortBig() {
        System.out.println("sort big array");
        QuickSort.sort(big);
    }
}