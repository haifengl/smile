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

import smile.data.AutoMPG;
import smile.data.CPU;
import smile.data.CalHousing;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.sort.QuickSort;
import smile.validation.Validation;
import smile.validation.CrossValidation;
import smile.math.MathEx;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

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

    public void test(GradientTreeBoost.Loss loss, String name, Formula formula, DataFrame data) {
        System.out.println(name + "\t" + loss);

        double rss = CrossValidation.test(10, data, x -> GradientTreeBoost.fit(formula, x, loss, 100, 6, 0.05, 0.7));
        System.out.format("10-CV RMSE = %.4f%n", rss);
    }
    
    /**
     * Test of learn method, of class RegressionTree.
     */
    @Test
    public void testLS() {
        test(GradientTreeBoost.Loss.LeastSquares, "CPU", CPU.formula, CPU.data);
        //test(GradientTreeBoost.Loss.LeastSquares, "2dplanes", "weka/regression/2dplanes.arff", 6);
        //test(GradientTreeBoost.Loss.LeastSquares, "abalone", "weka/regression/abalone.arff", 8);
        //test(GradientTreeBoost.Loss.LeastSquares, "ailerons", "weka/regression/ailerons.arff", 40);
        //test(GradientTreeBoost.Loss.LeastSquares, "bank32nh", "weka/regression/bank32nh.arff", 32);
        test(GradientTreeBoost.Loss.LeastSquares, "autoMPG", AutoMPG.formula, AutoMPG.data);
        test(GradientTreeBoost.Loss.LeastSquares, "cal_housing", CalHousing.formula, CalHousing.data);
        //test(GradientTreeBoost.Loss.LeastSquares, "puma8nh", "weka/regression/puma8nh.arff", 8);
        //test(GradientTreeBoost.Loss.LeastSquares, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
    
    /**
     * Test of learn method, of class RegressionTree.
     */
    @Test
    public void testLAD() {
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "CPU", CPU.formula, CPU.data);
        //test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "2dplanes", "weka/regression/2dplanes.arff", 6);
        //test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "abalone", "weka/regression/abalone.arff", 8);
        //test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "ailerons", "weka/regression/ailerons.arff", 40);
        //test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "bank32nh", "weka/regression/bank32nh.arff", 32);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "autoMPG", AutoMPG.formula, AutoMPG.data);
        test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "cal_housing", CalHousing.formula, CalHousing.data);
        //test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "puma8nh", "weka/regression/puma8nh.arff", 8);
        //test(GradientTreeBoost.Loss.LeastAbsoluteDeviation, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
    
    /**
     * Test of learn method, of class RegressionTree.
     */
    @Test
    public void testHuber() {
        test(GradientTreeBoost.Loss.Huber, "CPU", CPU.formula, CPU.data);
        //test(GradientTreeBoost.Loss.Huber, "2dplanes", "weka/regression/2dplanes.arff", 6);
        //test(GradientTreeBoost.Loss.Huber, "abalone", "weka/regression/abalone.arff", 8);
        //test(GradientTreeBoost.Loss.Huber, "ailerons", "weka/regression/ailerons.arff", 40);
        //test(GradientTreeBoost.Loss.Huber, "bank32nh", "weka/regression/bank32nh.arff", 32);
        test(GradientTreeBoost.Loss.Huber, "autoMPG", AutoMPG.formula, AutoMPG.data);
        test(GradientTreeBoost.Loss.Huber, "cal_housing", CalHousing.formula, CalHousing.data);
        //test(GradientTreeBoost.Loss.Huber, "puma8nh", "weka/regression/puma8nh.arff", 8);
        //test(GradientTreeBoost.Loss.Huber, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
    
    /**
     * Test of learn method, of class GradientTreeBoost.
     */
    @Test
    public void testCPU() {
        System.out.println("CPU");

        int n = CPU.data.size();
        int m = 3 * n / 4;
        int[] index = MathEx.permutate(n);

        int[] train = new int[m];
        int[] test = new int[n-m];
        System.arraycopy(index, 0, train, 0, train.length);
        System.arraycopy(index, train.length, test, 0, test.length);

        DataFrame trainData = CPU.data.of(train);
        DataFrame testData = CPU.data.of(test);

        GradientTreeBoost boost = GradientTreeBoost.fit(CPU.formula, trainData, GradientTreeBoost.Loss.LeastAbsoluteDeviation, 100, 6, 0.005, 0.7);
        System.out.format("RMSE = %.4f%n", Validation.test(boost, testData));
            
        double[] rmse = boost.test(testData);
        for (int i = 1; i <= rmse.length; i++) {
            System.out.format("%d trees RMSE = %.4f%n", i, rmse[i-1]);
        }

        double[] importance = boost.importance();
        QuickSort.sort(importance);
        for (int i = importance.length; i-- > 0; ) {
            System.out.format("%s importance is %.4f%n", CPU.data.schema().fieldName(i), importance[i]);
        }
    }
}
