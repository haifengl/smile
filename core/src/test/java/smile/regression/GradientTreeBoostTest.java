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

import smile.data.*;
import smile.data.formula.Formula;
import smile.sort.QuickSort;
import smile.validation.LOOCV;
import smile.validation.RMSE;
import smile.validation.Validation;
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

    @Test
    public void testLongley() {
        System.out.println("longley");

        // to get repeatable results.
        MathEx.setSeed(19650218);
        GradientTreeBoost model = GradientTreeBoost.fit(Longley.formula, Longley.data);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        double[] prediction = LOOCV.regression(Longley.data, x -> GradientTreeBoost.fit(Longley.formula, x));
        double rmse = RMSE.apply(Longley.y, prediction);
        System.out.println("LOOCV RMSE = " + rmse);
        assertEquals(6.497666978811788, rmse, 1E-4);
    }

    public void test(GradientTreeBoost.Loss loss, String name, Formula formula, DataFrame data, double expected) {
        System.out.println(name + "\t" + loss);

        // to get repeatable results.
        MathEx.setSeed(19650218);
        GradientTreeBoost model = GradientTreeBoost.fit(formula, data);

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        double[] prediction = CrossValidation.regression(10, data, x -> GradientTreeBoost.fit(formula, x, loss, 100, 6, 5, 0.05, 0.7));
        double rmse = RMSE.apply(formula.y(data).toDoubleArray(), prediction);
        System.out.format("10-CV RMSE = %.4f%n", rmse);
        assertEquals(expected, rmse, 1E-4);
    }

    @Test
    public void testLS() {
        test(GradientTreeBoost.Loss.LeastSquares, "CPU", CPU.formula, CPU.data, 71.9149);
        test(GradientTreeBoost.Loss.LeastSquares, "2dplanes", Planes.formula, Planes.data, 1.1016);
        test(GradientTreeBoost.Loss.LeastSquares, "abalone", Abalone.formula, Abalone.train, 2.2199);
        test(GradientTreeBoost.Loss.LeastSquares, "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
        test(GradientTreeBoost.Loss.LeastSquares, "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0847);
        test(GradientTreeBoost.Loss.LeastSquares, "autoMPG", AutoMPG.formula, AutoMPG.data, 2.8148);
        test(GradientTreeBoost.Loss.LeastSquares, "cal_housing", CalHousing.formula, CalHousing.data, 60604.6920);
        test(GradientTreeBoost.Loss.LeastSquares, "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2487);
        test(GradientTreeBoost.Loss.LeastSquares, "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1802);
    }

    @Test
    public void testLAD() {
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "CPU", CPU.formula, CPU.data, 144.5555);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "2dplanes", Planes.formula, Planes.data, 2.6505);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "abalone", Abalone.formula, Abalone.train, 2.3665);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0969);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "autoMPG", AutoMPG.formula, AutoMPG.data, 3.1272);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "cal_housing", CalHousing.formula, CalHousing.data, 118361.1368);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "puma8nh", Puma8NH.formula, Puma8NH.data, 4.2626);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1893);
    }

    @Test
    public void testHuber() {
        test(GradientTreeBoost.Loss.Huber, "CPU", CPU.formula, CPU.data, 89.9275);
        test(GradientTreeBoost.Loss.Huber, "2dplanes", Planes.formula, Planes.data, 1.1122);
        test(GradientTreeBoost.Loss.Huber, "abalone", Abalone.formula, Abalone.train, 2.2244);
        test(GradientTreeBoost.Loss.Huber, "ailerons", Ailerons.formula, Ailerons.data, 0.0002);
        test(GradientTreeBoost.Loss.Huber, "bank32nh", Bank32nh.formula, Bank32nh.data, 0.0857);
        test(GradientTreeBoost.Loss.Huber, "autoMPG", AutoMPG.formula, AutoMPG.data, 2.8347);
        test(GradientTreeBoost.Loss.Huber, "cal_housing", CalHousing.formula, CalHousing.data, 62077.1824);
        test(GradientTreeBoost.Loss.Huber, "puma8nh", Puma8NH.formula, Puma8NH.data, 3.2767);
        test(GradientTreeBoost.Loss.Huber, "kin8nm", Kin8nm.formula, Kin8nm.data, 0.1827);
    }
}
