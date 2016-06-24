/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.regression;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
import smile.math.Math;
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
        
        double[][] longley = {
            {234.289,      235.6,        159.0,    107.608, 1947,   60.323},
            {259.426,      232.5,        145.6,    108.632, 1948,   61.122},
            {258.054,      368.2,        161.6,    109.773, 1949,   60.171},
            {284.599,      335.1,        165.0,    110.929, 1950,   61.187},
            {328.975,      209.9,        309.9,    112.075, 1951,   63.221},
            {346.999,      193.2,        359.4,    113.270, 1952,   63.639},
            {365.385,      187.0,        354.7,    115.094, 1953,   64.989},
            {363.112,      357.8,        335.0,    116.219, 1954,   63.761},
            {397.469,      290.4,        304.8,    117.388, 1955,   66.019},
            {419.180,      282.2,        285.7,    118.734, 1956,   67.857},
            {442.769,      293.6,        279.8,    120.445, 1957,   68.169},
            {444.546,      468.1,        263.7,    121.950, 1958,   66.513},
            {482.704,      381.3,        255.2,    123.366, 1959,   68.655},
            {502.601,      393.1,        251.4,    125.368, 1960,   69.564},
            {518.173,      480.6,        257.2,    127.852, 1961,   69.331},
            {554.894,      400.7,        282.7,    130.081, 1962,   70.551}
        };

        double[] y = {
            83.0,  88.5,  88.2,  89.5,  96.2,  98.1,  99.0, 100.0, 101.2,
            104.6, 108.4, 110.8, 112.6, 114.2, 115.7, 116.9
        };

        int n = longley.length;
        LOOCV loocv = new LOOCV(n);
        double rss = 0.0;
        for (int i = 0; i < n; i++) {
            double[][] trainx = Math.slice(longley, loocv.train[i]);
            double[] trainy = Math.slice(y, loocv.train[i]);
            RegressionTree tree = new RegressionTree(trainx, trainy, 3);

            double r = y[loocv.test[i]] - tree.predict(longley[loocv.test[i]]);
            rss += r * r;
        }

        System.out.println("MSE = " + rss/n);
        assertEquals(41.933087445771115, rss/n, 1E-4);
    }
    
    public void test(String dataset, String url, int response) {
        System.out.println(dataset);
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(response);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile(url));
            double[] datay = data.toArray(new double[data.size()]);
            double[][] datax = data.toArray(new double[data.size()][]);
            
            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double ad = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

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
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] datay = data.toArray(new double[data.size()]);
            double[][] datax = data.toArray(new double[data.size()][]);

            int n = datax.length;
            int m = 3 * n / 4;
            int[] index = Math.permutate(n);
            
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
