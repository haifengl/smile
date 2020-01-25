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

import smile.data.*;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RMSE;
import smile.validation.Validation;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author rayeaster
 */
public class ElasticNetTest {
    public ElasticNetTest() {
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
        Formula formula = Formula.lhs("y");
        LinearModel model = ElasticNet.fit(formula, df, 0.1, 0.001);
        System.out.println(model);

        double[] prediction = Validation.test(model, df);
        double rmse = RMSE.of(y, prediction);
        System.out.println("RMSE = " + rmse);

        assertEquals(5.1486, model.intercept(), 1E-4);
        double[] w = {0.8978, -0.0873, 0.8416, -0.1121};
        for (int i = 0; i < w.length; i++) {
            assertEquals(w[i], model.coefficients()[i], 1E-4);
        }
    }

    @Test(expected = Test.None.class)
    public void testLongley() throws Exception {
        System.out.println("longley");

        LinearModel model = ElasticNet.fit(Longley.formula, Longley.data, 0.1, 0.1);
        System.out.println(model);

        double[] prediction = LOOCV.regression(Longley.formula, Longley.data, (f, x) -> ElasticNet.fit(f, x, 0.1, 0.1));
        double rmse = RMSE.of(Longley.y, prediction);

        System.out.println("LOOCV RMSE = " + rmse);
        assertEquals(4.2299495472273, rmse, 1E-4);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.

        LinearModel model = ElasticNet.fit(CPU.formula, CPU.data, 0.8, 0.2);
        System.out.println(model);

        double[] prediction = CrossValidation.regression(10, CPU.formula, CPU.data, (f, x) -> ElasticNet.fit(f, x, 0.8, 0.2));
        double rmse = RMSE.of(CPU.y, prediction);

        System.out.println("10-CV RMSE = " + rmse);
        assertEquals(55.313225659429634, rmse, 1E-4);
    }

    @Test
    public void tesProstate() {
        System.out.println("Prostate");

        LinearModel model = ElasticNet.fit(Prostate.formula, Prostate.train, 0.8, 0.2);
        System.out.println(model);

        double[] prediction = Validation.test(model, Prostate.test);
        double rmse = RMSE.of(Prostate.testy, prediction);
        System.out.println("Test RMSE = " + rmse);
        assertEquals(0.7076752687983124, rmse, 1E-4);
    }

    @Test
    public void tesAbalone() {
        System.out.println("Abalone");

        LinearModel model = ElasticNet.fit(Abalone.formula, Abalone.train, 0.8, 0.2);
        System.out.println(model);

        double[] prediction = Validation.test(model, Abalone.test);
        double rmse = RMSE.of(Abalone.testy, prediction);
        System.out.println("Test RMSE = " + rmse);
        assertEquals(2.1395194279255536, rmse, 1E-4);
    }

    @Test
    public void tesDiabetes() {
        System.out.println("Diabetes");

        MathEx.setSeed(19650218); // to get repeatable results.

        LinearModel model = ElasticNet.fit(Diabetes.formula, Diabetes.data, 0.8, 0.2);
        System.out.println(model);

        double[] prediction = CrossValidation.regression(10, Diabetes.formula, Diabetes.data, (f, x) -> ElasticNet.fit(f, x, 0.8, 0.2));
        double rmse = RMSE.of(Diabetes.y, prediction);

        System.out.println("Diabetes 10-CV RMSE = " + rmse);
        assertEquals(59.59568301421299, rmse, 1E-4);
    }
}