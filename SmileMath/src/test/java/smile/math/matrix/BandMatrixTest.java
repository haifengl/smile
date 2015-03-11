/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.matrix;

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
public class BandMatrixTest {

    public BandMatrixTest() {
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

    /**
     * Test of solve method, of class BandMatrix.
     */
    @Test
    public void testSolve() {
        System.out.println("solve");
        double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
        };
        double[] b = {0.5, 0.5, 0.5};

        LUDecomposition lu = new LUDecomposition(A);
        double[] x = new double[b.length];
        lu.solve(b, x);

        BandMatrix instance = new BandMatrix(3, 1, 1);
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++)
                if (A[i][j] != 0.0)
                    instance.set(i, j, A[i][j]);
        }

        instance.decompose();
        double[] result = new double[b.length];
        instance.solve(b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-7);
        }

        instance.improve(b, result);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-15);
        }
    }
}