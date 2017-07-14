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

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;

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
    private static final Logger logger = LoggerFactory.getLogger(SammonMapping.class);

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
     * Constructor. Learn a 2-dimensional Sammon's mapping with default lambda = 0.2,
     * tolerance = 1E-4 and maxIter = 100.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     */
    public SammonMapping(double[][] proximity) {
        this(proximity, 2);
    }

    /**
     * Constructor. Learn Sammon's mapping with default lambda = 0.2, tolerance = 1E-4 and maxIter = 100.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param k the dimension of the projection.
     */
    public SammonMapping(double[][] proximity, int k) {
        this(proximity, k, 0.2, 1E-4, 100);
    }

    /**
     * Constructor. Learn Sammon's mapping with default lambda = 0.2, tolerance = 1E-4 and maxIter = 100.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param coordinates the initial projected coordinates, of which the column
     * size is the projection dimension.
     */
    public SammonMapping(double[][] proximity, double[][] coordinates) {
        this(proximity, coordinates, 0.2, 1E-4, 100);
    }

    /**
     * Constructor. Learn Sammon's mapping.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param k the dimension of the projection.
     * @param lambda initial value of the step size constant in diagonal Newton method.
     * @param tol tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     */
    public SammonMapping(double[][] proximity, int k, double lambda, double tol, int maxIter) {
        this(proximity, new MDS(proximity, k).getCoordinates(), lambda, tol, maxIter);
    }

    /**
     * Constructor. Learn Sammon's mapping.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param init the initial projected coordinates, of which the column
     * size is the projection dimension.
     * @param lambda initial value of the step size constant in diagonal Newton method.
     * @param tol tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     */
    public SammonMapping(double[][] proximity, double[][] init, double lambda, double tol, int maxIter) {
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

        coordinates = Math.clone(init);
        
        double c = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                c += proximity[i][j];
            }
        }

        int k = coordinates[0].length;
        double[][] xu = new double[n][k];

        stress = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dij = proximity[i][j];
                if (dij == 0.0) dij = 1.0E-10;
                double rij = Math.distance(coordinates[i], coordinates[j]);
                stress += Math.sqr(dij - rij) / dij;
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
                    double rij = Math.distance(xu[i], xu[j]);
                    stress += Math.sqr(dij - rij) / dij;
                }
            }
            stress /= c;

            if (stress > eprev) {
                stress = eprev;
                lambda = lambda * 0.2;
                if (lambda < 1E-3) {
                    logger.info(String.format("Sammon's Mapping stress after %3d iterations: %.5f", iter-1, stress));
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
                double[] mu = Math.colMeans(xu);
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
    }
}
