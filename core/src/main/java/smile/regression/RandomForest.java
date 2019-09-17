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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.base.cart.CART;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.vector.BaseVector;
import smile.math.MathEx;
import smile.validation.RMSE;
import smile.validation.RegressionMeasure;

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
public class RandomForest implements Regression<Tuple> {
    private static final long serialVersionUID = 2L;

    /**
     * Design matrix formula
     */
    private Formula formula;

    /**
     * Forest of regression trees.
     */
    private List<RegressionTree> trees;

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
     */
    public RandomForest(Formula formula, List<RegressionTree> trees, double error, double[] importance) {
        this.formula = formula;
        this.trees = trees;
        this.error = error;
        this.importance = importance;
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param ntrees the number of trees.
     * @param mtry the number of input variables to be used to determine the decision
     * at a node of the tree. p/3 seems to give generally good performance,
     * where dim is the number of variables.
     * @param nodeSize the number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     */
    public static RandomForest fit(Formula formula, DataFrame data, int ntrees, int mtry, int nodeSize, int maxNodes, double subsample) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (nodeSize < 2) {
            throw new IllegalArgumentException("Invalid minimum size of leaves: " + nodeSize);
        }

        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum number of leaves: " + maxNodes);
        }

        if (subsample <= 0 || subsample > 1) {
            throw new IllegalArgumentException("Invalid sampling rate: " + subsample);
        }

        DataFrame x = formula.frame(data);
        BaseVector y = formula.response(data);

        if (mtry < 1 || mtry > x.ncols()) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
        }

        final int n = x.nrows();
        double[] prediction = new double[n];
        int[] oob = new int[n];

        final int[][] order = CART.order(x);

        List<RegressionTree> trees = IntStream.range(0, ntrees).parallel().mapToObj(t -> {
            final int[] samples = new int[n];

            if (subsample == 1.0) {
                // Training samples draw with replacement.
                IntStream.range(0, n).forEach(i -> samples[MathEx.randomInt(n)]++);
            } else {
                // Training samples draw without replacement.
                int[] perm = IntStream.range(0, n).toArray();
                MathEx.permutate(perm);

                IntStream.range(0, (int) Math.round(n * subsample)).forEach(i -> samples[perm[i]]++);
            }

            RegressionTree tree = new RegressionTree(x, y, nodeSize, maxNodes, mtry, samples, order);

            IntStream.range(0, n).filter(i -> samples[i] == 0).forEach(i -> {
                double pred = tree.predict(x.get(i));
                prediction[i] += pred;
                oob[i]++;
            });

            return tree;
        }).collect(Collectors.toList());

        int m = 0;
        double error = 0.0;
        for (int i = 0; i < n; i++) {
            if (oob[i] > 0) {
                m++;
                double pred = prediction[i] / oob[i];
                error += MathEx.sqr(pred - y.getInt(i));
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

        ArrayList<RegressionTree> mergedTrees = new ArrayList<>();
        mergedTrees.addAll(this.trees);
        mergedTrees.addAll(other.trees);

        double weightedMergedError = ((this.error * this.trees.size()) + (other.error * other.trees.size())) / (this.trees.size() + other.trees.size());
        double[] mergedImportance = calculateImportance(mergedTrees);

        return new RandomForest(formula, mergedTrees, weightedMergedError, mergedImportance);
    }

    private static double[] calculateImportance(List<RegressionTree> trees) {
        double[] importance = new double[trees.get(0).importance().length];
        for (RegressionTree tree : trees) {
            double[] imp = tree.importance();
            for (int i = 0; i < imp.length; i++) {
                importance[i] += imp[i];
            }
        }
        return importance;
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
        return trees.size();
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
        
        List<RegressionTree> model = new ArrayList<>(ntrees);
        for (int i = 0; i < ntrees; i++) {
            model.add(trees.get(i));
        }
        
        trees = model;
    }
    
    @Override
    public double predict(Tuple x) {
        double y = 0;
        for (RegressionTree tree : trees) {
            y += tree.predict(x);
        }
        
        return y / trees.size();
    }
    
    /**
     * Test the model on a validation dataset.
     *
     * @param data the test data set.
     * @return RMSEs with first 1, 2, ..., regression trees.
     */
    public double[] test(DataFrame data) {
        DataFrame x = formula.frame(data);
        double[] y = formula.response(data).toDoubleArray();

        int T = trees.size();
        double[] rmse = new double[T];

        int n = x.nrows();
        double[] sum = new double[n];
        double[] prediction = new double[n];

        RMSE measure = new RMSE();
        
        for (int i = 0, nt = 1; i < T; i++, nt++) {
            for (int j = 0; j < n; j++) {
                sum[j] += trees.get(i).predict(x.get(j));
                prediction[j] = sum[j] / nt;
            }

            rmse[i] = measure.measure(y, prediction);
        }

        return rmse;
    }
    
    /**
     * Test the model on a validation dataset.
     *
     * @param data the test data set.
     * @param measures the performance measures of regression.
     * @return performance measures with first 1, 2, ..., regression trees.
     */
    public double[][] test(DataFrame data, RegressionMeasure[] measures) {
        DataFrame x = formula.frame(data);
        double[] y = formula.response(data).toDoubleArray();

        int T = trees.size();
        int m = measures.length;
        double[][] results = new double[T][m];

        int n = x.nrows();
        double[] sum = new double[n];
        double[] prediction = new double[n];

        for (int i = 0, nt = 1; i < T; i++, nt++) {
            for (int j = 0; j < n; j++) {
                sum[j] += trees.get(i).predict(x.get(j));
                prediction[j] = sum[j] / nt;
            }

            for (int j = 0; j < m; j++) {
                results[i][j] = measures[j].measure(y, prediction);
            }
        }
        return results;
    }

    /**
     * Returns the regression trees.
     */
    public RegressionTree[] getTrees() {
        return trees.toArray(new RegressionTree[trees.size()]);
    }
}
