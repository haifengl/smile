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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import smile.io.Read;
import smile.io.Write;
import smile.linalg.NativeLibrary;
import static org.junit.jupiter.api.Assertions.*;
import static smile.tensor.ScalarType.*;

/**
 *
 * @author Haifeng Li
 */
public class BandMatrixTest {
    double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};

    DenseMatrix a = DenseMatrix.of(A);
    BandMatrix band = BandMatrix.zeros(Float64, 3, 3, 1, 1);

    public BandMatrixTest() {
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++) {
                if (A[i][j] != 0.0) {
                    band.set(i, j, A[i][j]);
                }
            }
        }
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
        System.clearProperty(NativeLibrary.BACKEND_PROPERTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testMv(String backend) {
        System.out.println("mv");
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        Vector x = Vector.column(b);
        Vector y = a.mv(x);
        Vector y2 = band.mv(x);
        assertEquals(y, y2);
    }

    @Test
    public void testTranspose() {
        System.out.println("transpose");
        BandMatrix t = band.transpose();
        assertEquals(3, t.nrow());
        assertEquals(3, t.ncol());
        assertEquals(1, t.kl());
        assertEquals(1, t.ku());
        assertEquals(0.9, t.get(0, 0), 1E-7);
        assertEquals(0.8, t.get(2, 2), 1E-7);
        assertEquals(0.5, t.get(1, 1), 1E-7);
        assertEquals(0.0, t.get(0, 2), 1E-7);
        assertEquals(0.0, t.get(2, 0), 1E-7);
        assertEquals(0.4, t.get(1, 0), 1E-7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testSolve(String backend) {
        System.out.println("solve");
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        Vector y = a.copy().lu().solve(b);
        Vector y2 = band.solve(b);
        assertEquals(y, y2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "java"})
    public void testMatrixSolveAndSymmetricBand(String backend) {
        System.setProperty(NativeLibrary.BACKEND_PROPERTY, backend);
        double[][] B = {
                {0.5, 0.2},
                {0.5, 0.8},
                {0.5, 0.3}
        };
        DenseMatrix expected = DenseMatrix.of(B);
        a.copy().lu().solve(expected);

        DenseMatrix actual = DenseMatrix.of(B);
        band.solve(actual);
        assertEquals(expected, actual);

        BandMatrix symmLower = band.copy().withUplo(smile.linalg.UPLO.LOWER);
        BandMatrix symmUpper = band.copy().withUplo(smile.linalg.UPLO.UPPER);
        Vector x = Vector.column(b);
        assertEquals(a.mv(x), symmLower.mv(x));
        assertEquals(a.mv(x), symmUpper.mv(x));

        BandMatrix scaled = band.copy().scale(2.0);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (Math.abs(i - j) <= 1) {
                    assertEquals(2.0 * A[i][j], scaled.get(i, j), 1E-10);
                }
            }
        }
    }

    @Test
    public void testSerialize() throws Exception {
        System.out.println("serialize");
        java.nio.file.Path temp = Write.object(band);
        BandMatrix matrix = (BandMatrix) Read.object(temp);
        assertEquals(3, matrix.nrow());
        assertEquals(3, matrix.ncol());
    }
}
