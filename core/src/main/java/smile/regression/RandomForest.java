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

package smile.regression;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.LongStream;
import smile.base.cart.CART;
import smile.base.cart.Loss;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.feature.TreeSHAP;
import smile.math.MathEx;
import smile.validation.RegressionMetrics;
import smile.validation.metric.*;

/**
 * Random forest for regression. Random forest is an ensemble method that
 * consists of many regression trees and outputs the average of individual
 * trees. The method combines bagging idea and the random selection of features.
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
 * <li> For many data sets, it produces a highly accurate model.
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
 * even more pronounced in noisy classification/regression tasks.
 * <li> For data including categorical variables with different number of
 * levels, random forests are biased in favor of those attributes with more
 * levels. Therefore, the variable importance scores from random forest are
 * not reliable for this type of data.
 * </ul>
 * 
 * @author Haifeng Li
 */
public class RandomForest implements DataFrameRegression, TreeSHAP {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RandomForest.class);

    /**
     * The base model.
     */
    public static class Model implements Serializable {
        /** The decision tree. */
        public final RegressionTree tree;
        /** The performance metrics on out-of-bag samples. */
        public final RegressionMetrics metrics;

        /** Constructor. */
        Model(RegressionTree tree, RegressionMetrics metrics) {
            this.tree = tree;
            this.metrics = metrics;
        }
    }

    /**
     * The model formula.
     */
    private final Formula formula;

    /**
     * Forest of regression trees.
     */
    private final Model[] models;

    /**
     * The overall out-of-bag metrics, which are quite accurate given that
     * enough trees have been grown (otherwise the OOB error estimate can
     * bias upward).
     */
    private final RegressionMetrics metrics;

    /**
     * Variable importance. Every time a split of a node is made on variable
     * the impurity criterion for the two descendent nodes is less than the
     * parent node. Adding up the decreases for each individual variable over
     * all trees in the forest gives a fast variable importance that is often
     * very consistent with the permutation importance measure.
     */
    private final double[] importance;

    /**
     * Constructor.
     * @param formula a symbolic description of the model to be fitted.
     * @param models the base models.
     * @param metrics the overall out-of-bag metric estimations.
     * @param importance the feature importance.
     */
    public RandomForest(Formula formula, Model[] models, RegressionMetrics metrics, double[] importance) {
        this.formula = formula;
        this.models = models;
        this.metrics = metrics;
        this.importance = importance;
    }

    /**
     * Fits a random forest for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Fits a random forest for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data, Properties params) {
        int ntrees = Integer.parseInt(params.getProperty("smile.random_forest.trees", "500"));
        int mtry = Integer.parseInt(params.getProperty("smile.random_forest.mtry", "0"));
        int maxDepth = Integer.parseInt(params.getProperty("smile.random_forest.max_depth", "20"));
        int maxNodes = Integer.parseInt(params.getProperty("smile.random_forest.max_nodes", String.valueOf(data.size() / 5)));
        int nodeSize = Integer.parseInt(params.getProperty("smile.random_forest.node_size", "5"));
        double subsample = Double.parseDouble(params.getProperty("smile.random_forest.sampling_rate", "1.0"));
        return fit(formula, data, ntrees, mtry, maxDepth, maxNodes, nodeSize, subsample);
    }

    /**
     * Fits a random forest for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param ntrees the number of trees.
     * @param mtry the number of input variables to be used to determine the
     *             decision at a node of the tree. p/3 generally give good
     *             performance, where p is the number of variables.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree will
     *                 not split, nodeSize = 5 generally gives good results.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with
     *                  replacement. {@code < 1.0} means sampling without replacement.
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry, int maxDepth, int maxNodes, int nodeSize, double subsample) {
        return fit(formula, data, ntrees, mtry, maxDepth, maxNodes, nodeSize, subsample, null);
    }

    /**
     * Fits a random forest for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param ntrees the number of trees.
     * @param mtry the number of input variables to be used to determine the
     *             decision at a node of the tree. p/3 generally give good
     *             performance, where p is the number of variables.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree will
     *                 not split, nodeSize = 5 generally gives good results.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with
     *                  replacement. {@code < 1.0} means sampling without replacement.
     * @param seeds optional RNG seeds for each regression tree.
     * @return the model.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry, int maxDepth, int maxNodes, int nodeSize, double subsample, LongStream seeds) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (subsample <= 0 || subsample > 1) {
            throw new IllegalArgumentException("Invalid sampling rate: " + subsample);
        }

        formula = formula.expand(data.schema());
        DataFrame x = formula.x(data);
        BaseVector<?, ?, ?> response = formula.y(data);
        StructField field = response.field();
        double[] y = response.toDoubleArray();

        if (mtry > x.ncol()) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
        }

        int mtryFinal = mtry > 0 ? mtry : Math.max(x.ncol()/3, 1);

        final int n = x.nrow();
        double[] prediction = new double[n];
        int[] oob = new int[n];
        final int[][] order = CART.order(x);

        // generate seeds with sequential stream
        long[] seedArray = (seeds != null ? seeds : LongStream.range(-ntrees, 0)).sequential().distinct().limit(ntrees).toArray();
        if (seedArray.length != ntrees) {
            throw new IllegalArgumentException(String.format("seed stream has only %d distinct values, expected %d", seedArray.length, ntrees));
        }

        // train trees with parallel stream
        Model[] models = Arrays.stream(seedArray).parallel().mapToObj(seed -> {
            // set RNG seed for the tree
            if (seed > 1) MathEx.setSeed(seed);

            final int[] samples = new int[n];
            if (subsample == 1.0) {
                // Training samples draw with replacement.
                for (int i = 0; i < n; i++) {
                    samples[MathEx.randomInt(n)]++;
                }
            } else {
                // Training samples draw without replacement.
                int[] permutation = MathEx.permutate(n);
                int N = (int) Math.round(n * subsample);
                for (int i = 0; i < N; i++) {
                    samples[permutation[i]] = 1;
                }
            }

            long start = System.nanoTime();
            RegressionTree tree = new RegressionTree(x, Loss.ls(y), field, maxDepth, maxNodes, nodeSize, mtryFinal, samples, order);
            double fitTime = (System.nanoTime() - start) / 1E6;

            // estimate OOB metrics
            start = System.nanoTime();
            int noob = 0;
            for (int i = 0; i < n; i++) {
                if (samples[i] == 0) {
                    noob++;
                }
            }

            double[] truth = new double[noob];
            double[] predict = new double[noob];
            for (int i = 0, j = 0; i < n; i++) {
                if (samples[i] == 0) {
                    truth[j] = y[i];
                    double yi = tree.predict(x.get(i));
                    predict[j] = yi;
                    oob[i]++;
                    prediction[i] += yi;
                    j++;
                }
            }
            double scoreTime = (System.nanoTime() - start) / 1E6;

            RegressionMetrics metrics = new RegressionMetrics(
                    fitTime, scoreTime, noob,
                    RSS.of(truth, predict),
                    MSE.of(truth, predict),
                    RMSE.of(truth, predict),
                    MAD.of(truth, predict),
                    R2.of(truth, predict)
            );

            if (noob != 0) {
                logger.info("Regression tree OOB R2: {}", String.format("%.2f%%", 100*metrics.r2));
            } else {
                logger.error("Regression tree trained without OOB samples.");
            }

            return new Model(tree, metrics);
        }).toArray(Model[]::new);

        double fitTime = 0.0, scoreTime = 0.0;
        for (Model model : models) {
            fitTime += model.metrics.fitTime;
            scoreTime += model.metrics.scoreTime;
        }

        for (int i = 0; i < n; i++) {
            if (oob[i] > 0) {
                prediction[i] /= oob[i];
            }
        }

        RegressionMetrics metrics = new RegressionMetrics(
                fitTime, scoreTime, n,
                RSS.of(y, prediction),
                MSE.of(y, prediction),
                RMSE.of(y, prediction),
                MAD.of(y, prediction),
                R2.of(y, prediction)
        );

        double[] importance = calculateImportance(models);
        return new RandomForest(formula, models, metrics, importance);
    }

    /** Returns the sum of importance of all trees. */
    private static double[] calculateImportance(Model[] models) {
        double[] importance = new double[models[0].tree.importance().length];
        for (Model model : models) {
            double[] imp = model.tree.importance();
            for (int i = 0; i < imp.length; i++) {
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
     * @return the overall out-of-bag metric estimations.
     */
    public RegressionMetrics metrics() {
        return metrics;
    }
        
    /**
     * Returns the variable importance. Every time a split of a node is made
     * on variable the impurity criterion for the two descendent nodes is less
     * than the parent node. Adding up the decreases for each individual
     * variable over all trees in the forest gives a fast measure of variable
     * importance that is often very consistent with the permutation importance
     * measure.
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
    public RegressionTree[] trees() {
        return Arrays.stream(models).map(model -> model.tree).toArray(RegressionTree[]::new);
    }

    /**
     * Trims the tree model set to a smaller size in case of over-fitting.
     * Or if extra decision trees in the model don't improve the performance,
     * we may remove them to reduce the model size and also improve the speed of
     * prediction.
     * 
     * @param ntrees the new (smaller) size of tree model set.
     * @return the trimmed model.
     */
    public RandomForest trim(int ntrees) {
        if (ntrees > models.length) {
            throw new IllegalArgumentException("The new model size is larger than the current size.");
        }
        
        if (ntrees <= 0) {
            throw new IllegalArgumentException("Invalid new model size: " + ntrees);
        }

        Arrays.sort(models, Comparator.comparingDouble(model -> model.metrics.rmse));
        return new RandomForest(formula, Arrays.copyOf(models, ntrees), metrics, importance);
    }

    /**
     * Merges two random forests.
     *
     * @param other the model to merge with.
     * @return the merged model.
     */
    public RandomForest merge(RandomForest other) {
        if (!formula.equals(other.formula)) {
            throw new IllegalArgumentException("RandomForest have different model formula");
        }

        Model[] forest = new Model[models.length + other.models.length];
        System.arraycopy(models, 0, forest, 0, models.length);
        System.arraycopy(other.models, 0, forest, models.length, other.models.length);

        // rough estimation
        RegressionMetrics mergedMetrics = new RegressionMetrics(
                metrics.fitTime * other.metrics.fitTime,
                metrics.scoreTime * other.metrics.scoreTime,
                metrics.size,
                (metrics.rss * other.metrics.rss) / 2,
                (metrics.mse * other.metrics.mse) / 2,
                (metrics.rmse * other.metrics.rmse) / 2,
                (metrics.mad * other.metrics.mad) / 2,
                (metrics.r2 * other.metrics.r2) / 2
        );

        double[] mergedImportance = importance.clone();
        for (int i = 0; i < importance.length; i++) {
            mergedImportance[i] += other.importance[i];
        }

        return new RandomForest(formula, forest, mergedMetrics, mergedImportance);
    }

    @Override
    public double predict(Tuple x) {
        Tuple xt = formula.x(x);
        double y = 0;
        for (Model model : models) {
            y += model.tree.predict(xt);
        }
        
        return y / models.length;
    }

    /**
     * Test the model on a validation dataset.
     *
     * @param data the test data set.
     * @return the predictions with first 1, 2, ..., regression trees.
     */
    public double[][] test(DataFrame data) {
        DataFrame x = formula.x(data);

        int n = x.nrow();
        int ntrees = models.length;
        double[][] prediction = new double[ntrees][n];

        for (int j = 0; j < n; j++) {
            Tuple xj = x.get(j);
            double base = 0;
            for (int i = 0; i < ntrees; i++) {
                base = base + models[i].tree.predict(xj);
                prediction[i][j] = base / (i+1);
            }
        }

        return prediction;
    }
}
