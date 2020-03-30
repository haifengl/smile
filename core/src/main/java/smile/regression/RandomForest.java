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

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import smile.base.cart.CART;
import smile.base.cart.Loss;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.math.MathEx;

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
 * <li> If there are M input variables, a number m &lt;&lt; M is specified such
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
public class RandomForest implements Regression<Tuple>, DataFrameRegression {
    private static final long serialVersionUID = 2L;

    /**
     * Design matrix formula
     */
    private Formula formula;

    /**
     * Forest of regression trees.
     */
    private RegressionTree[] trees;

    /**
     * Out-of-bag estimation of RMSE, which is quite accurate given that
     * enough trees have been grown (otherwise the OOB estimate can
     * bias upward).
     */
    private double error;

    /**
     * Variable importance. Every time a split of a node is made on variable
     * the impurity criterion for the two descendent nodes is less than the
     * parent node. Adding up the decreases for each individual variable over
     * all trees in the forest gives a fast variable importance that is often
     * very consistent with the permutation importance measure.
     */
    private double[] importance;

    /**
     * Constructor.
     * @param formula a symbolic description of the model to be fitted.
     * @param trees forest of regression trees.
     * @param error out-of-bag estimation of RMSE
     * @param importance variable importance
     */
    public RandomForest(Formula formula, RegressionTree[] trees, double error, double[] importance) {
        this.formula = formula;
        this.trees = trees;
        this.error = error;
        this.importance = importance;
    }

    /**
     * Learns a random forest for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static RandomForest fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Learns a random forest for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static RandomForest fit(Formula formula, DataFrame data, Properties prop) {
        int ntrees = Integer.valueOf(prop.getProperty("smile.random.forest.trees", "500"));
        int mtry = Integer.valueOf(prop.getProperty("smile.random.forest.mtry", "0"));
        int maxDepth = Integer.valueOf(prop.getProperty("smile.random.forest.max.depth", "20"));
        int maxNodes = Integer.valueOf(prop.getProperty("smile.random.forest.max.nodes", String.valueOf(data.size() / 5)));
        int nodeSize = Integer.valueOf(prop.getProperty("smile.random.forest.node.size", "5"));
        double subsample = Double.valueOf(prop.getProperty("smile.random.forest.sample.rate", "1.0"));
        return fit(formula, data, ntrees, mtry, maxDepth, maxNodes, nodeSize, subsample);
    }

    /**
     * Learns a random forest for regression.
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
     *                  replacement. < 1.0 means sampling without replacement.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry, int maxDepth, int maxNodes, int nodeSize, double subsample) {
        return fit(formula, data, ntrees, mtry, maxDepth, maxNodes, nodeSize, subsample, null);
    }

    /**
     * Learns a random forest for regression.
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
     *                  replacement. < 1.0 means sampling without replacement.
     * @param seeds optional RNG seeds for each regression tree.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry, int maxDepth, int maxNodes, int nodeSize, double subsample, LongStream seeds) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (subsample <= 0 || subsample > 1) {
            throw new IllegalArgumentException("Invalid sampling rate: " + subsample);
        }

        DataFrame x = formula.x(data);
        BaseVector response = formula.y(data);
        StructField field = response.field();
        double[] y = response.toDoubleArray();

        if (mtry > x.ncols()) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
        }

        int mtryFinal = mtry > 0 ? mtry : Math.max(x.ncols()/3, 1);

        final int n = x.nrows();
        double[] prediction = new double[n];
        int[] oob = new int[n];
        final int[][] order = CART.order(x);

        // generate seeds with sequential stream
        long[] seedArray = (seeds != null ? seeds : LongStream.range(-ntrees, 0)).sequential().distinct().limit(ntrees).toArray();
        if (seedArray.length != ntrees) {
            throw new IllegalArgumentException(String.format("seed stream has only %d distinct values, expected %d", seedArray.length, ntrees));
        }

        // train trees with parallel stream
        RegressionTree[] trees = Arrays.stream(seedArray).parallel().mapToObj(seed -> {
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

            RegressionTree tree = new RegressionTree(x, Loss.ls(y), field, maxDepth, maxNodes, nodeSize, mtryFinal, samples, order);

            IntStream.range(0, n).filter(i -> samples[i] == 0).forEach(i -> {
                double pred = tree.predict(x.get(i));
                prediction[i] += pred;
                oob[i]++;
            });

            return tree;
        }).toArray(RegressionTree[]::new);

        int m = 0;
        double error = 0.0;
        for (int i = 0; i < n; i++) {
            if (oob[i] > 0) {
                m++;
                double pred = prediction[i] / oob[i];
                error += MathEx.sqr(pred - y[i]);
            }
        }

        if (m > 0) {
            error = Math.sqrt(error / m);
        }

        double[] importance = calculateImportance(trees);
        return new RandomForest(formula, trees, error, importance);
    }

    /**
     * Merges together two random forests and returns a new forest consisting of trees from both input forests.
     */
    public RandomForest merge(RandomForest other) {
        if (!formula.equals(other.formula)) {
            throw new IllegalArgumentException("RandomForest have different sizes of feature vectors");
        }

        RegressionTree[] forest = new RegressionTree[trees.length + other.trees.length];
        System.arraycopy(trees, 0, forest, 0, trees.length);
        System.arraycopy(other.trees, 0, forest, trees.length, other.trees.length);

        double mergedError = (this.error * other.error) / 2; // rough estimation
        double[] mergedImportance = importance.clone();
        for (int i = 0; i < importance.length; i++) {
            mergedImportance[i] += other.importance[i];
        }

        return new RandomForest(formula, forest, mergedError, mergedImportance);
    }

    /** Returns the sum of importance of all trees. */
    private static double[] calculateImportance(RegressionTree[] trees) {
        double[] importance = new double[trees[0].importance().length];
        for (RegressionTree tree : trees) {
            double[] imp = tree.importance();
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
        return trees[0].schema();
    }

    /**
     * Returns the out-of-bag estimation of RMSE. The OOB estimate is
     * quite accurate given that enough trees have been grown. Otherwise the
     * OOB estimate can bias upward.
     * 
     * @return the out-of-bag estimation of RMSE
     */
    public double error() {
        return error;
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
        return trees.length;
    }

    /**
     * Returns the regression trees.
     */
    public RegressionTree[] trees() {
        return trees;
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
        if (ntrees > trees.length) {
            throw new IllegalArgumentException("The new model size is larger than the current size.");
        }
        
        if (ntrees <= 0) {
            throw new IllegalArgumentException("Invalid new model size: " + ntrees);
        }
        
        RegressionTree[] model = new RegressionTree[ntrees];
        System.arraycopy(trees, 0, model, 0, ntrees);
        trees = model;
    }
    
    @Override
    public double predict(Tuple x) {
        Tuple xt = formula.x(x);
        double y = 0;
        for (RegressionTree tree : trees) {
            y += tree.predict(xt);
        }
        
        return y / trees.length;
    }

    /**
     * Test the model on a validation dataset.
     *
     * @param data the test data set.
     * @return the predictions with first 1, 2, ..., regression trees.
     */
    public double[][] test(DataFrame data) {
        DataFrame x = formula.x(data);

        int n = x.nrows();
        int ntrees = trees.length;
        double[][] prediction = new double[ntrees][n];

        for (int j = 0; j < n; j++) {
            Tuple xj = x.get(j);
            double base = 0;
            for (int i = 0; i < ntrees; i++) {
                base = base + trees[i].predict(xj);
                prediction[i][j] = base / (i+1);
            }
        }

        return prediction;
    }
}
