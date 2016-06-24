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
import smile.math.Math;

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
            instance.add(Math.random());
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
            instance.add(Math.randomInt(1000000));
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
            instance.add((float) Math.random());
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
            instance.add(Math.random());
        }

        for (int j = 0; j < 10; j++) {
            System.out.println(instance.get(j));
        }
    }
}