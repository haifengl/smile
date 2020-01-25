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

package smile.regression;

import java.util.*;
import java.util.stream.IntStream;
import smile.base.cart.*;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.math.MathEx;

/**
 * Decision tree for regression. A decision tree can be learned by
 * splitting the training set into subsets based on an attribute value
 * test. This process is repeated on each derived subset in a recursive
 * manner called recursive partitioning.
 * <p>
 * Classification and Regression Tree techniques have a number of advantages
 * over many of those alternative techniques.
 * <dl>
 * <dt>Simple to understand and interpret.</dt>
 * <dd>In most cases, the interpretation of results summarized in a tree is
 * very simple. This simplicity is useful not only for purposes of rapid
 * classification of new observations, but can also often yield a much simpler
 * "model" for explaining why observations are classified or predicted in a
 * particular manner.</dd>
 * <dt>Able to handle both numerical and categorical data.</dt>
 * <dd>Other techniques are usually specialized in analyzing datasets that
 * have only one type of variable. </dd>
 * <dt>Tree methods are nonparametric and nonlinear.</dt>
 * <dd>The final results of using tree methods for classification or regression
 * can be summarized in a series of (usually few) logical if-then conditions
 * (tree nodes). Therefore, there is no implicit assumption that the underlying
 * relationships between the predictor variables and the dependent variable
 * are linear, follow some specific non-linear link function, or that they
 * are even monotonic in nature. Thus, tree methods are particularly well
 * suited for data mining tasks, where there is often little a priori
 * knowledge nor any coherent set of theories or predictions regarding which
 * variables are related and how. In those types of data analytics, tree
 * methods can often reveal simple relationships between just a few variables
 * that could have easily gone unnoticed using other analytic techniques.</dd>
 * </dl>
 * One major problem with classification and regression trees is their high
 * variance. Often a small change in the data can result in a very different
 * series of splits, making interpretation somewhat precarious. Besides,
 * decision-tree learners can create over-complex trees that cause over-fitting.
 * Mechanisms such as pruning are necessary to avoid this problem.
 * Another limitation of trees is the lack of smoothness of the prediction
 * surface.
 * <p>
 * Some techniques such as bagging, boosting, and random forest use more than
 * one decision tree for their analysis.
 *
 * @author Haifeng Li
 * @see GradientTreeBoost
 * @see RandomForest
 */
public class RegressionTree extends CART implements Regression<Tuple>, DataFrameRegression {
    private static final long serialVersionUID = 2L;

    /** The dependent variable. */
    private transient double[] y;

    /**
     * The loss function.
     */
    private transient Loss loss;

    @Override
    protected double impurity(LeafNode node) {
        return ((RegressionNode) node).impurity();
    }

    @Override
    protected LeafNode newNode(int[] nodeSamples) {
        // The output of node may be different from the sample mean.
        // In fact, it may be based on different data from the response
        // in gradient tree boosting.
        double out = loss.output(nodeSamples, samples);

        // RSS computation should always based on the sample mean in the node.
        double mean = out;
        if (!loss.toString().equals("LeastSquares")) {
            int n = 0;
            mean = 0.0;
            for (int i : nodeSamples) {
                n += samples[i];
                mean += y[i] * samples[i];
            }

            mean /= n;
        }

        int n = 0;
        double rss = 0.0;
        for (int i : nodeSamples) {
            n += samples[i];
            rss += samples[i] * MathEx.sqr(y[i] - mean);
        }

        return new RegressionNode(n, out, mean, rss);
    }

