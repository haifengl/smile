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

package smile.classification;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import smile.base.cart.CART;
import smile.base.cart.SplitRule;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.math.MathEx;
import smile.util.IntSet;
import smile.util.Strings;

/**
 * Random forest for classification. Random forest is an ensemble classifier
 * that consists of many decision trees and outputs the majority vote of
 * individual trees. The method combines bagging idea and the random
 * selection of features.
 * <p>
 * Each tree is constructed using the following algorithm:
 * <ol>
 * <li> If the number of cases in the training set is N, randomly sample N cases
 * with replacement from the original data. This sample will
 * be the training set for growing the tree. 
 * <li> If there are M input variables, a number m &lt;&lt; M is specified such
 * that at each node, m variables are selected at random out of the M and
 * the best split on these m is used to split the node. The value of m is
 * held constant during the forest growing. 
 * <li> Each tree is grown to the largest extent possible. There is no pruning. 
 * </ol>
 * The advantages of random forest are:
 * <ul>
 * <li> For many data sets, it produces a highly accurate classifier.
 * <li> It runs efficiently on large data sets.
 * <li> It can handle thousands of input variables without variable deletion.
 * <li> It gives estimates of what variables are important in the classification.
 * <li> It generates an internal unbiased estimate of the generalization error
 * as the forest building progresses.
 * <li> It has an effective method for estimating missing data and maintains
 * accuracy when a large proportion of the data are missing.
 * </ul>
 * The disadvantages are
 * <ul>
 * <li> Random forests are prone to over-fitting for some datasets. This is
 * even more pronounced on noisy data.
 * <li> For data including categorical variables with different number of
 * levels, random forests are biased in favor of those attributes with more
 * levels. Therefore, the variable importance scores from random forest are
 * not reliable for this type of data.
 * </ul>
 * 
 * @author Haifeng Li
 */
