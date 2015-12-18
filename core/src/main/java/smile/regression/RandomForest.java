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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import smile.data.Attribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.math.Random;
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
public class RandomForest implements Regression<double[]> {
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
        private int T = 500;
        /**
         * The number of random selected features to be used to determine the decision
         * at a node of the tree. floor(sqrt(dim)) seems to give generally good performance,
         * where dim is the number of variables.        
         */
        private int M = -1;
        /**
         * The minimum number of instances in leaf nodes.
         */
        private int S = 5;

        /**
         * Constructor.
         * 
         * @param T the number of trees.
         */
        public Trainer(int T) {
            if (T < 1) {
                throw new IllegalArgumentException("Invlaid number of trees: " + T);
            }

            this.T = T;
        }

        /**
         * Constructor.
         * 
         * @param attributes the attributes of independent variable.
         * @param T the number of trees.
         */
        public Trainer(Attribute[] attributes, int T) {
            super(attributes);

            if (T < 1) {
                throw new IllegalArgumentException("Invlaid number of trees: " + T);
            }

            this.T = T;
        }
        
        /**
         * Sets the number of trees in the random forest.
         * @param T the number of trees.
         */
        public Trainer setNumTrees(int T) {
            if (T < 1) {
                throw new IllegalArgumentException("Invlaid number of trees: " + T);
            }

            this.T = T;
            return this;
        }
        
        /**
         * Sets the number of random selected features for splitting.
         * @param M the number of random selected features to be used to determine
         * the decision at a node of the tree. floor(sqrt(dim)) seems to give
         * generally good performance, where dim is the number of variables.
         */
        public Trainer setNumRandomFeatures(int M) {
            if (M < 1) {
                throw new IllegalArgumentException("Invalid number of random selected features for splitting: " + M);
            }

            this.M = M;
            return this;
        }
        
        /**
         * Sets the minimum size of leaf nodes.
         * @param S the number of instances in a node below which the tree will
         * not split, setting S = 5 generally gives good results.
         */
        public Trainer setMinimumNodeSize(int S) {
            if (S <= 0) {
                throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + S);
            }

            this.S = S;
            return this;
        }
        
        @Override
        public RandomForest train(double[][] x, double[] y) {
            return new RandomForest(attributes, x, y, T, M, S);
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
        int M;
        /**
         * The minimum number of instances in leaf nodes.
         */
        int S;
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
        TrainingTask(Attribute[] attributes, double[][] x, double[] y, int[][] order, int M, int S, double[] prediction, int[] oob) {
            this.attributes = attributes;
            this.x = x;
            this.y = y;
            this.order = order;
            this.M = M;
            this.S = S;
            this.prediction = prediction;
            this.oob = oob;
        }

        @Override
        public RegressionTree call() {
            int n = x.length;
            Random random = new Random(Thread.currentThread().getId() * System.currentTimeMillis());
            int[] samples = new int[n]; // Training samples draw with replacement.
            for (int i = 0; i < n; i++) {
                samples[random.nextInt(n)]++;
            }
            
            RegressionTree tree = new RegressionTree(attributes, x, y, M, S, order, samples);
            
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
     * @param T the number of trees.
     */
    public RandomForest(double[][] x, double[] y, int T) {
        this(null, x, y, T);
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param T the number of trees.
     * @param M the number of input variables to be used to determine the decision
     * at a node of the tree. dim/3 seems to give generally good performance,
     * where dim is the number of variables.
     * @param S the number of instances in a node below which the tree will
     * not split, setting S = 5 generally gives good results.
     */
    public RandomForest(double[][] x, double[] y, int T, int M, int S) {
        this(null, x, y, T, M, S);
    }

    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param T the number of trees.
     */
    public RandomForest(Attribute[] attributes, double[][] x, double[] y, int T) {
        this(attributes, x, y, T, -1, 5);
    }
    
    /**
     * Constructor. Learns a random forest for regression.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param T the number of trees.
     * @param M the number of input variables to be used to determine the decision
     * at a node of the tree. dim/3 seems to give generally good performance,
     * where dim is the number of variables.
     * @param S the number of instances in a node below which the tree will
     * not split, setting S = 5 generally gives good results.
     */
    public RandomForest(Attribute[] attributes, double[][] x, double[] y, int T, int M, int S) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (attributes == null) {
            int p = x[0].length;
            attributes = new Attribute[p];
            for (int i = 0; i < p; i++) {
                attributes[i] = new NumericAttribute("V" + (i + 1));
            }
        }

        if (M <= 0) {
            M = Math.max(1, x[0].length / 3);
        }
        
        if (S <= 0) {
            throw new IllegalArgumentException("Invalid minimum leaf node size: " + S);
        }
        
        int n = x.length;
        double[] prediction = new double[n];
        int[] oob = new int[n];
        
        int[][] order = SmileUtils.sort(attributes, x);
        List<TrainingTask> tasks = new ArrayList<TrainingTask>();
        for (int i = 0; i < T; i++) {
            tasks.add(new TrainingTask(attributes, x, y, order, M, S, prediction, oob));
        }
        
        try {
            trees = MulticoreExecutor.run(tasks);
        } catch (Exception ex) {
            ex.printStackTrace();

            trees = new ArrayList<RegressionTree>(T);
            for (int i = 0; i < T; i++) {
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
     * @param T the new (smaller) size of tree model set.
     */
    public void trim(int T) {
        if (T > trees.size()) {
            throw new IllegalArgumentException("The new model size is larger than the current size.");
        }
        
        if (T <= 0) {
            throw new IllegalArgumentException("Invalid new model size: " + T);            
        }
        
        List<RegressionTree> model = new ArrayList<RegressionTree>(T);
        for (int i = 0; i < T; i++) {
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
     * @param y the test data labels.
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
}
