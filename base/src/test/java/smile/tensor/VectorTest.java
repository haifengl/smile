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
import static smile.tensor.ScalarType.*;

/**
 * Tests for Vector (Float64 and Float32).
 *
 * @author Haifeng Li
 */
public class VectorTest {

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    // ── Construction ────────────────────────────────────────────────────────

    @Test
    public void testColumnDouble() {
        System.out.println("Vector.column(double[])");
        double[] data = {1.0, 2.0, 3.0};
        Vector v = Vector.column(data);
        assertEquals(Float64, v.scalarType());
        assertEquals(3, v.size());
        assertEquals(3, v.nrow());
        assertEquals(1, v.ncol());
        assertEquals(1.0, v.get(0), 1E-10);
        assertEquals(2.0, v.get(1), 1E-10);
        assertEquals(3.0, v.get(2), 1E-10);
    }

    @Test
    public void testColumnFloat() {
        System.out.println("Vector.column(float[])");
        float[] data = {1.0f, 2.0f, 3.0f};
        Vector v = Vector.column(data);
        assertEquals(Float32, v.scalarType());
        assertEquals(3, v.size());
        assertEquals(1.0, v.get(0), 1E-6);
        assertEquals(2.0, v.get(1), 1E-6);
        assertEquals(3.0, v.get(2), 1E-6);
    }

    @Test
    public void testZeros() {
        System.out.println("Vector zeros");
        Vector v = Vector.zeros(Float64, 4);
        assertEquals(4, v.size());
        for (int i = 0; i < 4; i++) assertEquals(0.0, v.get(i), 1E-10);
    }

    @Test
    public void testOnes() {
        System.out.println("Vector ones");
        Vector v = Vector.ones(Float64, 4);
        assertEquals(4, v.size());
        for (int i = 0; i < 4; i++) assertEquals(1.0, v.get(i), 1E-10);
    }

    // ── Mutation ────────────────────────────────────────────────────────────

    @Test
    public void testSetAddSubMulDiv() {
        System.out.println("Vector set/add/sub/mul/div");
        Vector v = Vector.zeros(Float64, 3);
        v.set(0, 10.0);
        assertEquals(10.0, v.get(0), 1E-10);
        v.add(0, 5.0);
        assertEquals(15.0, v.get(0), 1E-10);
        v.sub(0, 3.0);
        assertEquals(12.0, v.get(0), 1E-10);
        v.mul(0, 2.0);
        assertEquals(24.0, v.get(0), 1E-10);
        v.div(0, 4.0);
        assertEquals(6.0, v.get(0), 1E-10);
    }

    @Test
    public void testFill() {
        System.out.println("Vector fill");
        Vector v = Vector.zeros(Float64, 5);
        v.fill(0, 5, 7.0);
        for (int i = 0; i < 5; i++) assertEquals(7.0, v.get(i), 1E-10);
        v.fill(1, 3, -1.0);
        assertEquals(7.0,  v.get(0), 1E-10);
        assertEquals(-1.0, v.get(1), 1E-10);
        assertEquals(-1.0, v.get(2), 1E-10);
        assertEquals(7.0,  v.get(3), 1E-10);
    }

    @Test
    public void testScale() {
        System.out.println("Vector scale");
        Vector v = Vector.column(new double[]{1.0, 2.0, 3.0});
        v.scale(2.0);
        assertEquals(2.0, v.get(0), 1E-10);
        assertEquals(4.0, v.get(1), 1E-10);
        assertEquals(6.0, v.get(2), 1E-10);
    }

    // ── Statistics ──────────────────────────────────────────────────────────

    @Test
    public void testMinMaxPositive() {
        System.out.println("Vector min/max positive");
        Vector v = Vector.column(new double[]{3.0, 1.0, 4.0, 1.5, 9.0, 2.6});
        assertEquals(1.0, v.min(), 1E-10);
        assertEquals(9.0, v.max(), 1E-10);
    }

    @Test
    public void testMaxAllNegative() {
        System.out.println("Vector.max() all-negative (regression: was Double.MIN_VALUE bug)");
        Vector v = Vector.column(new double[]{-3.0, -1.0, -2.0});
        // Before fix, this returned Double.MIN_VALUE ≈ 5e-324 instead of -1.0
        assertEquals(-1.0, v.max(), 1E-10);
    }

