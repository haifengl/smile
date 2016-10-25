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
import java.io.Serializable;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.data.Attribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.util.SmileUtils;
import smile.validation.Accuracy;
import smile.validation.ClassificationMeasure;

/**
 * AdaBoost (Adaptive Boosting) classifier with decision trees. In principle,
 * AdaBoost is a meta-algorithm, and can be used in conjunction with many other
 * learning algorithms to improve their performance. In practice, AdaBoost with
 * decision trees is probably the most popular combination. AdaBoost is adaptive
 * in the sense that subsequent classifiers built are tweaked in favor of those
 * instances misclassified by previous classifiers. AdaBoost is sensitive to
 * noisy data and outliers. However in some problems it can be less susceptible
 * to the over-fitting problem than most learning algorithms.
 * <p>
 * AdaBoost calls a weak classifier repeatedly in a series of rounds from
 * total T classifiers. For each call a distribution of weights is updated
 * that indicates the importance of examples in the data set for the
 * classification. On each round, the weights of each incorrectly classified
 * example are increased (or alternatively, the weights of each correctly
 * classified example are decreased), so that the new classifier focuses more
 * on those examples.
 * <p>
 * The basic AdaBoost algorithm is only for binary classification problem.
 * For multi-class classification, a common approach is reducing the
 * multi-class classification problem to multiple two-class problems.
 * This implementation is a multi-class AdaBoost without such reductions.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Yoav Freund, Robert E. Schapire. A Decision-Theoretic Generalization of on-Line Learning and an Application to Boosting, 1995.</li>
 * <li> Ji Zhu, Hui Zhou, Saharon Rosset and Trevor Hastie. Multi-class Adaboost, 2009.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class AdaBoost implements SoftClassifier<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(AdaBoost.class);
    private static final String INVALID_NUMBER_OF_TREES = "Invalid number of trees: ";

    /**
     * The number of classes.
     */
    private int k;
    /**
     * Forest of decision trees.
     */
    private DecisionTree[] trees;
    /**
     * The weight of each decision tree.
     */
    private double[] alpha;
    /**
     * The weighted error of each decision tree during training.
     */
    private double[] error;
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
     * Trainer for AdaBoost classifiers.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {
        /**
         * The number of trees.
         */
        private int ntrees = 500;
        /**
         * The maximum number of leaf nodes in the tree.
         */
        private int maxNodes = 2;

        /**
         * Default constructor of 500 trees and maximal 2 leaf nodes in the tree.
         */
        public Trainer() {

        }

        /**
         * Constructor.
         * 
         * @param ntrees the number of trees.
         */
        public Trainer(int ntrees) {
            if (ntrees < 1) {
                throw new IllegalArgumentException(INVALID_NUMBER_OF_TREES + ntrees);
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
                throw new IllegalArgumentException(INVALID_NUMBER_OF_TREES + ntrees);
            }

            this.ntrees = ntrees;
        }
        
        /**
         * Sets the number of trees in the random forest.
         * @param ntrees the number of trees.
         */
        public Trainer setNumTrees(int ntrees) {
            if (ntrees < 1) {
                throw new IllegalArgumentException(INVALID_NUMBER_OF_TREES + ntrees);
            }

            this.ntrees = ntrees;
            return this;
        }
        
        /**
         * Sets the maximum number of leaf nodes in the tree.
         * @param maxNodes the maximum number of leaf nodes in the tree.
         */
        public Trainer setMaxNodes(int maxNodes) {
            if (maxNodes < 2) {
                throw new IllegalArgumentException("Invalid maximum number of leaf nodes: " + maxNodes);
            }
            
            this.maxNodes = maxNodes;
            return this;
        }
        
        @Override
        public AdaBoost train(double[][] x, int[] y) {
            return new AdaBoost(attributes, x, y, ntrees, maxNodes);
        }
    }
    
    /**
     * Constructor. Learns AdaBoost with decision stumps.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of trees.
     */
    public AdaBoost(double[][] x, int[] y, int ntrees) {
        this(null, x, y, ntrees);
    }

    /**
     * Constructor. Learns AdaBoost with decision trees.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param maxNodes the maximum number of leaf nodes in the trees.
     */
    public AdaBoost(double[][] x, int[] y, int ntrees, int maxNodes) {
        this(null, x, y, ntrees, maxNodes);
    }

    /**
     * Constructor. Learns AdaBoost with decision stumps.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of trees.
     */
    public AdaBoost(Attribute[] attributes, double[][] x, int[] y, int ntrees) {
        this(attributes, x, y, ntrees, 2);
    }
    /**
     * Constructor.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param maxNodes the maximum number of leaf nodes in the trees.
     */
    public AdaBoost(Attribute[] attributes, double[][] x, int[] y, int ntrees, int maxNodes) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (ntrees < 1) {
            throw new IllegalArgumentException(INVALID_NUMBER_OF_TREES + ntrees);
        }
        
        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum leaves: " + maxNodes);
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

        int[][] order = SmileUtils.sort(attributes, x);
        
        int n = x.length;
        int[] samples = new int[n];
        double[] w = new double[n];
        boolean[] err = new boolean[n];
        for (int i = 0; i < n; i++) {
            w[i] = 1.0;
        }
        
        double guess = 1.0 / k; // accuracy of random guess.
        double b = Math.log(k - 1); // the bias to tree weight in case of multi-class.
        int failures = 0; // the number of weak classifiers less accurate than guess.
        
        trees = new DecisionTree[ntrees];
        alpha = new double[ntrees];
        error = new double[ntrees];
        for (int t = 0; t < ntrees; t++) {
            double W = Math.sum(w);
            for (int i = 0; i < n; i++) {
                w[i] /= W;
            }
            
            Arrays.fill(samples, 0);  
            int[] rand = Math.random(w, n);
            for (int s : rand) {
                samples[s]++;
            }
            
            trees[t] = new DecisionTree(attributes, x, y, maxNodes, 1, x[0].length, DecisionTree.SplitRule.GINI, samples, order);
            
            for (int i = 0; i < n; i++) {
                err[i] = trees[t].predict(x[i]) != y[i];
            }
            
            double e = 0.0; // weighted error
            for (int i = 0; i < n; i++) {
                if (err[i]) {
                    e += w[i];
                }
            }
            
            if (1 - e <= guess) {
                logger.error(String.format("Skip the weak classifier %d makes %.2f%% weighted error", t, 100*e));
                if (++failures > 3) {
                    trees = Arrays.copyOf(trees, t);
                    alpha = Arrays.copyOf(alpha, t);
                    error = Arrays.copyOf(error, t);
                    break;
                } else {
                    t--;
                    continue;
                }
            } else failures = 0;
            
            error[t] = e;
            alpha[t] = Math.log((1-e)/Math.max(1E-10,e)) + b;
            double a = Math.exp(alpha[t]);
            for (int i = 0; i < n; i++) {
                if (err[i]) {
                    w[i] *= a;
                }
            }
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
     * Returns the variable importance. Every time a split of a node is made
     * on variable the (GINI, information gain, etc.) impurity criterion for
     * the two descendent nodes is less than the parent node. Adding up the
     * decreases for each individual variable over all trees in the forest
     * gives a simple measure of variable importance.
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
        
        if (ntrees < trees.length) {
            trees = Arrays.copyOf(trees, ntrees);
            alpha = Arrays.copyOf(alpha, ntrees);
            error = Arrays.copyOf(error, ntrees);
        }
    }
    
    @Override
    public int predict(double[] x) {
        double[] y = new double[k];

        for (int i = 0; i < trees.length; i++) {
            y[trees[i].predict(x)] += alpha[i];
        }
            
        return Math.whichMax(y);
    }
    
    /**
     * Predicts the class label of an instance and also calculate a posteriori
     * probabilities. Not supported.
     */
    @Override
    public int predict(double[] x, double[] posteriori) {
        Arrays.fill(posteriori, 0.0);

        for (int i = 0; i < trees.length; i++) {
            posteriori[trees[i].predict(x)] += alpha[i];
        }

        double sum = Math.sum(posteriori);
        for (int i = 0; i < k; i++) {
            posteriori[i] /= sum;
        }

        return Math.whichMax(posteriori);
    }
    
    /**
     * Test the model on a validation dataset.
     * 
     * @param x the test data set.
     * @param y the test data response values.
     * @return accuracies with first 1, 2, ..., decision trees.
     */
    public double[] test(double[][] x, int[] y) {
        int T = trees.length;
        double[] accuracy = new double[T];

        int n = x.length;
        int[] label = new int[n];

        Accuracy measure = new Accuracy();
        
        if (k == 2) {
            double[] prediction = new double[n];
            for (int i = 0; i < T; i++) {
                for (int j = 0; j < n; j++) {
                    prediction[j] += alpha[i] * trees[i].predict(x[j]);
                    label[j] = prediction[j] > 0 ? 1 : 0;
                }
                accuracy[i] = measure.measure(y, label);
            }
        } else {
            double[][] prediction = new double[n][k];
            for (int i = 0; i < T; i++) {
                for (int j = 0; j < n; j++) {
                    prediction[j][trees[i].predict(x[j])] += alpha[i];
                    label[j] = Math.whichMax(prediction[j]);
                }

                accuracy[i] = measure.measure(y, label);
            }
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
        int T = trees.length;
        int m = measures.length;
        double[][] results = new double[T][m];

        int n = x.length;
        int[] label = new int[n];

        if (k == 2) {
            double[] prediction = new double[n];
            for (int i = 0; i < T; i++) {
                for (int j = 0; j < n; j++) {
                    prediction[j] += alpha[i] * trees[i].predict(x[j]);
                    label[j] = prediction[j] > 0 ? 1 : 0;
                }

                for (int j = 0; j < m; j++) {
                    results[i][j] = measures[j].measure(y, label);
                }
            }
        } else {
            double[][] prediction = new double[n][k];
            for (int i = 0; i < T; i++) {
                for (int j = 0; j < n; j++) {
                    prediction[j][trees[i].predict(x[j])] += alpha[i];
                    label[j] = Math.whichMax(prediction[j]);
                }

                for (int j = 0; j < m; j++) {
                    results[i][j] = measures[j].measure(y, label);
                }
            }

        }
        
        return results;
    }

    /**
     * Returns the decision trees.
     */
    public DecisionTree[] getTrees() {
        return trees;
    }
}

