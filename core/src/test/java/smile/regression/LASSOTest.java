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

package smile.regression;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.CPU;
import smile.data.DataFrame;
import smile.data.Longley;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class LASSOTest {
    public LASSOTest() {
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
    /*
    @Test
    public void testToy() {
        double[][] A = {
            {1, 0, 0, 0.5},
            {0, 1, 0.2, 0.3},
            {0, 0.1, 1, 0.2}
        };
        
        double[] x0 = {1, 0, 1, 0};    // original signal
        double[] y = new double[A.length];
        Math.ax(A, x0, y);          // measurements with no noise

        LASSO lasso = new LASSO(A, y, 0.01, 0.01, 500);
        
        double rss = 0.0;
        int n = A.length;
        for (int i = 0; i < n; i++) {
            double r = y[i] - lasso.predict(A[i]);
            rss += r * r;
        }
        System.out.println("MSE = " + rss / n);
        
        assertEquals(0.0, lasso.intercept(), 1E-4);
        double[] w = {0.9930, 0.0004, 0.9941, 0.0040};
        for (int i = 0; i < w.length; i++) {
            assertEquals(w[i], lasso.coefficients()[i], 1E-4);
        }
    }
    */

    @Test
    public void testToy2() {
        double[][] A = {
            {1, 0, 0, 0.5},
            {0, 1, 0.2, 0.3},
            {1, 0.5, 0.2, 0.3},
            {0, 0.1, 0, 0.2},
            {0, 0.1, 1, 0.2}
        };
        
        double[] y = {6, 5.2, 6.2, 5, 6};
        /*
        double[] x0 = {1, 0, 1, 0};    // original signal
        DenseMatrix a = Matrix.of(A);
        a.ax(x0, y);          // measurements with no noise
        for (int i = 0; i < y.length; i++) {
            y[i] += 5;
        }
         */

        DataFrame df = DataFrame.of(A, "V1", "V2", "V3", "V4");
        df = df.merge(DoubleVector.of("y", y));
        
        LinearModel lasso = LASSO.fit(Formula.lhs("y"), df, 0.1, 0.001, 500);

        double rss = 0.0;
        int n = A.length;
        for (int i = 0; i < n; i++) {
            double r = y[i] - lasso.predict(A[i]);
            rss += r * r;
        }
        System.out.println("MSE = " + rss / n);
        
        assertEquals(5.0259443688265355, lasso.intercept(), 1E-7);
        double[] w = {0.9659945126777854, -3.7147706312985876E-4, 0.9553629503697613, 9.416740009376934E-4};
        for (int i = 0; i < w.length; i++) {
            assertEquals(w[i], lasso.coefficients()[i], 1E-5);
        }
    }

    /**
     * Test of learn method, of class RidgeRegression.
     */
    @Test
    public void testLongley() {
        System.out.println("longley");

        LinearModel model = LASSO.fit(Longley.formula, Longley.data, 0.1);
        System.out.println(model);

        double rss = LOOCV.test(Longley.data, (x) -> LASSO.fit(CPU.formula, x, 0.1));
        System.out.println("LOOCV RMSE = " + rss);
        assertEquals(2.0012529348358212, rss, 1E-4);
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void testCPU() {
        System.out.println("CPU");
        LinearModel model = LASSO.fit(CPU.formula, CPU.data, 50);
        System.out.println(model);

        double rss = CrossValidation.test(10, CPU.data, (x) -> LASSO.fit(CPU.formula, x, 50));
        System.out.println("CPU 10-CV RMSE = " + rss);
    }
}