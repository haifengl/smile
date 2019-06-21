/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.nd4j;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class NDMatrixTest {

    double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};
    double[][] C = {
            {0.97, 0.56, 0.12},
            {0.56, 0.50, 0.39},
            {0.12, 0.39, 0.73}
    };

    NDMatrix matrix = new NDMatrix(A);

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

    /**
     * Test of nrows method, of class NDMatrix.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(3, matrix.nrows());
    }

    /**
     * Test of ncols method, of class NDMatrix.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(3, matrix.ncols());
    }

    /**
     * Test of get method, of class NDMatrix.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(0.9, matrix.get(0, 0), 1E-7);
        assertEquals(0.8, matrix.get(2, 2), 1E-7);
        assertEquals(0.5, matrix.get(1, 1), 1E-7);
        assertEquals(0.0, matrix.get(2, 0), 1E-7);
        assertEquals(0.0, matrix.get(0, 2), 1E-7);
        assertEquals(0.4, matrix.get(0, 1), 1E-7);
    }

    /**
     * Test of ax method, of class NDMatrix.
     */
    @Test
    public void testAx() {
        System.out.println("ax");
        double[] d = new double[matrix.nrows()];
        matrix.ax(b, d);
        assertEquals(0.65, d[0], 1E-7);
        assertEquals(0.60, d[1], 1E-7);
        assertEquals(0.55, d[2], 1E-7);
    }

    /**
     * Test of atx method, of class NDMatrix.
     */
    @Test
    public void testAtx() {
        System.out.println("atx");
        double[] d = new double[matrix.nrows()];
        matrix.atx(b, d);
        assertEquals(0.65, d[0], 1E-7);
        assertEquals(0.60, d[1], 1E-7);
        assertEquals(0.55, d[2], 1E-7);
    }

    /**
     * Test of AAT method, of class NDMatrix.
     */
    @Test
    public void testAAT() {
        System.out.println("AAT");
        NDMatrix c = matrix.aat();
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Test of mm method, of class NDMatrix.
     */
    @Test
    public void testMm() {
        System.out.println("mm");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = {
                {0.6881997, -0.07121225, 0.7220180},
                {0.3700456, 0.89044952, -0.2648886},
                {0.6240573, -0.44947578, -0.6391588}
        };
        double[][] C = {
                {0.9527204, -0.2973347, 0.06257778},
                {-0.2808735, -0.9403636, -0.19190231},
                {0.1159052, 0.1652528, -0.97941688}
        };
        double[][] D = {
                { 0.9887140,  0.1482942, -0.0212965},
                { 0.1482942, -0.9889421, -0.0015881},
                {-0.0212965, -0.0015881, -0.9997719 }
        };
        double[][] E = {
                {0.0000,  0.0000, 1.0000},
                {0.0000, -1.0000, 0.0000},
                {1.0000,  0.0000, 0.0000}
        };

        NDMatrix a = new NDMatrix(A);
        NDMatrix b = new NDMatrix(B);
        double[][] F = b.abmm(a).transpose().toArray();

        assertTrue(MathEx.equals(a.abmm(b).toArray(), C, 1E-7));
        assertTrue(MathEx.equals(a.abtmm(b).toArray(), D, 1E-7));
        assertTrue(MathEx.equals(a.atbmm(b).toArray(), E, 1E-7));
        assertTrue(MathEx.equals(a.atbtmm(b).toArray(), F, 1E-7));
    }
}