public class RandomForest implements SoftClassifier<Tuple>, DataFrameClassifier {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RandomForest.class);

    /**
     * Decision tree wrapper with a weight. Currently, the weight is the accuracy of
     * tree on the OOB samples, which can be used when aggregating
     * tree votes.
     */
    static class Tree implements Serializable {
        DecisionTree tree;
        double weight;
        Tree(DecisionTree tree, double weight) {
            this.tree = tree;
            this.weight = weight;
        }
    }

    /**
     * Design matrix formula
     */
    private Formula formula;

    /**
     * Forest of decision trees. The second value is the accuracy of
     * tree on the OOB samples, which can be used a weight when aggregating
     * tree votes.
     */
    private List<Tree> trees;

    /**
     * The number of classes.
     */
    private int k = 2;

    /**
     * Out-of-bag estimation of error rate, which is quite accurate given that
     * enough trees have been grown (otherwise the OOB estimate can
     * bias upward).
     */
    private double error;

    /**
     * Variable importance. Every time a split of a node is made on variable
     * the (GINI, information gain, etc.) impurity criterion for the two
     * descendent nodes is less than the parent node. Adding up the decreases
     * for each individual variable over all trees in the forest gives a fast
     * variable importance that is often very consistent with the permutation
     * importance measure.
     */
    private double[] importance;

    /**
     * The class label encoder.
     */
    private IntSet labels;

    /**
     * Constructor.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param k the number of classes.
     * @param trees forest of decision trees.
     * @param error the out-of-bag estimation of error rate.
     * @param importance variable importance
     */
    public RandomForest(Formula formula, int k, List<Tree> trees, double error, double[] importance) {
        this(formula, k, trees, error, importance, IntSet.of(k));
    }

    /**
     * Constructor.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param k the number of classes.
     * @param trees forest of decision trees.
     * @param error the out-of-bag estimation of error rate.
     * @param importance variable importance
     * @param labels class labels
     */
    public RandomForest(Formula formula, int k, List<Tree> trees, double error, double[] importance, IntSet labels) {
        this.formula = formula;
        this.k = k;
        this.trees = trees;
        this.error = error;
        this.importance = importance;
        this.labels = labels;
    }

    /**
     * Fits a random forest for classification.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static RandomForest fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Fits a random forest for classification.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static RandomForest fit(Formula formula, DataFrame data, Properties prop) {
        int ntrees = Integer.valueOf(prop.getProperty("smile.random.forest.trees", "500"));
        int mtry = Integer.valueOf(prop.getProperty("smile.random.forest.mtry", "0"));
        SplitRule rule = SplitRule.valueOf(prop.getProperty("smile.random.forest.split.rule", "GINI"));
        int maxDepth = Integer.valueOf(prop.getProperty("smile.random.forest.max.depth", "20"));
        int maxNodes = Integer.valueOf(prop.getProperty("smile.random.forest.max.nodes", String.valueOf(data.size() / 5)));
        int nodeSize = Integer.valueOf(prop.getProperty("smile.random.forest.node.size", "5"));
        double subsample = Double.valueOf(prop.getProperty("smile.random.forest.sample.rate", "1.0"));
        int[] classWeight = Strings.parseIntArray(prop.getProperty("smile.random.forest.class.weight"));
        return fit(formula, data, ntrees, mtry, rule, maxDepth, maxNodes, nodeSize, subsample, classWeight, null);
    }

    /**
     * Fits a random forest for classification.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param ntrees the number of trees.
     * @param mtry the number of input variables to be used to determine the
     *             decision at a node of the tree. floor(sqrt(p)) generally
     *             gives good performance, where p is the number of variables
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree will
     *                 not split, nodeSize = 5 generally gives good results.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry, SplitRule rule, int maxDepth, int maxNodes, int nodeSize, double subsample) {
        return fit(formula, data, ntrees, mtry, rule, maxDepth, maxNodes, nodeSize, subsample, null);
    }

    /**
     * Fits a random forest for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param ntrees the number of trees.
     * @param mtry the number of input variables to be used to determine the
     *             decision at a node of the tree. floor(sqrt(p)) generally
     *             gives good performance, where p is the number of variables
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree will not split,
     *                 nodeSize = 5 generally gives good results.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     * @param classWeight Priors of the classes. The weight of each class
     *                    is roughly the ratio of samples in each class.
     *                    For example, if there are 400 positive samples
     *                    and 100 negative samples, the classWeight should
     *                    be [1, 4] (assuming label 0 is of negative, label 1 is of
     *                    positive).
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry, SplitRule rule, int maxDepth, int maxNodes, int nodeSize, double subsample, int[] classWeight) {
        return fit(formula, data, ntrees, mtry, rule, maxDepth, maxNodes, nodeSize, subsample, classWeight, null);
    }

    /**
     * Fits a random forest for classification.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param ntrees the number of trees.
     * @param mtry the number of input variables to be used to determine the
     *             decision at a node of the tree. floor(sqrt(p)) generally
     *             gives good performance, where p is the number of variables
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the minimum size of leaf nodes.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     * @param rule Decision tree split rule.
     * @param classWeight Priors of the classes. The weight of each class
     *                    is roughly the ratio of samples in each class.
     *                    For example, if there are 400 positive samples
     *                    and 100 negative samples, the classWeight should
     *                    be [1, 4] (assuming label 0 is of negative, label 1 is of
     *                    positive).
     * @param seeds optional RNG seeds for each regression tree.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry, SplitRule rule, int maxDepth, int maxNodes, int nodeSize, double subsample, int[] classWeight, LongStream seeds) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (subsample <= 0 || subsample > 1) {
            throw new IllegalArgumentException("Invalid sampling rating: " + subsample);
        }

        DataFrame x = formula.x(data);
        BaseVector y = formula.y(data);

        if (mtry > x.ncols()) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
        }

        int mtryFinal = mtry > 0 ? mtry : (int) Math.sqrt(x.ncols());

        ClassLabels codec = ClassLabels.fit(y);
        final int k = codec.k;
        final int n = x.nrows();

        final int[] weight = classWeight != null ? classWeight : Collections.nCopies(k, 1).stream().mapToInt(i -> i).toArray();

        final int[][] order = CART.order(x);
        final int[][] prediction = new int[n][k]; // out-of-bag prediction

        // generate seeds with sequential stream
        long[] seedArray = (seeds != null ? seeds : LongStream.range(-ntrees, 0)).sequential().distinct().limit(ntrees).toArray();
        if (seedArray.length != ntrees) {
            throw new IllegalArgumentException(String.format("seed stream has only %d distinct values, expected %d", seedArray.length, ntrees));
        }

        // # of samples in each class
        int[] count = new int[k];
        for (int i = 0; i < n; i++) {
            count[y.getInt(i)]++;
        }
        // samples in each class
        int[][] yi = new int[k][];
        for (int i = 0; i < k; i++) {
            yi[i] = new int[count[i]];
        }
        int[] idx = new int[k];
        for (int i = 0; i < n; i++) {
            int j = y.getInt(i);
            yi[j][idx[j]++] = i;
        }

        List<Tree> trees = Arrays.stream(seedArray).parallel().mapToObj(seed -> {
            // set RNG seed for the tree
            if (seed > 1) MathEx.setSeed(seed);

            final int[] samples = new int[n];
            // Stratified sampling in case that class is unbalanced.
            // That is, we sample each class separately.
            if (subsample == 1.0) {
                // Training samples draw with replacement.
                for (int i = 0; i < k; i++) {
                    // We used to do up sampling.
                    // But we switch to down sampling, which seems producing better AUC.
                    int ni = count[i];
                    int size = ni / weight[i];
                    int[] yj = yi[i];
                    for (int j = 0; j < size; j++) {
                        int xj = MathEx.randomInt(ni);
                        samples[yj[xj]] += 1; //classWeight[i];
                    }
                }
            } else {
                // Training samples draw without replacement.
                for (int i = 0; i < k; i++) {
                    // We used to do up sampling.
                    // But we switch to down sampling, which seems producing better AUC.
                    int size = (int) Math.round(subsample * count[i] / weight[i]);
                    int[] yj = yi[i];
                    int[] permutation = MathEx.permutate(count[i]);
                    for (int j = 0; j < size; j++) {
                        int xj = permutation[j];
                        samples[yj[xj]] += 1; //classWeight[i];
                    }
                }
            }

            DecisionTree tree = new DecisionTree(x, codec.y, codec.field, k, rule, maxDepth, maxNodes, nodeSize, mtryFinal, samples, order);

            // estimate OOB error
            int oob = 0;
            int correct = 0;
            for (int i = 0; i < n; i++) {
                if (samples[i] == 0) {
                    oob++;
                    int p = tree.predict(x.get(i));
                    if (p == y.getInt(i)) correct++;
                    prediction[i][p]++;
                }
            }

            double accuracy = 1.0;
            if (oob != 0) {
                accuracy = (double) correct / oob;
                logger.info("Random forest tree OOB size: {}, accuracy: {}", oob, String.format("%.2f%%", 100 * accuracy));
            } else {
                logger.error("Random forest has a tree trained without OOB samples.");
            }

            return new Tree(tree, accuracy);
        }).collect(Collectors.toList());

        int err = 0;
        int m = 0;
        for (int i = 0; i < n; i++) {
            int pred = MathEx.whichMax(prediction[i]);
            if (prediction[i][pred] > 0) {
                m++;
                if (pred != y.getInt(i)) {
                    err++;
                }
            }
        }

        double error = m > 0 ? (double) err / m : 0.0;

        return new RandomForest(formula, k, trees, error, importance(trees), codec.labels);
    }

    /** Calculate the importance of the whole forest. */
    private static double[] importance(List<Tree> trees) {
        int p = trees.get(0).tree.importance().length;
        double[] importance = new double[p];
        for (Tree tree : trees) {
            double[] imp = tree.tree.importance();
            for (int i = 0; i < p; i++) {
                importance[i] += imp[i];
            }
        }
        return importance;
    }

    @Override
    public Formula formula() {
        return formula;
    }

    @Override
    public StructType schema() {
        return trees.get(0).tree.schema();
    }

    /**
     * Returns the out-of-bag estimation of error rate. The OOB estimate is
     * quite accurate given that enough trees have been grown. Otherwise the
     * OOB estimate can bias upward.
     * 
     * @return the out-of-bag estimation of error rate
     */
    public double error() {
        return error;
    }
    
    /**
     * Returns the variable importance. Every time a split of a node is made
     * on variable the (GINI, information gain, etc.) impurity criterion for
     * the two descendent nodes is less than the parent node. Adding up the
     * decreases for each individual variable over all trees in the forest
     * gives a fast measure of variable importance that is often very
     * consistent with the permutation importance measure.
     *
     * @return the variable importance
     */
    public double[] importance() {
        return importance;
    }
    
    /**
     * Returns the number of trees in the model.
     * 
     * @return the number of trees in the model 
     */
    public int size() {
        return trees.size();
    }

    /**
     * Returns the decision trees.
     */
    public DecisionTree[] trees() {
        return trees.stream().map(t -> t.tree).toArray(DecisionTree[]::new);
    }

    /**
     * Trims the tree model set to a smaller size in case of over-fitting.
     * Or if extra decision trees in the model don't improve the performance,
     * we may remove them to reduce the model size and also improve the speed of
     * prediction.
     * 
     * @param ntrees the new (smaller) size of tree model set.
     */
    public void trim(int ntrees) {
        if (ntrees > trees.size()) {
            throw new IllegalArgumentException("The new model size is larger than the current size.");
        }
        
        if (ntrees <= 0) {
            throw new IllegalArgumentException("Invalid new model size: " + ntrees);
        }

        List<Tree> model = new ArrayList<>(ntrees);
        for (int i = 0; i < ntrees; i++) {
            model.add(trees.get(i));
        }
        
        trees = model;
    }
    
    @Override
    public int predict(Tuple x) {
        Tuple xt = formula.x(x);
        int[] y = new int[k];
        
        for (Tree tree : trees) {
            y[tree.tree.predict(xt)]++;
        }
        
        return labels.valueOf(MathEx.whichMax(y));
    }
    
    @Override
    public int predict(Tuple x, double[] posteriori) {
        if (posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        Tuple xt = formula.x(x);

        double[] prob = new double[k];
        Arrays.fill(posteriori, 0.0);
        for (Tree tree : trees) {
            tree.tree.predict(xt, prob);
            for (int i = 0; i < k; i++) {
                posteriori[i] += tree.weight * prob[i];
            }
        }

        MathEx.unitize1(posteriori);
        return labels.valueOf(MathEx.whichMax(posteriori));
    }

    /** Predict and estimate the probability by voting. */
    public int vote(Tuple x, double[] posteriori) {
        if (posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        Tuple xt = formula.x(x);
        Arrays.fill(posteriori, 0.0);
        for (Tree tree : trees) {
            posteriori[tree.tree.predict(xt)]++;
        }

        MathEx.unitize1(posteriori);
        return labels.valueOf(MathEx.whichMax(posteriori));
    }

    /**
     * Test the model on a validation dataset.
     *
     * @param data the test data set.
     * @return the predictions with first 1, 2, ..., decision trees.
     */
    public int[][] test(DataFrame data) {
        DataFrame x = formula.x(data);

        int n = x.size();
        int ntrees = trees.size();
        int[] p = new int[k];
        int[][] prediction = new int[ntrees][n];

        for (int j = 0; j < n; j++) {
            Tuple xj = x.get(j);
            Arrays.fill(p, 0);
            for (int i = 0; i < ntrees; i++) {
                p[trees.get(i).tree.predict(xj)]++;
                prediction[i][j] = MathEx.whichMax(p);
            }
        }

        return prediction;
    }

    /**
     * Returns a new random forest by reduced error pruning.
     * @param test the test data set to evaluate the errors of nodes.
     * @return a new pruned random forest.
     */
    public RandomForest prune(DataFrame test) {
        List<Tree> forest = trees.stream().parallel()
                .map(tree -> new Tree(tree.tree.prune(test, formula, labels), tree.weight))
                .collect(Collectors.toList());

        // The tree weight and OOB error are still the old one as we don't access to the training data here.
        return new RandomForest(formula, k, forest, error, importance(forest), labels);
    }
}
