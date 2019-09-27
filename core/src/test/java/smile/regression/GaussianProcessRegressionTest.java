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
import smile.clustering.KMeans;
import smile.data.CPU;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Arff;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.util.Paths;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;

import java.io.IOException;

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

        MathEx.standardize(longley);

        double rss = LOOCV.test(longley, y, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(8.0), 0.2));
        System.out.println("MSE = " + rss);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test
    public void testCPU() {
        System.out.println("CPU");
        double[][] x = CPU.x;
        MathEx.standardize(x);
        CrossValidation cv = new CrossValidation(x.length, 10);

        double rss = cv.test(x, CPU.y, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(47.02), 0.1));

        double sparseRSS30 = cv.test(10, x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        double nystromRSS30 = cv.test(x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        System.out.println("Regular 10-CV MSE = " + rss);
        System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30);
        System.out.println("Nystrom (30) 10-CV MSE = " + nystromRSS30);
    }
    
    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void test2DPlanes() throws Exception {
        System.out.println("2dplanes");
        Arff arff = new Arff(Paths.getTestData("weka/regression/2dplanes.arff"));
        DataFrame data = arff.read();
        Formula formula = Formula.lhs("y");
        double[][] x = formula.frame(data).toArray();
        double[] y = formula.response(data).toDoubleArray();

        int[] perm = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[perm[i]];
            datay[i] = y[perm[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double rss = cv.test(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(34.866), 0.1));

        double sparseRSS30 = cv.test(10, x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        double nystromRSS30 = cv.test(x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        System.out.println("Regular 10-CV MSE = " + rss);
        System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30);
        System.out.println("Nystrom (30) 10-CV MSE = " + nystromRSS30);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void testAilerons() throws Exception {
        System.out.println("ailerons");
        Arff arff = new Arff(Paths.getTestData("weka/regression/ailerons.arff"));
        DataFrame data = arff.read();
        Formula formula = Formula.lhs("goal");
        double[][] x = formula.frame(data).toArray();
        double[] y = formula.response(data).toDoubleArray();

        MathEx.standardize(x);
        for (int i = 0; i < y.length; i++) {
            y[i] *= 10000;
        }

        int[] perm = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[perm[i]];
            datay[i] = y[perm[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double rss = cv.test(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(183.96), 0.1));

        double sparseRSS30 = cv.test(10, x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        double nystromRSS30 = cv.test(x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        System.out.println("Regular 10-CV MSE = " + rss);
        System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30);
        System.out.println("Nystrom (30) 10-CV MSE = " + nystromRSS30);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void testBank32nh() throws Exception {
        System.out.println("bank32nh");
        Arff arff = new Arff(Paths.getTestData("weka/regression/bank32nh.arff"));
        DataFrame data = arff.read();
        Formula formula = Formula.lhs("rej");
        double[][] x = formula.frame(data).toArray();
        double[] y = formula.response(data).toDoubleArray();

        MathEx.standardize(x);
        int[] perm = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[perm[i]];
            datay[i] = y[perm[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double rss = cv.test(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(55.3), 0.1));

        double sparseRSS30 = cv.test(10, x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        double nystromRSS30 = cv.test(x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        System.out.println("Regular 10-CV MSE = " + rss);
        System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30);
        System.out.println("Nystrom (30) 10-CV MSE = " + nystromRSS30);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void testPuma8nh() throws Exception {
        System.out.println("puma8nh");
        Arff arff = new Arff(Paths.getTestData("weka/regression/puma8nh.arff"));
        DataFrame data = arff.read();
        Formula formula = Formula.lhs("thetadd3");
        double[][] x = formula.frame(data).toArray();
        double[] y = formula.response(data).toDoubleArray();

        int[] perm = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[perm[i]];
            datay[i] = y[perm[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double rss = cv.test(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(38.63), 0.1));

        double sparseRSS30 = cv.test(10, x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        double nystromRSS30 = cv.test(x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        System.out.println("Regular 10-CV MSE = " + rss);
        System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30);
        System.out.println("Nystrom (30) 10-CV MSE = " + nystromRSS30);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void testKin8nm() throws Exception {
        System.out.println("kin8nm");
        Arff arff = new Arff(Paths.getTestData("weka/regression/kin8nm.arff"));
        DataFrame data = arff.read();
        Formula formula = Formula.lhs("y");
        double[][] x = formula.frame(data).toArray();
        double[] y = formula.response(data).toDoubleArray();

        int[] perm = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[perm[i]];
            datay[i] = y[perm[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double rss = cv.test(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(34.97), 0.1));

        double sparseRSS30 = cv.test(10, x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        double nystromRSS30 = cv.test(x, CPU.y, (xi, yi) -> {
            KMeans kmeans = new KMeans(xi, 30, 10);
            double[][] centers = kmeans.centroids();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), 0.1);
        });

        System.out.println("Regular 10-CV MSE = " + rss);
        System.out.println("Sparse (30) 10-CV MSE = " + sparseRSS30);
        System.out.println("Nystrom (30) 10-CV MSE = " + nystromRSS30);
    }
}