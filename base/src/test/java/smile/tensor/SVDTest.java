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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import smile.linalg.NativeLibrary;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SVD} in both default and pure-Java backend modes.
 *
 * @author Haifeng Li
 */
public class SVDTest {
    static final double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
    };
    static final double[] b = {0.5, 0.5, 0.5};
    static final double[] x = {-0.2027027, 0.8783784, 0.4729730};

    @AfterEach
    public void tearDown() {
        System.clearProperty(NativeLibrary.BACKEND_PROPERTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testSquareSVD(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix orig = DenseMatrix.of(A);

        // When
        SVD svd = orig.copy().svd();
        Vector sol = svd.solve(b);
        DenseMatrix reconstructed = svd.U().mm(svd.diag()).mm(svd.Vt());

        // Then
        assertEquals(3, svd.rank());
        assertEquals(0, svd.nullity());
        assertNull(svd.nullspace());
        assertNotNull(svd.range());
        assertEquals(orig, reconstructed);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], sol.get(i), 1E-7);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testTallAndWideSVD(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        double[][] tallData = {
                { 1.19720880, -1.8391378,  0.3019585, -1.1165701},
                { 0.06605075,  1.0315583,  0.8294362, -0.3646043},
                {-1.02637715,  1.0747931, -0.8089055, -0.4726863},
                {-1.45817729, -0.8942353,  0.3459245,  1.5068363},
                {-0.07318103, -0.2783787,  1.2237598,  0.1995332},
                { 0.88248425, -0.9360321,  0.1393172,  0.1393281},
                { 0.12641406, -0.8710055, -0.2712301,  0.2296515}
        };
        DenseMatrix tall = DenseMatrix.of(tallData);
        DenseMatrix wide = tall.transpose();

        // When
        SVD svdTall = tall.copy().svd();
        SVD svdWide = wide.copy().svd();

        // Then
        assertEquals(7, svdTall.U().nrow());
        assertEquals(4, svdTall.U().ncol());
        assertEquals(4, svdTall.Vt().nrow());
        assertEquals(4, svdTall.Vt().ncol());
        assertEquals(tall.eye(4), svdTall.U().ata());
        assertEquals(tall.eye(4), svdTall.Vt().aat());
        assertEquals(tall, svdTall.U().mm(svdTall.s().diagflat()).mm(svdTall.Vt()));

        assertEquals(4, svdWide.U().nrow());
        assertEquals(4, svdWide.U().ncol());
        assertEquals(4, svdWide.Vt().nrow());
        assertEquals(7, svdWide.Vt().ncol());
        assertEquals(wide.eye(4), svdWide.U().ata());
        assertEquals(wide.eye(4), svdWide.Vt().aat());
        assertEquals(wide, svdWide.U().mm(svdWide.s().diagflat()).mm(svdWide.Vt()));
        assertArrayEquals(svdTall.s().toArray(new double[0]), svdWide.s().toArray(new double[0]), 1E-6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testRankDeficientNullspaceAndPinv(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        double[][] rankDef = {
                {1.0, 2.0, 3.0},
                {4.0, 5.0, 6.0},
                {5.0, 7.0, 9.0}
        };
        DenseMatrix a = DenseMatrix.of(rankDef);

        // When
        SVD svd = a.copy().svd();
        DenseMatrix nullspace = svd.nullspace();
        DenseMatrix pinv = svd.pinv();

        // Then
        assertEquals(2, svd.rank());
        assertEquals(1, svd.nullity());
        assertNotNull(nullspace);
        assertEquals(3, nullspace.nrow());
        assertEquals(1, nullspace.ncol());
        // A * nullspace = 0
        DenseMatrix zero = a.mm(nullspace);
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0, zero.get(i, 0), 1E-7);
        }
        // Moore-Penrose conditions: A * pinv * A = A, pinv * A * pinv = pinv
        assertEquals(a, a.mm(pinv).mm(a));
        assertEquals(pinv, pinv.mm(a).mm(pinv));
    }
}
