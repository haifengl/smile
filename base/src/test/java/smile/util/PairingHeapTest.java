/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
 
/**
 *
 * @author Karl Li
 */
public class PairingHeapTest {
    public PairingHeapTest() {
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
        System.out.println("PairingHeap");

        PairingHeap<Integer> heap = new PairingHeap<>();
        heap.add(Integer.MAX_VALUE);
        heap.add(10);
        heap.add(7);
        heap.add(5);
        heap.add(4);
        heap.add(9);
        heap.add(2);
        heap.add(3);
        heap.add(2); // add 2 again
        heap.add(1);
        heap.add(0);
        heap.add(-191);
        heap.add(Integer.MIN_VALUE);
        heap.add(6);
        heap.add(Integer.MAX_VALUE);

        assertEquals(15, heap.size());
        assertFalse(heap.isEmpty());
        assertEquals(Integer.MIN_VALUE, heap.poll());
        assertEquals(-191, heap.poll());
        assertEquals(0, heap.poll());
        assertEquals(1, heap.poll());
        assertEquals(2, heap.peek());
        assertEquals(11, heap.size());
        assertEquals(2, heap.remove());
        assertEquals(10, heap.size());
        assertEquals(2, heap.poll());
        assertEquals(3, heap.poll());
    }

    @Test
    public void testDecrease() {
        System.out.println("decrease");

        PairingHeap<Integer> heap = new PairingHeap<>();
        heap.add(1);
        heap.add(0);
        heap.add(3);
        heap.add(6);
        heap.add(7);
        heap.add(10);
        heap.add(4);

        assertEquals(0, heap.peek());
        heap.remove();
        assertEquals(1, heap.poll());
        var node = heap.addNode(5);
        assertEquals(6, heap.size());
        assertEquals(3, heap.peek());

        node.decrease(0);
        assertEquals(6, heap.size());
        assertEquals(0, heap.peek());
    }

    @Test
    public void testRebuild() {
        System.out.println("decrease");

        MutableInt i0 = new MutableInt(0);
        MutableInt i1 = new MutableInt(1);
        MutableInt i3 = new MutableInt(3);
        MutableInt i7 = new MutableInt(7);

        PairingHeap<MutableInt> heap = new PairingHeap<>();
        heap.add(i1);
        heap.add(i0);
        heap.add(i3);
        heap.add(i7);

        assertEquals(0, heap.peek().value);
        i0.value = 12;
        i1.value = -1;
        i3.value = -10;
        i7.value = 17;
        heap.rebuild();
        assertEquals(-10, heap.poll().value);
        assertEquals(-1, heap.poll().value);
        assertEquals(12, heap.poll().value);
        assertEquals(17, heap.poll().value);
        assertTrue(heap.isEmpty());
    }
}