    @Test
    public void testMaxSingleElement() {
        System.out.println("Vector.max() single element");
        Vector v = Vector.column(new double[]{-7.0});
        assertEquals(-7.0, v.max(), 1E-10);
    }

    @Test
    public void testMinSingleElement() {
        System.out.println("Vector.min() single element");
        Vector v = Vector.column(new double[]{42.0});
        assertEquals(42.0, v.min(), 1E-10);
    }

    @Test
    public void testSumMean() {
        System.out.println("Vector sum/mean");
        Vector v = Vector.column(new double[]{1.0, 2.0, 3.0, 4.0});
        assertEquals(10.0, v.sum(), 1E-10);
        assertEquals(2.5,  v.mean(), 1E-10);
    }

    /**
     * Classic variance example: [2,4,4,4,5,5,7,9] → mean=5, sample var=4.5714...
     */
    @Test
    public void testVarianceSd() {
        System.out.println("Vector variance/sd");
        Vector v = Vector.column(new double[]{2, 4, 4, 4, 5, 5, 7, 9});
        assertEquals(5.0, v.mean(), 1E-10);
        assertEquals(32.0 / 7.0, v.variance(), 1E-10);
        assertEquals(Math.sqrt(32.0 / 7.0), v.sd(), 1E-10);
    }

    @Test
    public void testVarianceSingleElement() {
        System.out.println("Vector variance single element");
        Vector v = Vector.column(new double[]{5.0});
        assertEquals(0.0, v.variance(), 1E-10);
        assertEquals(0.0, v.sd(), 1E-10);
    }

    @Test
    public void testVarianceAllEqual() {
        System.out.println("Vector variance all equal");
        Vector v = Vector.column(new double[]{3.0, 3.0, 3.0});
        assertEquals(0.0, v.variance(), 1E-10);
    }

    // ── Norms ───────────────────────────────────────────────────────────────

    @Test
    public void testNorm2() {
        System.out.println("Vector norm2");
        // ||[3,4]|| = 5
        Vector v = Vector.column(new double[]{3.0, 4.0});
        assertEquals(5.0, v.norm2(), 1E-10);
    }

    @Test
    public void testNorm1() {
        System.out.println("Vector norm1");
        Vector v = Vector.column(new double[]{-2.0, 3.0, -5.0});
        assertEquals(10.0, v.norm1(), 1E-10);
    }

    @Test
    public void testAsum() {
        System.out.println("Vector asum");
        Vector v = Vector.column(new double[]{-1.0, 2.0, -3.0});
        assertEquals(6.0, v.asum(), 1E-10);
    }

    @Test
    public void testNormInf() {
        System.out.println("Vector normInf");
        Vector v = Vector.column(new double[]{-5.0, 2.0, 3.0});
        assertEquals(5.0, v.normInf(), 1E-10);
    }

    // ── BLAS operations ─────────────────────────────────────────────────────

    @Test
    public void testDot() {
        System.out.println("Vector dot");
        // [1,2,3] · [4,5,6] = 4+10+18 = 32
        Vector a = Vector.column(new double[]{1.0, 2.0, 3.0});
        Vector b = Vector.column(new double[]{4.0, 5.0, 6.0});
        assertEquals(32.0, a.dot(b), 1E-10);
    }

    @Test
    public void testAxpy() {
        System.out.println("Vector axpy: y += alpha*x");
        // y=[1,1,1], x=[2,2,2], alpha=3 → y=[7,7,7]
        Vector y = Vector.column(new double[]{1.0, 1.0, 1.0});
        Vector x = Vector.column(new double[]{2.0, 2.0, 2.0});
        y.axpy(3.0, x);
        for (int i = 0; i < 3; i++) assertEquals(7.0, y.get(i), 1E-10);
    }

    @Test
    public void testSwap() {
        System.out.println("Vector swap");
        Vector a = Vector.column(new double[]{1.0, 2.0, 3.0});
        Vector b = Vector.column(new double[]{4.0, 5.0, 6.0});
        a.swap(b);
        assertEquals(4.0, a.get(0), 1E-10);
        assertEquals(1.0, b.get(0), 1E-10);
    }

    @Test
    public void testIamax() {
        System.out.println("Vector iamax");
        Vector v = Vector.column(new double[]{1.0, -5.0, 3.0});
        // iamax returns index of maximum absolute value → index 1 (|-5|=5)
        assertEquals(1, v.iamax());
    }

