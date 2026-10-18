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
 * Unit tests for {@link LU} in both default and pure-Java backend modes.
 *
 * @author Haifeng Li
 */
public class LUTest {
    static final double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
    };
    static final float[][] A32 = {
            {0.9000f, 0.4000f, 0.7000f},
            {0.4000f, 0.5000f, 0.3000f},
            {0.7000f, 0.3000f, 0.8000f}
    };
    static final double[] b = {0.5, 0.5, 0.5};
    static final float[] b32 = {0.5f, 0.5f, 0.5f};
    static final double[] x = {-0.2027027, 0.8783784, 0.4729730};
    static final double[][] B = {
            {0.5, 0.2},
            {0.5, 0.8},
            {0.5, 0.3}
    };
    static final float[][] B32 = {
            {0.5f, 0.2f},
            {0.5f, 0.8f},
            {0.5f, 0.3f}
    };
    static final double[][] X = {
            {-0.2027027, -1.2837838},
            { 0.8783784,  2.2297297},
            { 0.4729730,  0.6621622}
    };

    @AfterEach
    public void tearDown() {
        System.clearProperty(NativeLibrary.BACKEND_PROPERTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testSolveVector64(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix a = DenseMatrix.of(A);

        // When
        LU lu = a.lu();
        Vector sol = lu.solve(b);

        // Then
        assertFalse(lu.isSingular());
        assertEquals(x.length, sol.size());
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], sol.get(i), 1E-7);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testSolveMatrix64(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix a = DenseMatrix.of(A);
        DenseMatrix rhs = DenseMatrix.of(B);

        // When
        LU lu = a.lu();
        lu.solve(rhs);

        // Then
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], rhs.get(i, j), 1E-7);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testSolve32(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix a = DenseMatrix.of(A32);
        DenseMatrix rhs = DenseMatrix.of(B32);

        // When
        LU lu = a.lu();
        Vector sol = lu.solve(b32);
        lu.solve(rhs);

        // Then
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], sol.get(i), 1E-5);
        }
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], rhs.get(i, j), 1E-5);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testDetAndInverse(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix orig = DenseMatrix.of(A);
        LU lu = orig.copy().lu();

        // When
        double det = lu.det();
        DenseMatrix inv = lu.inverse();
        DenseMatrix prod = orig.mm(inv);

        // Then
        // det(A) = 0.9*(0.4-0.09) - 0.4*(0.32-0.21) + 0.7*(0.12-0.35) = 0.279 - 0.044 - 0.161 = 0.074
        assertEquals(0.074, det, 1E-7);
        DenseMatrix eye = orig.eye(3);
        assertEquals(eye, prod);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testSingularMatrix(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        double[][] singularData = {
                {1.0, 2.0, 3.0},
                {2.0, 4.0, 6.0},
                {0.0, 1.0, 1.0}
        };
        DenseMatrix a = DenseMatrix.of(singularData);

        // When
        LU lu = a.lu();

        // Then
        assertTrue(lu.isSingular());
        assertEquals(0.0, lu.det(), 1E-12);
        assertThrows(RuntimeException.class, () -> lu.solve(b));
    }
}
