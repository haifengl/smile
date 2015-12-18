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
package smile.classification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import smile.data.Attribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.math.Random;
import smile.util.MulticoreExecutor;
import smile.util.SmileUtils;
import smile.validation.Accuracy;
import smile.validation.ClassificationMeasure;

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
public class RandomForest implements Classifier<double[]> {
    /**
     * Forest of decision trees.
     */
    private List<DecisionTree> trees;
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
     * Trainer for random forest classifiers.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {
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
         * Default constructor of 500 trees.
         */
        public Trainer() {

        }

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
        
        @Override
        public RandomForest train(double[][] x, int[] y) {
            if (M < 0) {
                return new RandomForest(attributes, x, y, T);
            } else {
                return new RandomForest(attributes, x, y, T, M);
            }
        }
    }
    
    /**
     * Trains a regression tree.
     */
    static class TrainingTask implements Callable<DecisionTree> {
        /**
         * Attribute properties.
         */
        Attribute[] attributes;
        /**
         * Training instances.
         */
        double[][] x;
        /**
         * Training sample labels.
         */
        int[] y;
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
         * The out-of-bag predictions.
         */
        int[][] prediction;

        /**
         * Constructor.
         */
        TrainingTask(Attribute[] attributes, double[][] x, int[] y, int M, int[][] order, int[][] prediction) {
            this.attributes = attributes;
            this.x = x;
            this.y = y;
            this.order = order;
            this.M = M;
            this.prediction = prediction;
        }

        @Override
        public DecisionTree call() {            
            int n = x.length;
            Random random = new Random(Thread.currentThread().getId() * System.currentTimeMillis());
            int[] samples = new int[n]; // Training samples draw with replacement.
            for (int i = 0; i < n; i++) {
                samples[random.nextInt(n)]++;
            }
            
            DecisionTree tree = new DecisionTree(attributes, x, y, M, samples, order);
            
            for (int i = 0; i < n; i++) {
                if (samples[i] == 0) {
                    int p = tree.predict(x[i]);
                    synchronized (prediction[i]) {
                        prediction[i][p]++;
                    }
                }
            }
            
            return tree;
        }
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param T the number of trees.
     */
    public RandomForest(double[][] x, int[] y, int T) {
        this(null, x, y, T);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param T the number of trees.
     * @param M the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     */
    public RandomForest(double[][] x, int[] y, int T, int M) {
        this(null, x, y, T, M);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param T the number of trees.
     */
    public RandomForest(Attribute[] attributes, double[][] x, int[] y, int T) {
        this(attributes, x, y, T, (int) Math.floor(Math.sqrt(x[0].length)));        
    }
    
    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param T the number of trees.
     * @param M the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     */
    public RandomForest(Attribute[] attributes, double[][] x, int[] y, int T, int M) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (T < 1) {
            throw new IllegalArgumentException("Invlaid number of trees: " + T);
        }
        
        if (M < 1) {
            throw new IllegalArgumentException("Invalid number of variables for splitting: " + M);
        }
        
        // class label set.
        int[] labels = Math.unique(y);
        Arrays.sort(labels);
        
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]); 
            }
            
            if (i > 0 && labels[i] - labels[i-1] > 1) {
                throw new IllegalArgumentException("Missing class: " + labels[i]+1);                 
            }
        }

        k = labels.length;
        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");            
        }
        
        if (attributes == null) {
            int p = x[0].length;
            attributes = new Attribute[p];
            for (int i = 0; i < p; i++) {
                attributes[i] = new NumericAttribute("V" + (i + 1));
            }
        }

        int n = x.length;
        int[][] prediction = new int[n][k]; // out-of-bag prediction
        int[][] order = SmileUtils.sort(attributes, x);
        List<TrainingTask> tasks = new ArrayList<TrainingTask>();
        for (int i = 0; i < T; i++) {
            tasks.add(new TrainingTask(attributes, x, y, M, order, prediction));
        }
        
        try {
            trees = MulticoreExecutor.run(tasks);
        } catch (Exception ex) {
            System.err.println(ex);

            trees = new ArrayList<DecisionTree>(T);
            for (int i = 0; i < T; i++) {
                trees.add(tasks.get(i).call());
            }
        }
        
        int m = 0;
        for (int i = 0; i < n; i++) {
            int pred = Math.whichMax(prediction[i]);
            if (prediction[i][pred] > 0) {
                m++;
                if (pred != y[i]) {
                    error++;
                }
            }
        }

        if (m > 0) {
            error /= m;
        }
        
        importance = new double[attributes.length];
        for (DecisionTree tree : trees) {
            double[] imp = tree.importance();
            for (int i = 0; i < imp.length; i++) {
                importance[i] += imp[i];
            }
        }
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

        List<DecisionTree> model = new ArrayList<DecisionTree>(T);
        for (int i = 0; i < T; i++) {
            model.add(trees.get(i));
        }
        
        trees = model;
    }
    
    @Override
    public int predict(double[] x) {
        int[] y = new int[k];
        
        for (DecisionTree tree : trees) {
            y[tree.predict(x)]++;
        }
        
        return Math.whichMax(y);
    }
    
    @Override
    public int predict(double[] x, double[] posteriori) {
        if (posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        int[] y = new int[k];
        
        for (DecisionTree tree : trees) {
            y[tree.predict(x)]++;
        }
        
        double n = trees.size();
        for (int i = 0; i < k; i++) {
            posteriori[i] = y[i] / n;
        }
        
        return Math.whichMax(y);
    }    
    
    /**
     * Test the model on a validation dataset.
     * 
     * @param x the test data set.
     * @param y the test data response values.
     * @return accuracies with first 1, 2, ..., decision trees.
     */
    public double[] test(double[][] x, int[] y) {
        int T = trees.size();
        double[] accuracy = new double[T];

        int n = x.length;
        int[] label = new int[n];
        int[][] prediction = new int[n][k];

        Accuracy measure = new Accuracy();
        
        for (int i = 0; i < T; i++) {
            for (int j = 0; j < n; j++) {
                prediction[j][trees.get(i).predict(x[j])]++;
                label[j] = Math.whichMax(prediction[j]);
            }

            accuracy[i] = measure.measure(y, label);
        }

        return accuracy;
    }
    
    /**
     * Test the model on a validation dataset.
     * 
     * @param x the test data set.
     * @param y the test data labels.
     * @param measures the performance measures of classification.
     * @return performance measures with first 1, 2, ..., decision trees.
     */
    public double[][] test(double[][] x, int[] y, ClassificationMeasure[] measures) {
        int T = trees.size();
        int m = measures.length;
        double[][] results = new double[T][m];

        int n = x.length;
        int[] label = new int[n];
        double[][] prediction = new double[n][k];

        for (int i = 0; i < T; i++) {
            for (int j = 0; j < n; j++) {
                prediction[j][trees.get(i).predict(x[j])]++;
                label[j] = Math.whichMax(prediction[j]);
            }

            for (int j = 0; j < m; j++) {
                results[i][j] = measures[j].measure(y, label);
            }
        }
        return results;
    }
}
