/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

package smile.math.matrix;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static smile.math.blas.UPLO.*;

/**
 *
 * @author Haifeng Li
 */
public class ARPACKTest {
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

    public ARPACKTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testSA() {
        System.out.println("SA");
        Matrix a = new Matrix(A);
        a.uplo(LOWER);
        Matrix.EVD eig = ARPACK.syev(a, 2, ARPACK.SymmWhich.SA);
        assertEquals(eigenValues[1], eig.wr[0], 1E-4);
        assertEquals(eigenValues[2], eig.wr[1], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][1]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][2]), Math.abs(eig.Vr.get(i, 1)), 1E-4);
        }
    }

    @Test
    public void testLA() {
        System.out.println("LA");
        Matrix a = new Matrix(A);
        a.uplo(LOWER);
        Matrix.EVD eig = ARPACK.syev(a, 1, ARPACK.SymmWhich.LA);
        assertEquals(eigenValues[0], eig.wr[0], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }

        // non-symmetric
        eig = ARPACK.eigen(a, 1, ARPACK.AsymmWhich.LM);
        assertEquals(eigenValues[0], eig.wr[0], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }
    }

    @Test
    public void testSymmSA() {
        System.out.println("Symm SA");
        SymmMatrix a = new SymmMatrix(LOWER, A);
        Matrix.EVD eig = ARPACK.syev(a, 2, ARPACK.SymmWhich.SA);
        assertEquals(eigenValues[1], eig.wr[0], 1E-4);
        assertEquals(eigenValues[2], eig.wr[1], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][1]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][2]), Math.abs(eig.Vr.get(i, 1)), 1E-4);
        }
    }

    @Test
    public void testSymmLA() {
        System.out.println("SymmLA");
        SymmMatrix a = new SymmMatrix(LOWER, A);
        Matrix.EVD eig = ARPACK.syev(a, 1, ARPACK.SymmWhich.LA);
        assertEquals(eigenValues[0], eig.wr[0], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }

        // non-symmetric
        eig = ARPACK.eigen(a, 1, ARPACK.AsymmWhich.LM);
        assertEquals(eigenValues[0], eig.wr[0], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(eig.Vr.get(i, 0)), 1E-4);
        }
    }

    @Test
    public void testDiag500() {
        System.out.println("500 x 500 diagonal matrix");
        BandMatrix a = new BandMatrix(500, 500, 0, 0);
        a.set(0, 0, 2.0);
        a.set(1, 1, 2.0);
        a.set(2, 2, 2.0);
        a.set(3, 3, 2.0);
        for (int i = 4; i < 500; i++) {
            a.set(i, i, (500 - i) / 500.0);
        }

        Matrix.EVD eig = ARPACK.syev(a, 6, ARPACK.SymmWhich.LA);
        assertEquals(2.0, eig.wr[0], 1E-4);
        assertEquals(2.0, eig.wr[1], 1E-4);
        assertEquals(2.0, eig.wr[2], 1E-4);
        assertEquals(2.0, eig.wr[3], 1E-4);
        assertEquals(0.992, eig.wr[4], 1E-4);
        assertEquals(0.990, eig.wr[5], 1E-4);

        // non-symmetric
        eig = ARPACK.eigen(a, 6, ARPACK.AsymmWhich.LM);
        assertEquals(2.0, eig.wr[0], 1E-4);
        assertEquals(2.0, eig.wr[1], 1E-4);
        assertEquals(2.0, eig.wr[2], 1E-4);
        assertEquals(2.0, eig.wr[3], 1E-4);
        assertEquals(0.992, eig.wr[4], 1E-4);
        assertEquals(0.990, eig.wr[5], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }
    }
}