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

package smile.classification;

import java.io.Serializable;
import java.util.*;
import java.util.stream.LongStream;
import smile.base.cart.CART;
import smile.base.cart.SplitRule;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.feature.TreeSHAP;
import smile.math.MathEx;
import smile.util.IntSet;
import smile.util.Strings;
import smile.validation.ClassificationMetrics;
import smile.validation.metric.*;
import smile.validation.metric.Error;

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
 * <li> If there are M input variables, a number {@code m << M} is specified such
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
public class RandomForest extends AbstractClassifier<Tuple> implements DataFrameClassifier, TreeSHAP {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RandomForest.class);

    /**
     * The base model.
     */
    public static class Model implements Serializable {
        /** The decision tree. */
        public final DecisionTree tree;
        /** The performance metrics on out-of-bag samples. */
        public final ClassificationMetrics metrics;
        /** The weight of tree, which can be used when aggregating tree votes. */
        public final double weight;

        /** Constructor. */
        Model(DecisionTree tree, ClassificationMetrics metrics) {
            this.tree = tree;
            this.metrics = metrics;
            this.weight = metrics.accuracy;
        }
    }

    /**
     * The model formula.
     */
    private final Formula formula;

    /**
     * Forest of decision trees. The second value is the accuracy of
     * tree on the OOB samples, which can be used a weight when aggregating
     * tree votes.
     */
    private final Model[] models;

    /**
     * The number of classes.
     */
    private final int k;

    /**
     * The overall out-of-bag metrics, which are quite accurate given that
     * enough trees have been grown (otherwise the OOB error estimate can
     * bias upward).
     */
    private final ClassificationMetrics metrics;

    /**
     * Variable importance. Every time a split of a node is made on variable
     * the (GINI, information gain, etc.) impurity criterion for the two
     * descendent nodes is less than the parent node. Adding up the decreases
     * for each individual variable over all trees in the forest gives a fast
     * variable importance that is often very consistent with the permutation
     * importance measure.
     */
    private final double[] importance;

    /**
     * Constructor.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param k the number of classes.
     * @param models forest of decision trees.
     * @param metrics the overall out-of-bag metric estimation.
     * @param importance the feature importance.
     */
    public RandomForest(Formula formula, int k, Model[] models, ClassificationMetrics metrics, double[] importance) {
        this(formula, k, models, metrics, importance, IntSet.of(k));
    }

    /**
     * Constructor.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param k the number of classes.
     * @param models the base models.
     * @param metrics the overall out-of-bag metric estimation.
     * @param importance the feature importance.
     * @param labels the class label encoder.
     */
    public RandomForest(Formula formula, int k, Model[] models, ClassificationMetrics metrics, double[] importance, IntSet labels) {
        super(labels);
        this.formula = formula;
        this.k = k;
        this.models = models;
        this.metrics = metrics;
        this.importance = importance;
    }

    /**
     * Fits a random forest for classification.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Fits a random forest for classification.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data, Properties params) {
        int ntrees = Integer.parseInt(params.getProperty("smile.random_forest.trees", "500"));
        int mtry = Integer.parseInt(params.getProperty("smile.random_forest.mtry", "0"));
        SplitRule rule = SplitRule.valueOf(params.getProperty("smile.random_forest.split_rule", "GINI"));
        int maxDepth = Integer.parseInt(params.getProperty("smile.random_forest.max_depth", "20"));
        int maxNodes = Integer.parseInt(params.getProperty("smile.random_forest.max_nodes", String.valueOf(data.size() / 5)));
        int nodeSize = Integer.parseInt(params.getProperty("smile.random_forest.node_size", "5"));
        double subsample = Double.parseDouble(params.getProperty("smile.random_forest.sampling_rate", "1.0"));
        int[] classWeight = Strings.parseIntArray(params.getProperty("smile.random_forest.class_weight"));
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
     *             gives good performance, where p is the number of variables.
     * @param rule Decision tree split rule.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree
     *                 will not split, nodeSize = 5 generally gives good
     *                 results.
     * @param subsample the sampling rate for training tree. 1.0 means sampling
     *                  with replacement. {@code < 1.0} means sampling without
     *                  replacement.
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry,
                                   SplitRule rule, int maxDepth, int maxNodes, int nodeSize, double subsample) {
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
     * @param rule Decision tree split rule.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree
     *                 will not split, nodeSize = 5 generally gives good
     *                 results.
     * @param subsample the sampling rate for training tree. 1.0 means sampling
     *                  with replacement. {@code < 1.0} means sampling without
     *                  replacement.
     * @param classWeight Priors of the classes. The weight of each class
     *                    is roughly the ratio of samples in each class.
     *                    For example, if there are 400 positive samples
     *                    and 100 negative samples, the classWeight should
     *                    be [1, 4] (assuming label 0 is of negative, label
     *                    1 is of positive).
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry,
                                   SplitRule rule, int maxDepth, int maxNodes, int nodeSize,
                                   double subsample, int[] classWeight) {
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
     *             gives good performance, where p is the number of variables.
     * @param rule Decision tree split rule.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree
     *                 will not split, nodeSize = 5 generally gives good
     *                 results.
     * @param subsample the sampling rate for training tree. 1.0 means sampling
     *                  with replacement. {@code < 1.0} means sampling without
     *                  replacement.
     * @param classWeight Priors of the classes. The weight of each class
     *                    is roughly the ratio of samples in each class.
     *                    For example, if there are 400 positive samples
     *                    and 100 negative samples, the classWeight should
     *                    be [1, 4] (assuming label 0 is of negative, label 1 is of
     *                    positive).
     * @param seeds optional RNG seeds for each regression tree.
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry,
                                   SplitRule rule, int maxDepth, int maxNodes, int nodeSize,
                                   double subsample, int[] classWeight, LongStream seeds) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (subsample <= 0 || subsample > 1) {
            throw new IllegalArgumentException("Invalid sampling rating: " + subsample);
        }

        formula = formula.expand(data.schema());
        DataFrame x = formula.x(data);
        BaseVector<?, ?, ?> y = formula.y(data);

        if (mtry > x.ncol()) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
        }

        int mtryFinal = mtry > 0 ? mtry : (int) Math.sqrt(x.ncol());

        ClassLabels codec = ClassLabels.fit(y);
        final int k = codec.k;
        final int n = x.nrow();

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
            count[codec.y[i]]++;
        }
        // samples in each class
        int[][] yi = new int[k][];
        for (int i = 0; i < k; i++) {
            yi[i] = new int[count[i]];
        }
        int[] idx = new int[k];
        for (int i = 0; i < n; i++) {
            int j = codec.y[i];
            yi[j][idx[j]++] = i;
        }

        Model[] models = Arrays.stream(seedArray).parallel().mapToObj(seed -> {
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

            long start = System.nanoTime();
            DecisionTree tree = new DecisionTree(x, codec.y, y.field(), k, rule, maxDepth, maxNodes, nodeSize, mtryFinal, samples, order);
            double fitTime = (System.nanoTime() - start) / 1E6;

            // estimate OOB metrics
            start = System.nanoTime();
            int noob = 0;
            for (int i = 0; i < n; i++) {
                if (samples[i] == 0) {
                    noob++;
                }
            }

            int[] truth = new int[noob];
            int[] oob = new int[noob];
            double[][] posteriori = new double[noob][k];
            for (int i = 0, j = 0; i < n; i++) {
                if (samples[i] == 0) {
                    truth[j] = codec.y[i];
                    int p = tree.predict(x.get(i), posteriori[j]);
                    oob[j] = p;
                    prediction[i][p]++;
                    j++;
                }
            }
            double scoreTime = (System.nanoTime() - start) / 1E6;

            // When data is very small, OOB samples may miss some classes.
            int oobk = MathEx.unique(truth).length;
            ClassificationMetrics metrics;
            if (oobk == 2) {
                double[] probability = Arrays.stream(posteriori).mapToDouble(p -> p[1]).toArray();
                metrics = new ClassificationMetrics(fitTime, scoreTime, noob,
                            Error.of(truth, oob),
                            Accuracy.of(truth, oob),
                            Sensitivity.of(truth, oob),
                            Specificity.of(truth, oob),
                            Precision.of(truth, oob),
                            FScore.F1.score(truth, oob),
                            MatthewsCorrelation.of(truth, oob),
                            AUC.of(truth, probability),
                            LogLoss.of(truth, probability)
                    );
            } else {
                metrics = new ClassificationMetrics(fitTime, scoreTime, noob,
                            Error.of(truth, oob),
                            Accuracy.of(truth, oob),
                            CrossEntropy.of(truth, posteriori)
                );
            }

            if (noob != 0) {
                logger.info("Decision tree OOB accuracy: {}", String.format("%.2f%%", 100*metrics.accuracy));
            } else {
                logger.error("Decision tree trained without OOB samples.");
            }

            return new Model(tree, metrics);
        }).toArray(Model[]::new);

        double fitTime = 0.0, scoreTime = 0.0;
        for (Model model : models) {
            fitTime += model.metrics.fitTime;
            scoreTime += model.metrics.scoreTime;
        }

        int[] vote = new int[n];
        for (int i = 0; i < n; i++) {
            vote[i] = MathEx.whichMax(prediction[i]);
        }

        ClassificationMetrics metrics = new ClassificationMetrics(fitTime, scoreTime, n,
                Error.of(codec.y, vote),
                Accuracy.of(codec.y, vote)
        );

        return new RandomForest(formula, k, models, metrics, importance(models), codec.classes);
    }

    /** Calculate the importance of the whole forest. */
    private static double[] importance(Model[] models) {
        int p = models[0].tree.importance().length;
        double[] importance = new double[p];
        for (Model model : models) {
            double[] imp = model.tree.importance();
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
        return models[0].tree.schema();
    }

    /**
     * Returns the overall out-of-bag metric estimations. The OOB estimate is
     * quite accurate given that enough trees have been grown. Otherwise the
     * OOB error estimate can bias upward.
     * 
     * @return the out-of-bag metrics estimations.
     */
    public ClassificationMetrics metrics() {
        return metrics;
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
     * @return the number of trees in the model.
     */
    public int size() {
        return models.length;
    }

    /**
     * Returns the base models.
     * @return the base models.
     */
    public Model[] models() {
        return models;
    }

    @Override
    public DecisionTree[] trees() {
        return Arrays.stream(models).map(model -> model.tree).toArray(DecisionTree[]::new);
    }

    /**
     * Trims the tree model set to a smaller size in case of over-fitting.
     * Or if extra decision trees in the model don't improve the performance,
     * we may remove them to reduce the model size and also improve the speed of
     * prediction.
     * 
     * @param ntrees the new (smaller) size of tree model set.
     * @return a new trimmed forest.
     */
    public RandomForest trim(int ntrees) {
        if (ntrees > models.length) {
            throw new IllegalArgumentException("The new model size is larger than the current size.");
        }
        
        if (ntrees <= 0) {
            throw new IllegalArgumentException("Invalid new model size: " + ntrees);
        }

        Arrays.sort(models, Comparator.comparingDouble(model -> -model.weight));

        // The OOB metrics are still the old one
        // as we don't access to the training data here.
        return new RandomForest(formula, k, Arrays.copyOf(models, ntrees), metrics, importance(models), classes);
    }

    /**
     * Merges two random forests.
     * @param other the other forest to merge with.
     * @return the merged forest.
     */
    public RandomForest merge(RandomForest other) {
        if (!formula.equals(other.formula)) {
            throw new IllegalArgumentException("RandomForest have different model formula");
        }

        Model[] forest = new Model[models.length + other.models.length];
        System.arraycopy(models, 0, forest, 0, models.length);
        System.arraycopy(other.models, 0, forest, models.length, other.models.length);

        // rough estimation
        ClassificationMetrics mergedMetrics = new ClassificationMetrics(
                metrics.fitTime * other.metrics.fitTime,
                metrics.scoreTime * other.metrics.scoreTime,
                metrics.size,
                (metrics.error * other.metrics.error) / 2,
                (metrics.accuracy * other.metrics.accuracy) / 2,
                (metrics.sensitivity * other.metrics.sensitivity) / 2,
                (metrics.specificity * other.metrics.specificity) / 2,
                (metrics.precision * other.metrics.precision) / 2,
                (metrics.f1 * other.metrics.f1) / 2,
                (metrics.mcc * other.metrics.mcc) / 2,
                (metrics.auc * other.metrics.auc) / 2,
                (metrics.logloss * other.metrics.logloss) / 2,
                (metrics.crossentropy * other.metrics.crossentropy) / 2
        );

        double[] mergedImportance = importance.clone();
        for (int i = 0; i < importance.length; i++) {
            mergedImportance[i] += other.importance[i];
        }

        return new RandomForest(formula, k, forest, mergedMetrics, mergedImportance, classes);
    }

    @Override
    public int predict(Tuple x) {
        Tuple xt = formula.x(x);
        int[] y = new int[k];
        
        for (Model model : models) {
            y[model.tree.predict(xt)]++;
        }
        
        return classes.valueOf(MathEx.whichMax(y));
    }

    @Override
    public boolean soft() {
        return true;
    }

    @Override
    public int predict(Tuple x, double[] posteriori) {
        if (posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        Tuple xt = formula.x(x);

        double[] prob = new double[k];
        Arrays.fill(posteriori, 0.0);
        for (Model model : models) {
            model.tree.predict(xt, prob);
            for (int i = 0; i < k; i++) {
                posteriori[i] += model.weight * prob[i];
            }
        }

        MathEx.unitize1(posteriori);
        return classes.valueOf(MathEx.whichMax(posteriori));
    }

    /**
     * Predict and estimate the probability by voting.
     *
     * @param x the instances to be classified.
     * @param posteriori a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    public int vote(Tuple x, double[] posteriori) {
        if (posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        Tuple xt = formula.x(x);
        Arrays.fill(posteriori, 0.0);
        for (Model model : models) {
            posteriori[model.tree.predict(xt)]++;
        }

        MathEx.unitize1(posteriori);
        return classes.valueOf(MathEx.whichMax(posteriori));
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
        int ntrees = models.length;
        int[] p = new int[k];
        int[][] prediction = new int[ntrees][n];

        for (int j = 0; j < n; j++) {
            Tuple xj = x.get(j);
            Arrays.fill(p, 0);
            for (int i = 0; i < ntrees; i++) {
                p[models[i].tree.predict(xj)]++;
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
        Model[] forest = Arrays.stream(models).parallel()
                .map(model -> new Model(model.tree.prune(test, formula, classes), model.metrics))
                .toArray(Model[]::new);

        // The tree weight and OOB metrics are still the old one
        // as we don't access to the training data here.
        return new RandomForest(formula, k, forest, metrics, importance(forest), classes);
    }
}
