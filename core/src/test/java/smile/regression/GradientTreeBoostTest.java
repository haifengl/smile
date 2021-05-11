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

import smile.base.cart.Loss;
import smile.data.*;
import smile.data.formula.Formula;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RegressionMetrics;
import smile.validation.RegressionValidations;
import smile.validation.metric.RMSE;
import smile.math.MathEx;
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
public class GradientTreeBoostTest {
    
    public GradientTreeBoostTest() {
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

        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(Longley.formula, Longley.data);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %12.4f%n", model.schema().name(i), importance[i]);
        }

        System.out.println("----- Progressive RMSE -----");
        double[][] test = model.test(Longley.data);
        for (int i = 0; i < test.length; i++) {
            System.out.format("RMSE with %3d trees: %.4f%n", i+1, RMSE.of(Longley.y, test[i]));
        }

        RegressionMetrics metrics = LOOCV.regression(Longley.formula, Longley.data, GradientTreeBoost::fit);

        System.out.println(metrics);
        assertEquals(3.5453, metrics.rmse, 1E-4);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    public void test(Loss loss, String name, Formula formula, DataFrame data, double expected) {
        System.out.println(name + "\t" + loss);

        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(formula, data);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %12.4f%n", model.schema().name(i), importance[i]);
        }

        RegressionValidations<GradientTreeBoost> result = CrossValidation.regression(10, formula, data,
                (f, x) -> GradientTreeBoost.fit(f, x, loss, 100, 20, 6, 5, 0.05, 0.7));

        System.out.println(result);
        assertEquals(expected, result.avg.rmse, 1E-4);
    }

    @Test
    public void testCpuLS() {
        test(Loss.ls(), "CPU", CPU.formula, CPU.data, 60.5335);
    }

    @Test
    public void testCpuLAD() {
        test(Loss.lad(), "CPU", CPU.formula, CPU.data, 66.0549);
    }

    @Test
    public void testCpuQuantile() {
        test(Loss.quantile(0.5), "CPU", CPU.formula, CPU.data, 66.0549);
    }

    @Test
    public void testCpuHuber() {
        test(Loss.huber(0.9), "CPU", CPU.formula, CPU.data, 65.4128);
    }

    @Test
    public void test2DPlanesLS() {
        test(Loss.ls(), "2dplanes", Planes.formula, Planes.data, 1.1016);
    }

    @Test
    public void test2DPlanesLAD() {
        test(Loss.lad(), "2dplanes", Planes.formula, Planes.data, 1.1347);
    }

    @Test
    public void test2DPlanesQuantile() {
        test(Loss.quantile(0.5), "2dplanes", Planes.formula, Planes.data, 1.1347);
    }

    @Test
    public void test2DPlanesHuber() {
        test(Loss.huber(0.9), "2dplanes", Planes.formula, Planes.data, 1.1080);
    }

    @Test
    public void testAbaloneLS() {
        test(Loss.ls(), "abalone", Abalone.formula, Abalone.train, 2.2159);
    }

    @Test
    public void testAbaloneLAD() {
        test(Loss.lad(), "abalone", Abalone.formula, Abalone.train, 2.2958);
    }

    @Test
    public void testAbaloneQuantile() {
        test(Loss.quantile(0.5), "abalone", Abalone.formula, Abalone.train, 2.2958);
    }

    @Test
    public void testAbaloneHuber() {
        test(Loss.huber(0.9), "abalone", Abalone.formula, Abalone.train, 2.2228);
    }

    @Test
    public void testAileronsLS() {
        test(Loss.ls(), "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
    }

    @Test
    public void testAileronsLAD() {
        test(Loss.lad(), "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
    }

    @Test
    public void testAileronsQuantile() {
        test(Loss.quantile(0.5), "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
    }

    @Test
    public void testAileronsHuber() {
        test(Loss.huber(0.9), "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
    }

    @Test
    public void testBank32nhLS() {
        test(Loss.ls(), "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0845);
    }

    @Test
    public void testBank32nhLAD() {
        test(Loss.lad(), "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0909);
    }

    @Test
    public void testBank32nhQuantile() {
        test(Loss.quantile(0.5), "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0909);
    }

    @Test
    public void testBank32nhHuber() {
        test(Loss.huber(0.9), "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0853);
    }

    @Test
    public void testAutoMPGLS() {
        test(Loss.ls(), "autoMPG", AutoMPG.formula, AutoMPG.data, 3.0904);
    }

    @Test
    public void testAutoMPGLAD() {
        test(Loss.lad(), "autoMPG", AutoMPG.formula, AutoMPG.data, 3.0979);
    }

    @Test
    public void testAutoMPGQuantile() {
        test(Loss.quantile(0.5), "autoMPG", AutoMPG.formula, AutoMPG.data, 3.0979);
    }

    @Test
    public void testAutoMPGHuber() {
        test(Loss.huber(0.9), "autoMPG", AutoMPG.formula, AutoMPG.data, 3.1155);
    }

    @Test
    public void testCalHousingLS() {
        test(Loss.ls(), "cal_housing", CalHousing.formula, CalHousing.data, 60581.4183);
    }

    @Test
    public void testCalHousingLAD() {
        test(Loss.lad(), "cal_housing", CalHousing.formula, CalHousing.data, 66742.1902);
    }

    @Test
    public void testCalHousingQuantile() {
        test(Loss.quantile(0.5), "cal_housing", CalHousing.formula, CalHousing.data, 66742.1902);
    }

    @Test
    public void testCalHousingHuber() {
        test(Loss.huber(0.9), "cal_housing", CalHousing.formula, CalHousing.data, 62090.2639);
    }

    @Test
    public void testPuma8nhLS() {
        test(Loss.ls(), "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2482);
    }

    @Test
    public void testPuma8nhLAD() {
        test(Loss.lad(), "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2486);
    }

    @Test
    public void testPuma8nhQuantile() {
        test(Loss.quantile(0.5), "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2486);
    }

    @Test
    public void testPuma8nhHuber() {
        test(Loss.huber(0.9), "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2429);
    }

    @Test
    public void testKin8nmLS() {
        test(Loss.ls(), "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1802);
    }

    @Test
    public void testKin8nmLAD() {
        test(Loss.lad(), "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1814);
    }

    @Test
    public void testKin8nmQuantile() {
        test(Loss.quantile(0.5), "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1814);
    }

    @Test
    public void testKin8nmHuber() {
        test(Loss.huber(0.9), "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1795);
    }

    @Test
    public void testShap() {
        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(BostonHousing.formula, BostonHousing.data, Loss.ls(), 100, 20, 100, 5, 0.05, 0.7);
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

        String[] expected = {"CHAS", "ZN", "RAD", "INDUS", "B", "TAX", "AGE", "PTRATIO", "NOX", "CRIM", "DIS", "RM", "LSTAT"};
        assertArrayEquals(expected, fields);
    }
}
