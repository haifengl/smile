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
import smile.data.*;
import smile.data.formula.Formula;
import smile.io.Arff;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.util.Paths;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RMSE;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

    @Test(expected = Test.None.class)
    public void testLongley() throws Exception {
        System.out.println("longley");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] longley = MathEx.clone(Longley.x);
        MathEx.standardize(longley);

        KernelMachine<double[]> model = GaussianProcessRegression.fit(longley, Longley.y, new GaussianKernel(8.0), 0.2);
        System.out.println(model);

        double[] prediction = LOOCV.regression(longley, Longley.y, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(8.0), 0.2));
        double rmse = RMSE.of(Longley.y, prediction);

        System.out.println("RMSE = " + rmse);
        assertEquals(3.978109808216234, rmse, 1E-4);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(CPU.x);
        MathEx.standardize(x);
        CrossValidation cv = new CrossValidation(x.length, 10);

        double[] prediction = cv.regression(x, CPU.y, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(47.02), 0.1));

        double[] sparsePrediction = cv.regression(10, x, CPU.y, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double[] nystromPrediction = cv.regression(x, CPU.y, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double rmse = RMSE.of(CPU.y, prediction);
        double sparseRMSE = RMSE.of(CPU.y, sparsePrediction);
        double nystromRMSE = RMSE.of(CPU.y, nystromPrediction);

        System.out.println("Regular 10-CV RMSE = " + rmse);
        System.out.println("Sparse 10-CV RMSE = " + sparseRMSE);
        System.out.println("Nystrom 10-CV RMSE = " + nystromRMSE);
        assertEquals(76.32510156134408, rmse, 1E-4);
        assertEquals(68.68104843938221, sparseRMSE, 1E-4);
        assertEquals(65.9687213249394, nystromRMSE, 1E-4);
    }
    
    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void test2DPlanes() throws Exception {
        System.out.println("2dplanes");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(Planes.x);
        double[] y = Planes.y;

        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double[] prediction = cv.regression(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(34.866), 0.1));

        double[] sparsePrediction = cv.regression(10, datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double[] nystromPrediction = cv.regression(datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double rmse = RMSE.of(datay, prediction);
        double sparseRMSE = RMSE.of(datay, sparsePrediction);
        double nystromRMSE = RMSE.of(datay, nystromPrediction);

        System.out.println("Regular 10-CV RMSE = " + rmse);
        System.out.println("Sparse 10-CV RMSE = " + sparseRMSE);
        System.out.println("Nystrom 10-CV RMSE = " + nystromRMSE);
        assertEquals(2.398555967342820, rmse, 1E-4);
        assertEquals(2.176467597770095, sparseRMSE, 1E-4);
        assertEquals(2.109326738693354, nystromRMSE, 1E-4);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void testAilerons() throws Exception {
        System.out.println("ailerons");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(Ailerons.x);
        MathEx.standardize(x);
        double[] y = Ailerons.y.clone();
        for (int i = 0; i < y.length; i++) {
            y[i] *= 10000;
        }

        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double[] prediction = cv.regression(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(183.96), 0.1));

        double[] sparsePrediction = cv.regression(10, datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double[] nystromPrediction = cv.regression(datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double rmse = RMSE.of(datay, prediction);
        double sparseRMSE = RMSE.of(datay, sparsePrediction);
        double nystromRMSE = RMSE.of(datay, nystromPrediction);

        System.out.println("Regular 10-CV RMSE = " + rmse);
        System.out.println("Sparse 10-CV RMSE = " + sparseRMSE);
        System.out.println("Nystrom 10-CV RMSE = " + nystromRMSE);
        assertEquals(2.163041768091070, rmse, 1E-4);
        assertEquals(2.287503841914310, sparseRMSE, 1E-4);
        assertEquals(2.210658612900254, nystromRMSE, 1E-4);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void testBank32nh() throws Exception {
        System.out.println("bank32nh");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(Bank32nh.x);
        double[] y = Bank32nh.y;
        MathEx.standardize(x);

        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double[] prediction = cv.regression(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(55.3), 0.1));

        double[] sparsePrediction = cv.regression(10, datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double[] nystromPrediction = cv.regression(datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double rmse = RMSE.of(datay, prediction);
        double sparseRMSE = RMSE.of(datay, sparsePrediction);
        double nystromRMSE = RMSE.of(datay, nystromPrediction);

        System.out.println("Regular 10-CV RMSE = " + rmse);
        System.out.println("Sparse 10-CV RMSE = " + sparseRMSE);
        System.out.println("Nystrom 10-CV RMSE = " + nystromRMSE);
        assertEquals(0.08434491755621974, rmse, 1E-4);
        assertEquals(0.08494071211767774, sparseRMSE, 1E-4);
        assertEquals(0.346626208923527, nystromRMSE, 1E-4);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void testPuma8nh() throws Exception {
        System.out.println("puma8nh");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = Puma8NH.x;
        double[] y = Puma8NH.y;

        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double[] prediction = cv.regression(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(38.63), 0.1));

        double[] sparsePrediction = cv.regression(10, datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double[] nystromPrediction = cv.regression(datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double rmse = RMSE.of(datay, prediction);
        double sparseRMSE = RMSE.of(datay, sparsePrediction);
        double nystromRMSE = RMSE.of(datay, nystromPrediction);

        System.out.println("Regular 10-CV RMSE = " + rmse);
        System.out.println("Sparse 10-CV RMSE = " + sparseRMSE);
        System.out.println("Nystrom 10-CV RMSE = " + nystromRMSE);
        assertEquals(4.441587058240469, rmse, 1E-4);
        assertEquals(4.421052805028641, sparseRMSE, 1E-4);
        assertEquals(4.414595386286706, nystromRMSE, 1E-4);
    }

    /**
     * Test of learn method, of class GaussianProcessRegression.
     */
    @Test(expected = Test.None.class)
    public void testKin8nm() throws Exception {
        System.out.println("kin8nm");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(Kin8nm.x);
        double[] y = Kin8nm.y;
        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        CrossValidation cv = new CrossValidation(datax.length, 10);

        double[] prediction = cv.regression(datax, datay, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(34.97), 0.1));

        double[] sparsePrediction = cv.regression(10, datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double[] nystromPrediction = cv.regression(datax, datay, (xi, yi) -> {
            KMeans kmeans = KMeans.fit(xi, 30);
            double[][] centers = kmeans.centroids;
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

        double rmse = RMSE.of(datay, prediction);
        double sparseRMSE = RMSE.of(datay, sparsePrediction);
        double nystromRMSE = RMSE.of(datay, nystromPrediction);

        System.out.println("Regular 10-CV RMSE = " + rmse);
        System.out.println("Sparse 10-CV RMSE = " + sparseRMSE);
        System.out.println("Nystrom 10-CV RMSE = " + nystromRMSE);
        assertEquals(0.20205594684848896, rmse, 1E-4);
        assertEquals(0.19819268891978126, sparseRMSE, 1E-4);
        assertEquals(0.19562556001290177, nystromRMSE, 1E-4);
    }
}