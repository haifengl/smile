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
import smile.clustering.KMeans;
import smile.math.Math;
import smile.math.kernel.GaussianKernel;
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;

/**
 *
 * @author Haifeng Li
 */
public class GaussianProcessRegressionTest {
    public GaussianProcessRegressionTest() {
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
     * Test of learn method, of class RKHSRegression.
     */
    @Test
    public void testLearn() {
        System.out.println("learn");

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

        Math.standardize(longley);

        int n = longley.length;
        LOOCV loocv = new LOOCV(n);
        double rss = 0.0;
        for (int i = 0; i < n; i++) {
            double[][] trainx = Math.slice(longley, loocv.train[i]);
            double[] trainy = Math.slice(y, loocv.train[i]);
            GaussianProcessRegression<double[]> rkhs = new GaussianProcessRegression<>(trainx, trainy, new GaussianKernel(8.0), 0.2);

            double r = y[loocv.test[i]] - rkhs.predict(longley[loocv.test[i]]);
            rss += r * r;
        }

        System.out.println("MSE = " + rss/n);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
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
            Math.standardize(datax);
            
            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double sparseRSS30 = 0.0;
            double nystromRSS30 = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

                GaussianProcessRegression<double[]> rkhs = new GaussianProcessRegression<>(trainx, trainy, new GaussianKernel(47.02), 0.1);

                KMeans kmeans = new KMeans(trainx, 30, 10);
                double[][] centers = kmeans.centroids();
                double r0 = 0.0;
                for (int l = 0; l < centers.length; l++) {
                    for (int j = 0; j < l; j++) {
                        r0 += Math.distance(centers[l], centers[j]);
                    }
                }
                r0 /= (2 * centers.length);
                System.out.println("Kernel width = " + r0);
                GaussianProcessRegression<double[]> sparse30 = new GaussianProcessRegression<>(trainx, trainy, centers, new GaussianKernel(r0), 0.1);
                GaussianProcessRegression<double[]> nystrom30 = new GaussianProcessRegression<>(trainx, trainy, centers, new GaussianKernel(r0), 0.1, true);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - rkhs.predict(testx[j]);
                    rss += r * r;
                    
                    r = testy[j] - sparse30.predict(testx[j]);
                    sparseRSS30 += r * r;

                    r = testy[j] - nystrom30.predict(testx[j]);
                    nystromRSS30 += r * r;
                }
            }

