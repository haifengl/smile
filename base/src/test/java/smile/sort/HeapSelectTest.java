/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.sort;

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class HeapSelectTest {

    public HeapSelectTest() {
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

    @Test
    public void test() {
        System.out.println("HeapSelect");
        HeapSelect<Integer> heap = new HeapSelect<>(Integer.class, 9);
        heap.add(63);
        heap.add(107);

        heap.add(53);
        heap.add(106);

        heap.add(98);
        heap.add(105);

        heap.add(100);
        heap.add(104);

        heap.add(101);
        heap.add(103);

        heap.add(3);
        heap.add(102);

        heap.add(54);
        heap.add(99);

        heap.add(89);
        heap.add(91);

        assertEquals(3,  heap.get(0));
        assertEquals(53, heap.get(1));
        assertEquals(54, heap.get(2));
        assertEquals(63, heap.get(3));
        assertEquals(89, heap.get(4));
        assertEquals(91, heap.get(5));
        assertEquals(98, heap.get(6));
        assertEquals(99, heap.get(7));
        assertEquals(100, heap.get(8));

        for (int i = 0; i < 9; i++) {
            System.out.println(heap.get(i));
        }
    }

    @Test
    public void test1000() {
        System.out.println("HeapSelect 1000");
        HeapSelect<Integer> instance = new HeapSelect<>(Integer.class, 10);
        for (int i = 0; i < 1000; i++) {
            instance.add(i);
            if (i > 10) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), Integer.valueOf(j));
                }
            }
        }

        instance = new HeapSelect<>(Integer.class, 10);
        for (int i = 0; i < 1000; i++) {
            instance.add(1000-i);
            if (i >= 9) {
                for (int j = 0; j < 10; j++) {
                    assertEquals(instance.get(j), Integer.valueOf(1000-i+j));
                }
            }
        }
    }

    @Test
    public void test100000000() {
        System.out.println("HeapSelect 100000000");
        HeapSelect<Double> instance = new HeapSelect<>(Double.class, 10);
        for (int i = 0; i < 100000000; i++) {
            instance.add(MathEx.random());
        }

        for (int j = 0; j < 10; j++) {
            System.out.println(instance.get(j));
        }
    }
}
