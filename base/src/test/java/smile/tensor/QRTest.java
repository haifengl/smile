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
 * Unit tests for {@link QR} in both default and pure-Java backend modes.
 *
 * @author Haifeng Li
 */
public class QRTest {
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
    public void testSquareQR(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix orig = DenseMatrix.of(A);

        // When
        QR qr = orig.copy().qr();
        DenseMatrix Q = qr.Q();
        DenseMatrix R = qr.R();
        Vector sol = qr.solve(b);
        DenseMatrix rhs = DenseMatrix.of(B);
        qr.solve(rhs);

        // Then
        // Q is orthogonal: Q' * Q = I
        assertEquals(orig.eye(3), Q.ata());
        // Q * R = A
        assertEquals(orig, Q.mm(R));

        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], sol.get(i), 1E-7);
        }
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], rhs.get(i, j), 1E-7);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testRectangularLeastSquaresAndToCholesky(String backend) {
        // Given: overdetermined 4x2 system A * x ≈ b
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        double[][] rect = {
                {1.0, 1.0},
                {1.0, 2.0},
                {1.0, 3.0},
                {1.0, 4.0}
        };
        double[] target = {6.0, 5.0, 7.0, 10.0};
        DenseMatrix orig = DenseMatrix.of(rect);

        // When
        QR qr = orig.copy().qr();
        DenseMatrix Q = qr.Q();
        DenseMatrix R = qr.R();
        Vector ls = qr.solve(target);
        Cholesky chol = qr.toCholesky();
        Vector normalSol = chol.solve(orig.tv(Vector.column(target)).toArray(new double[0]));

        // Then: least-squares line for (1,6),(2,5),(3,7),(4,10) is y = 3.5 + 1.4*t
        assertEquals(orig.eye(2), Q.ata());
        assertEquals(orig, Q.mm(R));
        assertEquals(2, ls.size());
        assertEquals(3.5, ls.get(0), 1E-7);
        assertEquals(1.4, ls.get(1), 1E-7);
        assertEquals(3.5, normalSol.get(0), 1E-7);
        assertEquals(1.4, normalSol.get(1), 1E-7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testQR32(String backend) {
        // Given
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        DenseMatrix orig = DenseMatrix.of(A32);

        // When
        QR qr = orig.copy().qr();
        DenseMatrix Q = qr.Q();
        DenseMatrix R = qr.R();
        Vector sol = qr.solve(b32);

        // Then
        assertEquals(orig, Q.mm(R));
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], sol.get(i), 1E-5);
        }
    }
}
