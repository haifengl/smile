/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.matrix;

import java.util.Arrays;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class PowerIterationTest {
    double[][] A = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
    };
    double[][] eigenVectors = {
        {0.6881997, -0.07121225,  0.7220180},
        {0.3700456,  0.89044952, -0.2648886},
        {0.6240573, -0.44947578, -0.6391588}
    };
    double[] eigenValues = {1.7498382, 0.3165784, 0.1335834};
    
    public PowerIterationTest() {
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
    public void testEigen() {
        System.out.println("Eigen");
        double[] v = new double[3];
        Arrays.fill(v, 1.0);

        Matrix matrix = Matrix.of(A);
        double eigenvalue = matrix.eigen(v);
        assertEquals(eigenValues[0], eigenvalue, 1E-4);

        double ratio = Math.abs(eigenVectors[0][0]/v[0]);
        for (int i = 1; i < 3; i++) {
            assertEquals(ratio, Math.abs(eigenVectors[i][0]/v[i]), 1E-4);
        }

        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++)
                A[i][j] = -A[i][j];
        }

        Arrays.fill(v, 1.0);
        matrix = Matrix.of(A);
        eigenvalue = matrix.eigen(v, 0.22, 1E-4, 4);
        assertEquals(-eigenValues[0], eigenvalue, 1E-3);

        ratio = Math.abs(eigenVectors[0][0]/v[0]);
        for (int i = 1; i < 3; i++) {
            assertEquals(ratio, Math.abs(eigenVectors[i][0]/v[i]), 1E-3);
        }
    }
}