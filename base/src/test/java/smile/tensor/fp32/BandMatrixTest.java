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
package smile.tensor.fp32;

import org.junit.jupiter.api.*;
import smile.tensor.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BandMatrixTest {

    public BandMatrixTest() {
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
    public void testFloatBandMatrix() {
        System.out.println("FloatBandMatrix");
        float[][] A = {
            {0.9000f, 0.4000f, 0.0000f},
            {0.4000f, 0.5000f, 0.3000f},
            {0.0000f, 0.3000f, 0.8000f}
        };
        float[] b = {0.5f, 0.5f, 0.5f};

        DenseMatrix a = DenseMatrix.of(A);
        BandMatrix band = BandMatrix.zeros(ScalarType.Float32, 3, 3, 1, 1);
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++) {
                if (A[i][j] != 0.0f) {
                    band.set(i, j, A[i][j]);
                }
            }
        }

        Vector x = Vector.column(b);
        Vector y = a.mv(x);
        Vector y2 = band.mv(x);
        assertEquals(y, y2);
    }
}