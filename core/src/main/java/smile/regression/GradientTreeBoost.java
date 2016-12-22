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
import java.util.Arrays;

import smile.data.Attribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.sort.QuickSelect;
import smile.util.SmileUtils;
import smile.validation.RMSE;
import smile.validation.RegressionMeasure;

/**
 * Gradient boosting for regression. Gradient boosting is typically used
 * with decision trees (especially CART regression trees) of a fixed size as
 * base learners. For this special case Friedman proposes a modification to
 * gradient boosting method which improves the quality of fit of each base
 * learner.
 * <p>
 * Generic gradient boosting at the t-th step would fit a regression tree to
 * pseudo-residuals. Let J be the number of its leaves. The tree partitions
 * the input space into J disjoint regions and predicts a constant value in
 * each region. The parameter J controls the maximum allowed
 * level of interaction between variables in the model. With J = 2 (decision
 * stumps), no interaction between variables is allowed. With J = 3 the model
 * may include effects of the interaction between up to two variables, and
 * so on. Hastie et al. comment that typically 4 &le; J &le; 8 work well
 * for boosting and results are fairly insensitive to the choice of in
 * this range, J = 2 is insufficient for many applications, and J &gt; 10 is
 * unlikely to be required.
 * <p>
 * Fitting the training set too closely can lead to degradation of the model's
 * generalization ability. Several so-called regularization techniques reduce
 * this over-fitting effect by constraining the fitting procedure.
 * One natural regularization parameter is the number of gradient boosting
 * iterations T (i.e. the number of trees in the model when the base learner
 * is a decision tree). Increasing T reduces the error on training set,
 * but setting it too high may lead to over-fitting. An optimal value of T
 * is often selected by monitoring prediction error on a separate validation
 * data set.
 * <p>
 * Another regularization approach is the shrinkage which times a parameter
 * &eta; (called the "learning rate") to update term.
 * Empirically it has been found that using small learning rates (such as
 * &eta; &lt; 0.1) yields dramatic improvements in model's generalization ability
 * over gradient boosting without shrinking (&eta; = 1). However, it comes at
 * the price of increasing computational time both during training and
 * prediction: lower learning rate requires more iterations.
 * <p>
 * Soon after the introduction of gradient boosting Friedman proposed a
 * minor modification to the algorithm, motivated by Breiman's bagging method.
 * Specifically, he proposed that at each iteration of the algorithm, a base
 * learner should be fit on a subsample of the training set drawn at random
 * without replacement. Friedman observed a substantional improvement in
 * gradient boosting's accuracy with this modification.
 * <p>
 * Subsample size is some constant fraction f of the size of the training set.
 * When f = 1, the algorithm is deterministic and identical to the one
 * described above. Smaller values of f introduce randomness into the
 * algorithm and help prevent over-fitting, acting as a kind of regularization.
 * The algorithm also becomes faster, because regression trees have to be fit
 * to smaller datasets at each iteration. Typically, f is set to 0.5, meaning
 * that one half of the training set is used to build each base learner.
 * <p>
 * Also, like in bagging, sub-sampling allows one to define an out-of-bag
 * estimate of the prediction performance improvement by evaluating predictions
 * on those observations which were not used in the building of the next
 * base learner. Out-of-bag estimates help avoid the need for an independent
 * validation dataset, but often underestimate actual performance improvement
 * and the optimal number of iterations.
 * <p>
 * Gradient tree boosting implementations often also use regularization by
 * limiting the minimum number of observations in trees' terminal nodes.
 * It's used in the tree building process by ignoring any splits that lead
 * to nodes containing fewer than this number of training set instances.
 * Imposing this limit helps to reduce variance in predictions at leaves.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> J. H. Friedman. Greedy Function Approximation: A Gradient Boosting Machine, 1999.</li>
 * <li> J. H. Friedman. Stochastic Gradient Boosting, 1999.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class GradientTreeBoost implements Regression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Regression loss function.
     */
    public static enum Loss {
        /**
         * Least squares regression. Least-squares is highly efficient for
         * normally distributed errors but is prone to long tails and outliers.
         */
        LeastSquares,
        /**
         * Least absolute deviation regression. The gradient tree boosting based
         * on this loss function is highly robust. The trees use only order
         * information on the input variables and the pseudo-response has only
         * two values {-1, +1}. The line searches (terminal node values) use
         * only medians.
         */
        LeastAbsoluteDeviation,
        /**
         * Huber loss function for M-regression, which attempts resistance to
         * long-tailed error distributions and outliers while maintaining high
         * efficency for normally distributed errors.
         */
        Huber,
    }
    
    /**
     * Forest of regression trees.
     */
    private RegressionTree[] trees;
    /**
     * The intercept.
     */
    private double b = 0.0;
    /**
     * Variable importance. Every time a split of a node is made on variable
     * the impurity criterion for the two descendent nodes is less than the
     * parent node. Adding up the decreases for each individual variable over
     * all trees in the forest gives a simple variable importance.
     */
    private double[] importance;
    /**
     * Loss function.
     */
    private Loss loss = Loss.LeastAbsoluteDeviation;
    /**
     * The shrinkage parameter in (0, 1] controls the learning rate of procedure.
     */
    private double shrinkage = 0.005;
    /**
     * The number of leaves in each tree.
     */
    private int maxNodes = 6;
    /**
     * The number of trees.
     */
    private int ntrees = 500;
    /**
     * The sampling rate for stochastic tree boosting.
     */
    private double f = 0.7;
    
    /**
     * Trainer for GradientTreeBoost regression.
     */
    public static class Trainer extends RegressionTrainer<double[]> {
        /**
         * Loss function.
         */
        private Loss loss = Loss.LeastAbsoluteDeviation;
        /**
         * The number of trees.
         */
        private int ntrees = 500;
        /**
         * The shrinkage parameter in (0, 1] controls the learning rate of procedure.
         */
        private double shrinkage = 0.005;
        /**
         * The number of leaves in each tree.
         */
        private int maxNodes = 6;
        /**
         * The sampling rate for stochastic tree boosting.
         */
        private double f = 0.7;
        
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
         * Sets the loss function.
         * @param loss the loss function.
         */
        public Trainer setLoss(Loss loss) {
            this.loss = loss;
            return this;
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
         * Sets the maximum number of leaf nodes in the tree.
         * @param maxNodes the maximum number of leaf nodes in the tree.
         */
        public Trainer setMaxNodes(int maxNodes) {
            if (maxNodes < 2) {
                throw new IllegalArgumentException("Invalid number of leaf nodes: " + maxNodes);
            }
            
            this.maxNodes = maxNodes;
            return this;
        }
        
        /**
         * Sets the shrinkage parameter in (0, 1] controls the learning rate of procedure.
         * @param shrinkage the learning rate.
         */
        public Trainer setShrinkage(double shrinkage) {
            if (shrinkage <= 0 || shrinkage > 1) {
                throw new IllegalArgumentException("Invalid shrinkage: " + shrinkage);
            }

            this.shrinkage = shrinkage;
            return this;
        }

        /**
         * Sets the sampling rate for stochastic tree boosting.
         * @param f the sampling rate for stochastic tree boosting.
         */
        public Trainer setSamplingRates(double f) {
            if (f <= 0 || f > 1) {
                throw new IllegalArgumentException("Invalid sampling fraction: " + f);
            }

            this.f = f;
            return this;
        }
        
        @Override
        public GradientTreeBoost train(double[][] x, double[] y) {
            return new GradientTreeBoost(attributes, x, y, loss, ntrees, maxNodes, shrinkage, f);
        }
    }
    
    /**
     * Constructor. Learns a gradient tree boosting for regression.
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of iterations (trees).
     */
    public GradientTreeBoost(double[][] x, double[] y, int ntrees) {
        this(null, x, y, ntrees);
    }
    
    /**
     * Constructor. Learns a gradient tree boosting for regression.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param loss loss function for regression. By default, least absolute
     * deviation is employed for robust regression.
     * @param ntrees the number of iterations (trees).
     * @param maxNodes the number of leaves in each tree.
     * @param shrinkage the shrinkage parameter in (0, 1] controls the learning rate of procedure.
     * @param f the sampling rate for stochastic tree boosting.
     */
    public GradientTreeBoost(double[][] x, double[] y, Loss loss, int ntrees, int maxNodes, double shrinkage, double f) {
        this(null, x, y, loss, ntrees, maxNodes, shrinkage, f);
    }

    /**
     * Constructor. Learns a gradient tree boosting for regression.
     * 
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of iterations (trees).
     */
    public GradientTreeBoost(Attribute[] attributes, double[][] x, double[] y, int ntrees) {
        this(attributes, x, y, Loss.LeastAbsoluteDeviation, ntrees, 6, x.length < 2000 ? 0.005 : 0.05, 0.7);
    }
    
    /**
     * Constructor. Learns a gradient tree boosting for regression.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param loss loss function for regression. By default, least absolute
     * deviation is employed for robust regression.
     * @param ntrees the number of iterations (trees).
     * @param maxNodes the number of leaves in each tree.
     * @param shrinkage the shrinkage parameter in (0, 1] controls the learning rate of procedure.
     * @param f the sampling fraction for stochastic tree boosting.
     */
    public GradientTreeBoost(Attribute[] attributes, double[][] x, double[] y, Loss loss, int ntrees, int maxNodes, double shrinkage, double f) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }
        
        if (shrinkage <= 0 || shrinkage > 1) {
            throw new IllegalArgumentException("Invalid shrinkage: " + shrinkage);            
        }

        if (f <= 0 || f > 1) {
            throw new IllegalArgumentException("Invalid sampling fraction: " + f);            
        }
        
        if (attributes == null) {
            int p = x[0].length;
            attributes = new Attribute[p];
            for (int i = 0; i < p; i++) {
                attributes[i] = new NumericAttribute("V" + (i + 1));
            }
        }
        
        this.loss = loss;
        this.ntrees = ntrees;
        this.maxNodes = maxNodes;
        this.shrinkage = shrinkage;
        this.f = f;

        int n = x.length;
        int N = (int) Math.round(n * f);
        
        int[] perm = new int[n];
        int[] samples = new int[n];
        for (int i = 0; i < n; i++) {
            perm[i] = i;
        }        
        
        double[] residual = new double[n];
        double[] response = null; // response variable for regression tree.
        
        RegressionTree.NodeOutput output = null;
        if (loss == Loss.LeastSquares) {
            response = residual;
            b = Math.mean(y);
            for (int i = 0; i < n; i++) {
                residual[i] = y[i] - b;
            }
        } else if (loss == Loss.LeastAbsoluteDeviation) {
            output = new LADNodeOutput(residual);
            System.arraycopy(y, 0, residual, 0, n);
            b = QuickSelect.median(residual);
            response = new double[n];
            for (int i = 0; i < n; i++) {
                residual[i] = y[i] - b;
                response[i] = Math.signum(residual[i]);
            }
        } else if (loss == Loss.Huber) {
            response = new double[n];
            System.arraycopy(y, 0, residual, 0, n);
            b = QuickSelect.median(residual);
            for (int i = 0; i < n; i++) {
                residual[i] = y[i] - b;
            }
        }
        
        int[][] order = SmileUtils.sort(attributes, x);        
        trees = new RegressionTree[ntrees];

        for (int m = 0; m < ntrees; m++) {
            Arrays.fill(samples, 0);
            
            Math.permutate(perm);
            for (int i = 0; i < N; i++) {
                samples[perm[i]] = 1;
            }
            
            if (loss == Loss.Huber) {
                output = new HuberNodeOutput(residual, response, 0.9);                
            }
            
            trees[m] = new RegressionTree(attributes, x, response, maxNodes, 5, x[0].length, order, samples, output);
            
            for (int i = 0; i < n; i++) {
                residual[i] -= shrinkage * trees[m].predict(x[i]);
                if (loss == Loss.LeastAbsoluteDeviation) {
                    response[i] = Math.signum(residual[i]);
                }
            }
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
     * Returns the variable importance. Every time a split of a node is made
     * on variable the impurity criterion for the two descendant nodes is less
     * than the parent node. Adding up the decreases for each individual
     * variable over all trees in the forest gives a simple measure of variable
     * importance.
     *
     * @return the variable importance
     */
    public double[] importance() {
        return importance;
    }
    
    /**
     * Class to calculate node output for LAD regression.
     */
    class LADNodeOutput implements RegressionTree.NodeOutput {

        /**
         * Residuals to fit.
         */
        double[] residual;
        /**
         * Constructor.
         * @param residual response to fit.
         */
        public LADNodeOutput(double[] residual) {
            this.residual = residual;
        }
        
        @Override
        public double calculate(int[] samples) {
            int n = 0;
            for (int s : samples) {
                if (s > 0) n++;
            }
            
            double[] r = new double[n];
            for (int i = 0, j = 0; i < samples.length; i++) {
                if (samples[i] > 0) {
                    r[j++] = residual[i];
                }
            }
            
            return QuickSelect.median(r);
        }        
    }
    
    /**
     * Returns the sampling rate for stochastic gradient tree boosting.
     * @return the sampling rate for stochastic gradient tree boosting.
     */
    public double getSamplingRate() {
    	return f;
    }
  
    /**
     * Returns the maximum number of leaves in decision tree.
     * @return the maximum number of leaves in decision tree.
     */
    public int getmaxNodes() {
    	return maxNodes;
    }
    
    /**
     * Returns the loss function.
     * @return the loss function.
     */
    public Loss getLossFunction() {
    	return loss;
    }
    
    /**
     * Class to calculate node output for Huber regression.
     */
    class HuberNodeOutput implements RegressionTree.NodeOutput {

        /**
         * Residuals.
         */
        double[] residual;
        /**
         * Responses to fit.
         */
        double[] response;
        /**
         * The value to choose &alpha;-quantile of residuals.
         */
        double alpha;
        /**
         * Cutoff.
         */
        double delta;
        /**
         * Constructor.
         */
        public HuberNodeOutput(double[] residual, double[] response, double alpha) {
            this.residual = residual;
            this.response = response;
            this.alpha = alpha;
            
            int n = residual.length;
            for (int i = 0; i < n; i++) {
                response[i] = Math.abs(residual[i]);
            }
            
            delta = QuickSelect.select(response, (int) (n * alpha));
            
            for (int i = 0; i < n; i++) {
                if (Math.abs(residual[i]) <= delta) {
                    response[i] = residual[i];
                } else {
                    response[i] = delta * Math.signum(residual[i]);
                }
            }
        }
        
        @Override
        public double calculate(int[] samples) {
            int n = 0;
            for (int s : samples) {
                if (s > 0) n++;
            }
            
            double[] res = new double[n];
            for (int i = 0, j = 0; i < samples.length; i++) {
                if (samples[i] > 0) {
                    res[j++] = residual[i];
                }
            }
            
            double r = QuickSelect.median(res);
            double output = 0.0;
            for (int i = 0; i < samples.length; i++) {
                if (samples[i] > 0) {
                    double d = residual[i] - r;
                    output += Math.signum(d) * Math.min(delta, Math.abs(d));
                }
            }
            
            output = r + output / n;
            return output;
        }        
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
        
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid new model size: " + ntrees);
        }
        
        if (ntrees < trees.length) {
            trees = Arrays.copyOf(trees, ntrees);
            this.ntrees = ntrees;
        }
    }
    
    @Override
    public double predict(double[] x) {
        double y = b;
        for (int i = 0; i < ntrees; i++) {
            y += shrinkage * trees[i].predict(x);
        }
        
        return y;
    }    
    
    /**
     * Test the model on a validation dataset.
     * 
     * @param x the test data set.
     * @param y the test data response values.
     * @return RMSEs with first 1, 2, ..., regression trees.
     */
    public double[] test(double[][] x, double[] y) {
        double[] rmse = new double[ntrees];

        int n = x.length;
        double[] prediction = new double[n];
        Arrays.fill(prediction, b);

        RMSE measure = new RMSE();
        
        for (int i = 0; i < ntrees; i++) {
            for (int j = 0; j < n; j++) {
                prediction[j] += shrinkage * trees[i].predict(x[j]);
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
        int m = measures.length;
        double[][] results = new double[ntrees][m];

        int n = x.length;
        double[] prediction = new double[n];
        Arrays.fill(prediction, b);

        for (int i = 0; i < ntrees; i++) {
            for (int j = 0; j < n; j++) {
                prediction[j] += shrinkage * trees[i].predict(x[j]);
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
        return trees;
    }
}
