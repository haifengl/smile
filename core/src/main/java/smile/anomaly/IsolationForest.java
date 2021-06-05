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

package smile.anomaly;

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
     * in the random slope. With 0 extension level, it is coincident
     * with the standard Isolation Forest.
     */
    private final int extensionLevel;

    /**
     * Constructor.
     *
     * @param n the number of samples to train the forest.
     * @param extensionLevel the extension level, i.e. how many dimension
     *                       are specified in the random slope.
     * @param trees forest of isolation trees.
     */
    public IsolationForest(int n, int extensionLevel, IsolationTree... trees) {
        this.trees = trees;
        this.extensionLevel = extensionLevel;
        this.c = factor(n);
    }

    /**
     * Fits an isolation forest.
     *
     * @param data the training data.
     * @return the model.
     */
    public static IsolationForest fit(double[][] data) {
        return fit(data, new Properties());
    }

    /**
     * Fits a random forest for classification.
     *
     * @param data the training data.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static IsolationForest fit(double[][] data, Properties params) {
        int ntrees = Integer.parseInt(params.getProperty("smile.isolation_forest.trees", "100"));
        double subsample = Double.parseDouble(params.getProperty("smile.isolation_forest.sampling_rate", "0.7"));

        int maxDepth = (int) MathEx.log2(data.length);
        String depth = params.getProperty("smile.isolation_forest.max_depth");
        if (depth != null) {
            maxDepth = Integer.parseInt(depth);
        }

        int extensionLevel = data[0].length - 1;
        String level = params.getProperty("smile.isolation_forest.extension_level");
        if (level != null) {
            extensionLevel = Integer.parseInt(level);
        }

        return fit(data, ntrees, maxDepth, subsample, extensionLevel);
    }

    /**
     * Fits a random forest for classification.
     *
     * @param data the training data.
     * @param ntrees the number of trees.
     * @param maxDepth the maximum depth of the tree.
     * @param subsample the sampling rate for training tree.
     * @param extensionLevel the extension level.
     * @return the model.
     */
    public static IsolationForest fit(double[][] data, int ntrees, int maxDepth, double subsample, int extensionLevel) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (subsample <= 0 || subsample >= 1) {
            throw new IllegalArgumentException("Invalid sampling rating: " + subsample);
        }

        if (extensionLevel < 0 || extensionLevel > data[0].length - 1) {
            throw new IllegalArgumentException("Invalid extension level: " + extensionLevel);
        }

        final int n = data.length;
        final int m = (int) Math.round(n * subsample);

        IsolationTree[] trees = IntStream.range(0, ntrees).parallel().mapToObj(k -> {
            ArrayList<double[]> samples = new ArrayList<>(m);
            for (int i : MathEx.permutate(n)) {
                samples.add(data[i]);
            }

            return new IsolationTree(samples, maxDepth, extensionLevel);
        }).toArray(IsolationTree[]::new);

        return new IsolationForest(n, extensionLevel, trees);
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
        return trees;
    }

    /**
     * Returns the extension level.
     * @return the extension level.
     */
    public int getExtensionLevel() {
        return extensionLevel;
    }

    /**
     * Returns the anomaly score.
     *
     * @param x the sample.
     * @return the anomaly score.
     */
    public double score(double[] x) {
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
     * @return the anomaly scores.
     */
    public double[] score(double[][] x) {
        return Arrays.stream(x).parallel().mapToDouble(this::score).toArray();
    }

    /**
     * Returns the normalizing factor defined as the average depth
     * in an unsuccessful search in a binary search tree.
     *
     * @param n the number of samples to construct the isolation tree.
     * @return the normalizing factor.
     */
    static double factor(int n) {
        return 2.0 * ((Math.log(n-1) + EULER) - (n - 1.0) / n);
    }
}
