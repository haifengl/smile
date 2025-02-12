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

import java.lang.ref.WeakReference;
import java.util.concurrent.Flow;
import smile.base.cart.Loss;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructField;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.util.IterativeAlgorithmController;
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
    static class TrainingStatusSubscriber implements Flow.Subscriber<GradientTreeBoost.TrainingStatus> {
        private WeakReference<IterativeAlgorithmController<GradientTreeBoost.TrainingStatus>> controller;
        private Flow.Subscription subscription;

        TrainingStatusSubscriber(IterativeAlgorithmController<GradientTreeBoost.TrainingStatus> controller) {
            this.controller = new WeakReference<>(controller);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(GradientTreeBoost.TrainingStatus status) {
            System.out.format("Tree %d: loss = %.4f, validation metrics = %s%n",
                    status.tree(), status.loss(), status.metrics());
            if (status.tree() == 100) controller.get().stop();
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            System.err.println("Controller receives an exception: " + throwable.getMessage());
        }

        @Override
        public void onComplete() {
            System.out.println("Training is done");
            controller.clear();
            controller = null;
        }
    }

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

    public double test(Loss loss, String name, Formula formula, DataFrame data, DataFrame test) {
        System.out.println(name + "\t" + loss);
        MathEx.setSeed(19650218); // to get repeatable results.

        try (var controller = new IterativeAlgorithmController<GradientTreeBoost.TrainingStatus>()) {
            controller.subscribe(new TrainingStatusSubscriber(controller));
            var options = new GradientTreeBoost.Options(loss, 500, 20, 6, 5, 0.05, 0.7, test, controller);
            GradientTreeBoost model = GradientTreeBoost.fit(formula, data, options);
            double[] importance = model.importance();
            System.out.println("----- importance -----");
            for (int i = 0; i < importance.length; i++) {
                System.out.format("%-15s %12.4f%n", model.schema().names()[i], importance[i]);
            }
        }

        var options = new GradientTreeBoost.Options(loss, 100);
        RegressionValidations<GradientTreeBoost> result = CrossValidation.regression(5, formula, data,
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(result);
        return result.avg().rmse();
    }

    @Test
    public void testCpuLS() {
        assertEquals(72.3851, test(Loss.ls(), "CPU", cpu.formula(), cpu.data(), cpu.data()), 1E-4);
    }

    @Test
    public void testCpuLAD() {
        assertEquals(72.6645, test(Loss.lad(), "CPU", cpu.formula(), cpu.data(), cpu.data()), 1E-4);
    }

    @Test
    public void testCpuQuantile() {
        assertEquals(72.6645, test(Loss.quantile(0.5), "CPU", cpu.formula(), cpu.data(), cpu.data()), 1E-4);
    }

    @Test
    public void testCpuHuber() {
        assertEquals(70.6405, test(Loss.huber(0.9), "CPU", cpu.formula(), cpu.data(), cpu.data()), 1E-4);
    }

    @Test
    public void test2DPlanesLS() {
        assertEquals(1.1031, test(Loss.ls(), "2dplanes", planes.formula(), planes.data(), null), 1E-4);
    }

    @Test
    public void test2DPlanesLAD() {
        assertEquals(1.1357, test(Loss.lad(), "2dplanes", planes.formula(), planes.data(), null), 1E-4);
    }

    @Test
    public void test2DPlanesQuantile() {
        assertEquals(1.1357, test(Loss.quantile(0.5), "2dplanes", planes.formula(), planes.data(), null), 1E-4);
    }

    @Test
    public void test2DPlanesHuber() {
        assertEquals(1.1078, test(Loss.huber(0.9), "2dplanes", planes.formula(), planes.data(), null), 1E-4);
    }

    @Test
    public void testAbaloneLS() {
        assertEquals(2.2116, test(Loss.ls(), "abalone", abalone.formula(), abalone.train(), abalone.test()), 1E-4);
    }

    @Test
    public void testAbaloneLAD() {
        assertEquals(2.3048, test(Loss.lad(), "abalone", abalone.formula(), abalone.train(), abalone.test()), 1E-4);
    }

    @Test
    public void testAbaloneQuantile() {
        assertEquals(2.3048, test(Loss.quantile(0.5), "abalone", abalone.formula(), abalone.train(), abalone.test()), 1E-4);
    }

    @Test
    public void testAbaloneHuber() {
        assertEquals(2.2193, test(Loss.huber(0.9), "abalone", abalone.formula(), abalone.train(), abalone.test()), 1E-4);
    }

    @Test
    public void testAileronsLS() {
        assertEquals(0.0002, test(Loss.ls(), "ailerons", ailerons.formula(), ailerons.data(), null), 1E-4);
    }

    @Test
    public void testAileronsLAD() {
        assertEquals(0.0002, test(Loss.lad(), "ailerons", ailerons.formula(), ailerons.data(), null), 1E-4);
    }

    @Test
    public void testAileronsQuantile() {
        assertEquals(0.0002, test(Loss.quantile(0.5), "ailerons", ailerons.formula(), ailerons.data(), null), 1E-4);
    }

    @Test
    public void testAileronsHuber() {
        assertEquals(0.0002, test(Loss.huber(0.9), "ailerons", ailerons.formula(), ailerons.data(), null), 1E-4);
    }

    @Test
    public void testBank32nhLS() {
        assertEquals(0.0845, test(Loss.ls(), "bank32nh", bank32nh.formula(), bank32nh.data(), null), 1E-4);
    }

    @Test
    public void testBank32nhLAD() {
        assertEquals(0.0917, test(Loss.lad(), "bank32nh", bank32nh.formula(), bank32nh.data(), null), 1E-4);
    }

    @Test
    public void testBank32nhQuantile() {
        assertEquals(0.0917, test(Loss.quantile(0.5), "bank32nh", bank32nh.formula(), bank32nh.data(), null), 1E-4);
    }

    @Test
    public void testBank32nhHuber() {
        assertEquals(0.0855, test(Loss.huber(0.9), "bank32nh", bank32nh.formula(), bank32nh.data(), null), 1E-4);
    }

    @Test
    public void testAutoMPGLS() {
        assertEquals(3.0646, test(Loss.ls(), "autoMPG", autoMPG.formula(), autoMPG.data(), null), 1E-4);
    }

    @Test
    public void testAutoMPGLAD() {
        assertEquals(3.0642, test(Loss.lad(), "autoMPG", autoMPG.formula(), autoMPG.data(), null), 1E-4);
    }

    @Test
    public void testAutoMPGQuantile() {
        assertEquals(3.0642, test(Loss.quantile(0.5), "autoMPG", autoMPG.formula(), autoMPG.data(), null), 1E-4);
    }

    @Test
    public void testAutoMPGHuber() {
        assertEquals(3.0942, test(Loss.huber(0.9), "autoMPG", autoMPG.formula(), autoMPG.data(), null), 1E-4);
    }

    @Test
    public void testCalHousingLS() {
        assertEquals(60947.2303, test(Loss.ls(), "cal_housing", calHousing.formula(), calHousing.data(), null), 1E-4);
    }

    @Test
    public void testCalHousingLAD() {
        assertEquals(66584.1426, test(Loss.lad(), "cal_housing", calHousing.formula(), calHousing.data(), null), 1E-4);
    }

    @Test
    public void testCalHousingQuantile() {
        assertEquals(66584.1426, test(Loss.quantile(0.5), "cal_housing", calHousing.formula(), calHousing.data(), null), 1E-4);
    }

    @Test
    public void testCalHousingHuber() {
        assertEquals(62226.2926, test(Loss.huber(0.9), "cal_housing", calHousing.formula(), calHousing.data(), null), 1E-4);
    }

    @Test
    public void testPuma8nhLS() {
        assertEquals(3.2361, test(Loss.ls(), "puma8nh", puma.formula(), puma.data(), null), 1E-4);
    }

    @Test
    public void testPuma8nhLAD() {
        assertEquals(3.2636, test(Loss.lad(), "puma8nh", puma.formula(), puma.data(), null), 1E-4);
    }

    @Test
    public void testPuma8nhQuantile() {
        assertEquals(3.2636, test(Loss.quantile(0.5), "puma8nh", puma.formula(), puma.data(), null), 1E-4);
    }

    @Test
    public void testPuma8nhHuber() {
        assertEquals(3.2465, test(Loss.huber(0.9), "puma8nh", puma.formula(), puma.data(), null), 1E-4);
    }

    @Test
    public void testKin8nmLS() {
        assertEquals(0.1809, test(Loss.ls(), "kin8nm", kin8nm.formula(), kin8nm.data(), null), 1E-4);
    }

    @Test
    public void testKin8nmLAD() {
        assertEquals(0.1832, test(Loss.lad(), "kin8nm", kin8nm.formula(), kin8nm.data(), null), 1E-4);
    }

    @Test
    public void testKin8nmQuantile() {
        assertEquals(0.1832, test(Loss.quantile(0.5), "kin8nm", kin8nm.formula(), kin8nm.data(), null), 1E-4);
    }

    @Test
    public void testKin8nmHuber() {
        assertEquals(0.1803, test(Loss.huber(0.9), "kin8nm", kin8nm.formula(), kin8nm.data(), null), 1E-4);
    }

    @Test
    public void testShap() {
        MathEx.setSeed(19650218); // to get repeatable results.
        System.setProperty("smile.regression_tree.bins", "1");

        var options = new GradientTreeBoost.Options(Loss.ls(), 100, 20, 100, 5, 0.05, 0.7, null, null);
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
