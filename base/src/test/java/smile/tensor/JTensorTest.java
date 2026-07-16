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
package smile.tensor;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JTensor (AbstractTensor implementation).
 *
 * @author Haifeng Li
 */
public class JTensorTest {

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    // ── Construction ────────────────────────────────────────────────────────

    @Test
    public void testFloat1D() {
        System.out.println("JTensor float 1D");
        float[] data = {1.0f, 2.0f, 3.0f};
        JTensor t = JTensor.of(data, 3);
        assertEquals(ScalarType.Float32, t.scalarType());
        assertEquals(1, t.dim());
        assertEquals(3, t.size(0));
        assertEquals(3L, t.length());
        assertEquals(1.0f, t.getFloat(0), 1E-7f);
        assertEquals(3.0f, t.getFloat(2), 1E-7f);
    }

    @Test
    public void testDouble2D() {
        System.out.println("JTensor double 2D");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        JTensor t = JTensor.of(data, 2, 3);
        assertEquals(ScalarType.Float64, t.scalarType());
        assertEquals(2, t.dim());
        assertEquals(2, t.size(0));
        assertEquals(3, t.size(1));
        assertEquals(6L, t.length());
        // row-major: [0,0]=1, [0,1]=2, [0,2]=3, [1,0]=4, [1,1]=5, [1,2]=6
        assertEquals(1.0, t.getDouble(0, 0), 1E-10);
        assertEquals(3.0, t.getDouble(0, 2), 1E-10);
        assertEquals(4.0, t.getDouble(1, 0), 1E-10);
        assertEquals(6.0, t.getDouble(1, 2), 1E-10);
    }

    @Test
    public void testInt3D() {
        System.out.println("JTensor int 3D");
        int[] data = new int[24];
        for (int i = 0; i < 24; i++) data[i] = i;
        JTensor t = JTensor.of(data, 2, 3, 4);
        assertEquals(ScalarType.Int32, t.scalarType());
        assertEquals(3, t.dim());
        assertEquals(24L, t.length());
        assertEquals(0, t.getInt(0, 0, 0));
        assertEquals(23, t.getInt(1, 2, 3));
    }

    @Test
    public void testByte1D() {
        System.out.println("JTensor byte 1D");
        byte[] data = {10, 20, 30};
        JTensor t = JTensor.of(data, 3);
        assertEquals(ScalarType.Int8, t.scalarType());
        assertEquals(10, t.getByte(0));
        assertEquals(30, t.getByte(2));
    }

    // ── Shape ───────────────────────────────────────────────────────────────

    @Test
    public void testShape() {
        System.out.println("JTensor shape");
        double[] data = new double[60];
        JTensor t = JTensor.of(data, 3, 4, 5);
        int[] shape = t.shape();
        assertArrayEquals(new int[]{3, 4, 5}, shape);
    }

    // ── Reshape ─────────────────────────────────────────────────────────────

    @Test
    public void testReshape() {
        System.out.println("JTensor reshape");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        JTensor t = JTensor.of(data, 2, 3);
        JTensor r = (JTensor) t.reshape(3, 2);
        assertEquals(2, r.dim());
        assertEquals(3, r.size(0));
        assertEquals(2, r.size(1));
        // Data is shared (view)
        assertEquals(t.getDouble(0, 0), r.getDouble(0, 0), 1E-10);
    }

    @Test
    public void testReshapeIncompatible() {
        System.out.println("JTensor reshape incompatible");
        double[] data = new double[6];
        JTensor t = JTensor.of(data, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> t.reshape(2, 4));
    }

    // ── Mutation ────────────────────────────────────────────────────────────

    @Test
    public void testSetDouble() {
        System.out.println("JTensor set double");
        double[] data = new double[6];
        JTensor t = JTensor.of(data, 2, 3);
        t.set(3.14, 1, 2);
        assertEquals(3.14, t.getDouble(1, 2), 1E-10);
    }

    @Test
    public void testSetFloat() {
        System.out.println("JTensor set float");
        float[] data = new float[4];
        JTensor t = JTensor.of(data, 2, 2);
        t.set(2.71f, 0, 1);
        assertEquals(2.71f, t.getFloat(0, 1), 1E-6f);
    }

    @Test
    public void testSetInt() {
        System.out.println("JTensor set int");
        int[] data = new int[6];
        JTensor t = JTensor.of(data, 2, 3);
        t.set(42, 0, 2);
        assertEquals(42, t.getInt(0, 2));
    }

    @Test
    public void testSetSubTensor() {
        System.out.println("JTensor set sub-tensor");
        double[] base = new double[6];
        JTensor t = JTensor.of(base, 2, 3);
        double[] sub = {10.0, 20.0, 30.0};
        JTensor s = JTensor.of(sub, 3);
        t.set(s, 0);   // set row 0
        assertEquals(10.0, t.getDouble(0, 0), 1E-10);
        assertEquals(20.0, t.getDouble(0, 1), 1E-10);
        assertEquals(30.0, t.getDouble(0, 2), 1E-10);
    }

    // ── Slicing ─────────────────────────────────────────────────────────────

    @Test
    public void testGet() {
        System.out.println("JTensor get sub-tensor (row slice)");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        JTensor t = JTensor.of(data, 2, 3);
        JTensor row1 = (JTensor) t.get(1);
        assertEquals(1, row1.dim());
        assertEquals(3, row1.size(0));
        assertEquals(4.0, row1.getDouble(0), 1E-10);
        assertEquals(5.0, row1.getDouble(1), 1E-10);
        assertEquals(6.0, row1.getDouble(2), 1E-10);
    }

    // ── Bounds checking ─────────────────────────────────────────────────────

    @Test
    public void testOutOfBoundsNegative() {
        System.out.println("JTensor out-of-bounds negative index");
        float[] data = {1f, 2f, 3f, 4f, 5f, 6f};
        JTensor t = JTensor.of(data, 2, 3);
        assertThrows(IndexOutOfBoundsException.class, () -> t.getFloat(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getFloat(0, -1));
    }

    @Test
    public void testOutOfBoundsOverflow() {
        System.out.println("JTensor out-of-bounds overflow index");
        float[] data = {1f, 2f, 3f, 4f, 5f, 6f};
        JTensor t = JTensor.of(data, 2, 3);
        assertThrows(IndexOutOfBoundsException.class, () -> t.getFloat(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getFloat(0, 3));
    }

    @Test
    public void testWrongIndexLength() {
        System.out.println("JTensor wrong index length");
        double[] data = new double[6];
        JTensor t = JTensor.of(data, 2, 3);
        // index length > dim should throw
        assertThrows(IllegalArgumentException.class, () -> t.getDouble(0, 0, 0));
        // partial index (length < dim) is valid — returns a sub-tensor slice, no exception
        assertDoesNotThrow(() -> t.get(0));   // returns row 0
    }

    // ── Invalid construction ─────────────────────────────────────────────────

    @Test
    public void testWrongDataLength() {
        System.out.println("JTensor wrong data length");
        assertThrows(IllegalArgumentException.class,
                () -> JTensor.of(new double[]{1.0, 2.0}, 3));
        assertThrows(IllegalArgumentException.class,
                () -> JTensor.of(new float[]{1f, 2f, 3f, 4f}, 2, 3));
    }

    // ── toString ────────────────────────────────────────────────────────────

    @Test
    public void testToString() {
        System.out.println("JTensor toString");
        double[] data = new double[6];
        JTensor t = JTensor.of(data, 2, 3);
        String s = t.toString();
        assertTrue(s.contains("2"));
        assertTrue(s.contains("3"));
    }
}

