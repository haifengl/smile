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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RotaryPositionalEncodingTest {

    public RotaryPositionalEncodingTest() {
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
        var cis = RotaryPositionalEncoding.computeFreqCis(128, 4096, 500000.0, false);

        long[] shape = { 4096, 64 };
        assertArrayEquals(shape, cis.shape());

        var real = cis.viewAsReal();
        // real has shape [4096, 64, 2]
        assertEquals(1.0f, real.getFloat(0, 0, 0), 1E-4);
        assertEquals(0.0f, real.getFloat(0, 0, 1), 1E-4);
        assertEquals(1.0f, real.getFloat(0, 1, 0), 1E-4);
        assertEquals(0.0f, real.getFloat(0, 1, 1), 1E-4);
        assertEquals(0.5403f, real.getFloat(1, 0, 0), 1E-4);
        assertEquals(0.8415f, real.getFloat(1, 0, 1), 1E-4);
        assertEquals(0.6861f, real.getFloat(1, 1, 0), 1E-4);
        assertEquals(0.7275f, real.getFloat(1, 1, 1), 1E-4);

        assertEquals(0.9999f, real.getFloat(4095, 63, 0), 1E-4);
        assertEquals(0.0101f, real.getFloat(4095, 63, 1), 1E-4);
    }

    @Test
    public void testScaledRope() {
        var cis = RotaryPositionalEncoding.computeFreqCis(128, 4096, 500000.0, true);

        long[] shape = { 4096, 64 };
        assertArrayEquals(shape, cis.shape());

        var real = cis.viewAsReal();
        // real has shape [4096, 64, 2]
        assertEquals(1.0f, real.getFloat(0, 0, 0), 1E-4);
        assertEquals(0.0f, real.getFloat(0, 0, 1), 1E-4);
        assertEquals(1.0f, real.getFloat(0, 1, 0), 1E-4);
        assertEquals(0.0f, real.getFloat(0, 1, 1), 1E-4);
        assertEquals(0.5403f, real.getFloat(1, 0, 0), 1E-4);
        assertEquals(0.8415f, real.getFloat(1, 0, 1), 1E-4);
        assertEquals(0.6861f, real.getFloat(1, 1, 0), 1E-4);
        assertEquals(0.7275f, real.getFloat(1, 1, 1), 1E-4);

        assertEquals(0.9999f, real.getFloat(4095, 63, 0), 1E-4);
        assertEquals(0.00126f, real.getFloat(4095, 63, 1), 1E-5);
    }
}
