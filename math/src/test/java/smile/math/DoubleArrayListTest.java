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
package smile.math;

import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

/**
 *
 * @author Haifeng Li
 */
public class DoubleArrayListTest {
    public DoubleArrayListTest() {
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
     * Test of all methods, of class DoubleArrayListTest.
     */
    @Test
    public void testAll() {
        System.out.println("DoubleArrayList");
        DoubleArrayList a = new DoubleArrayList();
        assertEquals(true, a.isEmpty());

        a.add(1);
        a.add(2);
        assertEquals(2, a.size());
        assertEquals(1, a.get(0), 1E-15);
        assertEquals(2, a.get(1), 1E-15);
        assertEquals(false, a.isEmpty());

        a.remove(0);
        assertEquals(1, a.size());
        assertEquals(2, a.get(0), 1E-15);
        assertEquals(false, a.isEmpty());


        a.remove(0);
        assertEquals(0, a.size());
        assertEquals(true, a.isEmpty());

        a.add(new double[]{1, 2, 3, 4});
        assertEquals(4, a.size());
        assertEquals(3, a.get(2), 1E-15);
        assertEquals(false, a.isEmpty());
        assertEquals(4, a.stream().count());

        a.set(2, 4);
        assertEquals(4, a.get(2), 1E-15);

        double[] b = a.toArray();
        assertArrayEquals(new double[]{1, 2, 4, 4}, b, 1E-15);
    }
}
