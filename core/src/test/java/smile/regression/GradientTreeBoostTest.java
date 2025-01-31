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

import smile.base.cart.Loss;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructField;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RegressionMetrics;
import smile.validation.RegressionValidations;
import smile.validation.metric.RMSE;
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GradientTreeBoostTest {
    Abalone abalone;
    Ailerons ailerons;
    AutoMPG autoMPG;
    Bank32nh bank32nh;
    BostonHousing bostonHousing;
    CalHousing calHousing;
    CPU cpu;
    Kin8nm kin8nm;
    Planes2D planes;
    Puma8NH puma;
    public GradientTreeBoostTest() throws Exception {
        abalone = new Abalone();
        ailerons = new Ailerons();
        autoMPG = new AutoMPG();
        bank32nh = new Bank32nh();
        bostonHousing = new BostonHousing();
        calHousing = new CalHousing();
        cpu = new CPU();
        kin8nm = new Kin8nm();
        planes = new Planes2D();
        puma = new Puma8NH();
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
        MathEx.setSeed(19650218); // to get repeatable results.
        var longley = new Longley();
        GradientTreeBoost model = GradientTreeBoost.fit(longley.formula(), longley.data());

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %12.4f%n", model.schema().names()[i], importance[i]);
        }

        System.out.println("----- Progressive RMSE -----");
        double[][] test = model.test(longley.data());
        for (int i = 0; i < test.length; i++) {
            System.out.format("RMSE with %3d trees: %.4f%n", i+1, RMSE.of(longley.y(), test[i]));
        }

        RegressionMetrics metrics = LOOCV.regression(longley.formula(), longley.data(), GradientTreeBoost::fit);

        System.out.println(metrics);
        assertEquals(3.5453, metrics.rmse(), 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    public void test(Loss loss, String name, Formula formula, DataFrame data, double expected) {
        System.out.println(name + "\t" + loss);

        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(formula, data);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %12.4f%n", model.schema().names()[i], importance[i]);
        }

        var options = new GradientTreeBoost.Options(loss, 100, 20, 6, 5, 0.05, 0.7);
        RegressionValidations<GradientTreeBoost> result = CrossValidation.regression(10, formula, data,
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(result);
        assertEquals(expected, result.avg().rmse(), 1E-4);
    }

    @Test
    public void testCpuLS() {
        test(Loss.ls(), "CPU", cpu.formula(), cpu.data(), 60.5335);
    }

    @Test
    public void testCpuLAD() {
        test(Loss.lad(), "CPU", cpu.formula(), cpu.data(), 66.0549);
    }

    @Test
    public void testCpuQuantile() {
        test(Loss.quantile(0.5), "CPU", cpu.formula(), cpu.data(), 66.0549);
    }

    @Test
    public void testCpuHuber() {
        test(Loss.huber(0.9), "CPU", cpu.formula(), cpu.data(), 63.7212);
    }

    @Test
    public void test2DPlanesLS() {
        test(Loss.ls(), "2dplanes", planes.formula(), planes.data(), 1.1016);
    }

    @Test
    public void test2DPlanesLAD() {
        test(Loss.lad(), "2dplanes", planes.formula(), planes.data(), 1.1347);
    }

    @Test
    public void test2DPlanesQuantile() {
        test(Loss.quantile(0.5), "2dplanes", planes.formula(), planes.data(), 1.1347);
    }

    @Test
    public void test2DPlanesHuber() {
        test(Loss.huber(0.9), "2dplanes", planes.formula(), planes.data(), 1.1080);
    }

    @Test
    public void testAbaloneLS() {
        test(Loss.ls(), "abalone", abalone.formula(), abalone.train(), 2.1994);
    }

    @Test
    public void testAbaloneLAD() {
        test(Loss.lad(), "abalone", abalone.formula(), abalone.train(), 2.2933);
    }

    @Test
    public void testAbaloneQuantile() {
        test(Loss.quantile(0.5), "abalone", abalone.formula(), abalone.train(), 2.2933);
    }

    @Test
    public void testAbaloneHuber() {
        test(Loss.huber(0.9), "abalone", abalone.formula(), abalone.train(), 2.2184);
    }

    @Test
    public void testAileronsLS() {
        test(Loss.ls(), "ailerons", ailerons.formula(), ailerons.data(), 0.0002);
    }

    @Test
    public void testAileronsLAD() {
        test(Loss.lad(), "ailerons", ailerons.formula(), ailerons.data(), 0.0002);
    }

    @Test
    public void testAileronsQuantile() {
        test(Loss.quantile(0.5), "ailerons", ailerons.formula(), ailerons.data(), 0.0002);
    }

    @Test
    public void testAileronsHuber() {
        test(Loss.huber(0.9), "ailerons", ailerons.formula(), ailerons.data(), 0.0002);
    }

    @Test
    public void testBank32nhLS() {
        test(Loss.ls(), "bank32nh", bank32nh.formula(), bank32nh.data(), 0.0845);
    }

    @Test
    public void testBank32nhLAD() {
        test(Loss.lad(), "bank32nh", bank32nh.formula(), bank32nh.data(), 0.0911);
    }

    @Test
    public void testBank32nhQuantile() {
        test(Loss.quantile(0.5), "bank32nh", bank32nh.formula(), bank32nh.data(), 0.0911);
    }

    @Test
    public void testBank32nhHuber() {
        test(Loss.huber(0.9), "bank32nh", bank32nh.formula(), bank32nh.data(), 0.0854);
    }

    @Test
    public void testAutoMPGLS() {
        test(Loss.ls(), "autoMPG", autoMPG.formula(), autoMPG.data(), 3.0795);
    }

    @Test
    public void testAutoMPGLAD() {
        test(Loss.lad(), "autoMPG", autoMPG.formula(), autoMPG.data(), 3.1365);
    }

    @Test
    public void testAutoMPGQuantile() {
        test(Loss.quantile(0.5), "autoMPG", autoMPG.formula(), autoMPG.data(), 3.1365);
    }

    @Test
    public void testAutoMPGHuber() {
        test(Loss.huber(0.9), "autoMPG", autoMPG.formula(), autoMPG.data(), 3.0694);
    }

    @Test
    public void testCalHousingLS() {
        test(Loss.ls(), "cal_housing", calHousing.formula(), calHousing.data(), 60870.2308);
    }

    @Test
    public void testCalHousingLAD() {
        test(Loss.lad(), "cal_housing", calHousing.formula(), calHousing.data(), 66813.0877);
    }

    @Test
    public void testCalHousingQuantile() {
        test(Loss.quantile(0.5), "cal_housing", calHousing.formula(), calHousing.data(), 66813.0877);
    }

    @Test
    public void testCalHousingHuber() {
        test(Loss.huber(0.9), "cal_housing", calHousing.formula(), calHousing.data(), 62348.1547);
    }

    @Test
    public void testPuma8nhLS() {
        test(Loss.ls(), "puma8nh", puma.formula(), puma.data(), 3.2372);
    }

    @Test
    public void testPuma8nhLAD() {
        test(Loss.lad(), "puma8nh", puma.formula(), puma.data(), 3.2502);
    }

    @Test
    public void testPuma8nhQuantile() {
        test(Loss.quantile(0.5), "puma8nh", puma.formula(), puma.data(), 3.2502);
    }

    @Test
    public void testPuma8nhHuber() {
        test(Loss.huber(0.9), "puma8nh", puma.formula(), puma.data(), 3.2405);
    }

    @Test
    public void testKin8nmLS() {
        test(Loss.ls(), "kin8nm", kin8nm.formula(), kin8nm.data(), 0.1803);
    }

    @Test
    public void testKin8nmLAD() {
        test(Loss.lad(), "kin8nm", kin8nm.formula(), kin8nm.data(), 0.1822);
    }

    @Test
    public void testKin8nmQuantile() {
        test(Loss.quantile(0.5), "kin8nm", kin8nm.formula(), kin8nm.data(), 0.1822);
    }

    @Test
    public void testKin8nmHuber() {
        test(Loss.huber(0.9), "kin8nm", kin8nm.formula(), kin8nm.data(), 0.1795);
    }

    @Test
    public void testShap() {
        MathEx.setSeed(19650218); // to get repeatable results.
        System.setProperty("smile.regression_tree.bins", "1");

        var options = new GradientTreeBoost.Options(Loss.ls(), 100, 20, 100, 5, 0.05, 0.7);
        GradientTreeBoost model = GradientTreeBoost.fit(bostonHousing.formula(), bostonHousing.data(), options);
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

        String[] expected = {"CHAS", "ZN", "RAD", "INDUS", "B", "TAX", "AGE", "PTRATIO", "NOX", "CRIM", "DIS", "RM", "LSTAT"};
        assertArrayEquals(expected, fields);
    }
}
