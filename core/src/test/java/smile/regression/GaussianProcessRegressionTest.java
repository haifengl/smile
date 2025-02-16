/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression;

import java.util.Arrays;
import smile.clustering.KMeans;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.MercerKernel;
import smile.math.matrix.Matrix;
import smile.regression.GaussianProcessRegression.Options;
import smile.validation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GaussianProcessRegressionTest {
    public GaussianProcessRegressionTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testOutOfBoundsException() {
        double[][] x = {
                {4.543,  3.135, 0.86},
                {5.159,  5.043, 1.53},
                {5.366,  5.438, 1.57},
                {5.759,  7.496, 1.81},
                {4.663,  3.807, 0.99},
                {5.697,  7.601, 1.09},
                {5.892,  8.726, 1.29},
                {6.078,  7.966, 1.78},
                {4.898,  3.85,  1.29},
                {5.242,  4.174, 1.58},
                {5.74 ,  6.142, 1.68},
                {6.446,  7.908, 1.9 },
                {4.477,  2.996, 1.06},
                {5.236,  4.942, 1.3 },
                {6.151,  6.752, 1.52},
                {6.365,  9.588, 1.74},
                {4.787,  3.912, 1.16},
                {5.412,  4.7  , 1.49},
                {5.247,  6.174, 1.63},
                {5.438,  9.064, 1.99},
                {4.564,  4.949, 1.15},
                {5.298,  5.22,  1.33},
                {5.455,  9.242, 1.44},
                {5.855, 10.199, 2   },
                {5.366,  3.664, 1.31},
                {6.043,  3.219, 1.46},
                {6.458,  6.962, 1.72},
                {5.328,  3.912, 1.25},
                {5.802,  6.685, 1.08},
                {6.176,  4.787, 1.25}
        };
        double[] y = {
                12.3, 20.9, 39, 47.9, 5.6, 25.9, 37.3, 21.9, 18.1, 21, 34.9, 57.2, 0.7, 25.9, 54.9,
                40.9, 15.9, 6.4, 18, 38.9, 14, 15.2, 32, 56.71, 16.8, 11.6, 26.5, 0.7, 13.4, 5.5
        };

        GaussianProcessRegression<double[]> model = GaussianProcessRegression.fit(x, y,
                new GaussianKernel(3),
                new Options(1e-5, false, 1e-5, 1024)
        );
        System.out.println(model);
    }

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");
        MathEx.setSeed(19650218); // to get repeatable results.
        var longley = new Longley();
        double[][] x = longley.x();
        double[] y = longley.y();
        MathEx.standardize(x);

        RegressionMetrics metrics = LOOCV.regression(x, y,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi,
                        new GaussianKernel(8.0),
                        new Options(0.2)));

        System.out.println(metrics);
        assertEquals(2.7492, metrics.rmse(), 1E-4);

        GaussianProcessRegression<double[]> model = GaussianProcessRegression.fit(x, y,
                new GaussianKernel(8.0),
                new Options(0.2));
        System.out.println(model);

        GaussianProcessRegression<double[]>.JointPrediction joint = model.query(Arrays.copyOf(x, 10));
        System.out.println(joint);

        int n = joint.mu.length;
        double[] musd = new double[2];
        for (int i = 0; i < n; i++) {
            model.predict(x[i], musd);
            assertEquals(musd[0], joint.mu[i], 1E-7);
            assertEquals(musd[1], joint.sd[i], 1E-7);
        }

        double[][] samples = joint.sample(500);
        System.out.format("samples = %s\n", Matrix.of(samples));
        System.out.format("sample cov = %s\n", Matrix.of(MathEx.cov(samples)).toString(true));

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testHPO() {
        System.out.println("HPO longley");
        MathEx.setSeed(19650218); // to get repeatable results.
        var longley = new Longley();
        double[][] x = longley.x();
        double[] y = longley.y();
        MathEx.standardize(x);

        GaussianProcessRegression<double[]> model = GaussianProcessRegression.fit(x, y,
                new GaussianKernel(8.0),
                new Options(0.2, true, 1E-5, 500));
        System.out.println(model);
        assertEquals(-0.8996, model.L, 1E-4);
        assertEquals(0.0137, model.noise, 1E-4);

        MercerKernel<double[]> kernel = model.kernel;
        double noise = model.noise;

        RegressionMetrics metrics = LOOCV.regression(x, y,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, kernel, new Options(noise)));

        System.out.println(metrics);
        assertEquals(1.7104, metrics.rmse(), 1E-4);
    }

    @Test
    public void testCPU() throws Exception {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cpu = new CPU();
        double[][] x = cpu.x();
        double[] y = cpu.y();
        MathEx.standardize(x);

        var result = CrossValidation.regression(10, x, y,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi,
                        new GaussianKernel(47.02),
                        new Options(0.1)));

        var sparseResult = CrossValidation.regression(10, x, y, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        var nystromResult = CrossValidation.regression(10, x, y, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(62.7723, result.avg().rmse(), 1E-4);
        assertEquals(50.6879, sparseResult.avg().rmse(), 1E-4);
        assertEquals(50.1312, nystromResult.avg().rmse(), 1E-4);
    }

    @Test
    public void test2DPlanes() throws Exception {
        System.out.println("2dplanes");
        MathEx.setSeed(19650218); // to get repeatable results.
        var planes = new Planes2D();
        double[][] x = MathEx.clone(planes.x());
        double[] y = planes.y();

        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi,
                        new GaussianKernel(34.866), new Options(0.1)));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(2.3968, result.avg().rmse(), 1E-4);
        assertEquals(2.1752, sparseResult.avg().rmse(), 1E-4);
        assertEquals(2.1083, nystromResult.avg().rmse(), 1E-4);
    }

    @Test
    public void testAilerons() throws Exception {
        System.out.println("ailerons");

        MathEx.setSeed(19650218); // to get repeatable results.
        var ailerons = new Ailerons();
        double[][] x = ailerons.x();
        double[] y = ailerons.y();
        MathEx.standardize(x);
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
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(183.96), new Options(0.1)));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(2.1624, result.avg().rmse(), 1E-4);
        assertEquals(2.2827, sparseResult.avg().rmse(), 1E-4);
        assertEquals(2.2695, nystromResult.avg().rmse(), 1E-4);
    }

    @Test
    public void testBank32nh() throws Exception {
        System.out.println("bank32nh");

        MathEx.setSeed(19650218); // to get repeatable results.

        var bank32nh = new Bank32nh();
        double[][] x = bank32nh.x();
        double[] y = bank32nh.y();
        MathEx.standardize(x);

        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(55.3), new Options(0.1)));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(0.0843, result.avg().rmse(), 1E-4);
        assertEquals(0.0849, sparseResult.avg().rmse(), 1E-4);
        assertEquals(0.3131, nystromResult.avg().rmse(), 1E-4);
    }

    @Test
    public void testPuma8nh() throws Exception {
        System.out.println("puma8nh");

        MathEx.setSeed(19650218); // to get repeatable results.
        var puma = new Puma8NH();
        double[][] x = puma.x();
        double[] y = puma.y();

        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(38.63), new Options(0.1)));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(4.4398, result.avg().rmse(), 1E-4);
        assertEquals(4.4194, sparseResult.avg().rmse(), 1E-4);
        assertEquals(4.4097, nystromResult.avg().rmse(), 1E-4);
    }

    @Test
    public void testKin8nm() throws Exception {
        System.out.println("kin8nm");

        MathEx.setSeed(19650218); // to get repeatable results.
        var kin8nm = new Kin8nm();
        double[][] x = kin8nm.x();
        double[] y = kin8nm.y();
        int[] permutation = MathEx.permutate(x.length);
        double[][] datax = new double[4000][];
        double[] datay = new double[datax.length];
        for (int i = 0; i < datax.length; i++) {
            datax[i] = x[permutation[i]];
            datay[i] = y[permutation[i]];
        }

        RegressionValidations<GaussianProcessRegression<double[]>> result = CrossValidation.regression(10, datax, datay,
                (xi, yi) -> GaussianProcessRegression.fit(xi, yi, new GaussianKernel(34.97), new Options(0.1)));

        RegressionValidations<GaussianProcessRegression<double[]>> sparseResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.fit(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        RegressionValidations<GaussianProcessRegression<double[]>> nystromResult = CrossValidation.regression(10, datax, datay, (xi, yi) -> {
            var kmeans = KMeans.fit(xi, 30, 100);
            double[][] centers = kmeans.centers();
            double r0 = 0.0;
            for (int l = 0; l < centers.length; l++) {
                for (int j = 0; j < l; j++) {
                    r0 += MathEx.distance(centers[l], centers[j]);
                }
            }
            r0 /= (2 * centers.length);
            System.out.println("Kernel width = " + r0);
            return GaussianProcessRegression.nystrom(xi, yi, centers, new GaussianKernel(r0), new Options(0.1));
        });

        System.out.println("GPR: " + result);
        System.out.println("Sparse: " + sparseResult);
        System.out.println("Nystrom: " + nystromResult);
        assertEquals(0.2020, result.avg().rmse(), 1E-4);
        assertEquals(0.1983, sparseResult.avg().rmse(), 1E-4);
        assertEquals(0.1956, nystromResult.avg().rmse(), 1E-4);
    }
}
