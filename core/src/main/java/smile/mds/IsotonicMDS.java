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

package smile.mds;

import java.util.Properties;

import smile.data.vector.DoubleVector;
import smile.math.BFGS;
import smile.math.MathEx;
import smile.math.DifferentiableMultivariateFunction;
import smile.sort.QuickSort;

/**
 * Kruskal's nonmetric MDS. In non-metric MDS, only the rank order of entries
 * in the proximity matrix (not the actual dissimilarities) is assumed to
 * contain the significant information. Hence, the distances of the final
 * configuration should as far as possible be in the same rank order as the
 * original data. Note that a perfect ordinal re-scaling of the data into
 * distances is usually not possible. The relationship is typically found
 * using isotonic regression.
 *
 * @author Haifeng Li
 */
public class IsotonicMDS {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IsotonicMDS.class);

    /**
     * The final stress achieved.
     */
    public final double stress;
    /**
     * The coordinates.
     */
    public final double[][] coordinates;

    /**
     * Constructor.
     *
     * @param stress the objective function value.
     * @param coordinates the principal coordinates
     */
    public IsotonicMDS(double stress, double[][] coordinates) {
        this.stress = stress;
        this.coordinates = coordinates;
    }

    /**
     * Fits Kruskal's non-metric MDS with default k = 2, tolerance = 1E-4 and maxIter = 200.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     */
    public static IsotonicMDS of(double[][] proximity) {
        return of(proximity, new Properties());
    }

    /**
     * Fits Kruskal's non-metric MDS.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param k the dimension of the projection.
     */
    public static IsotonicMDS of(double[][] proximity, int k) {
        return of(proximity, k, 1E-4, 200);
    }

    /**
     * Fits Kruskal's non-metric MDS.
     *
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     */
    public static IsotonicMDS of(double[][] proximity, Properties prop) {
        int k = Integer.valueOf(prop.getProperty("smile.isotonic.mds.k", "2"));
        double tol = Double.valueOf(prop.getProperty("smile.isotonic.mds.tolerance", "1E-4"));
        int maxIter = Integer.valueOf(prop.getProperty("smile.isotonic.mds.max.iterations", "200"));
        return of(proximity, k, tol, maxIter);
    }

    /**
     * Fits Kruskal's non-metric MDS.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param k the dimension of the projection.
     * @param tol the tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     */
    public static IsotonicMDS of(double[][] proximity, int k, double tol, int maxIter) {
        Properties prop = new Properties();
        prop.setProperty("smile.mds.k", String.valueOf(k));
        return of(proximity, MDS.of(proximity, prop).coordinates, tol, maxIter);
    }

    /**
     * Fits Kruskal's non-metric MDS.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param init the initial projected coordinates, of which the column
     * size is the projection dimension.
     * @param tol the tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     */
    public static IsotonicMDS of(double[][] proximity, double[][] init, double tol, int maxIter) {
        if (proximity.length != proximity[0].length) {
            throw new IllegalArgumentException("The proximity matrix is not square.");
        }

        if (proximity.length != init.length) {
            throw new IllegalArgumentException("The proximity matrix and the initial coordinates are of different size.");
        }

        int nr = proximity.length;
        int nc = init[0].length;

        int n = nr * (nr - 1) / 2;
        double[] d = new double[n];
        for (int i = 0, l = 0; i < nr; i++) {
            for (int j = i + 1; j < nr; j++, l++) {
                d[l] = proximity[j][i];
            }
        }

        double[] x = new double[nr * nc];
        for (int i = 0, l = 0; i < nr; i++) {
            for (int j = 0; j < nc; j++, l++) {
                x[l] = init[i][j];
            }
        }

        int[] ord = QuickSort.sort(d);
        int[] ord2 = QuickSort.sort(ord.clone());

        ObjectiveFunction func = new ObjectiveFunction(nr, nc, d, ord, ord2);

        double stress = 0.0;
        BFGS bfgs = new BFGS(tol, maxIter);
        try {
            stress = bfgs.minimize(func, 5, x);
        } catch (Exception ex) {
            // If L-BFGS doesn't work, let's try BFGS.
            stress = bfgs.minimize(func, x);
        }

        if (stress == 0.0) {
            logger.info(String.format("Isotonic MDS: error = %.1f%%. The fit is perfect.", 100 * stress));
        } else if (stress <= 0.025) {
            logger.info(String.format("Isotonic MDS: error = %.1f%%. The fit is excellent.", 100 * stress));
        } else if (stress <= 0.05) {
            logger.info(String.format("Isotonic MDS: error = %.1f%%. The fit is good.", 100 * stress));
        } else if (stress <= 0.10) {
            logger.info(String.format("Isotonic MDS: error = %.1f%%. The fit is fair.", 100 * stress));
        } else {
            logger.info(String.format("Isotonic MDS: error = %.1f%%. The fit may be poor.", 100 * stress));
        }

        double[][] coordinates = new double[nr][nc];
        for (int i = 0, l = 0; i < nr; i++) {
            for (int j = 0; j < nc; j++, l++) {
                coordinates[i][j] = x[l];
            }
        }

        return new IsotonicMDS(stress, coordinates);
    }

    /**
     * Isotonic regression.
     */
    static class ObjectiveFunction implements DifferentiableMultivariateFunction {
        /** ranks of dissimilarities */
        int[] ord;
        /** inverse ordering (which one is rank i?) */
        int[] ord2;
        /** number of dissimilarities */
        int n;
        /** number of data points */
        int nr;
        /** # cols of fitted configuration */
        int nc;
        /** Size of configuration array */
        int dimx;
        /** dissimilarities */
        double[] d;
        /** fitted distances (in rank of d order) */
        double[] y;
        /** cumulative fitted distances (in rank of d order) */
        double[] yc;
        /** isotonic regression fitted values (ditto) */
        double[] yf;

        ObjectiveFunction(int nr, int nc, double[] d, int[] ord, int[] ord2) {
            this.d = d;
            this.ord = ord;
            this.ord2 = ord2;
            this.nr = nr;
            this.nc = nc;
            this.n = d.length;
            this.y = new double[n];
            this.yf = new double[n];
            this.yc = new double[n + 1];
        }

        void dist(double[] x) {
            int index = 0;
            for (int i = 0; i < nr; i++) {
                for (int j = i + 1; j < nr; j++) {
                    double tmp = 0.0;
                    for (int c = 0; c < nc; c++) {
                        tmp += MathEx.sqr(x[i * nc + c] - x[j * nc + c]);
                    }
                    d[index++] = Math.sqrt(tmp);
                }
            }

            for (index = 0; index < n; index++) {
                y[index] = d[ord[index]];
            }
        }

        @Override
        public double f(double[] x) {
            dist(x);

            yc[0] = 0.0;
            double tmp = 0.0;
            for (int i = 0; i < n; i++) {
                tmp += y[i];
                yc[i + 1] = tmp;
            }

            int ip = 0;
            int known = 0;
            do {
                double slope = 1.0e+200;
                for (int i = known + 1; i <= n; i++) {
                    tmp = (yc[i] - yc[known]) / (i - known);
                    if (tmp < slope) {
                        slope = tmp;
                        ip = i;
                    }
                }
                for (int i = known; i < ip; i++) {
                    yf[i] = (yc[ip] - yc[known]) / (ip - known);
                }
            } while ((known = ip) < n);

            double sstar = 0.0;
            double tstar = 0.0;
            for (int i = 0; i < n; i++) {
                tmp = y[i] - yf[i];
                sstar += tmp * tmp;
                tstar += y[i] * y[i];
            }
            double ssq = Math.sqrt(sstar / tstar);
            return ssq;
        }

        @Override
        public double g(double[] x, double[] g) {
            dist(x);

            yc[0] = 0.0;
            double tmp = 0.0;
            for (int i = 0; i < n; i++) {
                tmp += y[i];
                yc[i + 1] = tmp;
            }

            int ip = 0;
            int known = 0;
            do {
                double slope = 1.0e+200;
                for (int i = known + 1; i <= n; i++) {
                    tmp = (yc[i] - yc[known]) / (i - known);
                    if (tmp < slope) {
                        slope = tmp;
                        ip = i;
                    }
                }
                for (int i = known; i < ip; i++) {
                    yf[i] = (yc[ip] - yc[known]) / (ip - known);
                }
            } while ((known = ip) < n);

            double sstar = 0.0;
            double tstar = 0.0;
            for (int i = 0; i < n; i++) {
                tmp = y[i] - yf[i];
                sstar += tmp * tmp;
                tstar += y[i] * y[i];
            }
            double ssq = Math.sqrt(sstar / tstar);

            int k = 0;
            for (int u = 0; u < nr; u++) {
                for (int i = 0; i < nc; i++) {
                    tmp = 0.0;
                    for (int s = 0; s < nr; s++) {
                        if (s == u) {
                            continue;
                        }
                        if (s > u) {
                            k = nr * u - u * (u + 1) / 2 + s - u;
                        } else if (s < u) {
                            k = nr * s - s * (s + 1) / 2 + u - s;
                        }
                        k = ord2[k - 1];
                        if (k >= n) {
                            continue;
                        }
                        double tmp1 = (x[u * nc + i] - x[s * nc + i]);
                        double sgn = (tmp1 >= 0) ? 1 : -1;
                        tmp1 = Math.abs(tmp1) / y[k];
                        tmp += ((y[k] - yf[k]) / sstar - y[k] / tstar) * sgn * tmp1;
                    }
                    g[u * nc + i] = tmp * ssq;
                }
            }
            return ssq;
        }
    }
}
