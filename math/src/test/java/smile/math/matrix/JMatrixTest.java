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

package smile.math.matrix;

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
public class JMatrixTest {

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

    JMatrix matrix = new JMatrix(A);

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
     * Test of nrows method, of class ColumnMajorMatrix.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(3, matrix.nrows());
    }

    /**
     * Test of ncols method, of class ColumnMajorMatrix.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(3, matrix.ncols());
    }

    /**
     * Test of colMean method, of class Math.
     */
    @Test
    public void testColMeans() {
        System.out.println("colMeans");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.06067647, -0.12325383, 0.56076753};

        double[] result = new JMatrix(A).colMeans();
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of rowMean method, of class Math.
     */
    @Test
    public void testRowMeans() {
        System.out.println("rowMeans");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.4938100, -0.2617642, 0.1447914};

        double[] result = new JMatrix(A).rowMeans();
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of get method, of class ColumnMajorMatrix.
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
     * Test of ax method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAx() {
        System.out.println("ax");
        double[] d = new double[matrix.nrows()];
        matrix.ax(b, d);
        assertEquals(0.65, d[0], 1E-10);
        assertEquals(0.60, d[1], 1E-10);
        assertEquals(0.55, d[2], 1E-10);
    }

    /**
     * Test of axpy method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAxpy() {
        System.out.println("axpy");
        double[] d = new double[matrix.nrows()];
        for (int i = 0; i < d.length; i++) d[i] = 1.0;
        matrix.axpy(b, d);
        assertEquals(1.65, d[0], 1E-10);
        assertEquals(1.60, d[1], 1E-10);
        assertEquals(1.55, d[2], 1E-10);
    }

    /**
     * Test of axpy method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAxpy2() {
        System.out.println("axpy b = 2");
        double[] d = new double[matrix.nrows()];
        for (int i = 0; i < d.length; i++) d[i] = 1.0;
        matrix.axpy(b, d, 2.0);
        assertEquals(2.65, d[0], 1E-10);
        assertEquals(2.60, d[1], 1E-10);
        assertEquals(2.55, d[2], 1E-10);
    }

    /**
     * Test of atx method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAtx() {
        System.out.println("atx");
        double[] d = new double[matrix.nrows()];
        matrix.atx(b, d);
        assertEquals(0.65, d[0], 1E-10);
        assertEquals(0.60, d[1], 1E-10);
        assertEquals(0.55, d[2], 1E-10);
    }

    /**
     * Test of atxpy method, of class JMatrix.
     */
    @Test
    public void testAtxpy() {
        System.out.println("atxpy");
        double[] d = new double[matrix.nrows()];
        for (int i = 0; i < d.length; i++) d[i] = 1.0;
        matrix.atxpy(b, d);
        assertEquals(1.65, d[0], 1E-10);
        assertEquals(1.60, d[1], 1E-10);
        assertEquals(1.55, d[2], 1E-10);
    }

    /**
     * Test of atxpy method, of class JMatrix.
     */
    @Test
    public void testAtxpy2() {
        System.out.println("atxpy b = 2");
        double[] d = new double[matrix.nrows()];
        for (int i = 0; i < d.length; i++) d[i] = 1.0;
        matrix.atxpy(b, d, 2.0);
        assertEquals(2.65, d[0], 1E-10);
        assertEquals(2.60, d[1], 1E-10);
        assertEquals(2.55, d[2], 1E-10);
    }

    /**
     * Test of AAT method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAAT() {
        System.out.println("AAT");
        JMatrix c = matrix.aat();
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Test of plusEquals method, of class JMatrix.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
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
                {1.4102177, 0, 1.4102177},
                {0.1051570, 0, 0.1051570},
                {-0.0151015, 0, -0.0151015}
        };
        JMatrix a = new JMatrix(A);
        JMatrix b = new JMatrix(B);
        JMatrix c = a.add(b);
        assertTrue(MathEx.equals(C, c.toArray(), 1E-7));
    }

    /**
     * Test of minusEquals method, of class JMatrix.
     */
    @Test
    public void testSub() {
        System.out.println("sub");
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
                {0.0338183, 0.1424245, -0.0338183},
                {-0.6349342, -1.7808990, 0.6349342},
                {-1.2632161, 0.8989516, 1.2632161}
        };
        JMatrix a = new JMatrix(A);
        JMatrix b = new JMatrix(B);
        JMatrix c = a.sub(b);
        assertTrue(MathEx.equals(C, c.toArray(), 1E-7));
    }

    /**
     * Test of mm method, of class ColumnMajorMatrix.
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

        JMatrix a = new JMatrix(A);
        JMatrix b = new JMatrix(B);
        double[][] F = b.abmm(a).transpose().toArray();

        assertTrue(MathEx.equals(a.abmm(b).toArray(), C, 1E-7));
        assertTrue(MathEx.equals(a.abtmm(b).toArray(), D, 1E-7));
        assertTrue(MathEx.equals(a.atbmm(b).toArray(), E, 1E-7));
        assertTrue(MathEx.equals(a.atbtmm(b).toArray(), F, 1E-7));
    }

    /**
     * Test of market method, of class Matrix.
     */
    @Test(expected = Test.None.class)
    public void testMatrixMarket08blocks() throws Exception {
        System.out.println("market 08blocks");
        SparseMatrix data = (SparseMatrix) Matrix.market(smile.util.Paths.getTestData("matrix/08blocks.mtx"));
        assertEquals(592, data.length());
        assertEquals(300, data.nrows());
        assertEquals(300, data.ncols());
        assertEquals(94.0, data.get(36, 0), 1E-7);
        assertEquals(1.0, data.get(0, 1), 1E-7);
        assertEquals(33.0, data.get(36, 1), 1E-7);
        assertEquals(95.0, data.get(299, 299), 1E-7);
    }

    /**
     * Test of market method, of class Matrix.
     */
    @Test(expected = Test.None.class)
    public void testMatrixMarketGr900() throws Exception {
        System.out.println("market gr900");
        SparseMatrix data = (SparseMatrix) Matrix.market(smile.util.Paths.getTestData("matrix/gr_900_900_crg.mm"));
        assertEquals(true, data.isSymmetric());
        assertEquals(8644, data.length());
        assertEquals(900, data.nrows());
        assertEquals(900, data.ncols());
        assertEquals( 8.0, data.get(0, 0), 1E-7);
        assertEquals( 8.0, data.get(1, 1), 1E-7);
        assertEquals(-1.0, data.get(2, 1), 1E-7);
        assertEquals(-1.0, data.get(30, 1), 1E-7);
        assertEquals( 8.0, data.get(899, 899), 1E-7);

        // it is symmetric
        assertEquals(-1.0, data.get(1, 2), 1E-7);
        assertEquals(-1.0, data.get(1, 30), 1E-7);
    }

    /**
     * Test of market method, of class Matrix.
     */
    @Test(expected = Test.None.class)
    public void testMatrixMarketCrk() throws Exception {
        System.out.println("market crk");
        SparseMatrix data = (SparseMatrix) Matrix.market(smile.util.Paths.getTestData("matrix/m_05_05_crk.mm"));
        assertEquals(false, data.isSymmetric());
        assertEquals(8, data.length());
        assertEquals(5, data.nrows());
        assertEquals(5, data.ncols());
        assertEquals( 15.0, data.get(0, 4), 1E-7);
        assertEquals( 23.0, data.get(1, 2), 1E-7);
        assertEquals( 24.0, data.get(1, 3), 1E-7);
        assertEquals( 35.0, data.get(2, 4), 1E-7);

        // it is skew-symmetric
        assertEquals( -15.0, data.get(4, 0), 1E-7);
        assertEquals( -23.0, data.get(2, 1), 1E-7);
        assertEquals( -24.0, data.get(3, 1), 1E-7);
        assertEquals( -35.0, data.get(4, 2), 1E-7);
    }

    /**
     * Test of market method, of class Matrix.
     */
    @Test(expected = Test.None.class)
    public void testMatrixMarketCrs() throws Exception {
        System.out.println("market crs");
        SparseMatrix data = (SparseMatrix) Matrix.market(smile.util.Paths.getTestData("matrix/m_05_05_crs.mm"));
        assertEquals(true, data.isSymmetric());
        assertEquals(18, data.length());
        assertEquals(5, data.nrows());
        assertEquals(5, data.ncols());
        assertEquals(11.0, data.get(0, 0), 1E-7);
        assertEquals(15.0, data.get(0, 4), 1E-7);
        assertEquals(22.0, data.get(1, 1), 1E-7);
        assertEquals(23.0, data.get(1, 2), 1E-7);
        assertEquals(55.0, data.get(4, 4), 1E-7);

        // it is skew-symmetric
        assertEquals(15.0, data.get(4, 0), 1E-7);
        assertEquals(22.0, data.get(1, 1), 1E-7);
        assertEquals(23.0, data.get(2, 1), 1E-7);
    }

    /**
     * Test of market method, of class Matrix.
     */
    @Test(expected = Test.None.class)
    public void testMatrixMarketDense() throws Exception {
        System.out.println("market dense");
        Matrix data = Matrix.market(smile.util.Paths.getTestData("matrix/m_10_01.mm"));
        assertEquals(false, data.isSymmetric());
        assertEquals(10, data.nrows());
        assertEquals(1, data.ncols());
        assertEquals(0.193523, data.get(0, 0), 1E-7);
        assertEquals(0.200518, data.get(1, 0), 1E-7);
        assertEquals(0.625800, data.get(2, 0), 1E-7);
        assertEquals(0.585233, data.get(3, 0), 1E-7);
        assertEquals(0.127585, data.get(9, 0), 1E-7);
    }
}