    // ── Slice & Copy ────────────────────────────────────────────────────────

    @Test
    public void testSlice() {
        System.out.println("Vector slice");
        Vector v = Vector.column(new double[]{10.0, 20.0, 30.0, 40.0, 50.0});
        Vector s = v.slice(1, 4);
        assertEquals(3, s.size());
        assertEquals(20.0, s.get(0), 1E-10);
        assertEquals(30.0, s.get(1), 1E-10);
        assertEquals(40.0, s.get(2), 1E-10);
    }

    @Test
    public void testCopy() {
        System.out.println("Vector copy");
        Vector v = Vector.column(new double[]{1.0, 2.0, 3.0});
        Vector c = v.copy();
        c.set(0, 99.0);
        // Original must not change
        assertEquals(1.0, v.get(0), 1E-10);
        assertEquals(99.0, c.get(0), 1E-10);
    }

    @Test
    public void testCopyRange() {
        System.out.println("Vector copy range");
        Vector v = Vector.column(new double[]{1.0, 2.0, 3.0, 4.0, 5.0});
        Vector c = v.copy(1, 4);  // [2,3,4]
        assertEquals(3, c.size());
        assertEquals(2.0, c.get(0), 1E-10);
        assertEquals(3.0, c.get(1), 1E-10);
        assertEquals(4.0, c.get(2), 1E-10);
    }

    @Test
    public void testStaticCopy() {
        System.out.println("Vector.copy(src, pos, dest, destPos, length)");
        Vector src  = Vector.column(new double[]{10.0, 20.0, 30.0});
        Vector dest = Vector.zeros(Float64, 5);
        Vector.copy(src, 0, dest, 2, 3);
        assertEquals(0.0,  dest.get(0), 1E-10);
        assertEquals(0.0,  dest.get(1), 1E-10);
        assertEquals(10.0, dest.get(2), 1E-10);
        assertEquals(20.0, dest.get(3), 1E-10);
        assertEquals(30.0, dest.get(4), 1E-10);
    }

    // ── toArray ─────────────────────────────────────────────────────────────

    @Test
    public void testToArrayDouble() {
        System.out.println("Vector toArray double");
        Vector v = Vector.column(new double[]{1.0, 2.0, 3.0});
        double[] arr = v.toArray(new double[0]);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, arr, 1E-10);
    }

    @Test
    public void testToArrayFloat() {
        System.out.println("Vector toArray float");
        Vector v = Vector.column(new float[]{1.0f, 2.0f, 3.0f});
        float[] arr = v.toArray(new float[0]);
        assertEquals(3, arr.length);
        assertEquals(1.0f, arr[0], 1E-6f);
        assertEquals(2.0f, arr[1], 1E-6f);
        assertEquals(3.0f, arr[2], 1E-6f);
    }

    // ── diagflat ────────────────────────────────────────────────────────────

    @Test
    public void testDiagflat() {
        System.out.println("Vector diagflat");
        Vector v = Vector.column(new double[]{2.0, 3.0, 5.0});
        DenseMatrix d = v.diagflat();
        assertEquals(3, d.nrow());
        assertEquals(3, d.ncol());
        assertEquals(2.0, d.get(0, 0), 1E-10);
        assertEquals(3.0, d.get(1, 1), 1E-10);
        assertEquals(5.0, d.get(2, 2), 1E-10);
        assertEquals(0.0, d.get(0, 1), 1E-10);
        assertEquals(0.0, d.get(1, 0), 1E-10);
    }

    // ── Float32 (fp32) ──────────────────────────────────────────────────────

    @Test
    public void testFloat32Operations() {
        System.out.println("Vector Float32 basic operations");
        float[] data = {1.0f, 2.0f, 3.0f, 4.0f};
        Vector v = Vector.column(data);
        assertEquals(Float32, v.scalarType());
        assertEquals(10.0, v.sum(), 1E-5);
        assertEquals(2.5,  v.mean(), 1E-5);
        // norm2: sqrt(1+4+9+16) = sqrt(30)
        assertEquals(Math.sqrt(30.0), v.norm2(), 1E-5);
    }

    @Test
    public void testFloat32MaxNegative() {
        System.out.println("Vector Float32 max negative (regression fix)");
        Vector v = Vector.column(new float[]{-5.0f, -1.0f, -3.0f});
        assertEquals(-1.0, v.max(), 1E-5);
    }
}

