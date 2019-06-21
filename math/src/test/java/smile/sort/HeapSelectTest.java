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

package smile.sort;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.MathEx;

/**
 *
 * @author Haifeng Li
 */
public class HeapSelectTest {

    public HeapSelectTest() {
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
     * Test of get method, of class HeapSelect.
     */
    @Test
    public void testSelect() {
        System.out.println("HeapSelect");
        HeapSelect<Integer> instance = new HeapSelect<>(new Integer[10]);
        for (int i = 0; i < 1000; i++) {
            instance.add(i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), Integer.valueOf(j));
                }
            }
        }

        instance = new HeapSelect<>(new Integer[10]);
        for (int i = 0; i < 1000; i++) {
            instance.add(1000-i);
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), Integer.valueOf(1000-i+j));
                }
            }
        }
    }

    /**
     * Test of get method, of class HeapSelect.
     */
    @Test
    public void testSelectBig() {
        System.out.println("HeapSelect Big");
        HeapSelect<Double> instance = new HeapSelect<>(new Double[10]);
        for (int i = 0; i < 100000000; i++) {
            instance.add(MathEx.random());
        }

        for (int j = 0; j < 10; j++) {
            System.out.println(instance.get(j));
        }
    }

    /**
     * Test of get method, of class HeapSelect.
     */
    @Test
    public void testSelectInt() {
        System.out.println("IntHeapSelect");
        IntHeapSelect instance = new IntHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), j);
                }
            }
        }

        instance = new IntHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(1000-i);
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), 1000-i+j);
                }
            }
        }
    }

    /**
     * Test of get method, of class HeapSelect.
     */
    @Test
    public void testIntSelectBig() {
        System.out.println("IntHeapSelect Big");
        IntHeapSelect instance = new IntHeapSelect(10);
        for (int i = 0; i < 100000000; i++) {
            instance.add(MathEx.randomInt(1000000));
        }

        for (int j = 0; j < 10; j++) {
            System.out.println(instance.get(j));
        }
    }

    /**
     * Test of get method, of class HeapSelect.
     */
    @Test
    public void testSelectFloat() {
        System.out.println("FloatHeapSelect");
        FloatHeapSelect instance = new FloatHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(0.1f * i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), 0.1f*j, 1E-10);
                }
            }
        }

        instance = new FloatHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add((1000-i)*0.1f);
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), (1000-i+j)*0.1f, 1E-10);
                }
            }
        }
    }

    /**
     * Test of get method, of class HeapSelect.
     */
    @Test
    public void testFloatSelectBig() {
        System.out.println("FloatHeapSelect Big");
        FloatHeapSelect instance = new FloatHeapSelect(10);
        for (int i = 0; i < 100000000; i++) {
            instance.add((float) MathEx.random());
        }

        for (int j = 0; j < 10; j++) {
            System.out.println(instance.get(j));
        }
    }

    /**
     * Test of get method, of class HeapSelect.
     */
    @Test
    public void testSelectDouble() {
        System.out.println("DoubleHeapSelect");
        DoubleHeapSelect instance = new DoubleHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(0.1*i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), 0.1*j, 1E-10);
                }
            }
        }

        instance = new DoubleHeapSelect(10);
        for (int i = 0; i < 1000; i++) {
            instance.add(0.1*(1000-i));
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), 0.1*(1000-i+j), 1E-10);
                }
            }
        }
    }

    /**
     * Test of get method, of class HeapSelect.
     */
    @Test
    public void testDoubleSelectBig() {
        System.out.println("DoubleHeapSelect Big");
        DoubleHeapSelect instance = new DoubleHeapSelect(10);
        for (int i = 0; i < 100000000; i++) {
            instance.add(MathEx.random());
        }

        for (int j = 0; j < 10; j++) {
            System.out.println(instance.get(j));
        }
    }
}