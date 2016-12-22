/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.regression;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import smile.data.Attribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.util.MulticoreExecutor;
import smile.util.SmileUtils;
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
public class RandomForest implements Regression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

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
     * Trainer for random forest.
     */
    public static class Trainer extends RegressionTrainer<double[]> {
        /**
         * The number of trees.
         */
        private int ntrees = 500;
        /**
         * The number of random selected features to be used to determine the decision
         * at a node of the tree. floor(sqrt(dim)) seems to give generally good performance,
         * where dim is the number of variables.
         */
        private int mtry = -1;
        /**
         * The minimum number of instances in leaf nodes.
         */
        private int nodeSize = 5;
        /**
         * The maximum number of leaf nodes in the tree.
         */
        private int maxNodes = 100;
        /**
         * The sampling rate.
         */
        private double subsample = 1.0;

        /**
         * Constructor.
         *
         * @param ntrees the number of trees.
         */
        public Trainer(int ntrees) {
            if (ntrees < 1) {
                throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
            }

            this.ntrees = ntrees;
        }

        /**
         * Constructor.
         *
         * @param attributes the attributes of independent variable.
         * @param ntrees the number of trees.
         */
        public Trainer(Attribute[] attributes, int ntrees) {
            super(attributes);

            if (ntrees < 1) {
                throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
            }

            this.ntrees = ntrees;
        }

        /**
         * Sets the number of trees in the random forest.
         * @param ntrees the number of trees.
         */
        public Trainer setNumTrees(int ntrees) {
            if (ntrees < 1) {
                throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
            }

            this.ntrees = ntrees;
            return this;
        }

        /**
         * Sets the number of random selected features for splitting.
         * @param mtry the number of random selected features to be used to determine
         * the decision at a node of the tree. p/3 seems to give
         * generally good performance, where p is the number of variables.
         */
        public Trainer setNumRandomFeatures(int mtry) {
            if (mtry < 1) {
                throw new IllegalArgumentException("Invalid number of random selected features for splitting: " + mtry);
            }

            this.mtry = mtry;
            return this;
        }

        /**
         * Sets the maximum number of leaf nodes.
         * @param maxNodes the maximum number of leaf nodes.
         */
        public Trainer setMaxNodes(int maxNodes) {
            if (maxNodes < 2) {
                throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + maxNodes);
            }

            this.maxNodes = maxNodes;
            return this;
        }

        /**
         * Sets the minimum size of leaf nodes.
         * @param nodeSize the number of instances in a node below which the tree will not split.
         */
        public Trainer setNodeSize(int nodeSize) {
            if (nodeSize < 1) {
                throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
            }

            this.nodeSize = nodeSize;
            return this;
        }

        /**
         * Sets the sampling rate.
         * @param subsample the sampling rate.
         */
        public Trainer setSamplingRates(double subsample) {
            if (subsample <= 0 || subsample > 1) {
                throw new IllegalArgumentException("Invalid sampling rate: " + subsample);
            }

            this.subsample = subsample;
            return this;
        }

        @Override
        public RandomForest train(double[][] x, double[] y) {
            return new RandomForest(attributes, x, y, ntrees, maxNodes, nodeSize, mtry, subsample);
        }
    }

    /**
     * Trains a regression tree.
     */
    static class TrainingTask implements Callable<RegressionTree> {
        /**
         * Attribute properties.
         */
        Attribute[] attributes;
        /**
         * Training instances.
         */
        double[][] x;
        /**
         * Training response variable.
         */
        double[] y;
        /**
         * The index of training values in ascending order. Note that only
         * numeric attributes will be sorted.
         */
        int[][] order;
        /**
         * The number of variables to pick up in each node.
         */
        int mtry;
        /**
         * The minimum number of instances in leaf nodes.
         */
        int nodeSize = 5;
        /**
         * The maximum number of leaf nodes in the tree.
         */
        int maxNodes = 100;
        /**
         * The sampling rate.
         */
        double subsample = 1.0;
        /**
         * Predictions of of out-of-bag samples.
         */
        double[] prediction;
        /**
         * Out-of-bag sample
         */
        int[] oob;

        /**
         * Constructor.
         */
        TrainingTask(Attribute[] attributes, double[][] x, double[] y, int maxNodes, int nodeSize, int mtry, double subsample, int[][] order, double[] prediction, int[] oob) {
            this.attributes = attributes;
            this.x = x;
            this.y = y;
            this.order = order;
            this.mtry = mtry;
            this.nodeSize = nodeSize;
            this.maxNodes = maxNodes;
            this.subsample = subsample;
            this.prediction = prediction;
            this.oob = oob;
        }

        @Override
        public RegressionTree call() {
            int n = x.length;
            int[] samples = new int[n];

            if (subsample == 1.0) {
                // Training samples draw with replacement.
                for (int i = 0; i < n; i++) {
                    int xi = Math.randomInt(n);
                    samples[xi]++;
                }
            } else {
                // Training samples draw without replacement.
                int[] perm = new int[n];
                for (int i = 0; i < n; i++) {
                    perm[i] = i;
                }

                Math.permutate(perm);
                int m = (int) Math.round(n * subsample);
                for (int i = 0; i < m; i++) {
                    samples[perm[i]]++;
                }
            }

            RegressionTree tree = new RegressionTree(attributes, x, y, maxNodes, nodeSize, mtry, order, samples, null);

            for (int i = 0; i < n; i++) {
                if (samples[i] == 0) {
                    double pred = tree.predict(x[i]);
                    synchronized (x[i]) {
                        prediction[i] += pred;
                        oob[i]++;
                    }
                }
            }

            return tree;
        }
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     */
    public RandomForest(double[][] x, double[] y, int ntrees) {
        this(null, x, y, ntrees);
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of input variables to be used to determine the decision
     * at a node of the tree. p/3 seems to give generally good performance,
     * where p is the number of variables.
     * @param nodeSize the number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RandomForest(double[][] x, double[] y, int ntrees, int maxNodes, int nodeSize, int mtry) {
        this(null, x, y, ntrees, maxNodes, nodeSize, mtry);
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     */
    public RandomForest(Attribute[] attributes, double[][] x, double[] y, int ntrees) {
        this(attributes, x, y, ntrees, 100);
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RandomForest(Attribute[] attributes, double[][] x, double[] y, int ntrees, int maxNodes) {
        this(attributes, x, y, ntrees, maxNodes, 5);
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param nodeSize the number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RandomForest(Attribute[] attributes, double[][] x, double[] y, int ntrees, int maxNodes, int nodeSize) {
        this(attributes, x, y, ntrees, maxNodes, nodeSize, x[0].length / 3);
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of input variables to be used to determine the decision
     * at a node of the tree. p/3 seems to give generally good performance,
     * where dim is the number of variables.
     * @param nodeSize the number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RandomForest(Attribute[] attributes, double[][] x, double[] y, int ntrees, int maxNodes, int nodeSize, int mtry) {
        this(attributes, x, y, ntrees, maxNodes, nodeSize, mtry, 1.0);
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
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
    public RandomForest(Attribute[] attributes, double[][] x, double[] y, int ntrees, int maxNodes, int nodeSize, int mtry, double subsample) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (mtry < 1 || mtry > x[0].length) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
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

        if (attributes == null) {
            int p = x[0].length;
            attributes = new Attribute[p];
            for (int i = 0; i < p; i++) {
                attributes[i] = new NumericAttribute("V" + (i + 1));
            }
        }

        int n = x.length;
        double[] prediction = new double[n];
        int[] oob = new int[n];
        
        int[][] order = SmileUtils.sort(attributes, x);
        List<TrainingTask> tasks = new ArrayList<>();
        for (int i = 0; i < ntrees; i++) {
            tasks.add(new TrainingTask(attributes, x, y, maxNodes, nodeSize, mtry, subsample, order, prediction, oob));
        }
        
        try {
            trees = MulticoreExecutor.run(tasks);
        } catch (Exception ex) {
            ex.printStackTrace();

            trees = new ArrayList<>(ntrees);
            for (int i = 0; i < ntrees; i++) {
                trees.add(tasks.get(i).call());
            }
        }
        
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (oob[i] > 0) {
                m++;
                double pred = prediction[i] / oob[i];
                error += Math.sqr(pred - y[i]);
            }
        }

        if (m > 0) {
            error = Math.sqrt(error / m);
        }
        
        importance = new double[attributes.length];
        for (RegressionTree tree : trees) {
            double[] imp = tree.importance();
            for (int i = 0; i < imp.length; i++) {
                importance[i] += imp[i];
            }
        }
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
    public double predict(double[] x) {
        double y = 0;
        for (RegressionTree tree : trees) {
            y += tree.predict(x);
        }
        
        return y / trees.size();
    }
    
    /**
     * Test the model on a validation dataset.
     * 
     * @param x the test data set.
     * @param y the test data response values.
     * @return RMSEs with first 1, 2, ..., regression trees.
     */
    public double[] test(double[][] x, double[] y) {
        int T = trees.size();
        double[] rmse = new double[T];

        int n = x.length;
        double[] sum = new double[n];
        double[] prediction = new double[n];

        RMSE measure = new RMSE();
        
        for (int i = 0, nt = 1; i < T; i++, nt++) {
            for (int j = 0; j < n; j++) {
                sum[j] += trees.get(i).predict(x[j]);
                prediction[j] = sum[j] / nt;
            }

            rmse[i] = measure.measure(y, prediction);
        }

        return rmse;
    }
    
    /**
     * Test the model on a validation dataset.
     * 
     * @param x the test data set.
     * @param y the test data output values.
     * @param measures the performance measures of regression.
     * @return performance measures with first 1, 2, ..., regression trees.
     */
    public double[][] test(double[][] x, double[] y, RegressionMeasure[] measures) {
        int T = trees.size();
        int m = measures.length;
        double[][] results = new double[T][m];

        int n = x.length;
        double[] sum = new double[n];
        double[] prediction = new double[n];

        for (int i = 0, nt = 1; i < T; i++, nt++) {
            for (int j = 0; j < n; j++) {
                sum[j] += trees.get(i).predict(x[j]);
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
