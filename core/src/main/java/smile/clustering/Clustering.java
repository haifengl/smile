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
package smile.clustering;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import smile.util.AlgoStatus;
import smile.util.IterativeAlgorithmController;

/**
 * Clustering utility functions.
 *
 * @author Haifeng Li
 */
public interface Clustering {
    /**
     * Cluster label for outliers or noises.
     */
    int OUTLIER = Integer.MAX_VALUE;

    /**
     * Iterative clustering algorithm hyperparameters.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @param tol the tolerance of convergence test.
     * @param controller the optional training controller.
     */
    record Options(int k, int maxIter, double tol,
                   IterativeAlgorithmController<AlgoStatus> controller) {
        /** Constructor. */
        public Options {
            if (k < 2) {
                throw new IllegalArgumentException("Invalid number of clusters: " + k);
            }

            if (maxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
            }

            if (tol < 0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }
        }

        /**
         * Constructor.
         * @param k the number of clusters.
         * @param maxIter the maximum number of iterations.
         */
        public Options(int k, int maxIter) {
            this(k, maxIter, 1E-4, null);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.clustering.k", Integer.toString(k));
            props.setProperty("smile.clustering.iterations", Integer.toString(maxIter));
            props.setProperty("smile.clustering.tolerance", Double.toString(tol));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int k = Integer.parseInt(props.getProperty("smile.clustering.k", "2"));
            int maxIter = Integer.parseInt(props.getProperty("smile.clustering.iterations", "100"));
            double tol = Double.parseDouble(props.getProperty("smile.clustering.tolerance", "1E-4"));
            return new Options(k, maxIter, tol, null);
        }
    }

    /**
     * Runs a clustering algorithm 10 times and return the best one
     * (e.g. smallest distortion).
     * @param clustering the clustering algorithm.
     * @param <T> the type of clustering.
     * @return the model.
     */
    static <T extends Comparable<? super T>> T run(Supplier<T> clustering) {
        return run(clustering, 10, false);
    }

    /**
     * Runs a clustering algorithm multiple times and return the best one
     * (e.g. smallest distortion).
     * @param clustering the clustering algorithm.
     * @param runs the number of runs.
     * @param parallel true if running the algorithms in parallel.
     * @param <T> the type of clustering.
     * @return the model.
     */
    static <T extends Comparable<? super T>> T run(Supplier<T> clustering, int runs, boolean parallel) {
        if (runs <= 0) {
            throw new IllegalArgumentException("Invalid number of runs: " + runs);
        }

        var stream = IntStream.range(0, runs);
        if (parallel) {
            stream = stream.parallel();
        }

        return stream.mapToObj(run -> clustering.get())
                .min(Comparator.naturalOrder())
                .orElseThrow(NoSuchElementException::new);
    }
}
