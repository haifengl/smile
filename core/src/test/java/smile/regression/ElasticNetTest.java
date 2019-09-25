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

import smile.data.Abalone;
import smile.data.CPU;
import smile.data.Diabetes;
import smile.data.Prostate;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.Validation;

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

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void testCPU() {
        System.out.println("CPU");
        LinearModel model = ElasticNet.fit(CPU.formula, CPU.data, 0.8, 0.2);
        System.out.println(model);

        double rss = CrossValidation.test(10, CPU.data, (x) -> ElasticNet.fit(CPU.formula, x, 0.8, 0.2));
        System.out.println("CPU 10-CV RMSE = " + rss);
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void tesProstate() {
        System.out.println("---Prostate---");
        LinearModel model = ElasticNet.fit(Prostate.formula, Prostate.train, 0.8, 0.2);
        System.out.println(model);

        double rss = Validation.test(model, Prostate.test);
        System.out.println("Test RMSE = " + rss);
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void tesAbalone() {
        System.out.println("---Abalone---");
        LinearModel model = ElasticNet.fit(Abalone.formula, Abalone.train, 0.8, 0.2);
        System.out.println(model);

        double rss = Validation.test(model, Abalone.test);
        System.out.println("Test RMSE = " + rss);
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void tesDiabetes() {
        System.out.println("---Diabetes---");
        LinearModel model = ElasticNet.fit(Diabetes.formula, Diabetes.data, 0.8, 0.2);
        System.out.println(model);

        double rss = CrossValidation.test(10, Diabetes.data, (x) -> ElasticNet.fit(Diabetes.formula, x, 0.8, 0.2));
        System.out.println("Diabetes 10-CV RMSE = " + rss);
    }
}