            System.out.println("Regular 10-CV MSE = " + rss / n);
            System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30 / n);
            System.out.println("Nystrom (30) 10-CV MSE = " + nystromRSS30 / n);
         } catch (Exception ex) {
            ex.printStackTrace();
         }
    }
    
    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test
    public void test2DPlanes() {
        System.out.println("2dplanes");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(10);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/regression/2dplanes.arff"));
            double[][] x = data.toArray(new double[data.size()][]);
            double[] y = data.toArray(new double[data.size()]);

            int[] perm = Math.permutate(x.length);
            double[][] datax = new double[4000][];
            double[] datay = new double[datax.length];
            for (int i = 0; i < datax.length; i++) {
                datax[i] = x[perm[i]];
                datay[i] = y[perm[i]];
            }

            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double sparseRSS30 = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

                GaussianProcessRegression<double[]> rkhs = new GaussianProcessRegression<>(trainx, trainy, new GaussianKernel(34.866), 0.1);

                KMeans kmeans = new KMeans(trainx, 30, 10);
                double[][] centers = kmeans.centroids();
                double r0 = 0.0;
                for (int l = 0; l < centers.length; l++) {
                    for (int j = 0; j < l; j++) {
                        r0 += Math.distance(centers[l], centers[j]);
                    }
                }
                r0 /= (2 * centers.length);
                System.out.println("Kernel width = " + r0);
                GaussianProcessRegression<double[]> sparse30 = new GaussianProcessRegression<>(trainx, trainy, centers, new GaussianKernel(r0), 0.1);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - rkhs.predict(testx[j]);
                    rss += r * r;
                    
                    r = testy[j] - sparse30.predict(testx[j]);
                    sparseRSS30 += r * r;
                }
            }

            System.out.println("Regular 10-CV MSE = " + rss / n);
            System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30 / n);
         } catch (Exception ex) {
             System.err.println(ex);
         }
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test
    public void testAilerons() {
        System.out.println("ailerons");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(40);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/regression/ailerons.arff"));
            double[][] x = data.toArray(new double[data.size()][]);
            Math.standardize(x);

            double[] y = data.toArray(new double[data.size()]);
            for (int i = 0; i < y.length; i++) {
                y[i] *= 10000;
            }

            int[] perm = Math.permutate(x.length);
            double[][] datax = new double[4000][];
            double[] datay = new double[datax.length];
            for (int i = 0; i < datax.length; i++) {
                datax[i] = x[perm[i]];
                datay[i] = y[perm[i]];
            }

            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double sparseRSS30 = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

                GaussianProcessRegression<double[]> rkhs = new GaussianProcessRegression<>(trainx, trainy, new GaussianKernel(183.96), 0.1);

                KMeans kmeans = new KMeans(trainx, 30, 10);
                double[][] centers = kmeans.centroids();
                double r0 = 0.0;
                for (int l = 0; l < centers.length; l++) {
                    for (int j = 0; j < l; j++) {
                        r0 += Math.distance(centers[l], centers[j]);
                    }
                }
                r0 /= (2 * centers.length);
                System.out.println("Kernel width = " + r0);
                GaussianProcessRegression<double[]> sparse30 = new GaussianProcessRegression<>(trainx, trainy, centers, new GaussianKernel(r0), 0.1);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - rkhs.predict(testx[j]);
                    rss += r * r;
                    
                    r = testy[j] - sparse30.predict(testx[j]);
                    sparseRSS30 += r * r;
                }
            }

            System.out.println("Regular 10-CV MSE = " + rss / n);
            System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30 / n);
         } catch (Exception ex) {
             System.err.println(ex);
         }
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test
    public void testBank32nh() {
        System.out.println("bank32nh");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(32);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/regression/bank32nh.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);
            Math.standardize(x);

            int[] perm = Math.permutate(x.length);
            double[][] datax = new double[4000][];
            double[] datay = new double[datax.length];
            for (int i = 0; i < datax.length; i++) {
                datax[i] = x[perm[i]];
                datay[i] = y[perm[i]];
            }

            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double sparseRSS30 = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

                GaussianProcessRegression<double[]> rkhs = new GaussianProcessRegression<>(trainx, trainy, new GaussianKernel(55.3), 0.1);

                KMeans kmeans = new KMeans(trainx, 30, 10);
                double[][] centers = kmeans.centroids();
                double r0 = 0.0;
                for (int l = 0; l < centers.length; l++) {
                    for (int j = 0; j < l; j++) {
                        r0 += Math.distance(centers[l], centers[j]);
                    }
                }
                r0 /= (2 * centers.length);
                System.out.println("Kernel width = " + r0);
                GaussianProcessRegression<double[]> sparse30 = new GaussianProcessRegression<>(trainx, trainy, centers, new GaussianKernel(r0), 0.1);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - rkhs.predict(testx[j]);
                    rss += r * r;
                    
                    r = testy[j] - sparse30.predict(testx[j]);
                    sparseRSS30 += r * r;
                }
            }

            System.out.println("Regular 10-CV MSE = " + rss / n);
            System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30 / n);
         } catch (Exception ex) {
             System.err.println(ex);
         }
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test
    public void testPuma8nh() {
        System.out.println("puma8nh");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(8);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/regression/puma8nh.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);

            int[] perm = Math.permutate(x.length);
            double[][] datax = new double[4000][];
            double[] datay = new double[datax.length];
            for (int i = 0; i < datax.length; i++) {
                datax[i] = x[perm[i]];
                datay[i] = y[perm[i]];
            }

            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double sparseRSS30 = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

                GaussianProcessRegression<double[]> rkhs = new GaussianProcessRegression<>(trainx, trainy, new GaussianKernel(38.63), 0.1);

                KMeans kmeans = new KMeans(trainx, 30, 10);
                double[][] centers = kmeans.centroids();
                double r0 = 0.0;
                for (int l = 0; l < centers.length; l++) {
                    for (int j = 0; j < l; j++) {
                        r0 += Math.distance(centers[l], centers[j]);
                    }
                }
                r0 /= (2 * centers.length);
                System.out.println("Kernel width = " + r0);
                GaussianProcessRegression<double[]> sparse30 = new GaussianProcessRegression<>(trainx, trainy, centers, new GaussianKernel(r0), 0.1);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - rkhs.predict(testx[j]);
                    rss += r * r;
                    
                    r = testy[j] - sparse30.predict(testx[j]);
                    sparseRSS30 += r * r;
                }
            }

            System.out.println("Regular 10-CV MSE = " + rss / n);
            System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30 / n);
         } catch (Exception ex) {
             System.err.println(ex);
         }
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test
    public void testKin8nm() {
        System.out.println("kin8nm");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(8);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/regression/kin8nm.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);

            int[] perm = Math.permutate(x.length);
            double[][] datax = new double[4000][];
            double[] datay = new double[datax.length];
            for (int i = 0; i < datax.length; i++) {
                datax[i] = x[perm[i]];
                datay[i] = y[perm[i]];
            }

            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double sparseRSS30 = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

                GaussianProcessRegression<double[]> rkhs = new GaussianProcessRegression<>(trainx, trainy, new GaussianKernel(34.97), 0.1);

                KMeans kmeans = new KMeans(trainx, 30, 10);
                double[][] centers = kmeans.centroids();
                double r0 = 0.0;
                for (int l = 0; l < centers.length; l++) {
                    for (int j = 0; j < l; j++) {
                        r0 += Math.distance(centers[l], centers[j]);
                    }
                }
                r0 /= (2 * centers.length);
                System.out.println("Kernel width = " + r0);
                GaussianProcessRegression<double[]> sparse30 = new GaussianProcessRegression<>(trainx, trainy, centers, new GaussianKernel(r0), 0.1);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - rkhs.predict(testx[j]);
                    rss += r * r;
                    
                    r = testy[j] - sparse30.predict(testx[j]);
                    sparseRSS30 += r * r;
                }
            }

            System.out.println("Regular 10-CV MSE = " + rss / n);
            System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30 / n);
         } catch (Exception ex) {
             System.err.println(ex);
         }
    }
}