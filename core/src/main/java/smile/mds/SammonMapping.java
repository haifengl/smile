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

import java.util.Arrays;
import java.util.Properties;

import smile.math.MathEx;

/**
 * The Sammon's mapping is an iterative technique for making interpoint
 * distances in the low-dimensional projection as close as possible to the
 * interpoint distances in the high-dimensional object. Two points close
 * together in the high-dimensional space should appear close together in the
 * projection, while two points far apart in the high dimensional space should
 * appear far apart in the projection. The Sammon's mapping is a special case of
 * metric least-square multidimensional scaling.
 * <p>
 * Ideally when we project from a high dimensional space to a low dimensional
 * space the image would be geometrically congruent to the original figure.
 * This is called an isometric projection. Unfortunately it is rarely possible
 * to isometrically project objects down into lower dimensional spaces. Instead of
 * trying to achieve equality between corresponding inter-point distances we
 * can minimize the difference between corresponding inter-point distances.
 * This is one goal of the Sammon's mapping algorithm. A second goal of the Sammon's
 * mapping algorithm is to preserve the topology as best as possible by giving
 * greater emphasize to smaller interpoint distances. The Sammon's mapping
 * algorithm has the advantage that whenever it is possible to isometrically
 * project an object into a lower dimensional space it will be isometrically
 * projected into the lower dimensional space. But whenever an object cannot
 * be projected down isometrically the Sammon's mapping projects it down to reduce
 * the distortion in interpoint distances and to limit the change in the
 * topology of the object.
 * <p>
 * The projection cannot be solved in a closed form and may be found by an
 * iterative algorithm such as gradient descent suggested by Sammon. Kohonen
 * also provides a heuristic that is simple and works reasonably well.
 *
 * @see MDS
 * @see smile.projection.KPCA
 *
 * @author Haifeng Li
 */
