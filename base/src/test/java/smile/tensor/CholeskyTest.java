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
import static smile.linalg.UPLO.*;

/**
 * Unit tests for {@link Cholesky} in both default and pure-Java backend modes.
 *
 * @author Haifeng Li
 */
public class CholeskyTest {
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
    static final double[][] L = {
            {0.9486833,  0.00000000, 0.0000000},
            {0.4216370,  0.56764621, 0.0000000},
            {0.7378648, -0.01957401, 0.5051459}
    };
    static final double[] b = {0.5, 0.5, 0.5};
    static final float[] b32 = {0.5f, 0.5f, 0.5f};
    static final double[] x = {-0.2027027, 0.8783784, 0.4729730};
    static final double[][] B = {
            {0.5, 0.2},
            {0.5, 0.8},
            {0.5, 0.3}
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
    public void testLowerCholesky(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix a = DenseMatrix.of(A).withUplo(LOWER);

        // When
        Cholesky chol = a.cholesky();

        // Then
        for (int i = 0; i < a.nrow(); i++) {
            for (int j = 0; j <= i; j++) {
                assertEquals(Math.abs(L[i][j]), Math.abs(chol.lu().get(i, j)), 1E-7);
            }
        }
        assertEquals(0.074, chol.det(), 1E-7);
        assertEquals(Math.log(0.074), chol.logdet(), 1E-7);

        Vector sol = chol.solve(b);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], sol.get(i), 1E-7);
        }

        DenseMatrix rhs = DenseMatrix.of(B);
        chol.solve(rhs);
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], rhs.get(i, j), 1E-6);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testUpperCholesky(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix a = DenseMatrix.of(A).withUplo(UPPER);

        // When
        Cholesky chol = a.cholesky();
        Vector sol = chol.solve(b);
        DenseMatrix inv = chol.inverse();

        // Then
        for (int i = 0; i < a.nrow(); i++) {
            for (int j = i; j < a.ncol(); j++) {
                assertEquals(Math.abs(L[j][i]), Math.abs(chol.lu().get(i, j)), 1E-7);
            }
        }
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], sol.get(i), 1E-7);
        }
        assertEquals(DenseMatrix.of(A).eye(3), DenseMatrix.of(A).mm(inv));
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testCholesky32(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix a = DenseMatrix.of(A32).withUplo(LOWER);

        // When
        Cholesky chol = a.cholesky();
        Vector sol = chol.solve(b32);

        // Then
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], sol.get(i), 1E-5);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testNotPositiveDefiniteThrows(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        double[][] nonSpd = {
                {1.0, 2.0},
                {2.0, 1.0}
        };
        DenseMatrix a = DenseMatrix.of(nonSpd).withUplo(LOWER);

        // When / Then
        assertThrows(ArithmeticException.class, a::cholesky);
    }
}
