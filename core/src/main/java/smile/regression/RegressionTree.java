/*******************************************************************************
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
 ******************************************************************************/

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
import smile.feature.SHAP;
import smile.math.MathEx;

/**
 * Regression tree. A classification/regression tree can be learned by
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
public class RegressionTree extends CART implements Regression<Tuple>, DataFrameRegression, SHAP<Tuple> {
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

                if (!MathEx.isZero(xij - prevx, 1E-7)) {
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

    @Override
    public double[] shap(Tuple x) {
        int s = maxDepth * (maxDepth + 1);

        // SHAP values
        double[] phi = new double[schema.length()];
        // The split features along the tree traverse.
        int[] d = new int[s];
        Arrays.fill(d, Integer.MIN_VALUE);
        // The fraction of zero paths (where this feature is not
        // in the set S) that flow through this path.
        double[] pz = new double[s];
        // The fraction of one paths (where this feature is
        // in the set S) that flow through this path.
        double[] po = new double[s];
        // The proportion of sets of a given cardinality that are present.
        double[] pi = new double[s];

        // start the recursive algorithm
        recurse(root, x, phi, 0, d, pz, po, pi,
                1, 1, -1, 0.0,
                0, 1, root.size(), 0);

        return phi;
    }

    /**
     * To keep track of each possible subset size during the recursion,
     * grows all these subsets according to a given fraction of ones and
     * zeros.
     *
     * @param feature_indexes split feature array along the tree traverse
     * @param pz  the fraction of zero paths (where this feature is not in the set S) that flow through this branch
     * @param po   the fraction of one paths (where this feature is in the set S) that flow through this branch
     * @param pi        hold the proportion of sets of a given cardinality that are present
     * @param unique_depth    new depth deep down the tree
     * @param zero_fraction   new fraction of zero paths (where this feature is not in the set S) that flow through this branch
     * @param one_fraction    new fraction of one paths (where this feature is in the set S) that flow through this branch
     * @param feature_index   new split feature along the tree traverse
     * @param offsetDepth     indexing support sub-range operation
     */
    private void extend(int[] feature_indexes,
                        double[] pz,
                        double[] po,
                        double[] pi,
                        int unique_depth,
                        double zero_fraction,
                        double one_fraction,
                        int feature_index,
                        int offsetDepth) {

        feature_indexes[unique_depth + offsetDepth] = feature_index;
        pz[unique_depth + offsetDepth] = zero_fraction;
        po[unique_depth + offsetDepth] = one_fraction;
        pi[unique_depth + offsetDepth] = unique_depth == 0 ? 1.0 : 0.0;

        double uniquePathPlus1 = (unique_depth + 1.0);
        int startIdx = unique_depth - 1;
        for (int i = startIdx; i > -1; i--) {
            pi[i + 1 + offsetDepth] += (one_fraction * pi[i + offsetDepth] * (i + 1.0) / uniquePathPlus1);
            pi[i + offsetDepth] = zero_fraction * pi[i + offsetDepth] * (unique_depth - i + 0.0) / uniquePathPlus1;
        }
    }

    /**
     * Undo previous extensions when we split on the same feature twice,
     * and undo each extension of the path inside a leaf to compute
     * weights for each feature in the path.
     *
     * @param feature_indexes split feature array along the tree traverse
     * @param pz  the fraction of zero paths (where this feature is not in the set S) that flow through this branch
     * @param po   the fraction of one paths (where this feature is in the set S) that flow through this branch
     * @param pi        hold the proportion of sets of a given cardinality that are present
     * @param unique_depth    new depth deep down the tree
     * @param path_index      indexing for which is to unwind path value
     * @param offsetDepth     indexing support sub-range operation
     */
    private void unwind(int[] feature_indexes,
                        double[] pz,
                        double[] po,
                        double[] pi,
                        int unique_depth,
                        int path_index,
                        int offsetDepth) {

        double one_fraction = po[path_index + offsetDepth];
        double zero_fraction = pz[path_index + offsetDepth];
        double next_one_portion = pi[path_index + offsetDepth];

        double uniquePathPlus1 = (unique_depth + 1.0);
        int startIdx = unique_depth - 1;
        for (int i = startIdx; i > -1; i--) {
            if (one_fraction != 0) {
                double tmp = pi[i + offsetDepth];
                pi[i + offsetDepth] = next_one_portion * uniquePathPlus1 / ((i + 1.0) * one_fraction);
                next_one_portion = tmp - pi[i + offsetDepth] * zero_fraction * (unique_depth - i + 0.0) / uniquePathPlus1;
            } else {
                pi[i + offsetDepth] = (pi[i + offsetDepth] * uniquePathPlus1) / (zero_fraction * (unique_depth - i + 0.0));
            }
        }

        for (int i = path_index; i < unique_depth; i++) {
            feature_indexes[i + offsetDepth] = feature_indexes[i + 1 + offsetDepth];
            pz[i + offsetDepth] = pz[i + 1 + offsetDepth];
            po[i + offsetDepth] = po[i + 1 + offsetDepth];
        }
    }

    /**
     * determine what the total permutation weight would be if we unwound a previous extension in the tree path
     *
     * @param pz the fraction of zero paths (where this feature is not in the set S) that flow through this branch
     * @param po  the fraction of one paths (where this feature is in the set S) that flow through this branch
     * @param pi       hold the proportion of sets of a given cardinality that are present
     * @param unique_depth   new depth deep down the tree
     * @param path_index     indexing for which is to unwind path value
     * @param offsetDepth    indexing support sub-range operation
     * @return the total permutation weight would be if we unwound a previous extension in the tree path
     */
    private double unwound(double[] pz, double[] po,
                           double[] pi, int unique_depth, int path_index,
                           int offsetDepth) {

        double one_fraction = po[path_index + offsetDepth];
        double zero_fraction = pz[path_index + offsetDepth];
        double next_one_portion = pi[path_index + offsetDepth];
        double total = 0;

        double uniquePathPlus1 = (unique_depth + 1.0);
        int startIdx = unique_depth - 1;
        for (int i = startIdx; i > -1; i--) {
            if (one_fraction != 0) {
                double tmp = next_one_portion * uniquePathPlus1 / ((i + 1.0) * one_fraction);
                total += tmp;
                next_one_portion = pi[i + offsetDepth] - tmp * zero_fraction * ((unique_depth - i + 0.0) / uniquePathPlus1);
            } else {
                double numerator = pi[i + offsetDepth] / zero_fraction;
                double denominator = (unique_depth - i + 0.0) / uniquePathPlus1;
                total += (numerator / denominator);
            }
        }

        return total;
    }

    /**
     * replicate inside the given double array from [offsetDepth, offsetDepth + unique_depth + 1)
     * to range with index starting at offsetDepth + unique_depth + 1
     *
     * @param original     double array to replicate in place
     * @param unique_depth replicate size = unique_depth + 1
     * @param offsetDepth  indexing support sub-range operation
     * @return original array after in-place replication
     */
    private double[] replicateInPlace(double[] original, int unique_depth, int offsetDepth) {
        int uniqueDepthPlus1 = (unique_depth + 1);
        for (int i = 0; i < uniqueDepthPlus1; i++) {
            if (original[i + offsetDepth] != Double.MIN_VALUE) {
                original[i + uniqueDepthPlus1 + offsetDepth] = original[i + offsetDepth];
            }
        }
        return original;
    }

    /**
     * replicate inside the given integer array from [offsetDepth, offsetDepth + unique_depth + 1)
     * to range with index starting at (offsetDepth + unique_depth + 1)
     *
     * @param original     integer array to replicate in place
     * @param unique_depth replicate size = unique_depth + 1
     * @param offsetDepth  indexing support sub-range operation
     * @return original array after in-place replication
     */
    private int[] replicateInPlace(int[] original, int unique_depth, int offsetDepth) {
        int uniqueDepthPlus1 = (unique_depth + 1);
        for (int i = 0; i < uniqueDepthPlus1; i++) {
            if (original[i + offsetDepth] != Integer.MIN_VALUE) {
                original[i + uniqueDepthPlus1 + offsetDepth] = original[i + offsetDepth];
            }
        }
        return original;
    }

    /**
     * recursive computation of SHAP values for given tree and data tuple
     *
     * @param node                   current tree node during tree traverse
     * @param x                      data tuple
     * @param phi                    shap values
     * @param unique_depth           new depth deep down the tree
     * @param parent_feature_indexes split feature array along the tree traverse
     * @param parent_pz  the fraction of zero paths (where this feature is not in the set S) that flow through this branch
     * @param parent_po   the fraction of one paths (where this feature is in the set S) that flow through this branch
     * @param parent_pi        hold the proportion of sets of a given cardinality that are present
     * @param parent_zero_fraction   parent fraction of zero paths (where this feature is not in the set S) that flow through this branch
     * @param parent_one_fraction    parent fraction of one paths (where this feature is in the set S) that flow through this branch
     * @param parent_feature_index   parent split feature
     * @param condition              default 0, used to divide up the condition_fraction among the recursive calls
     * @param condition_feature      used to divide up the condition_fraction among the recursive calls
     * @param condition_fraction     used to divide up the condition_fraction among the recursive calls
     * @param totalSamples           total sample number in the tree
     * @param offsetDepth            depth offset indexing into path fractions array
     */
    private void recurse(Node node,
                         Tuple x, double[] phi,
                         int unique_depth,
                         int[] parent_feature_indexes,
                         double[] parent_pz,
                         double[] parent_po,
                         double[] parent_pi,
                         double parent_zero_fraction,
                         double parent_one_fraction,
                         int parent_feature_index,
                         double condition,
                         int condition_feature,
                         double condition_fraction,
                         int totalSamples,
                         int offsetDepth) {

        // stop if we have no weight coming down to us
        if (condition_fraction == 0) {
            return;
        }

        // extend the unique path
        int[] feature_indexes = replicateInPlace(parent_feature_indexes, unique_depth, offsetDepth);
        double[] pz = replicateInPlace(parent_pz, unique_depth, offsetDepth);
        double[] po = replicateInPlace(parent_po, unique_depth, offsetDepth);
        double[] pi = replicateInPlace(parent_pi, unique_depth, offsetDepth);

        // update the depth offset indexing into path fractions
        offsetDepth += (unique_depth + 1);

        if (condition == 0 || condition_feature != parent_feature_index) {
            extend(feature_indexes, pz, po, pi, unique_depth, parent_zero_fraction,
                    parent_one_fraction, parent_feature_index, offsetDepth);
        }

        int split_index = (node instanceof InternalNode) ? ((InternalNode) node).feature() : -1;

        // leaf node
        if (node instanceof LeafNode) {
            int loopLength = (unique_depth + 1);
            for (int i = 1; i < loopLength; i++) {
                double w = unwound(pz, po, pi, unique_depth, i, offsetDepth);
                double val = ((RegressionNode) node).output();
                phi[feature_indexes[i + offsetDepth]] += w * (po[i + offsetDepth] - pz[i + offsetDepth]) * val * condition_fraction;
            }
        } else {// internal node
            // find which branch is "hot" (meaning x would follow it)
            Node hot = ((InternalNode) node).trueChild();
            Node cold = ((InternalNode) node).falseChild();
            if (!((InternalNode) node).branch(x)) {
                hot = ((InternalNode) node).falseChild();
                cold = ((InternalNode) node).trueChild();
            }

            double w = (double) node.size() / (double) totalSamples;
            double hot_zero_fraction = ((double) (hot.size()) / (double) totalSamples) / w;
            double cold_zero_fraction = ((double) (cold.size()) / (double) totalSamples) / w;
            double incoming_zero_fraction = 1;
            double incoming_one_fraction = 1;

            // see if we have already split on this feature,
            // if so we undo that split so we can redo it for this node
            int path_index = 0;
            while (path_index <= unique_depth) {
                if (feature_indexes[path_index + offsetDepth] == split_index) {
                    break;
                }
                path_index += 1;
            }
            if (path_index != unique_depth + 1) {
                incoming_zero_fraction = pz[path_index + offsetDepth];
                incoming_one_fraction = po[path_index + offsetDepth];
                unwind(feature_indexes, pz, po, pi, unique_depth, path_index, offsetDepth);
                unique_depth -= 1;
            }

            // divide up the condition_fraction among the recursive calls
            double hot_condition_fraction = condition_fraction;
            double cold_condition_fraction = condition_fraction;
            if (condition > 0 && split_index == condition_feature) {
                cold_condition_fraction = 0;
                unique_depth -= 1;
            } else if (condition < 0 && split_index == condition_feature) {
                hot_condition_fraction *= hot_zero_fraction;
                cold_condition_fraction *= cold_zero_fraction;
                unique_depth -= 1;
            }

            recurse(hot, x, phi, unique_depth + 1, feature_indexes,
                    pz, po, pi,
                    hot_zero_fraction * incoming_zero_fraction,
                    incoming_one_fraction, split_index, condition,
                    condition_feature, hot_condition_fraction, totalSamples,
                    offsetDepth);

            recurse(cold, x, phi, unique_depth + 1, feature_indexes,
                    pz, po, pi,
                    cold_zero_fraction * incoming_zero_fraction, 0, split_index,
                    condition, condition_feature, cold_condition_fraction,
                    totalSamples, offsetDepth);
        } // end else internal node
    }
}
