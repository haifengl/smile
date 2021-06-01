/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.regression;

import java.util.Arrays;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.clustering.KMeans;
import smile.data.*;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.MercerKernel;
import smile.math.matrix.Matrix;
import smile.validation.*;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] longley = MathEx.clone(Longley.x);
        MathEx.standardize(longley);

        RegressionMetrics metrics = LOOCV.regression(longley, Longley.y,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(8.0), 0.2));

        System.out.println(metrics);
        assertEquals(2.7492, metrics.rmse, 1E-4);

        GaussianProcessRegression<double[]> model = GaussianProcessRegression.fit(longley, Longley.y, new GaussianKernel(8.0), 0.2);
        System.out.println(model);

        GaussianProcessRegression<double[]>.JointPrediction joint = model.query(Arrays.copyOf(longley, 10));
        System.out.println(joint);

        int n = joint.mu.length;
        double[] musd = new double[2];
        for (int i = 0; i < n; i++) {
            model.predict(longley[i], musd);
            assertEquals(musd[0], joint.mu[i], 1E-7);
            assertEquals(musd[1], joint.sd[i], 1E-7);
        }

        double[][] samples = joint.sample(500);
        System.out.format("samples = %s\n", Matrix.of(samples).toString());
        System.out.format("sample cov = %s\n", Matrix.of(MathEx.cov(samples)).toString(true));

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testHPO() {
        System.out.println("HPO longley");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] longley = MathEx.clone(Longley.x);
        MathEx.standardize(longley);

        GaussianProcessRegression<double[]> model = GaussianProcessRegression.fit(longley, Longley.y, new GaussianKernel(8.0), 0.2, true, 1E-5, 500);
        System.out.println(model);
        assertEquals(-0.8996, model.L, 1E-4);
        assertEquals(0.0137, model.noise, 1E-4);

        MercerKernel<double[]> kernel = model.kernel;
        double noise = model.noise;

        RegressionMetrics metrics = LOOCV.regression(longley, Longley.y, (xi, yi) -> GaussianProcessRegression.fit(xi, yi, kernel, noise));

        System.out.println(metrics);
        assertEquals(1.7104, metrics.rmse, 1E-4);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(CPU.x);
        MathEx.standardize(x);

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, x, CPU.y,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(47.02), 0.1));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, x, CPU.y, (xi, yi) -> {
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

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, x, CPU.y, (xi, yi) -> {
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

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(62.7723, result.avg.rmse, 1E-4);
        assertEquals(50.6879, sparseResult.avg.rmse, 1E-4);
        assertEquals(50.1312, nystromResult.avg.rmse, 1E-4);
    }

    @Test
    public void test2DPlanes() {
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

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(34.866), 0.1));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(2.3968, result.avg.rmse, 1E-4);
        assertEquals(2.1752, sparseResult.avg.rmse, 1E-4);
        assertEquals(2.1083, nystromResult.avg.rmse, 1E-4);
    }

    @Test
    public void testAilerons() {
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

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(183.96), 0.1));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(2.1624, result.avg.rmse, 1E-4);
        assertEquals(2.2827, sparseResult.avg.rmse, 1E-4);
        assertEquals(2.2695, nystromResult.avg.rmse, 1E-4);
    }

    @Test
    public void testBank32nh() {
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

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(55.3), 0.1));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(0.0843, result.avg.rmse, 1E-4);
        assertEquals(0.0849, sparseResult.avg.rmse, 1E-4);
        assertEquals(0.3131, nystromResult.avg.rmse, 1E-4);
    }

    @Test
    public void testPuma8nh() {
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

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(38.63), 0.1));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(4.4398, result.avg.rmse, 1E-4);
        assertEquals(4.4194, sparseResult.avg.rmse, 1E-4);
        assertEquals(4.4097, nystromResult.avg.rmse, 1E-4);
    }

    @Test
    public void testKin8nm() {
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

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(34.97), 0.1));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
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

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(0.2020, result.avg.rmse, 1E-4);
        assertEquals(0.1983, sparseResult.avg.rmse, 1E-4);
        assertEquals(0.1956, nystromResult.avg.rmse, 1E-4);
    }
}