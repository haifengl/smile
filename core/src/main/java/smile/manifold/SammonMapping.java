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
package smile.manifold;

import java.util.Arrays;
import java.util.Properties;
import smile.math.MathEx;
import smile.util.IterativeAlgorithmController;

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
 * mapping algorithm is to preserve the topology as good as possible by giving
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
 * @see smile.manifold.KPCA
 *
 * @param stress the objective function value.
 * @param coordinates the principal coordinates
 * @author Haifeng Li
 */
public record SammonMapping(double stress, double[][] coordinates) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SammonMapping.class);

    /**
     * Training status per epoch.
     * @param epoch the iteration index, starting at 0.
     * @param stress the objective function value.
     * @param step the step size in diagonal Newton method.
     */
    public record TrainingStatus(int epoch, double stress, double step) {

    }

    /**
     * Sammon's mapping hyperparameters.
     * @param d the dimension of the projection.
     * @param maxIter the maximum number of iterations.
     * @param tol the tolerance on objective function for stopping iterations.
     * @param step the initial step size in diagonal Newton method.
     * @param stepTol the tolerance on step size.
     */
    public record Options(int d, int maxIter, double tol, double step, double stepTol,
                          IterativeAlgorithmController<TrainingStatus> controller) {
        /** Constructor. */
        public Options {
            if (d < 2) {
                throw new IllegalArgumentException("Invalid dimension of feature space: " + d);
            }

            if (maxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
            }

            if (tol <= 0.0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }

            if (step < 0.0) {
                throw new IllegalArgumentException("Invalid step size: " + step);
            }

            if (stepTol <= 0.0) {
                throw new IllegalArgumentException("Invalid step size tolerance: " + stepTol);
            }
        }

        /**
         * Constructor.
         * @param d the dimension of the projection.
         * @param maxIter maximum number of iterations.
         */
        public Options(int d, int maxIter) {
            this(d, maxIter, 1E-4, 0.2, 1E-3, null);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.sammon.d", Integer.toString(d));
            props.setProperty("smile.sammon.iterations", Integer.toString(maxIter));
            props.setProperty("smile.sammon.tolerance", Double.toString(tol));
            props.setProperty("smile.sammon.step", Double.toString(step));
            props.setProperty("smile.sammon.step_tolerance", Double.toString(stepTol));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int d = Integer.parseInt(props.getProperty("smile.sammon.d", "2"));
            int maxIter = Integer.parseInt(props.getProperty("smile.sammon.iterations", "100"));
            double tol = Double.parseDouble(props.getProperty("smile.sammon.tolerance", "1E-4"));
            double step = Double.parseDouble(props.getProperty("smile.sammon.step", "0.2"));
            double stepTol = Double.parseDouble(props.getProperty("smile.sammon.step_tolerance", "1E-3"));
            return new Options(d, maxIter, tol, step, stepTol, null);
        }
    }

    /**
     * Fits Sammon's mapping with default d = 2, step = 0.2, tolerance = 1E-4 and maxIter = 100.
     * @param proximity the non-negative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @return the model.
     */
    public static SammonMapping fit(double[][] proximity) {
        return fit(proximity, new Options(2, 100));
    }

    /**
     * Fits Sammon's mapping.
     * @param proximity the non-negative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and symmetric.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static SammonMapping fit(double[][] proximity, Options options) {
        MDS mds = MDS.fit(proximity, new MDS.Options(options.d, false));
        return fit(proximity, mds.coordinates(), options);
    }

    /**
     * Fits Sammon's mapping.
     * @param proximity the non-negative proximity matrix of dissimilarities.
     *                  The diagonal should be zero and all other elements
     *                  should be positive and symmetric.
     * @param coordinates the initial projected coordinates, of which the column
     *             size is the projection dimension. It will be modified.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static SammonMapping fit(double[][] proximity, double[][] coordinates, Options options) {
        if (proximity.length != proximity[0].length) {
            throw new IllegalArgumentException("The proximity matrix is not square.");
        }

        if (proximity.length != coordinates.length) {
            throw new IllegalArgumentException("The proximity matrix and the initial coordinates are of different size.");
        }

        int n = proximity.length;
        double c = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                c += proximity[i][j];
            }
        }

        int d = options.d;
        double[][] xu = new double[n][d];

        double stress = stress(proximity, coordinates);
        stress /= c;

        double epast = stress;
        double eprev = stress;
        double[] xv = new double[d];
        double[] e1 = new double[d];
        double[] e2 = new double[d];

        double step = options.step;
        double tol = options.tol;
        double stepTol = options.stepTol;
        int maxIter = options.maxIter;

        logger.info("Initial stress: {}", stress);
        if (options.controller != null) {
            options.controller.submit(new TrainingStatus(0, stress, step));
        }

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
                    for (int l = 0; l < d; l++) {
                        double xd = ri[l] - rj[l];
                        rij += xd * xd;
                        xv[l] = xd;
                    }
                    rij = Math.sqrt(rij);
                    if (rij == 0.0) rij = 1.0E-10;

                    double dq = dij - rij;
                    double dr = dij * rij;
                    for (int l = 0; l < d; l++) {
                        e1[l] += xv[l] * dq / dr;
                        e2[l] += (dq - xv[l] * xv[l] * (1.0 + dq / rij) / rij) / dr;
                    }
                }

                // Correction
                for (int l = 0; l < d; l++) {
                    xu[i][l] = ri[l] + step * e1[l] / Math.abs(e2[l]);
                }
            }

            stress = stress(proximity, xu);
            stress /= c;

            if (stress > eprev) {
                stress = eprev;
                step = step * 0.2;
                if (step < stepTol) {
                    logger.info("Early stops after {} iterations: {}, step = {}", iter, stress, step);
                    break;
                } else {
                    logger.info("Decreases step size = {}", step);
                    iter--;
                    continue;
                }
            }

            step = Math.min(0.5, step * 1.5);
            eprev = stress;

            // Move the centroid to origin and update
            double[] mu = MathEx.colMeans(xu);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < d; j++) {
                    coordinates[i][j] = xu[i][j] - mu[j];
                }
            }

            if (iter % 10 == 0) {
                logger.info("Stress after {} iterations: {}, step = {}", iter, stress, step);
                if (stress > epast - tol) break;
                epast = stress;
            }

            if (options.controller != null) {
                options.controller.submit(new TrainingStatus(iter, stress, step));
                if (options.controller.isInterrupted()) break;
            }
        }

        return new SammonMapping(stress, coordinates);
    }

    /**
     * Computes the unnormalized objective function.
     * @param proximity the non-negative proximity matrix of dissimilarities.
     * @param coordinates the projected coordinates.
     * @return the stress value.
     */
    private static double stress(double[][] proximity, double[][] coordinates) {
        double stress = 0.0;
        int n = proximity.length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dij = proximity[i][j];
                if (dij == 0.0) dij = 1.0E-10;
                double rij = MathEx.distance(coordinates[i], coordinates[j]);
                stress += MathEx.pow2(dij - rij) / dij;
            }
        }
        return stress;
    }
}
