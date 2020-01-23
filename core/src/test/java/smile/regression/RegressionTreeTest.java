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
import smile.math.MathEx;
import smile.sort.QuickSort;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RMSE;
import smile.validation.Validation;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class RegressionTreeTest {
    
    public RegressionTreeTest() {
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
     * Test of predict method, of class RegressionTree.
     */
    @Test(expected = Test.None.class)
    public void testLongley() throws Exception {
        System.out.println("longley");

        RegressionTree model = RegressionTree.fit(Longley.formula, Longley.data, 100, 20, 2);
        System.out.println("----- dot -----");
        System.out.println(model);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        double[] prediction = LOOCV.regression(Longley.formula, Longley.data, (formula, x) -> RegressionTree.fit(formula, x, 100, 20, 2));
        double rmse = RMSE.of(Longley.y, prediction);

        System.out.println("LOOCV MSE = " + rmse);
        assertEquals(3.0848729264302333, rmse, 1E-4);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    public void test(String name, Formula formula, DataFrame data, double expected) {
        System.out.println(name);

        MathEx.setSeed(19650218); // to get repeatable results.

        RegressionTree model = RegressionTree.fit(formula, data);
        System.out.println("----- dot -----");
        System.out.println(model);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        double[] prediction = CrossValidation.regression(10, formula, data, (f, x) -> RegressionTree.fit(f, x));
        double rmse = RMSE.of(formula.y(data).toDoubleArray(), prediction);
        System.out.format("10-CV RMSE = %.4f%n", rmse);
        assertEquals(expected, rmse, 1E-4);
    }

    /**
     * Test of learn method, of class RegressionTree.
     */
    @Test
    public void testAll() {
        test("CPU", CPU.formula, CPU.data, 84.5224);
        test("2dplanes", Planes.formula, Planes.data, 1.1164);
        test("abalone", Abalone.formula, Abalone.train, 2.5891);
        test("ailerons", Ailerons.formula, Ailerons.data, 0.0003);
        test("bank32nh", Bank32nh.formula, Bank32nh.data, 0.1093);
        test("autoMPG", AutoMPG.formula, AutoMPG.data, 3.2882);
        test("cal_housing", CalHousing.formula, CalHousing.data, 59979.0575);
        test("puma8nh", Puma8NH.formula, Puma8NH.data, 3.9136);
        test("kin8nm", Kin8nm.formula, Kin8nm.data, 0.1936);
    }
}