public class SammonMapping {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SammonMapping.class);

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
    public SammonMapping(double stress, double[][] coordinates) {
        this.stress = stress;
        this.coordinates = coordinates;
    }

    /**
     * Fits Sammon's mapping with default k = 2, lambda = 0.2, tolerance = 1E-4 and maxIter = 100.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     */
    public static SammonMapping of(double[][] proximity) {
        return of(proximity, new Properties());
    }

    /**
     * Fits Sammon's mapping.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param k the dimension of the projection.
     */
    public static SammonMapping of(double[][] proximity, int k) {
        return of(proximity, k, 0.2, 1E-4, 1E-3, 100);
    }

    /**
     * Fits Sammon's mapping.
     *
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     */
    public static SammonMapping of(double[][] proximity, Properties prop) {
        int k = Integer.valueOf(prop.getProperty("smile.sammon.k", "2"));
        double lambda = Double.valueOf(prop.getProperty("smile.sammon.lambda", "0.2"));
        double tol = Double.valueOf(prop.getProperty("smile.sammon.tolerance", "1E-4"));
        double stepTol = Double.valueOf(prop.getProperty("smile.sammon.step.tolerance", "1E-3"));
        int maxIter = Integer.valueOf(prop.getProperty("smile.sammon.max.iterations", "100"));
        return of(proximity, k, lambda, tol, stepTol, maxIter);
    }

    /**
     * Fits Sammon's mapping.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param k the dimension of the projection.
     * @param lambda initial value of the step size constant in diagonal Newton method.
     * @param tol the tolerance on objective function for stopping iterations.
     * @param stepTol the tolerance on step size.
     * @param maxIter maximum number of iterations.
     */
    public static SammonMapping of(double[][] proximity, int k, double lambda, double tol, double stepTol, int maxIter) {
        Properties prop = new Properties();
        prop.setProperty("smile.mds.k", String.valueOf(k));
        return of(proximity, MDS.of(proximity, prop).coordinates, lambda, tol, stepTol, maxIter);
    }

    /**
     * Fits Sammon's mapping.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param init the initial projected coordinates, of which the column
     * size is the projection dimension. It will be modified.
     * @param lambda initial value of the step size constant in diagonal Newton method.
     * @param tol the tolerance for stopping iterations.
     * @param stepTol the tolerance on step size.
     * @param maxIter maximum number of iterations.
     */
    public static SammonMapping of(double[][] proximity, double[][] init, double lambda, double tol, double stepTol, int maxIter) {
        if (proximity.length != proximity[0].length) {
            throw new IllegalArgumentException("The proximity matrix is not square.");
        }

        if (proximity.length != init.length) {
            throw new IllegalArgumentException("The proximity matrix and the initial coordinates are of different size.");
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);            
        }
        
        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);            
        }
        
        int m = proximity.length;
        int n = proximity[0].length;

        if (m != n) {
            throw new IllegalArgumentException("The proximity matrix is not square.");
        }

        double[][] coordinates = init;
        
        double c = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                c += proximity[i][j];
            }
        }

        int k = coordinates[0].length;
        double[][] xu = new double[n][k];

        double stress = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dij = proximity[i][j];
                if (dij == 0.0) dij = 1.0E-10;
                double rij = MathEx.distance(coordinates[i], coordinates[j]);
                stress += MathEx.sqr(dij - rij) / dij;
            }
        }
        stress /= c;
        double epast = stress;
        double eprev = stress;
        logger.info(String.format("Sammon's Mapping initial stress: %.5f", stress));

        double[] xv = new double[k];
        double[] e1 = new double[k];
        double[] e2 = new double[k];

        for (int iter = 1; iter <= maxIter; iter++) {
            for (int i = 0; i < n; i++) {
                double[] ri = coordinates[i];

                Arrays.fill(e1, 0.0);
                Arrays.fill(e2, 0.0);

                for (int j = 0; j < n; j++) {
                    if (i == j) {
                        continue;
                    }

                    double[] rj = coordinates[j];

                    double dij = proximity[i][j];
                    if (dij == 0.0) dij = 1.0E-10;

                    double rij = 0.0;
                    for (int l = 0; l < k; l++) {
                        double xd = ri[l] - rj[l];
                        rij += xd * xd;
                        xv[l] = xd;
                    }
                    rij = Math.sqrt(rij);
                    if (rij == 0.0) rij = 1.0E-10;

                    double dq = dij - rij;
                    double dr = dij * rij;
                    for (int l = 0; l < k; l++) {
                        e1[l] += xv[l] * dq / dr;
                        e2[l] += (dq - xv[l] * xv[l] * (1.0 + dq / rij) / rij) / dr;
                    }
                }

                // Correction
                for (int l = 0; l < k; l++) {
                    xu[i][l] = ri[l] + lambda * e1[l] / Math.abs(e2[l]);
                }
            }

            stress = 0.0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dij = proximity[i][j];
                    if (dij == 0.0) dij = 1.0E-10;
                    double rij = MathEx.distance(xu[i], xu[j]);
                    stress += MathEx.sqr(dij - rij) / dij;
                }
            }
            stress /= c;

            if (stress > eprev) {
                stress = eprev;
                lambda = lambda * 0.2;
                if (lambda < stepTol) {
                    logger.info(String.format("Sammon's Mapping stops early as stress = %.5f after %d iterations", stress, iter-1));
                    break;
                }
                iter--;
            } else {
                lambda *= 1.5;
                if (lambda > 0.5) {
                    lambda = 0.5;
                }
                eprev = stress;

                // Move the centroid to origin and update
                double[] mu = MathEx.colMeans(xu);
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < k; j++) {
                        coordinates[i][j] = xu[i][j] - mu[j];
                    }
                }

                if (iter % 10 == 0) {
                    logger.info(String.format("Sammon's Mapping stress after %3d iterations: %.5f, magic = %5.3f", iter, stress, lambda));
                    if (stress > epast - tol) {
                        break;
                    }
                    epast = stress;
                }
            }
        }

        return new SammonMapping(stress, coordinates);
    }
}
