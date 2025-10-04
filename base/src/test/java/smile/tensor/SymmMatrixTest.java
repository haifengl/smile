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
import static org.junit.jupiter.api.Assertions.*;
import static smile.linalg.UPLO.*;

/**
 *
 * @author Haifeng Li
 */
public class SymmMatrixTest {

    public SymmMatrixTest() {
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
        System.out.println("SymmMatrix");
        double[][] A = {
                {0.9000f, 0.4000f, 0.0000f},
                {0.4000f, 0.5000f, 0.3000f},
                {0.0000f, 0.3000f, 0.8000f}
        };
        double[] b = {0.5f, 0.5f, 0.5f};

        DenseMatrix a = DenseMatrix.of(A);
        a.withUplo(LOWER);
        SymmMatrix symm = SymmMatrix.of(a);

        Vector x = Vector.column(b);
        Vector y = a.mv(x);
        Vector y2 = symm.mv(x);
        assertEquals(y, y2);
    }
}