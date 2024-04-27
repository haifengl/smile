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

package smile.regression;

import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.test.data.*;
import smile.validation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author rayeaster
 */
public class ElasticNetTest {
    public ElasticNetTest() {
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
    public void testToy() {
        double[][] A = {
                {1, 0, 0, 0.5},
                {0, 1, 0.2, 0.3},
                {1, 0.5, 0.2, 0.3},
                {0, 0.1, 0, 0.2},
                {0, 0.1, 1, 0.2}
        };

        double[] y = {6, 5.2, 6.2, 5, 6};

        DataFrame df = DataFrame.of(A).merge(DoubleVector.of("y", y));
        RegressionValidation<LinearModel> result = RegressionValidation.of(Formula.lhs("y"), df, df,
                (formula, data) -> ElasticNet.fit(formula, data, 0.1, 0.001));

        System.out.println(result.model);
        System.out.println(result);

        assertEquals(5.0262, result.model.intercept(), 1E-4);
        double[] w = {0.9659, -2.9425E-5, 0.9550, 7.4383E-5};
        for (int i = 0; i < w.length; i++) {
            assertEquals(w[i], result.model.coefficients()[i], 1E-4);
        }
    }

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");

        LinearModel model = ElasticNet.fit(Longley.formula, Longley.data, 0.1, 0.1);
        System.out.println(model);

        RegressionMetrics metrics = LOOCV.regression(Longley.formula, Longley.data,
                (f, x) -> ElasticNet.fit(f, x, 0.1, 0.1));

        System.out.println(metrics);
        assertEquals(1.7385, metrics.rmse, 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.

        LinearModel model = ElasticNet.fit(CPU.formula, CPU.data, 0.8, 0.2);
        System.out.println(model);

        RegressionValidations<LinearModel> result = CrossValidation.regression(10, CPU.formula, CPU.data,
                (f, x) -> ElasticNet.fit(f, x, 0.8, 0.2));

        System.out.println(result);
        assertEquals(50.9808, result.avg.rmse, 1E-4);
    }

    @Test
    public void tesProstate() {
        System.out.println("Prostate");

        RegressionValidation<LinearModel> result = RegressionValidation.of(Prostate.formula, Prostate.train, Prostate.test,
                (formula, data) -> ElasticNet.fit(formula, data, 0.8, 0.2));

        System.out.println(result.model);
        System.out.println(result);
        assertEquals(0.7103, result.metrics.rmse, 1E-4);
    }

    @Test
    public void tesAbalone() {
        System.out.println("Abalone");

        RegressionValidation<LinearModel> result = RegressionValidation.of(Abalone.formula, Abalone.train, Abalone.test,
                (formula, data) -> ElasticNet.fit(formula, data, 0.8, 0.2));

        System.out.println(result.model);
        System.out.println(result);
        assertEquals(2.1263, result.metrics.rmse, 1E-4);
    }

    @Test
    public void tesDiabetes() {
        System.out.println("Diabetes");

        MathEx.setSeed(19650218); // to get repeatable results.

        LinearModel model = ElasticNet.fit(Diabetes.formula, Diabetes.data, 0.8, 0.2);
        System.out.println(model);

        RegressionValidations<LinearModel> result = CrossValidation.regression(10, Diabetes.formula, Diabetes.data,
                (f, x) -> ElasticNet.fit(f, x, 0.8, 0.2));

        System.out.println(result);
        assertEquals(58.9498, result.avg.rmse, 0.01);
    }
}