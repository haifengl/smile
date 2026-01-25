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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import org.junit.jupiter.api.*;
import smile.io.Read;
import smile.io.Write;

import static org.junit.jupiter.api.Assertions.*;
import static smile.linalg.UPLO.*;

/**
 *
 * @author Haifeng Li
 */
public class SymmMatrixTest {
    double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};

    DenseMatrix a = DenseMatrix.of(A).withUplo(LOWER);
    SymmMatrix symm = SymmMatrix.of(a);

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
    public void testMv() throws Exception{
        System.out.println("mv");
        Vector x = Vector.column(b);
        Vector y = a.mv(x);
        Vector y2 = symm.mv(x);
        assertEquals(y, y2);
    }

    @Test
    public void testSolve() {
        System.out.println("solve");
        Vector y = a.copy().lu().solve(b);
        Vector y2 = symm.solve(b);
        assertEquals(y, y2);
    }

    @Test
    public void testSerialize() throws Exception {
        System.out.println("serialize");
        java.nio.file.Path temp = Write.object(symm);
        SymmMatrix matrix = (SymmMatrix) Read.object(temp);
        assertEquals(3, matrix.nrow());
        assertEquals(3, matrix.ncol());
    }
}
