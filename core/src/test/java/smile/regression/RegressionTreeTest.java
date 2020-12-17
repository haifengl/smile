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

package smile.regression;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.*;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RegressionMetrics;
import smile.validation.RegressionValidations;

import static org.junit.Assert.assertArrayEquals;
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

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");

        RegressionTree model = RegressionTree.fit(Longley.formula, Longley.data, 100, 20, 2);
        System.out.println("----- dot -----");
        System.out.println(model);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().name(i), importance[i]);
        }

        RegressionMetrics metrics = LOOCV.regression(Longley.formula, Longley.data, (formula, x) -> RegressionTree.fit(formula, x, 100, 20, 2));

        System.out.println(metrics);
        assertEquals(3.0848729264302333, metrics.rmse, 1E-4);

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
            System.out.format("%-15s %.4f%n", model.schema().name(i), importance[i]);
        }

        RegressionValidations<RegressionTree> result = CrossValidation.regression(10, formula, data, RegressionTree::fit);

        System.out.println(result);
        assertEquals(expected, result.avg.rmse, 1E-4);
    }

    @Test
    public void testAll() {
        test("CPU", CPU.formula, CPU.data, 74.3149);
        test("2dplanes", Planes.formula, Planes.data, 1.1164);
        test("abalone", Abalone.formula, Abalone.train, 2.5834);
        test("ailerons", Ailerons.formula, Ailerons.data, 0.0003);
        test("bank32nh", Bank32nh.formula, Bank32nh.data, 0.1093);
        test("autoMPG", AutoMPG.formula, AutoMPG.data, 3.8138);
        test("cal_housing", CalHousing.formula, CalHousing.data, 59944.8076);
        test("puma8nh", Puma8NH.formula, Puma8NH.data, 3.9117);
        test("kin8nm", Kin8nm.formula, Kin8nm.data, 0.1936);
    }

    @Test
    public void testShap() {
        MathEx.setSeed(19650218); // to get repeatable results.
        RegressionTree model = RegressionTree.fit(BostonHousing.formula, BostonHousing.data, 20, 100, 5);
        double[] importance = model.importance();
        double[] shap = model.shap(BostonHousing.data);

        System.out.println("----- importance -----");
        String[] fields = java.util.Arrays.stream(model.schema().fields()).map(field -> field.name).toArray(String[]::new);
        smile.sort.QuickSort.sort(importance, fields);
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %12.4f%n", fields[i], importance[i]);
        }

        System.out.println("----- SHAP -----");
        fields = java.util.Arrays.stream(model.schema().fields()).map(field -> field.name).toArray(String[]::new);
        smile.sort.QuickSort.sort(shap, fields);
        for (int i = 0; i < shap.length; i++) {
            System.out.format("%-15s %12.4f%n", fields[i], shap[i]);
        }

        String[] expected = {"CHAS", "RAD", "ZN", "INDUS", "B", "TAX", "AGE", "PTRATIO", "DIS", "NOX", "CRIM", "LSTAT", "RM"};
        assertArrayEquals(expected, fields);
    }
}
