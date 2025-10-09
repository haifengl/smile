/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import org.junit.jupiter.api.*;
import smile.io.Read;
import smile.io.Write;
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
    }

    @Test
    public void test() {
        System.out.println("mv");
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

    @Test
    public void testSolve() {
        System.out.println("solve");
        Vector y = a.copy().lu().solve(b);
        Vector y2 = band.solve(b);
        assertEquals(y, y2);
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