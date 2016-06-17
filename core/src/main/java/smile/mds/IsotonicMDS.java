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
package smile.mds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
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
    private static final Logger logger = LoggerFactory.getLogger(IsotonicMDS.class);

    /**
     * The final stress achieved.
     */
    private double stress;
    /**
     * Coordinate matrix.
     */
    private double[][] coordinates;

    /**
     * Returns the final stress achieved.
     */
    public double getStress() {
        return stress;
    }

    /**
     * Returns the coordinates of projected data.
     */
    public double[][] getCoordinates() {
        return coordinates;
    }

    /**
     * Constructor. Learn a 2-dimensional Kruskal's non-metric MDS with default
     * tolerance = 1E-4 and maxIter = 200.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     */
    public IsotonicMDS(double[][] proximity) {
        this(proximity, 2);
    }

    /**
     * Constructor. Learn Kruskal's non-metric MDS with default
     * tolerance = 1E-4 and maxIter = 200.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param k the dimension of the projection.
     */
    public IsotonicMDS(double[][] proximity, int k) {
        this(proximity, k, 1E-4, 200);
    }

    /**
     * Constructor. Learn Kruskal's non-metric MDS with default
     * tolerance = 1E-4 and maxIter = 100.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param coordinates the initial projected coordinates, of which the column
     * size is the projection dimension.
     */
    public IsotonicMDS(double[][] proximity, double[][] coordinates) {
        this(proximity, coordinates, 1E-4, 200);
    }

    /**
     * Constructor. Learn Kruskal's non-metric MDS.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param k the dimension of the projection.
     * @param tol tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     */
    public IsotonicMDS(double[][] proximity, int k, double tol, int maxIter) {
        this(proximity, new MDS(proximity, k).getCoordinates(), tol, maxIter);
    }

    /**
     * Constructor. Learn Kruskal's non-metric MDS.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param init the initial projected coordinates, of which the column
     * size is the projection dimension.
     * @param tol tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     */
    public IsotonicMDS(double[][] proximity, double[][] init, double tol, int maxIter) {
        if (proximity.length != proximity[0].length) {
            throw new IllegalArgumentException("The proximity matrix is not square.");
        }

        if (proximity.length != init.length) {
            throw new IllegalArgumentException("The proximity matrix and the initial coordinates are of different size.");
        }

        coordinates = Math.clone(init);
        int nr = proximity.length;
        int nc = coordinates[0].length;

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
                x[l] = coordinates[i][j];
            }
        }

        int[] ord = QuickSort.sort(d);
        int[] ord2 = QuickSort.sort(ord.clone());

        ObjectiveFunction func = new ObjectiveFunction(nr, nc, d, ord, ord2);

        stress = 0.0;
        try {
            stress = Math.min(func, 5, x, tol, maxIter);
        } catch (Exception ex) {
            // If L-BFGS doesn't work, let's try BFGS.
            stress = Math.min(func, x, tol, maxIter);
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

        coordinates = new double[nr][nc];
        for (int i = 0, l = 0; i < nr; i++) {
            for (int j = 0; j < nc; j++, l++) {
                coordinates[i][j] = x[l];
            }
        }
    }

    /**
     * Isotonic regression.
     */
    static class ObjectiveFunction implements DifferentiableMultivariateFunction {

        int[] ord;		/* ranks of dissimilarities */

        int[] ord2;		/* inverse ordering (which one is rank i?) */

        int n;			/* number of  dissimilarities */

        int nr;			/* number of data points */

        int nc;			/* # cols of  fitted configuration */

        int dimx;		/* Size of configuration array */

        double[] d;		/* dissimilarities */

        double[] y;		/* fitted distances (in rank of d order) */

        double[] yc;		/* cumulative fitted distances (in rank of d order) */

        double[] yf;		/* isotonic regression fitted values (ditto) */


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
                        tmp += Math.sqr(x[i * nc + c] - x[j * nc + c]);
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
        public double f(double[] x, double[] g) {
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
