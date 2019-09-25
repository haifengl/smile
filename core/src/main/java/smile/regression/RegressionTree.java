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
import smile.classification.Classifier;
import smile.classification.DecisionTree;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
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

    private transient RegressionNodeOutput output;

    @Override
    protected double impurity(LeafNode node) {
        return ((RegressionNode) node).impurity();
    }

    @Override
    protected LeafNode newNode(int[] nodeSamples) {
        double out = output.calculate(nodeSamples, samples);

        int n = 0;
        double rss = 0.0;
        for (int i : nodeSamples) {
            n += samples[i];
            rss += samples[i] * MathEx.sqr(y.getDouble(i) - out);
        }

        return new RegressionNode(n, out, rss);
    }

    @Override
    protected Optional<Split> findBestSplit(LeafNode leaf, int j, double impurity, int lo, int hi) {
        RegressionNode node = (RegressionNode) leaf;
        BaseVector xj = x.column(j);

        double sum = IntStream.range(lo, hi).map(i -> index[i]).mapToDouble(i -> y.getDouble(i) * samples[i]).sum();
        double nodeOutputSquared = node.size() * node.output() * node.output();

        Split split = null;
        double splitScore = 0.0;
        int splitTrueCount = 0;
        int splitFalseCount = 0;

        Measure measure = schema.field(j).measure;
        if (measure != null && measure instanceof NominalScale) {
            int splitValue = -1;
            NominalScale scale = (NominalScale) measure;
            int m = scale.levels().length;
            int[] trueCount = new int[m];
            double[] trueSum = new double[m];

            for (int i = lo; i < hi; i++) {
                int o = index[i];
                int idx = xj.getInt(o);
                trueCount[idx] += samples[o];
                trueSum[idx] += y.getDouble(o) * samples[o];
            }

            for (int l = 0; l < m; l++) {
                int tc = trueCount[l];
                int fc = node.size() - tc;

                // If either side is too small, skip this value.
                if (tc < nodeSize || fc < nodeSize) {
                    continue;
                }

                // compute penalized means
                double trueMean = trueSum[l] / tc;
                double falseMean = (sum - trueSum[l]) / fc;

                double gain = (tc * trueMean * trueMean + fc * falseMean * falseMean) - nodeOutputSquared;

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

                    double gain = (tc * trueMean * trueMean + fc * falseMean * falseMean) - nodeOutputSquared;

                    // new best split
                    if (gain > splitScore) {
                        splitValue = (xij + prevx) / 2;
                        splitTrueCount = tc;
                        splitFalseCount = fc;
                        splitScore = gain;
                    }
                }

                prevx = xij;
                trueSum += y.getDouble(o) * samples[o];
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
     * @param y the response variables.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param mtry the number of input variables to pick to split on at each
     *             node. It seems that sqrt(p) give generally good performance,
     *             where p is the number of variables.
     * @param samples the sample set of instances for stochastic learning.
     *               samples[i] is the number of sampling for instance i.
     * @param order the index of training values in ascending order. Note
     *              that only numeric attributes need be sorted.
     */
    public RegressionTree(DataFrame x, BaseVector y, int nodeSize, int maxNodes, int mtry, int[] samples, int[][] order, RegressionNodeOutput output) {
        super(x, y, nodeSize, maxNodes, mtry, samples, order);

        this.output = output;

        double mean = MathEx.mean(y.toDoubleArray());
        double rss = 0;
        LeafNode node = new RegressionNode(x.nrows(), mean, rss);
        this.root = node;

        Optional<Split> split = findBestSplit(node, 0, index.length, null);

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
        int nodeSize = Integer.parseInt(prop.getProperty("smile.cart.node.size", "5"));
        int maxNodes = Integer.parseInt(prop.getProperty("smile.cart.max.nodes", "6"));
        return fit(formula, data, nodeSize, maxNodes);
    }

    /**
     * Learns a regression tree.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public static RegressionTree fit(Formula formula, DataFrame data, int nodeSize, int maxNodes) {
        DataFrame x = formula.frame(data);
        BaseVector y = formula.response(data);
        RegressionNodeOutput output = new LeastSquaresNodeOutput(y.toDoubleArray());
        return new RegressionTree(x, y, nodeSize, maxNodes, -1, null, null, output);
    }

    /**
     * Returns the variable importance. Every time a split of a node is made
     * on variable the impurity criterion for the two descendent nodes is less
     * than the parent node. Adding up the decreases for each individual
     * variable over the tree gives a simple measure of variable importance.
     *
     * @return the variable importance
     */
    public double[] importance() {
        return importance;
    }

    @Override
    public double predict(Tuple x) {
        RegressionNode leaf = (RegressionNode) root.predict(x);
        return leaf.output();
    }

    /**
     * Returs the root node.
     * @return root node.
     */
    public Node getRoot() {
        return root;
    }
}
