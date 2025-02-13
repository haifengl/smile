/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression;

import java.io.Serial;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.base.cart.*;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.feature.importance.TreeSHAP;
import smile.math.MathEx;
import smile.util.IterativeAlgorithmController;
import smile.validation.RegressionMetrics;

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
 * so on. Hastie et al. comment that typically {@code 4 <= J <= 8} work well
 * for boosting and results are fairly insensitive to the choice of in
 * this range, J = 2 is insufficient for many applications, and {@code J > 10} is
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
 * &eta; {@code < 0.1}) yields dramatic improvements in model's generalization ability
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
public class GradientTreeBoost implements DataFrameRegression, TreeSHAP {
    @Serial
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GradientTreeBoost.class);

    /**
     * The model formula.
     */
    private final Formula formula;

    /**
     * Forest of regression trees.
     */
    private final RegressionTree[] trees;

    /**
     * The intercept.
     */
    private final double b;

    /**
     * Variable importance. Every time a split of a node is made on variable
     * the impurity criterion for the two descendent nodes is less than the
     * parent node. Adding up the decreases for each individual variable over
     * all trees in the forest gives a simple variable importance.
     */
    private final double[] importance;

    /**
     * The shrinkage parameter in (0, 1] controls the learning rate of procedure.
     */
    private final double shrinkage;

    /**
     * Constructor. Fits a gradient tree boosting for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param trees forest of regression trees.
     * @param b the intercept
     * @param shrinkage the shrinkage parameter in (0, 1] controls the learning rate of procedure.
     * @param importance variable importance
     */
    public GradientTreeBoost(Formula formula, RegressionTree[] trees, double b, double shrinkage, double[] importance) {
        this.formula = formula;
        this.trees = trees;
        this.b = b;
        this.shrinkage = shrinkage;
        this.importance = importance;
    }

    /**
     * Training status per tree.
     * @param tree the tree index, starting at 1.
     * @param loss the current loss function value.
     * @param metrics the optional validation metrics if test data is provided.
     */
    public record TrainingStatus(int tree, double loss, RegressionMetrics metrics) {

    }

    /**
     * Gradient tree boosting hyperparameters.
     * @param loss loss function for regression. By default, least absolute deviation
     *             is employed for robust regression.
     * @param ntrees the number of iterations (trees).
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the minimum size of leaf nodes.
     *                 Setting nodeSize = 5 generally gives good results.
     * @param shrinkage the shrinkage parameter in (0, 1] controls the learning rate of procedure.
     * @param subsample the sampling fraction for stochastic tree boosting.
     * @param test the optional test data for validation per epoch.
     * @param controller the optional training controller.
     */
    public record Options(Loss loss, int ntrees, int maxDepth, int maxNodes, int nodeSize, double shrinkage, double subsample,
                          DataFrame test, IterativeAlgorithmController<TrainingStatus> controller) {
        /** Constructor. */
        public Options {
            if (ntrees < 1) {
                throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
            }

            if (maxDepth < 2) {
                throw new IllegalArgumentException("Invalid maximal tree depth: " + maxDepth);
            }

            if (maxNodes < 2) {
                throw new IllegalArgumentException("Invalid maximum number of nodes: " + maxNodes);
            }

            if (nodeSize < 1) {
                throw new IllegalArgumentException("Invalid node size: " + nodeSize);
            }

            if (shrinkage <= 0 || shrinkage > 1) {
                throw new IllegalArgumentException("Invalid shrinkage: " + shrinkage);
            }

            if (subsample <= 0 || subsample > 1) {
                throw new IllegalArgumentException("Invalid sampling fraction: " + subsample);
            }
        }

        /**
         * Constructor with the least absolute deviation loss.
         * @param ntrees the number of iterations (trees).
         */
        public Options(int ntrees) {
            this(Loss.lad(), ntrees);
        }

        /**
         * Constructor.
         * @param loss loss function for regression.
         * @param ntrees the number of iterations (trees).
         */
        public Options(Loss loss, int ntrees) {
            this(loss, ntrees, 20, 6, 5, 0.05, 0.7, null, null);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.gradient_boost.loss", loss.toString());
            props.setProperty("smile.gradient_boost.trees", Integer.toString(ntrees));
            props.setProperty("smile.gradient_boost.max_depth", Integer.toString(maxDepth));
            props.setProperty("smile.gradient_boost.max_nodes", Integer.toString(maxNodes));
            props.setProperty("smile.gradient_boost.node_size", Integer.toString(nodeSize));
            props.setProperty("smile.gradient_boost.shrinkage", Double.toString(shrinkage));
            props.setProperty("smile.gradient_boost.sampling_rate", Double.toString(subsample));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            Loss loss = Loss.valueOf(props.getProperty("smile.gradient_boost.loss", "LeastAbsoluteDeviation"));
            int ntrees = Integer.parseInt(props.getProperty("smile.gradient_boost.trees", "500"));
            int maxDepth = Integer.parseInt(props.getProperty("smile.gradient_boost.max_depth", "20"));
            int maxNodes = Integer.parseInt(props.getProperty("smile.gradient_boost.max_nodes", "6"));
            int nodeSize = Integer.parseInt(props.getProperty("smile.gradient_boost.node_size", "5"));
            double shrinkage = Double.parseDouble(props.getProperty("smile.gradient_boost.shrinkage", "0.05"));
            double subsample = Double.parseDouble(props.getProperty("smile.gradient_boost.sampling_rate", "0.7"));
            return new Options(loss, ntrees, maxDepth, maxNodes, nodeSize, shrinkage, subsample, null, null);
        }
    }

    /**
     * Fits a gradient tree boosting for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @return the model.
     */
    public static GradientTreeBoost fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Options(500));
    }

    /**
     * Fits a gradient tree boosting for regression.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static GradientTreeBoost fit(Formula formula, DataFrame data, Options options) {
        long startTime = System.nanoTime();
        formula = formula.expand(data.schema());
        DataFrame x = formula.x(data);
        double[] y = formula.y(data).toDoubleArray();

        var loss = options.loss;
        var ntrees = options.ntrees;
        var shrinkage = options.shrinkage;
        final int n = x.size();
        final int N = (int) Math.round(n * options.subsample);
        final int[][] order = CART.order(x);

        int[] permutation = IntStream.range(0, n).toArray();
        int[] samples = new int[n];

        StructField field = new StructField("residual", DataTypes.DoubleType);
        double b = loss.intercept(y);
        double[] residual = loss.residual();

        DataFrame testx = null;
        double[] testy = null;
        double[] prediction = null;
        if (options.test != null) {
            testx = formula.x(options.test);
            testy = formula.y(options.test).toDoubleArray();
            prediction = new double[testy.length];
            Arrays.fill(prediction, b);
        }

        RegressionTree[] trees = new RegressionTree[ntrees];
        for (int t = 0; t < ntrees; t++) {
            Arrays.fill(samples, 0);
            MathEx.permutate(permutation);
            for (int i = 0; i < N; i++) {
                samples[permutation[i]]++;
            }

            trees[t] = new RegressionTree(x, loss, field, options.maxDepth, options.maxNodes, options.nodeSize, x.ncol(), samples, order);

            for (int i = 0; i < n; i++) {
                residual[i] -= shrinkage * trees[t].predict(x.get(i));
            }
            double lossValue = loss.value();
            logger.info("Tree {}: loss = {}", t+1, lossValue);

            double fitTime = (System.nanoTime() - startTime) / 1E6;
            RegressionMetrics metrics = null;
            if (options.test != null) {
                long testStartTime = System.nanoTime();
                var tree = trees[t];
                for (int i = 0; i < testy.length; i++) {
                    prediction[i] += shrinkage * tree.predict(testx.get(i));
                }
                double scoreTime = (System.nanoTime() - testStartTime) / 1E6;
                metrics = RegressionMetrics.of(fitTime, scoreTime, testy, prediction);
                logger.info("Validation metrics = {} ", metrics);
            }

            if (options.controller != null) {
                options.controller.submit(new TrainingStatus(t+1, lossValue, metrics));

                if (options.controller.isInterrupted()) {
                    trees = Arrays.copyOf(trees, t);
                    break;
                }
            }
        }
        
        double[] importance = new double[x.ncol()];
        for (RegressionTree tree : trees) {
            double[] imp = tree.importance();
            for (int i = 0; i < imp.length; i++) {
                importance[i] += imp[i];
            }
        }

        return new GradientTreeBoost(formula, trees, b, shrinkage, importance);
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
     * Returns the number of trees in the model.
     * 
     * @return the number of trees in the model 
     */
    public int size() {
        return trees.length;
    }

    @Override
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
     * @return the trimmed model.
     */
    public GradientTreeBoost trim(int ntrees) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid new model size: " + ntrees);
        }

        if (ntrees > trees.length) {
            throw new IllegalArgumentException("The new model size is larger than the current size.");
        }

        return new GradientTreeBoost(formula, Arrays.copyOf(trees, ntrees), b, shrinkage, importance);
    }
    
    @Override
    public double predict(Tuple x) {
        Tuple xt = formula.x(x);
        double y = b;
        for (RegressionTree tree : trees) {
            y += shrinkage * tree.predict(xt);
        }
        
        return y;
    }

    /**
     * Test the model on a validation dataset.
     *
     * @param data the test data set.
     * @return the predictions with first 1, 2, ..., regression trees.
     */
    public double[][] test(DataFrame data) {
        DataFrame x = formula.x(data);

        int n = x.size();
        int ntrees = trees.length;
        double[][] prediction = new double[ntrees][n];

        for (int j = 0; j < n; j++) {
            Tuple xj = x.get(j);
            double base = b;
            for (int i = 0; i < ntrees; i++) {
                base += shrinkage * trees[i].predict(xj);
                prediction[i][j] = base;
            }
        }

        return prediction;
    }
}
