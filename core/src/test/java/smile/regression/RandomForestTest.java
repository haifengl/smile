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

import org.junit.*;
import smile.data.AutoMPG;
import smile.data.CPU;
import smile.data.DataFrame;
import smile.data.Longley;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.sort.QuickSort;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.Validation;

/**
 *
 * @author Haifeng Li
 */
public class RandomForestTest {
    
    public RandomForestTest() {
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
     * Test of predict method, of class RandomForest.
     */
    @Test
    public void testPredict() {
        System.out.println("predict");

        double rss = LOOCV.test(Longley.data, (x) -> RandomForest.fit(Longley.formula, Longley.data));

        System.out.println("MSE = " + rss);
    }

    public void test(String name, Formula formula, DataFrame data) {
        System.out.println(name);

        double rss = CrossValidation.test(10, data, x -> RandomForest.fit(formula, x));
        System.out.format("10-CV RMSE = %.4f%n", rss);
    }
    
    /**
     * Test of learn method, of class RandomForest.
     */
    @Test
    public void testAll() {
        test("CPU", CPU.formula, CPU.data);
        //test("2dplanes", "weka/regression/2dplanes.arff", 6);
        //test("abalone", "weka/regression/abalone.arff", 8);
        //test("ailerons", "weka/regression/ailerons.arff", 40);
        //test("bank32nh", "weka/regression/bank32nh.arff", 32);
        test("autoMPG", AutoMPG.formula, AutoMPG.data);
        //test("cal_housing", "weka/regression/cal_housing.arff", 8);
        //test("puma8nh", "weka/regression/puma8nh.arff", 8);
        //test("kin8nm", "weka/regression/kin8nm.arff", 8);
    }
    
    /**
     * Test of learn method, of class RandomForest.
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

        RandomForest forest = RandomForest.fit(CPU.formula, trainData);
        System.out.format("RMSE = %.4f%n", Validation.test(forest, testData));

        double[] rmse = forest.test(testData);
        for (int i = 1; i <= rmse.length; i++) {
            System.out.format("%d trees RMSE = %.4f%n", i, rmse[i-1]);
        }

        double[] importance = forest.importance();
        QuickSort.sort(importance);
        for (int i = importance.length; i-- > 0; ) {
            System.out.format("%s importance is %.4f%n", CPU.data.schema().fieldName(i), importance[i]);
        }
    }

    @Test
    public void testRandomForestMerging() throws Exception {
        System.out.println("Random forest merging");
        int n = CPU.data.size();
        int m = 3 * n / 4;
        int[] index = MathEx.permutate(n);

        int[] train = new int[m];
        int[] test = new int[n-m];
        System.arraycopy(index, 0, train, 0, train.length);
        System.arraycopy(index, train.length, test, 0, test.length);

        DataFrame trainData = CPU.data.of(train);
        DataFrame testData = CPU.data.of(test);

        RandomForest forest1 = RandomForest.fit(CPU.formula, trainData);
        RandomForest forest2 = RandomForest.fit(CPU.formula, trainData);
        RandomForest forest = forest1.merge(forest2);
        System.out.format("Forest 1 RMSE = %.4f%n", Validation.test(forest1, testData));
        System.out.format("Forest 2 RMSE = %.4f%n", Validation.test(forest2, testData));
        System.out.format("Merged   RMSE = %.4f%n", Validation.test(forest, testData));
    }
}
