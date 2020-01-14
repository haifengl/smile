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

import smile.base.cart.Loss;
import smile.data.*;
import smile.data.formula.Formula;
import smile.validation.LOOCV;
import smile.validation.RMSE;
import smile.validation.CrossValidation;
import smile.math.MathEx;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

    @Test(expected = Test.None.class)
    public void testLongley() throws Exception {
        System.out.println("longley");

        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(Longley.formula, Longley.data);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        System.out.println("----- Progressive RMSE -----");
        double[][] test = model.test(Longley.data);
        for (int i = 0; i < test.length; i++) {
            System.out.format("RMSE with %3d trees: %.4f%n", i+1, RMSE.of(Longley.y, test[i]));
        }

        double[] prediction = LOOCV.regression(Longley.formula, Longley.data, (f, x) -> GradientTreeBoost.fit(f, x));
        double rmse = RMSE.of(Longley.y, prediction);
        System.out.println("LOOCV RMSE = " + rmse);
        assertEquals(3.545378550497016, rmse, 1E-4);

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
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        double[] prediction = CrossValidation.regression(10, formula, data, (f, x) -> GradientTreeBoost.fit(f, x, loss, 100, 20, 6, 5, 0.05, 0.7));
        double rmse = RMSE.of(formula.y(data).toDoubleArray(), prediction);
        System.out.format("10-CV RMSE = %.4f%n", rmse);
        assertEquals(expected, rmse, 1E-4);
    }

    @Test
    public void testLS() {
        test(Loss.ls(), "CPU", CPU.formula, CPU.data, 71.9149);
        test(Loss.ls(), "2dplanes", Planes.formula, Planes.data, 1.1016);
        test(Loss.ls(), "abalone", Abalone.formula, Abalone.train, 2.2199);
        test(Loss.ls(), "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
        test(Loss.ls(), "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0847);
        test(Loss.ls(), "autoMPG", AutoMPG.formula, AutoMPG.data, 2.8148);
        test(Loss.ls(), "cal_housing", CalHousing.formula, CalHousing.data, 60604.6920);
        test(Loss.ls(), "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2487);
        test(Loss.ls(), "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1802);
    }

    @Test
    public void testLAD() {
        test(Loss.lad(), "CPU", CPU.formula, CPU.data, 89.6677);
        test(Loss.lad(), "2dplanes", Planes.formula, Planes.data, 1.1347);
        test(Loss.lad(), "abalone", Abalone.formula, Abalone.train, 2.2990);
        test(Loss.lad(), "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
        test(Loss.lad(), "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0912);
        test(Loss.lad(), "autoMPG", AutoMPG.formula, AutoMPG.data, 2.8460);
        test(Loss.lad(), "cal_housing", CalHousing.formula, CalHousing.data, 66772.6697);
        test(Loss.lad(), "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2495);
        test(Loss.lad(), "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1815);
    }

    @Test
    public void testQuantile() {
        test(Loss.quantile(0.5), "CPU", CPU.formula, CPU.data, 89.6677);
        test(Loss.quantile(0.5), "2dplanes", Planes.formula, Planes.data, 1.1347);
        test(Loss.quantile(0.5), "abalone", Abalone.formula, Abalone.train, 2.2990);
        test(Loss.quantile(0.5), "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
        test(Loss.quantile(0.5), "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0912);
        test(Loss.quantile(0.5), "autoMPG", AutoMPG.formula, AutoMPG.data, 2.8460);
        test(Loss.quantile(0.5), "cal_housing", CalHousing.formula, CalHousing.data, 66772.6697);
        test(Loss.quantile(0.5), "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2495);
        test(Loss.quantile(0.5), "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1815);
    }

    @Test
    public void testHuber() {
        test(Loss.huber(0.9), "CPU", CPU.formula, CPU.data, 85.3103);
        test(Loss.huber(0.9), "2dplanes", Planes.formula, Planes.data, 1.1080);
        test(Loss.huber(0.9), "abalone", Abalone.formula, Abalone.train, 2.2262);
        test(Loss.huber(0.9), "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
        test(Loss.huber(0.9), "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0855);
        test(Loss.huber(0.9), "autoMPG", AutoMPG.formula, AutoMPG.data, 2.8316);
        test(Loss.huber(0.9), "cal_housing", CalHousing.formula, CalHousing.data, 62115.9896);
        test(Loss.huber(0.9), "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2435);
        test(Loss.huber(0.9), "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1795);
    }
}
