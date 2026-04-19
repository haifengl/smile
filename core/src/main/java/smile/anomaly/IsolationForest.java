/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.anomaly;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.math.MathEx;

/**
 * Isolation forest is an unsupervised learning algorithm for anomaly
 * detection that works on the principle of isolating anomalies.
 * The algorithm recursively generates partitions on the sample by
 * randomly selecting an attribute and then randomly selecting a split
 * value for the attribute, between the minimum and maximum values
 * allowed for that attribute. The recursive partitioning can be
 * represented by a tree structure named Isolation Tree. The number
 * of partitions required to isolate a point can be interpreted as
 * the length of the path, within the tree, to reach a terminating
 * node starting from the root. When the forest of isolation trees
 * collectively produces shorter path lengths for some samples,
 * they are likely to be anomalies.
 * <p>
 * Rather than selecting a random feature and value within the range
 * of data, Extended isolation forest slices the data using hyperplanes
 * with random slopes. The consistency and reliability of the algorithm
 * is much improved using this extension.
 * <p>
 * For an N dimensional dataset, we can consider N levels of extension.
 * In the fully extended case, we select our normal vector by drawing
 * each component from {@code N (0, 1)} as seen before. This results
 * in hyperplanes that can intersect any of the coordinates axes.
 * However, we can exclude some dimensions in specifying the lines
 * so that they are parallel to the coordinate axes. This is simply
 * accomplished by setting a coordinate of the normal vector to zero.
 * The lowest level of extension of the Extended Isolation Forest
 * is coincident with the standard Isolation Forest. As the extension
 * level of the algorithm is increased, the bias of the algorithm is
 * reduced. The idea of having multiple levels of extension can be useful
 * in cases where the dynamic range of the data in various dimensions are
 * very different. In such cases, reducing the extension level can help in
 * more appropriate selection of split hyperplanes.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Fei Liu, Kai Ming Ting, and Zhi-Hua Zhou. Isolation Forest. ICDM, 413–422, 2008.</li>
 * <li> Sahand Hariri, Matias Carrasco Kind, and Robert J. Brunner. Extended Isolation Forest. IEEE Transactions on Knowledge and Data Engineering, 2019.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class IsolationForest implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IsolationForest.class);

    /**
     * Euler constant.
     */
    private static final double EULER = 0.5772156649;
    /**
     * Forest of isolation trees.
     */
    private final IsolationTree[] trees;
    /**
     * The normalizing factor.
     */
    private final double c;
    /**
     * The extension level, i.e. how many dimension are specified
     * in the random slope. {@code 0} is coincident with the standard
     * Isolation Forest. Larger values increase extension.
     */
    private final int extensionLevel;
    /**
     * The dimensionality of input samples.
     */
    private final int p;

    /**
     * Constructor.
     *
     * @param n the number of samples to train the forest.
     * @param p the dimensionality of input samples.
     * @param extensionLevel the extension level, i.e. how many dimension
     *                       are specified in the random slope.
     *                       {@code 0} means standard Isolation Forest.
     * @param trees forest of isolation trees.
     */
    public IsolationForest(int n, int p, int extensionLevel, IsolationTree... trees) {
        if (n < 2) {
            throw new IllegalArgumentException("Too few training samples: " + n);
        }
        if (p < 1) {
            throw new IllegalArgumentException("Invalid dimensionality: " + p);
        }
        if (trees == null || trees.length == 0) {
            throw new IllegalArgumentException("No isolation trees provided");
        }

        this.trees = trees;
        this.extensionLevel = extensionLevel;
        this.p = p;
        this.c = factor(n);
    }

    /**
     * Isolation Forest hyperparameters.
     * @param ntrees the number of trees.
     * @param maxDepth the maximum depth of the tree.
     * @param subsample the sampling rate for training tree.
     * @param extensionLevel the extension level. {@code 0} means standard
     *                       Isolation Forest. Valid range is {@code [0, p-1]},
     *                       where {@code p} is input dimensionality.
     */
    public record Options(int ntrees, int maxDepth, double subsample, int extensionLevel) {
        /** Constructor. */
        public Options {
            if (ntrees < 1) {
                throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
            }

            if (subsample <= 0 || subsample >= 1) {
                throw new IllegalArgumentException("Invalid sampling rating: " + subsample);
            }

            if (extensionLevel < 0) {
                throw new IllegalArgumentException("Invalid extension level: " + extensionLevel);
            }
        }

        /** Constructor. */
        public Options() {
            this(100, 0, 0.7, 0);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.isolation_forest.trees", Integer.toString(ntrees));
            props.setProperty("smile.isolation_forest.max_depth", Integer.toString(maxDepth));
            props.setProperty("smile.isolation_forest.sampling_rate", Double.toString(subsample));
            props.setProperty("smile.isolation_forest.extension_level", Integer.toString(extensionLevel));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int ntrees = Integer.parseInt(props.getProperty("smile.isolation_forest.trees", "100"));
            int maxDepth = Integer.parseInt(props.getProperty("smile.isolation_forest.max_depth", "0"));
            double subsample = Double.parseDouble(props.getProperty("smile.isolation_forest.sampling_rate", "0.7"));
            int extensionLevel = Integer.parseInt(props.getProperty("smile.isolation_forest.extension_level", "0"));
            return new Options(ntrees, maxDepth, subsample, extensionLevel);
        }
    }

    /**
     * Fits an isolation forest.
     *
     * @param data the training data.
     *             When using default options, this fits standard Isolation Forest
     *             ({@code extensionLevel = 0}).
     * @return the model.
     */
    public static IsolationForest fit(double[][] data) {
        return fit(data, new Options());
    }

    /**
     * Fits an isolation forest.
     *
     * @param data the training data.
     * @param options the hyperparameters. In particular,
     *                {@code options.extensionLevel()} controls extension level.
     *                {@code 0} means standard Isolation Forest.
     * @return the model.
     */
    public static IsolationForest fit(double[][] data, Options options) {
        if (data == null || data.length < 2) {
            throw new IllegalArgumentException("IsolationForest requires at least 2 samples");
        }
        if (options == null) {
            throw new IllegalArgumentException("options is null");
        }

        int p = data[0].length;
        if (p == 0) {
            throw new IllegalArgumentException("Input dimensionality is zero");
        }
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null) {
                throw new IllegalArgumentException("Sample at index " + i + " is null");
            }
            if (data[i].length != p) {
                throw new IllegalArgumentException("Invalid input dimension: expected " + p + ", actual " + data[i].length);
            }
        }

        int extensionLevel = options.extensionLevel;
        if (extensionLevel >= p) {
            throw new IllegalArgumentException("Invalid extension level: " + extensionLevel);
        }

        final int n = data.length;
        final int m = Math.max(2, (int) Math.round(n * options.subsample));
        final int depth = options.maxDepth > 0 ? options.maxDepth : (int) MathEx.log2(m);

        IsolationTree[] trees = IntStream.range(0, options.ntrees).parallel().mapToObj(k -> {
            ArrayList<double[]> samples = new ArrayList<>(m);
            int[] permutation = MathEx.permutate(n);
            for (int i = 0; i < m; i++) {
                int j = permutation[i];
                samples.add(data[j]);
            }


            return new IsolationTree(samples, depth, extensionLevel);
        }).toArray(IsolationTree[]::new);

        return new IsolationForest(n, p, extensionLevel, trees);
    }

    /**
     * Returns the number of trees in the model.
     *
     * @return the number of trees in the model.
     */
    public int size() {
        return trees.length;
    }

    /**
     * Returns the isolation trees in the model.
     *
     * @return the isolation trees in the model.
     */
    public IsolationTree[] trees() {
        return trees.clone();
    }

    /**
     * Returns the extension level. {@code 0} means standard Isolation Forest.
     * Valid range is {@code [0, p-1]}, where {@code p} is input dimensionality.
     *
     * @return the extension level.
     */
    public int extensionLevel() {
        return extensionLevel;
    }

    /**
     * Returns the anomaly score in the range {@code (0, 1]}.
     * Scores closer to 1 indicate anomalies; scores around 0.5 are normal.
     * Specifically, a score significantly above 0.5 (e.g., {@code > 0.6})
     * suggests an anomaly, while a score well below 0.5 suggests a normal instance.
     *
     * @param x the sample.
     * @return the anomaly score in {@code (0, 1]}.
     */
    public double score(double[] x) {
        if (x == null) {
            throw new IllegalArgumentException("Input sample is null");
        }
        if (x.length != p) {
            throw new IllegalArgumentException("Invalid input dimension: expected " + p + ", actual " + x.length);
        }

        double length = 0.0;
        for (IsolationTree tree : trees) {
            length += tree.path(x);
        }

        length /= trees.length;
        return Math.pow(2.0, -length/c);
    }

    /**
     * Returns the anomaly scores.
     *
     * @param x the samples.
     * @return the anomaly scores in {@code (0, 1]}.
     */
    public double[] score(double[][] x) {
        return Arrays.stream(x).parallel().mapToDouble(this::score).toArray();
    }

    /**
     * Predicts whether a sample is an anomaly using the given threshold.
     *
     * @param x the sample.
     * @param threshold the anomaly score threshold. A sample is considered
     *                  an anomaly if its score exceeds this value.
     *                  Typical values are in the range {@code (0.5, 1.0)}.
     * @return {@code true} if the sample is predicted as an anomaly.
     */
    public boolean predict(double[] x, double threshold) {
        return score(x) > threshold;
    }

    /**
     * Returns the normalizing factor defined as the average depth
     * in an unsuccessful search in a binary search tree.
     *
     * @param n the number of samples to construct the isolation tree.
     * @return the normalizing factor.
     */
    static double factor(int n) {
        if (n < 2) {
            throw new IllegalArgumentException("n must be >= 2: " + n);
        }
        return 2.0 * ((Math.log(n-1) + EULER) - (n - 1.0) / n);
    }
}