    @Override
    protected Optional<Split> findBestSplit(LeafNode leaf, int j, double impurity, int lo, int hi) {
        RegressionNode node = (RegressionNode) leaf;
        BaseVector xj = x.column(j);

        double sum = IntStream.range(lo, hi).map(i -> index[i]).mapToDouble(i -> y[i] * samples[i]).sum();
        double nodeMeanSquared = node.size() * node.mean() * node.mean();

        Split split = null;
        double splitScore = 0.0;
        int splitTrueCount = 0;
        int splitFalseCount = 0;

        Measure measure = schema.field(j).measure;
        if (measure instanceof NominalScale) {
            int splitValue = -1;
            NominalScale scale = (NominalScale) measure;
            int m = scale.size();
            int[] trueCount = new int[m];
            double[] trueSum = new double[m];

            for (int i = lo; i < hi; i++) {
                int o = index[i];
                int idx = xj.getInt(o);
                trueCount[idx] += samples[o];
                trueSum[idx] += y[o] * samples[o];
            }

            for (int l : scale.values()) {
                int tc = trueCount[l];
                int fc = node.size() - tc;

                // If either side is too small, skip this value.
                if (tc < nodeSize || fc < nodeSize) {
                    continue;
                }

                // compute penalized means
                double trueMean = trueSum[l] / tc;
                double falseMean = (sum - trueSum[l]) / fc;

                double gain = (tc * trueMean * trueMean + fc * falseMean * falseMean) - nodeMeanSquared;

                // new best split
                if (gain > splitScore) {
                    splitValue = l;
                    splitTrueCount = tc;
                    splitFalseCount = fc;
                    splitScore = gain;
                }
            }

            if (splitScore > 0.0) {
                final int value = splitValue;
                split = new NominalSplit(leaf, j, splitValue, splitScore, lo, hi, splitTrueCount, splitFalseCount, (int o) -> xj.getInt(o) == value);
            }
        } else {
            double splitValue = 0.0;
            int tc = 0;
            double trueSum = 0.0;
            int[] orderj = order[j];

            int first = orderj[lo];
            double prevx = xj.getDouble(first);

            for (int i = lo; i < hi; i++) {
                int fc = 0;

                int o = orderj[i];
                double xij = xj.getDouble(o);

                if (xij != prevx) {
                    fc = node.size() - tc;
                }

                // If either side is empty, skip this value.
                if (tc >= nodeSize && fc >= nodeSize) {
                    double trueMean = trueSum / tc;
                    double falseMean = (sum - trueSum) / fc;

                    double gain = (tc * trueMean * trueMean + fc * falseMean * falseMean) - nodeMeanSquared;

                    // new best split
                    if (gain > splitScore) {
                        splitValue = (xij + prevx) / 2;
                        splitTrueCount = tc;
                        splitFalseCount = fc;
                        splitScore = gain;
                    }
                }

                prevx = xij;
                trueSum += y[o] * samples[o];
                tc += samples[o];
            }

            if (splitScore > 0.0) {
                final double value = splitValue;
                split = new OrdinalSplit(leaf, j, splitValue, splitScore, lo, hi, splitTrueCount, splitFalseCount, (int o) -> xj.getDouble(o) <= value);
            }
        }

        return Optional.ofNullable(split);
    }

    /**
     * Constructor. Learns a regression tree for AdaBoost and Random Forest.
     * @param x the data frame of the explanatory variable.
     * @param loss the loss function.
     * @param response the metadata of response variable.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the minimum size of leaf nodes.
     * @param mtry the number of input variables to pick to split on at each
     *             node. It seems that sqrt(p) give generally good performance,
     *             where p is the number of variables.
     * @param samples the sample set of instances for stochastic learning.
     *               samples[i] is the number of sampling for instance i.
     * @param order the index of training values in ascending order. Note
     *              that only numeric attributes need be sorted.
     */
    public RegressionTree(DataFrame x, Loss loss, StructField response, int maxDepth, int maxNodes, int nodeSize, int mtry, int[] samples, int[][] order) {
        super(x, response, maxDepth, maxNodes, nodeSize, mtry, samples, order);
        this.loss = loss;
        this.y = loss.response();

        LeafNode node = newNode(IntStream.range(0, x.size()).filter(i -> this.samples[i] > 0).toArray());
        this.root = node;

        Optional<Split> split = findBestSplit(node, 0, index.length, new boolean[x.ncols()]);

        if (maxNodes == Integer.MAX_VALUE) {
            // deep-first split
            split.ifPresent(s -> split(s, null));
        } else {
            // best-first split
            PriorityQueue<Split> queue = new PriorityQueue<>(2 * maxNodes, Split.comparator.reversed());
            split.ifPresent(s -> queue.add(s));

            for (int leaves = 1; leaves < this.maxNodes && !queue.isEmpty(); ) {
                if (split(queue.poll(), queue)) leaves++;
            }
        }

        // merge the sister leaves that produce the same output.
        this.root = this.root.merge();

        clear();
    }

    /**
     * Learns a regression tree.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static RegressionTree fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Learns a regression tree.
     * The hyper-parameters in <code>prop</code> include
     * <ul>
     * <li><code>smile.cart.node.size</code>
     * <li><code>smile.cart.max.nodes</code>
     * </ul>
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param prop Training algorithm hyper-parameters and properties.
     */
    public static RegressionTree fit(Formula formula, DataFrame data, Properties prop) {
        int maxDepth = Integer.valueOf(prop.getProperty("smile.cart.max.depth", "20"));
        int maxNodes = Integer.valueOf(prop.getProperty("smile.cart.max.nodes", String.valueOf(data.size() / 5)));
        int nodeSize = Integer.valueOf(prop.getProperty("smile.cart.node.size", "5"));
        return fit(formula, data, maxDepth, maxNodes, nodeSize);
    }

    /**
     * Learns a regression tree.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the minimum size of leaf nodes.
     */
    public static RegressionTree fit(Formula formula, DataFrame data, int maxDepth, int maxNodes, int nodeSize) {
        DataFrame x = formula.x(data);
        BaseVector y = formula.y(data);
        RegressionTree tree = new RegressionTree(x, Loss.ls(y.toDoubleArray()), y.field(), maxDepth, maxNodes, nodeSize, -1, null, null);
        tree.formula = formula;
        return tree;
    }

    @Override
    public double predict(Tuple x) {
        RegressionNode leaf = (RegressionNode) root.predict(predictors(x));
        return leaf.output();
    }

    /** Returns null if the tree is part of ensemble algorithm. */
    @Override
    public Formula formula() {
        return formula;
    }

    @Override
    public StructType schema() {
        return schema;
    }
}
