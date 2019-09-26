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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.Longley;
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
    
    public void test(String dataset, String url, int response) {
        System.out.println(dataset);
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(response);
        try {
            AttributeDataset data = parser.parse(smile.util.Paths.getTestData(url));
            double[] datay = data.toArray(new double[data.size()]);
            double[][] datax = data.toArray(new double[data.size()][]);
            
            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double ad = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = MathEx.slice(datax, cv.train[i]);
                double[] trainy = MathEx.slice(datay, cv.train[i]);
                double[][] testx = MathEx.slice(datax, cv.test[i]);
                double[] testy = MathEx.slice(datay, cv.test[i]);

                RegressionTree tree = new RegressionTree(data.attributes(), trainx, trainy, 20);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - tree.predict(testx[j]);
                    rss += r * r;
                    ad += Math.abs(r);
                }
            }

            System.out.format("10-CV RMSE = %.4f \t AbsoluteDeviation = %.4f%n", Math.sqrt(rss/n), ad/n);
         } catch (Exception ex) {
             System.err.println(ex);
         }
    }
    
    /**
     * Test of learn method, of class RegressionTree.
     */
    @Test
    public void testAll() {
        test("CPU", "weka/cpu.arff", 6);
        //test("2dplanes", "weka/regression/2dplanes.arff", 6);
        //test("abalone", "weka/regression/abalone.arff", 8);
        //test("ailerons", "weka/regression/ailerons.arff", 40);
        //test("bank32nh", "weka/regression/bank32nh.arff", 32);
        test("autoMPG", "weka/regression/autoMpg.arff", 7);
        test("cal_housing", "weka/regression/cal_housing.arff", 8);
        test("puma8nh", "weka/regression/puma8NH.arff", 8);
        test("kin8nm", "weka/regression/kin8nm.arff", 8);
    }
    
    /**
     * Test of learn method, of class RegressionTree.
     */
    @Test
    public void testCPU() {
        System.out.println("CPU");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.util.Paths.getTestData("weka/cpu.arff"));
            double[] datay = data.toArray(new double[data.size()]);
            double[][] datax = data.toArray(new double[data.size()][]);

            int n = datax.length;
            int m = 3 * n / 4;
            int[] index = MathEx.permutate(n);
            
            double[][] trainx = new double[m][];
            double[] trainy = new double[m];            
            for (int i = 0; i < m; i++) {
                trainx[i] = datax[index[i]];
                trainy[i] = datay[index[i]];
            }
            
            double[][] testx = new double[n-m][];
            double[] testy = new double[n-m];            
            for (int i = m; i < n; i++) {
                testx[i-m] = datax[index[i]];
                testy[i-m] = datay[index[i]];                
            }

            RegressionTree tree = new RegressionTree(data.attributes(), trainx, trainy, 20);
            System.out.format("RMSE = %.4f%n", Validation.test(tree, testx, testy));
            
            double[] importance = tree.importance();
            index = QuickSort.sort(importance);
            for (int i = importance.length; i-- > 0; ) {
                System.out.format("%s importance is %.4f%n", data.attributes()[index[i]], importance[i]);
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
