/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep.tensor;

import java.io.IOException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class TensorTest {

    public TensorTest() {
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
    public void test() throws IOException {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        Tensor t = Tensor.of(x, 3, 3);
        t.print();

        long[] shape = {3, 3};
        assertArrayEquals(shape, t.shape());
        assertEquals(2.0f, t.getFloat(0, 1));
        t.put_(10f, 1, 1);
        assertEquals(10f, t.getFloat(1, 1));

        long[] shape2 = {3};
        var t2 = t.get(Index.Colon, Index.of(1));
        t2.print();
        assertArrayEquals(shape2, t2.shape());
        assertEquals(2f, t2.getFloat(0));
        assertEquals(10f, t2.getFloat(1));
        assertEquals(8f, t2.getFloat(2));

        var t3 = t.get(Index.Ellipsis, Index.of(2));
        t3.print();
        assertArrayEquals(shape2, t3.shape());
        assertEquals(3f, t3.getFloat(0));
        assertEquals(6f, t3.getFloat(1));
        assertEquals(9f, t3.getFloat(2));

        var t4 = t.get(Index.None, Index.of(2));
        t4.print();
        long[] shape4 = {1, 3};
        assertArrayEquals(shape4, t4.shape());
        assertEquals(7f, t4.getFloat(0, 0));
        assertEquals(8f, t4.getFloat(0, 1));
        assertEquals(9f, t4.getFloat(0, 2));

        int[] index = {1, 2};
        var t5 = t.get(Tensor.of(index, 2));
        t5.print();
        long[] shape5 = {2, 3};
        assertArrayEquals(shape5, t5.shape());
        assertEquals(10f, t5.getFloat(0, 1));
        assertEquals(8f, t5.getFloat(1, 1));
    }
}