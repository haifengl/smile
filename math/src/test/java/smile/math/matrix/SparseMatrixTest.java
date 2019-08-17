/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.math.matrix;

import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class SparseMatrixTest {

    private SparseMatrix sm;
    double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};
    private double[][] C = {
            {0.97, 0.56, 0.12},
            {0.56, 0.50, 0.39},
            {0.12, 0.39, 0.73}
    };

    public SparseMatrixTest() {
        int[] rowIndex = {0, 1, 0, 1, 2, 1, 2};
        int[] colIndex = {0, 2, 5, 7};
        double[] val = {0.9, 0.4, 0.4, 0.5, 0.3, 0.3, 0.8};
        sm = new SparseMatrix(3, 3, val, rowIndex, colIndex);
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

    /**
     * Test of nrows method, of class SparseMatrix.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(3, sm.nrows());
    }

    /**
     * Test of ncols method, of class SparseMatrix.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(3, sm.ncols());
    }

    /**
     * Test of size method, of class SparseMatrix.
     */
    @Test
    public void testNvals() {
        System.out.println("nvals");
        assertEquals(7, sm.size());
    }

    /**
     * Test of get method, of class SparseMatrix.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(0.9, sm.get(0, 0), 1E-7);
        assertEquals(0.8, sm.get(2, 2), 1E-7);
        assertEquals(0.5, sm.get(1, 1), 1E-7);
        assertEquals(0.0, sm.get(2, 0), 1E-7);
        assertEquals(0.0, sm.get(0, 2), 1E-7);
        assertEquals(0.4, sm.get(0, 1), 1E-7);
    }

    /**
     * Test of times method, of class SparseMatrix.
     */
    @Test
    public void testTimes() {
        System.out.println("times");
        SparseMatrix c = sm.abmm(sm);
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
        assertEquals(c.size(), 9);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Test of AAT method, of class SparseMatrix.
     */
    @Test
    public void testAAT() {
        System.out.println("AAT");
        SparseMatrix c = sm.aat();
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
        assertEquals(c.size(), 9);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Load sparse matrix.
     */
    @SuppressWarnings("resource")
    SparseMatrix loadMatrix(String file) {
        int nrows, ncols;
        int[] colIndex, rowIndex;
        double[] data;

        Scanner scanner = new Scanner(getClass().getResourceAsStream(file));

        nrows = scanner.nextInt();
        ncols = scanner.nextInt();
        int n = scanner.nextInt();

        colIndex = new int[ncols + 1];
        rowIndex = new int[n];
        data = new double[n];

        for (int i = 0; i <= ncols; i++) {
            colIndex[i] = scanner.nextInt() - 1;
        }

        for (int i = 0; i < n; i++) {
            rowIndex[i] = scanner.nextInt() - 1;
        }

        for (int i = 0; i < n; i++) {
            data[i] = scanner.nextDouble();
        }

        return new SparseMatrix(nrows, ncols, data, rowIndex, colIndex);
    }

    @Test
    public void nonZeroIterator() {
        Random rand = new Random();
        double[][] d = new double[1000][2000];
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
            d[row][col] = rand.nextGaussian() + 10;
        }

        // now that we have the data, we can to the actual SparseMatrix part
        SparseMatrix m = new SparseMatrix(d, 1e-10);

        // verify all values and the number of non-zeros
        AtomicInteger k = new AtomicInteger(0);
        m.foreachNonzero((i, j, x) -> {
            assertEquals(d[i][j], x, 0);
            assertEquals(d[i][j], m.get(i, j), 0);
            k.incrementAndGet();
        });
        assertEquals(nonzeroCount, k.get());

        // iterate over just a few columns
        k.set(0);
        m.foreachNonzero(100, 400, (i, j, x) -> {
            assertTrue(j >= 100);
            assertTrue(j < 400);
            assertEquals(d[i][j], x, 0);
            assertEquals(d[i][j], m.get(i, j), 0);
            k.incrementAndGet();
        });
        assertEquals(limitedNonZeros, k.get());

        k.set(0);
        m.nonzeros()
                .forEach(
                        entry -> {
                            int i = entry.row;
                            int j = entry.col;
                            double x = entry.value;

                            assertEquals(d[i][j], x, 0);
                            assertEquals(d[i][j], m.get(i, j), 0);
                            k.incrementAndGet();
                        }
                );
        assertEquals(nonzeroCount, k.get());

        k.set(0);
        m.nonzeros(100, 400)
                .forEach(
                        entry -> {
                            int col = entry.col;

                            assertTrue(col >= 100);
                            assertTrue(col < 400);

                            assertEquals(d[entry.row][col], entry.value, 0);
                            assertEquals(d[entry.row][col], m.get(entry.row, col), 0);
                            k.incrementAndGet();
                        }
                );
        assertEquals(limitedNonZeros, k.get());
    }

    @Test
    public void iterationSpeed() {
        Random rand = new Random();
        double[][] d = new double[1000][2000];
        for (int i = 0; i < 500000; i++) {
            int row, col;
            do {
                row = rand.nextInt(1000);
                col = rand.nextInt(2000);
            } while (d[row][col] != 0);
            d[row][col] = rand.nextGaussian();
        }
        SparseMatrix m = new SparseMatrix(d, 1e-10);

        double t0 = System.nanoTime() / 1e9;
        double[] sum1 = new double[2000];
        for (int rep = 0; rep < 1000; rep++) {
            m.foreachNonzero(
                    (i, j, x) -> sum1[j] += x
            );
        }
        double t1 = System.nanoTime() / 1e9;
        double sum = 0;
        for (double v : sum1) {
            sum += v;
        }
        System.out.printf("%.3f (%.2f)\n", (t1 - t0), sum);

        t0 = System.nanoTime() / 1e9;
        double[] sum2 = new double[2000];
        for (int rep = 0; rep < 1000; rep++) {
            m.nonzeros()
                    .forEach(entry -> sum2[entry.col] += entry.value);
        }
        t1 = System.nanoTime() / 1e9;
        sum = 0;
        for (double v : sum2) {
            sum += v;
        }
        System.out.printf("%.3f (%.2f)\n", (t1 - t0), sum);
    }
}
