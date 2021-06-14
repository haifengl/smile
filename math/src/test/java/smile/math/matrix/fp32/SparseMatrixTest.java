/*
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
 */

package smile.math.matrix.fp32;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import static smile.math.blas.Transpose.NO_TRANSPOSE;
import static smile.math.blas.Transpose.TRANSPOSE;

/**
 *
 * @author Haifeng Li
 */
public class SparseMatrixTest {

    float[][] A = {
            {0.9000f, 0.4000f, 0.0000f},
            {0.4000f, 0.5000f, 0.3000f},
            {0.0000f, 0.3000f, 0.8000f}
    };
    float[] b = {0.5f, 0.5f, 0.5f};
    float[][] C = {
            {0.97f, 0.56f, 0.12f},
            {0.56f, 0.50f, 0.39f},
            {0.12f, 0.39f, 0.73f}
    };

    SparseMatrix sparse = new SparseMatrix(A, 1E-6f);

    public SparseMatrixTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testNrows() {
        System.out.println("nrow");
        assertEquals(3, sparse.nrow());
    }

    @Test
    public void testNcols() {
        System.out.println("ncol");
        assertEquals(3, sparse.ncol());
    }

    @Test
    public void testSize() {
        System.out.println("size");
        assertEquals(7, sparse.size());
    }

    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(0.9, sparse.get(0, 0), 1E-7);
        assertEquals(0.8, sparse.get(2, 2), 1E-7);
        assertEquals(0.5, sparse.get(1, 1), 1E-7);
        assertEquals(0.0, sparse.get(2, 0), 1E-7);
        assertEquals(0.0, sparse.get(0, 2), 1E-7);
        assertEquals(0.4, sparse.get(0, 1), 1E-7);
    }

    @Test
    public void testAx() {
        System.out.println("ax");
        float[] d = new float[sparse.nrow()];
        sparse.mv(b, d);
        assertEquals(0.65, d[0], 1E-7f);
        assertEquals(0.60, d[1], 1E-7f);
        assertEquals(0.55, d[2], 1E-7f);
    }

    @Test
    public void testAxpy() {
        System.out.println("axpy");
        float[] d = new float[sparse.nrow()];
        Arrays.fill(d, 1.0f);
        sparse.mv(NO_TRANSPOSE, 1.0f, b, 1.0f, d);
        assertEquals(1.65, d[0], 1E-7f);
        assertEquals(1.60, d[1], 1E-7f);
        assertEquals(1.55, d[2], 1E-7f);
    }

    @Test
    public void testAxpy2() {
        System.out.println("axpy b = 2");
        float[] d = new float[sparse.nrow()];
        Arrays.fill(d, 1.0f);
        sparse.mv(NO_TRANSPOSE, 1.0f, b, 2.0f, d);
        assertEquals(2.65, d[0], 1E-7f);
        assertEquals(2.60, d[1], 1E-7f);
        assertEquals(2.55, d[2], 1E-7f);
    }

    @Test
    public void testAtx() {
        System.out.println("atx");
        float[] d = new float[sparse.nrow()];
        sparse.tv(b, d);
        assertEquals(0.65, d[0], 1E-7f);
        assertEquals(0.60, d[1], 1E-7f);
        assertEquals(0.55, d[2], 1E-7f);
    }

    @Test
    public void testAtxpy() {
        System.out.println("atxpy");
        float[] d = new float[sparse.nrow()];
        Arrays.fill(d, 1.0f);
        sparse.mv(TRANSPOSE, 1.0f, b, 1.0f, d);
        assertEquals(1.65, d[0], 1E-7f);
        assertEquals(1.60, d[1], 1E-7f);
        assertEquals(1.55, d[2], 1E-7f);
    }

    @Test
    public void testAtxpy2() {
        System.out.println("atxpy b = 2");
        float[] d = new float[sparse.nrow()];
        Arrays.fill(d, 1.0f);
        sparse.mv(TRANSPOSE, 1.0f, b, 2.0f, d);
        assertEquals(2.65, d[0], 1E-7f);
        assertEquals(2.60, d[1], 1E-7f);
        assertEquals(2.55, d[2], 1E-7f);
    }

    @Test
    public void testMm() {
        System.out.println("mm");
        SparseMatrix c = sparse.mm(sparse);
        assertEquals(3, c.nrow());
        assertEquals(3, c.ncol());
        assertEquals(9, c.size());
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    @Test
    public void testAAT() {
        System.out.println("AAT");
        SparseMatrix c = sparse.aat();
        assertEquals(3, c.nrow());
        assertEquals(3, c.ncol());
        assertEquals(9, c.size());
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    @Test
    public void testIteration() {
        System.out.println("iteration");
        Random rand = new Random();
        float[][] d = new float[1000][2000];
        int nonzeroCount = 10000;
        int limitedNonZeros = 0;
        for (int i = 0; i < nonzeroCount; i++) {
            int row, col;
            do {
                row = rand.nextInt(1000);
                col = rand.nextInt(2000);
            } while (d[row][col] != 0);
            if (col >= 100 && col < 400) {
                limitedNonZeros++;
            }
            d[row][col] = (float) rand.nextGaussian() + 10;
        }

        // now that we have the data, we can to the actual SparseMatrix part
        SparseMatrix m = new SparseMatrix(d, 1e-7f);

        // verify all values and the number of non-zeros
        AtomicInteger k = new AtomicInteger(0);
        for (SparseMatrix.Entry e : m) {
            assertEquals(d[e.i][e.j], e.x, 0.0);
            assertEquals(d[e.i][e.j], m.get(e.i, e.j), 0.0);
            k.incrementAndGet();
        }
        assertEquals(nonzeroCount, k.get());

        // iterate over just a few columns
        k.set(0);
        m.iterator(100, 400).forEachRemaining(e -> {
            assertTrue(e.j >= 100);
            assertTrue(e.j < 400);
            assertEquals(d[e.i][e.j], e.x, 0);
            assertEquals(d[e.i][e.j], m.get(e.i, e.j), 0);
            k.incrementAndGet();
        });
        assertEquals(limitedNonZeros, k.get());

        k.set(0);
        m.nonzeros().forEach(e -> {
            assertEquals(d[e.i][e.j], e.x, 0);
            assertEquals(d[e.i][e.j], m.get(e.i, e.j), 0);
            k.incrementAndGet();
        });
        assertEquals(nonzeroCount, k.get());

        k.set(0);
        m.nonzeros(100, 400).forEach(e -> {
            assertTrue(e.j >= 100);
            assertTrue(e.j < 400);

            assertEquals(d[e.i][e.j], e.x, 0);
            assertEquals(d[e.i][e.j], m.get(e.i, e.j), 0);
            k.incrementAndGet();
        });
        assertEquals(limitedNonZeros, k.get());
    }

    @Test
    public void benchmarkIterationSpeed() {
        System.out.println("speed");

        Random rand = new Random();
        float[][] d = new float[1000][2000];
        for (int i = 0; i < 500000; i++) {
            int row, col;
            do {
                row = rand.nextInt(1000);
                col = rand.nextInt(2000);
            } while (d[row][col] != 0);
            d[row][col] = (float) rand.nextGaussian();
        }
        SparseMatrix m = new SparseMatrix(d, 1e-7f);

        double t0 = System.nanoTime() / 1e9;
        float[] sum1 = new float[2000];
        for (int rep = 0; rep < 1000; rep++) {
            m.iterator().forEachRemaining(e -> sum1[e.j] += e.x);
        }
        double t1 = System.nanoTime() / 1e9;
        float sum = 0;
        for (float v : sum1) {
            sum += v;
        }
        System.out.printf("iterator: %.3f (%.2f)\n", (t1 - t0), sum);

        t0 = System.nanoTime() / 1e9;
        float[] sum2 = new float[2000];
        for (int rep = 0; rep < 1000; rep++) {
            m.nonzeros().forEach(e -> sum2[e.j] += e.x);
        }
        t1 = System.nanoTime() / 1e9;
        sum = 0;
        for (float v : sum2) {
            sum += v;
        }
        System.out.printf("stream: %.3f (%.2f)\n", (t1 - t0), sum);
    }

    @Test
    public void testText() throws Exception {
        System.out.println("text");
        SparseMatrix data = SparseMatrix.text(smile.util.Paths.getTestData("matrix/08blocks.txt"));
        assertEquals(592, data.size());
        assertEquals(300, data.nrow());
        assertEquals(300, data.ncol());
        assertEquals(94.0, data.get(36, 0), 1E-7);
        assertEquals(1.0, data.get(0, 1), 1E-7);
        assertEquals(33.0, data.get(36, 1), 1E-7);
        assertEquals(95.0, data.get(299, 299), 1E-7);
    }

    @Test
    public void testHarwell() throws Exception {
        System.out.println("HB exchange format");
        SparseMatrix data = SparseMatrix.harwell(smile.util.Paths.getTestData("matrix/5by5_rua.hb"));
        assertEquals(13, data.size());
        assertEquals(5, data.nrow());
        assertEquals(5, data.ncol());
        assertEquals(11.0, data.get(0, 0), 1E-7);
        assertEquals(31.0, data.get(2, 0), 1E-7);
        assertEquals(51.0, data.get(4, 0), 1E-7);
        assertEquals(55.0, data.get(4, 4), 1E-7);
    }

    @Test
    public void testMatrixMarket08blocks() throws Exception {
        System.out.println("market 08blocks");
        SparseMatrix data = (SparseMatrix) IMatrix.market(smile.util.Paths.getTestData("matrix/08blocks.mtx"));
        assertEquals(592, data.size());
        assertEquals(300, data.nrow());
        assertEquals(300, data.ncol());
        assertEquals(94.0, data.get(36, 0), 1E-7);
        assertEquals(1.0, data.get(0, 1), 1E-7);
        assertEquals(33.0, data.get(36, 1), 1E-7);
        assertEquals(95.0, data.get(299, 299), 1E-7);
    }

    @Test
    public void testMatrixMarketGr900() throws Exception {
        System.out.println("market gr900");
        SparseMatrix data = (SparseMatrix) IMatrix.market(smile.util.Paths.getTestData("matrix/gr_900_900_crg.mm"));
        //assertEquals(true, data.isSymmetric());
        assertEquals(8644, data.size());
        assertEquals(900, data.nrow());
        assertEquals(900, data.ncol());
        assertEquals( 8.0, data.get(0, 0), 1E-7);
        assertEquals( 8.0, data.get(1, 1), 1E-7);
        assertEquals(-1.0, data.get(2, 1), 1E-7);
        assertEquals(-1.0, data.get(30, 1), 1E-7);
        assertEquals( 8.0, data.get(899, 899), 1E-7);

        // it is symmetric
        assertEquals(-1.0, data.get(1, 2), 1E-7);
        assertEquals(-1.0, data.get(1, 30), 1E-7);
    }

    @Test
    public void testMatrixMarketCrk() throws Exception {
        System.out.println("market crk");
        SparseMatrix data = (SparseMatrix) IMatrix.market(smile.util.Paths.getTestData("matrix/m_05_05_crk.mm"));
        assertEquals(8, data.size());
        assertEquals(5, data.nrow());
        assertEquals(5, data.ncol());
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
    @Test
    public void testMatrixMarketCrs() throws Exception {
        System.out.println("market crs");
        SparseMatrix data = (SparseMatrix) IMatrix.market(smile.util.Paths.getTestData("matrix/m_05_05_crs.mm"));
        assertEquals(18, data.size());
        assertEquals(5, data.nrow());
        assertEquals(5, data.ncol());
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

    @Test
    public void testMatrixMarketDense() throws Exception {
        System.out.println("market dense");
        Matrix data = (Matrix) IMatrix.market(smile.util.Paths.getTestData("matrix/m_10_01.mm"));
        assertFalse(data.isSymmetric());
        assertEquals(10, data.nrow());
        assertEquals(1, data.ncol());
        assertEquals(0.193523, data.get(0, 0), 1E-7);
        assertEquals(0.200518, data.get(1, 0), 1E-7);
        assertEquals(0.625800, data.get(2, 0), 1E-7);
        assertEquals(0.585233, data.get(3, 0), 1E-7);
        assertEquals(0.127585, data.get(9, 0), 1E-7);
    }
}
