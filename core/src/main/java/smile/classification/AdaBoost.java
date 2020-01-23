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

import java.util.Arrays;
import java.util.Properties;
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
public class AdaBoost implements SoftClassifier<Tuple>, DataFrameClassifier {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AdaBoost.class);

    /**
     * Design matrix formula
     */
    private Formula formula;
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
     * The class label encoder.
     */
    private IntSet labels;

    /**
     * Constructor.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param k the number of classes.
     * @param trees forest of decision trees.
     * @param alpha the weight of each decision tree.
     * @param error the weighted error of each decision tree during training.
     * @param importance variable importance
     */
    public AdaBoost(Formula formula, int k, DecisionTree[] trees, double[] alpha, double[] error, double[] importance) {
        this(formula, k, trees, alpha, error, importance, IntSet.of(k));
    }

    /**
     * Constructor.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param k the number of classes.
     * @param trees forest of decision trees.
     * @param alpha the weight of each decision tree.
     * @param error the weighted error of each decision tree during training.
     * @param importance variable importance
     * @param labels class labels
     */
    public AdaBoost(Formula formula, int k, DecisionTree[] trees, double[] alpha, double[] error, double[] importance, IntSet labels) {
        this.formula = formula;
        this.k = k;
        this.trees = trees;
        this.alpha = alpha;
        this.error = error;
        this.importance = importance;
        this.labels = labels;
    }

    /**
     * Fits a AdaBoost model.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static AdaBoost fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Fits a AdaBoost model.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static AdaBoost fit(Formula formula, DataFrame data, Properties prop) {
        int ntrees = Integer.valueOf(prop.getProperty("smile.adaboost.trees", "500"));
        int maxDepth = Integer.valueOf(prop.getProperty("smile.adaboost.max.depth", "20"));
        int maxNodes = Integer.valueOf(prop.getProperty("smile.adaboost.max.nodes", "6"));
        int nodeSize = Integer.valueOf(prop.getProperty("smile.adaboost.node.size", "1"));
        return fit(formula, data, ntrees, maxDepth, maxNodes, nodeSize);
    }

    /**
     * Fits a AdaBoost model.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param ntrees the number of trees.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree will
     *                 not split, setting nodeSize = 5 generally gives good results.
     */
    public static AdaBoost fit(Formula formula, DataFrame data, int ntrees, int maxDepth, int maxNodes, int nodeSize) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        DataFrame x = formula.x(data);
        BaseVector y = formula.y(data);

        ClassLabels codec = ClassLabels.fit(y);
        int[][] order = CART.order(x);

        int k = codec.k;
        int n = data.size();
        int[] samples = new int[n];
        double[] w = new double[n];
        boolean[] err = new boolean[n];
        for (int i = 0; i < n; i++) {
            w[i] = 1.0;
        }
        
        double guess = 1.0 / k; // accuracy of random guess.
        double b = Math.log(k - 1); // the bias to tree weight in case of multi-class.
        int failures = 0; // the number of weak classifiers less accurate than guess.

        DecisionTree[] trees = new DecisionTree[ntrees];
        double[] alpha = new double[ntrees];
        double[] error = new double[ntrees];
        for (int t = 0; t < ntrees; t++) {
            double W = MathEx.sum(w);
            for (int i = 0; i < n; i++) {
                w[i] /= W;
            }
            
            Arrays.fill(samples, 0);  
            int[] rand = MathEx.random(w, n);
            for (int s : rand) {
                samples[s]++;
            }

            logger.info("Training {} tree", Strings.ordinal(t+1));
            trees[t] = new DecisionTree(x, codec.y, codec.field, k, SplitRule.GINI, maxDepth, maxNodes, nodeSize, -1, samples, order);
            
            for (int i = 0; i < n; i++) {
                err[i] = trees[t].predict(x.get(i)) != y.getInt(i);
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
            alpha[t] = Math.log((1-e)/ Math.max(1E-10,e)) + b;
            double a = Math.exp(alpha[t]);
            for (int i = 0; i < n; i++) {
                if (err[i]) {
                    w[i] *= a;
                }
            }
        }
        
        double[] importance = new double[x.ncols()];
        for (DecisionTree tree : trees) {
            double[] imp = tree.importance();
            for (int i = 0; i < imp.length; i++) {
                importance[i] += imp[i];
            }
        }

        return new AdaBoost(formula, k, trees, alpha, error, importance, codec.labels);
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
     * Returns the decision trees.
     */
    public DecisionTree[] trees() {
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
        
        if (ntrees < trees.length) {
            trees = Arrays.copyOf(trees, ntrees);
            alpha = Arrays.copyOf(alpha, ntrees);
            error = Arrays.copyOf(error, ntrees);
        }
    }
    
    @Override
    public int predict(Tuple x) {
        Tuple xt = formula.x(x);
        double[] y = new double[k];

        for (int i = 0; i < trees.length; i++) {
            y[trees[i].predict(xt)] += alpha[i];
        }
            
        return labels.valueOf(MathEx.whichMax(y));
    }
    
    /**
     * Predicts the class label of an instance and also calculate a posteriori
     * probabilities. Not supported.
     */
    @Override
    public int predict(Tuple x, double[] posteriori) {
        Arrays.fill(posteriori, 0.0);

        for (int i = 0; i < trees.length; i++) {
            posteriori[trees[i].predict(x)] += alpha[i];
        }

        double sum = MathEx.sum(posteriori);
        for (int i = 0; i < k; i++) {
            posteriori[i] /= sum;
        }

        return labels.valueOf(MathEx.whichMax(posteriori));
    }
    
    /**
     * Test the model on a validation dataset.
     * 
     * @return the predictions with first 1, 2, ..., decision trees.
     */
    public int[][] test(DataFrame data) {
        DataFrame x = formula.x(data);

        int n = x.size();
        int ntrees = trees.length;
        int[][] prediction = new int[ntrees][n];

        if (k == 2) {
            for (int j = 0; j < n; j++) {
                Tuple xj = x.get(j);
                double base = 0;
                for (int i = 0; i < ntrees; i++) {
                    base += alpha[i] * trees[i].predict(xj);
                    prediction[i][j] = base > 0 ? 1 : 0;
                }
            }
        } else {
            double[] p = new double[k];
            for (int j = 0; j < n; j++) {
                Tuple xj = x.get(j);
                Arrays.fill(p, 0);
                for (int i = 0; i < ntrees; i++) {
                    p[trees[i].predict(xj)] += alpha[i];
                    prediction[i][j] = MathEx.whichMax(p);
                }
            }
        }
        
        return prediction;
    }
}

