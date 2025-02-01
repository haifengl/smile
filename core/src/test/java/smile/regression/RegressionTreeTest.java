/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression;

import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructField;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RegressionMetrics;
import smile.validation.RegressionValidations;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RegressionTreeTest {
    
    public RegressionTreeTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }
    
    @BeforeEach
    public void setUp() {
        System.setProperty("smile.regression_tree.bins", "100");
    }
    
    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");
        var longley = new Longley();
        var options = new RegressionTree.Options(20, 100, 2);
        RegressionTree model = RegressionTree.fit(longley.formula(), longley.data(), options);
        System.out.println("----- dot -----");
        System.out.println(model);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        RegressionMetrics metrics = LOOCV.regression(longley.formula(), longley.data(), (formula, x) -> RegressionTree.fit(formula, x, options));

        System.out.println(metrics);
        assertEquals(3.0848729264302333, metrics.rmse(), 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
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
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        RegressionValidations<RegressionTree> result = CrossValidation.regression(10, formula, data, RegressionTree::fit);

        System.out.println(result);
        assertEquals(expected, result.avg().rmse(), 1E-4);
    }

    @Test
    public void testCPU() throws Exception {
        var cpu = new CPU();
        test("CPU", cpu.formula(), cpu.data(), 74.3149);
    }

    @Test
    public void test2DPlanes() throws Exception {
        var planes = new Planes2D();
        test("2dplanes", planes.formula(), planes.data(), 1.0025);
    }

    @Test
    public void testAbalone() throws Exception {
        var abalone = new Abalone();
        test("abalone", abalone.formula(), abalone.train(), 2.3670);
    }

    @Test
    public void testAilerons() throws Exception {
        var ailerons = new Ailerons();
        test("ailerons", ailerons.formula(), ailerons.data(), 1.9538E-4);
    }

    @Test
    public void testBank32nh() throws Exception {
        var bank32nh = new Bank32nh();
        test("bank32nh", bank32nh.formula(), bank32nh.data(), 0.09187);
    }

    @Test
    public void testAutoMPG() throws Exception {
        var autoMPG = new AutoMPG();
        test("autoMPG", autoMPG.formula(), autoMPG.data(), 3.84619);
    }

    @Test
    public void testCalHousing() throws Exception {
        System.setProperty("smile.regression_tree.bins", "300");
        var calHousing = new CalHousing();
        test("cal_housing", calHousing.formula(), calHousing.data(), 60563.2112);
    }

    @Test
    public void testPuma8nh() throws Exception {
        var puma = new Puma8NH();
        test("puma8nh", puma.formula(), puma.data(), 3.2726);
    }

    @Test
    public void testKin8nm() throws Exception {
        var kin8nm = new Kin8nm();
        test("kin8nm", kin8nm.formula(), kin8nm.data(), 0.1967);
    }

    @Test
    public void testShap() throws Exception {
        MathEx.setSeed(19650218); // to get repeatable results.
        var bostonHousing = new BostonHousing();
        var options = new RegressionTree.Options(20, 100, 5);
        RegressionTree model = RegressionTree.fit(bostonHousing.formula(), bostonHousing.data(), options);
        double[] importance = model.importance();
        double[] shap = model.shap(bostonHousing.data());

        System.out.println("----- importance -----");
        String[] fields = model.schema().fields().stream().map(StructField::name).toArray(String[]::new);
        smile.sort.QuickSort.sort(importance, fields);
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %12.4f%n", fields[i], importance[i]);
        }

        System.out.println("----- SHAP -----");
        fields = model.schema().fields().stream().map(StructField::name).toArray(String[]::new);
        smile.sort.QuickSort.sort(shap, fields);
        for (int i = 0; i < shap.length; i++) {
            System.out.format("%-15s %12.4f%n", fields[i], shap[i]);
        }

        String[] expected = {"CHAS", "RAD", "ZN", "INDUS", "B", "TAX", "AGE", "PTRATIO", "DIS", "NOX", "CRIM", "LSTAT", "RM"};
        assertArrayEquals(expected, fields);
    }
}
