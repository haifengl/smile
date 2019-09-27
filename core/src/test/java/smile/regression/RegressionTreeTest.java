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

import org.apache.http.auth.AuthOption;
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
    @Test
    public void testLongley() {
        System.out.println("longley");
        
        double rss = LOOCV.test(Longley.data, (x) -> RegressionTree.fit(Longley.formula, Longley.data, 3, 500));

        System.out.println("MSE = " + rss);
        assertEquals(41.933087445771115, rss, 1E-4);
    }

    public void test(String name, Formula formula, DataFrame data) {
        System.out.println(name);

        double rss = CrossValidation.test(10, data, x -> RegressionTree.fit(formula, x));
        System.out.format("10-CV RMSE = %.4f%n", rss);
    }

    /**
     * Test of learn method, of class RegressionTree.
     */
    @Test
    public void testAll() {
        test("CPU", CPU.formula, CPU.data);
        //test("2dplanes", "weka/regression/2dplanes.arff", 6);
        //test("abalone", "weka/regression/abalone.arff", 8);
        //test("ailerons", "weka/regression/ailerons.arff", 40);
        //test("bank32nh", "weka/regression/bank32nh.arff", 32);
        test("autoMPG", AutoMPG.formula, AutoMPG.data);
        test("cal_housing", CalHousing.formula, CalHousing.data);
        test("puma8nh", Puma8NH.formula, Puma8NH.data);
        test("kin8nm", Kin8nm.formula, Kin8nm.data);
    }
    
    /**
     * Test of learn method, of class RegressionTree.
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

        RegressionTree tree = RegressionTree.fit(CPU.formula, trainData);
        System.out.format("RMSE = %.4f%n", Validation.test(tree, testData));

        double[] importance = tree.importance();
        QuickSort.sort(importance);
        for (int i = importance.length; i-- > 0; ) {
            System.out.format("%s importance is %.4f%n", CPU.data.schema().fieldName(i), importance[i]);
        }
    }
}
