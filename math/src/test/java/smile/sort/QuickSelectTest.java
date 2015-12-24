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
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class QuickSelectTest {

    public QuickSelectTest() {
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
     * Test of median method, of class QuickSelect.
     */
    @Test
    public void testMedianInt() {
        System.out.println("median int");
        int[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1));
        int[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2));
        int[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3));
        int[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4));
        int[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5));
        int[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6));
    }

    /**
     * Test of median method, of class QuickSelect.
     */
    @Test
    public void testMedianFloat() {
        System.out.println("median float");
        float[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1), 1E-10);
        float[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2), 1E-10);
        float[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3), 1E-10);
        float[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4), 1E-10);
        float[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5), 1E-10);
        float[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6), 1E-10);
    }

    /**
     * Test of median method, of class QuickSelect.
     */
    @Test
    public void testMedianDouble() {
        System.out.println("median double");
        double[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1), 1E-10);
        double[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2), 1E-10);
        double[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3), 1E-10);
        double[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4), 1E-10);
        double[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5), 1E-10);
        double[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6), 1E-10);
    }

    /**
     * Test of median method, of class QuickSelect.
     */
    @Test
    public void testMedianObject() {
        System.out.println("median object");
        Integer[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1), 1E-10);
        Integer[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2), 1E-10);
        Integer[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3), 1E-10);
        Integer[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4), 1E-10);
        Integer[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5), 1E-10);
        Integer[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6), 1E-10);
    }
}
