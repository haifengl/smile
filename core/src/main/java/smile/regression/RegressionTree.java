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
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import smile.data.Attribute;
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.sort.QuickSort;
import smile.util.MulticoreExecutor;

/**
 * Decision tree for regression. A decision tree can be learned by
 * splitting the training set into subsets based on an attribute value
 * test. This process is repeated on each derived subset in a recursive
 * manner called recursive partitioning.
 * <p>
 * Classification and Regression Tree techniques have a number of advantages
 * over many of those alternative techniques.
 * <dl>
 * <dt>Simple to understand and interpret.</dt>
 * <dd>In most cases, the interpretation of results summarized in a tree is
 * very simple. This simplicity is useful not only for purposes of rapid
 * classification of new observations, but can also often yield a much simpler
 * "model" for explaining why observations are classified or predicted in a
 * particular manner.</dd>
 * <dt>Able to handle both numerical and categorical data.</dt>
 * <dd>Other techniques are usually specialized in analyzing datasets that
 * have only one type of variable. </dd>
 * <dt>Tree methods are nonparametric and nonlinear.</dt>
 * <dd>The final results of using tree methods for classification or regression
 * can be summarized in a series of (usually few) logical if-then conditions
 * (tree nodes). Therefore, there is no implicit assumption that the underlying
 * relationships between the predictor variables and the dependent variable
 * are linear, follow some specific non-linear link function, or that they
 * are even monotonic in nature. Thus, tree methods are particularly well
 * suited for data mining tasks, where there is often little a priori
 * knowledge nor any coherent set of theories or predictions regarding which
 * variables are related and how. In those types of data analytics, tree
 * methods can often reveal simple relationships between just a few variables
 * that could have easily gone unnoticed using other analytic techniques.</dd>
 * </dl>
 * One major problem with classification and regression trees is their high
 * variance. Often a small change in the data can result in a very different
 * series of splits, making interpretation somewhat precarious. Besides,
 * decision-tree learners can create over-complex trees that cause over-fitting.
 * Mechanisms such as pruning are necessary to avoid this problem.
 * Another limitation of trees is the lack of smoothness of the prediction
 * surface.
 * <p>
 * Some techniques such as bagging, boosting, and random forest use more than
 * one decision tree for their analysis.
 * 
 * @see GradientTreeBoost
 * @see RandomForest
 *  
 * @author Haifeng Li
 */
public class RegressionTree implements Regression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The attributes of independent variable.
     */
    private Attribute[] attributes;
    /**
     * Variable importance. Every time a split of a node is made on variable
     * the impurity criterion for the two descendent nodes is less than the
     * parent node. Adding up the decreases for each individual variable
     * over the tree gives a simple measure of variable importance.
     */
    private double[] importance;
    /**
     * The root of the regression tree
     */
    private Node root;
    /**
     * The number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     */
    private int nodeSize = 5;
    /**
     * The maximum number of leaf nodes in the tree.
     */
    private int maxNodes = 6;
    /**
     * The number of input variables to be used to determine the decision
     * at a node of the tree.
     */
    private int mtry;
    /**
     * The number of binary features.
     */
    private int numFeatures;
    /**
     * The index of training values in ascending order. Note that only numeric
     * attributes will be sorted.
     */
    private transient int[][] order;

    /**
     * Trainer for regression tree.
     */
    public static class Trainer extends RegressionTrainer<double[]> {
        /**
         * The minimum size of leaf nodes.
         */
        private int nodeSize = 1;
        /**
         * The maximum number of leaf nodes in the tree.
         */
        private int maxNodes = 100;
        /**
         * The number of sparse binary features.
         */
        private int numFeatures = -1;

        /**
         * Constructor.
         * 
         * @param maxNodes the maximum number of leaf nodes in the tree.
         */
        public Trainer(int maxNodes) {
            if (maxNodes < 2) {
                throw new IllegalArgumentException("Invalid maximum number of leaf nodes: " + maxNodes);
            }
            
            this.maxNodes = maxNodes;
        }
        
        /**
         * Constructor.
         * 
         * @param attributes the attributes of independent variable.
         * @param maxNodes the maximum number of leaf nodes in the tree.
         */
        public Trainer(Attribute[] attributes, int maxNodes) {
            super(attributes);
            
            if (maxNodes < 2) {
                throw new IllegalArgumentException("Invalid maximum number of leaf nodes: " + maxNodes);
            }
            
            this.maxNodes = maxNodes;
        }
        
        /**
         * Constructor.
         * 
         * @param numFeatures the number of features.
         * @param maxNodes the maximum number of leaf nodes in the tree.
         */
        public Trainer(int numFeatures, int maxNodes) {
            if (numFeatures <= 0) {
                throw new IllegalArgumentException("Invalid number of sparse binary features: " + numFeatures);
            }
            
            if (maxNodes < 2) {
                throw new IllegalArgumentException("Invalid maximum number of leaf nodes: " + maxNodes);
            }
            
            this.numFeatures = numFeatures;
            this.maxNodes = maxNodes;
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

        /**
         * Sets the minimum size of leaf nodes.
         * @param nodeSize the minimum size of leaf nodes..
         */
        public Trainer setNodeSize(int nodeSize) {
            if (nodeSize < 2) {
                throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
            }

            this.nodeSize = nodeSize;
            return this;
        }

        @Override
        public RegressionTree train(double[][] x, double[] y) {
            return new RegressionTree(attributes, x, y, maxNodes, nodeSize);
        }
        
        public RegressionTree train(int[][] x, double[] y) {
            if (numFeatures <= 0) {
                return new RegressionTree(Math.max(x) + 1, x, y, maxNodes, nodeSize);
            } else {
                return new RegressionTree(numFeatures, x, y, maxNodes, nodeSize);
            }
        }
    }
    
    /**
     * An interface to calculate node output. Note that samples[i] is the
     * number of sampling of dataset[i]. 0 means that the datum is not
     * included and values of greater than 1 are possible because of
     * sampling with replacement.
     */
    public interface NodeOutput {
        /**
         * Calculate the node output.
         * @param samples the samples in the node.
         * @return the node output
         */
        public double calculate(int[] samples);
    }
    
    /**
     * Regression tree node.
     */
    class Node implements Serializable {

        /**
         * Predicted real value for this node.
         */
        double output = 0.0;
        /**
         * The split feature for this node.
         */
        int splitFeature = -1;
        /**
         * The split value.
         */
        double splitValue = Double.NaN;
        /**
         * Reduction in squared error compared to parent.
         */
        double splitScore = 0.0;
        /**
         * Children node.
         */
        Node trueChild;
        /**
         * Children node.
         */
        Node falseChild;
        /**
         * Predicted output for children node.
         */
        double trueChildOutput = 0.0;
        /**
         * Predicted output for children node.
         */
        double falseChildOutput = 0.0;

        /**
         * Constructor.
         */
        public Node(double output) {
            this.output = output;
        }

        /**
         * Evaluate the regression tree over an instance.
         */
        public double predict(double[] x) {
            if (trueChild == null && falseChild == null) {
                return output;
            } else {
                if (attributes[splitFeature].getType() == Attribute.Type.NOMINAL) {
                    if (Math.equals(x[splitFeature], splitValue)) {
                        return trueChild.predict(x);
                    } else {
                        return falseChild.predict(x);
                    }
                } else if (attributes[splitFeature].getType() == Attribute.Type.NUMERIC) {
                    if (x[splitFeature] <= splitValue) {
                        return trueChild.predict(x);
                    } else {
                        return falseChild.predict(x);
                    }
                } else {
                    throw new IllegalStateException("Unsupported attribute type: " + attributes[splitFeature].getType());
                }
            }
        }

        /**
         * Evaluate the regression tree over an instance.
         */
        public double predict(int[] x) {
            if (trueChild == null && falseChild == null) {
                return output;
            } else if (x[splitFeature] == (int) splitValue) {
                return trueChild.predict(x);
            } else {
                return falseChild.predict(x);
            }
        }
    }

    /**
     * Regression tree node for training purpose.
     */
    class TrainNode implements Comparable<TrainNode> {
        /**
         * The associated regression tree node.
         */
        Node node;
        /**
         * Child node that passes the test.
         */
        TrainNode trueChild;
        /**
         * Child node that fails the test.
         */
        TrainNode falseChild;
        /**
         * Training dataset.
         */
        double[][] x;
        /**
         * Training data response value.
         */
        double[] y;
        /**
         * The samples for training this node. Note that samples[i] is the
         * number of sampling of dataset[i]. 0 means that the datum is not
         * included and values of greater than 1 are possible because of
         * sampling with replacement.
         */
        int[] samples;

        /**
         * Constructor.
         */
        public TrainNode(Node node, double[][] x, double[] y, int[] samples) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.samples = samples;
        }

        @Override
        public int compareTo(TrainNode a) {
            return (int) Math.signum(a.node.splitScore - node.splitScore);
        }

        /**
         * Calculate the node output for leaves.
         * @param output the output calculate functor.
         */
        public void calculateOutput(NodeOutput output) {
            if (node.trueChild == null && node.falseChild == null) {
                node.output = output.calculate(samples);
            } else {
                if (trueChild != null) {
                    trueChild.calculateOutput(output);
                }
                if (falseChild != null) {
                    falseChild.calculateOutput(output);
                }
            }
        }
        
        /**
         * Finds the best attribute to split on at the current node. Returns
         * true if a split exists to reduce squared error, false otherwise.
         */
        public boolean findBestSplit() {
            int n = 0;
            for (int s : samples) {
                n += s;
            }

            if (n <= nodeSize) {
                return false;
            }
            
            double sum = node.output * n;
            int p = attributes.length;
            int[] variables = new int[p];
            for (int i = 0; i < p; i++) {
                variables[i] = i;
            }
            
            // Loop through features and compute the reduction of squared error,
            // which is trueCount * trueMean^2 + falseCount * falseMean^2 - count * parentMean^2                    
            if (mtry < p) {
                Math.permutate(variables);
                
                // Random forest already runs on parallel.
                for (int j = 0; j < mtry; j++) {
                    Node split = findBestSplit(n, sum, variables[j]);
                    if (split.splitScore > node.splitScore) {
                        node.splitFeature = split.splitFeature;
                        node.splitValue = split.splitValue;
                        node.splitScore = split.splitScore;
                        node.trueChildOutput = split.trueChildOutput;
                        node.falseChildOutput = split.falseChildOutput;
                    }
                }
            } else {

                List<SplitTask> tasks = new ArrayList<>(mtry);
                for (int j = 0; j < mtry; j++) {
                    tasks.add(new SplitTask(n, sum, variables[j]));
                }

                try {
                    for (Node split : MulticoreExecutor.run(tasks)) {
                        if (split.splitScore > node.splitScore) {
                            node.splitFeature = split.splitFeature;
                            node.splitValue = split.splitValue;
                            node.splitScore = split.splitScore;
                            node.trueChildOutput = split.trueChildOutput;
                            node.falseChildOutput = split.falseChildOutput;
                        }
                    }
                } catch (Exception ex) {
                    for (int j = 0; j < mtry; j++) {
                        Node split = findBestSplit(n, sum, variables[j]);
                        if (split.splitScore > node.splitScore) {
                            node.splitFeature = split.splitFeature;
                            node.splitValue = split.splitValue;
                            node.splitScore = split.splitScore;
                            node.trueChildOutput = split.trueChildOutput;
                            node.falseChildOutput = split.falseChildOutput;
                        }
                    }
                }
            }
            
            return (node.splitFeature != -1);
        }
        
        /**
         * Task to find the best split cutoff for attribute j at the current node.
         */
        class SplitTask implements Callable<Node> {

            /**
             * The number instances in this node.
             */
            int n;
            /**
             * The sum of responses of this node.
             */
            double sum;
            /**
             * The index of variables for this task.
             */
            int j;

            SplitTask(int n, double sum, int j) {
                this.n = n;
                this.sum = sum;                
                this.j = j;
            }

            @Override
            public Node call() {
                return findBestSplit(n, sum, j);
            }
        }
        
        /**
         * Finds the best split cutoff for attribute j at the current node.
         * @param n the number instances in this node.
         * @param j the attribute to split on.
         */
        public Node findBestSplit(int n, double sum, int j) {
            Node split = new Node(0.0);
            if (attributes[j].getType() == Attribute.Type.NOMINAL) {
                int m = ((NominalAttribute) attributes[j]).size();
                double[] trueSum = new double[m];
                int[] trueCount = new int[m];

                for (int i = 0; i < x.length; i++) {
                    if (samples[i] > 0) {
                        double target = samples[i] * y[i];

                        // For each true feature of this datum increment the
                        // sufficient statistics for the "true" branch to evaluate
                        // splitting on this feature.
                        int index = (int) x[i][j];
                        trueSum[index] += target;
                        trueCount[index] += samples[i];
                    }
                }

                for (int k = 0; k < m; k++) {
                    double tc = (double) trueCount[k];
                    double fc = n - tc;

                    // If either side is empty, skip this feature.
                    if (tc < nodeSize || fc < nodeSize) {
                        continue;
                    }

                    // compute penalized means
                    double trueMean = trueSum[k] / tc;
                    double falseMean = (sum - trueSum[k]) / fc;

                    double gain = (tc * trueMean * trueMean + fc * falseMean * falseMean) - n * split.output * split.output;
                    if (gain > split.splitScore) {
                        // new best split
                        split.splitFeature = j;
                        split.splitValue = k;
                        split.splitScore = gain;
                        split.trueChildOutput = trueMean;
                        split.falseChildOutput = falseMean;
                    }
                }
            } else if (attributes[j].getType() == Attribute.Type.NUMERIC) {
                double trueSum = 0.0;
                int trueCount = 0;
                double prevx = Double.NaN;

                for (int i : order[j]) {
                    if (samples[i] > 0) {
                        if (Double.isNaN(prevx) || x[i][j] == prevx) {
                            prevx = x[i][j];
                            trueSum += samples[i] * y[i];
                            trueCount += samples[i];
                            continue;
                        }

                        double falseCount = n - trueCount;

                        // If either side is empty, skip this feature.
                        if (trueCount < nodeSize || falseCount < nodeSize) {
                            prevx = x[i][j];
                            trueSum += samples[i] * y[i];
                            trueCount += samples[i];
                            continue;
                        }

                        // compute penalized means
                        double trueMean = trueSum / trueCount;
                        double falseMean = (sum - trueSum) / falseCount;

                        // The gain is actually -(reduction in squared error) for
                        // sorting in priority queue, which treats smaller number with
                        // higher priority.
                        double gain = (trueCount * trueMean * trueMean + falseCount * falseMean * falseMean) - n * split.output * split.output;
                        if (gain > split.splitScore) {
                            // new best split
                            split.splitFeature = j;
                            split.splitValue = (x[i][j] + prevx) / 2;
                            split.splitScore = gain;
                            split.trueChildOutput = trueMean;
                            split.falseChildOutput = falseMean;
                        }

                        prevx = x[i][j];
                        trueSum += samples[i] * y[i];
                        trueCount += samples[i];
                    }
                }
            } else {
                throw new IllegalStateException("Unsupported attribute type: " + attributes[j].getType());
            }

            return split;
        }
    
        /**
         * Split the node into two children nodes. Returns true if split success.
         */
        public boolean split(PriorityQueue<TrainNode> nextSplits) {
            if (node.splitFeature < 0) {
                throw new IllegalStateException("Split a node with invalid feature.");
            }

            int n = x.length;
            int tc = 0;
            int fc = 0;
            int[] trueSamples = new int[n];
            //int[] falseSamples = new int[n];

            if (attributes[node.splitFeature].getType() == Attribute.Type.NOMINAL) {
                for (int i = 0; i < n; i++) {
                    if (samples[i] > 0) {
                        if (Math.equals(x[i][node.splitFeature], node.splitValue)) {
                            trueSamples[i] = samples[i];
                            tc += trueSamples[i];
                            samples[i] = 0;
                        } else {
                            //falseSamples[i] = samples[i];
                            fc += samples[i];
                        }
                    }
                }
            } else if (attributes[node.splitFeature].getType() == Attribute.Type.NUMERIC) {
                for (int i = 0; i < n; i++) {
                    if (samples[i] > 0) {
                        if (x[i][node.splitFeature] <= node.splitValue) {
                            trueSamples[i] = samples[i];
                            tc += trueSamples[i];
                            samples[i] = 0;
                        } else {
                            //falseSamples[i] = samples[i];
                            fc += samples[i];
                        }
                    }
                }
            } else {
                throw new IllegalStateException("Unsupported attribute type: " + attributes[node.splitFeature].getType());
            }
            
            if (tc < nodeSize || fc < nodeSize) {
                node.splitFeature = -1;
                node.splitValue = Double.NaN;
                node.splitScore = 0.0;
                return false;
            }
            
            node.trueChild = new Node(node.trueChildOutput);
            node.falseChild = new Node(node.falseChildOutput);
            
            trueChild = new TrainNode(node.trueChild, x, y, trueSamples);
            if (tc > nodeSize && trueChild.findBestSplit()) {
                if (nextSplits != null) {
                    nextSplits.add(trueChild);
                } else {
                    trueChild.split(null);
                }
            }

            falseChild = new TrainNode(node.falseChild, x, y, samples);
            if (fc > nodeSize && falseChild.findBestSplit()) {
                if (nextSplits != null) {
                    nextSplits.add(falseChild);
                } else {
                    falseChild.split(null);
                }
            }
            
            importance[node.splitFeature] += node.splitScore;
            
            return true;
        }
    }
    
    /**
     * Regression tree training node for sparse binary features.
     */
    class SparseBinaryTrainNode implements Comparable<SparseBinaryTrainNode> {

        /**
         * The associated regression tree node.
         */
        Node node;
        /**
         * Child node that passes the test.
         */
        SparseBinaryTrainNode trueChild;
        /**
         * Child node that fails the test.
         */
        SparseBinaryTrainNode falseChild;
        /**
         * Training dataset.
         */
        int[][] x;
        /**
         * Training data response value.
         */
        double[] y;
        /**
         * The samples for training this node. Note that samples[i] is the
         * number of sampling of dataset[i]. 0 means that the datum is not
         * included and values of greater than 1 are possible because of
         * sampling with replacement.
         */
        int[] samples;

        /**
         * Constructor.
         */
        public SparseBinaryTrainNode(Node node, int[][] x, double[] y, int[] samples) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.samples = samples;
        }

        @Override
        public int compareTo(SparseBinaryTrainNode a) {
            return (int) Math.signum(a.node.splitScore - node.splitScore);
        }

        /**
         * Finds the best attribute to split on at the current node. Returns
         * true if a split exists to reduce squared error, false otherwise.
         */
        public boolean findBestSplit() {
            if (node.trueChild != null || node.falseChild != null) {
                throw new IllegalStateException("Split non-leaf node.");
            }

            int p = numFeatures;
            double[] trueSum = new double[p];
            int[] trueCount = new int[p];
            int[] featureIndex = new int[p];

            int n = Math.sum(samples);
            double sumX = 0.0;
            for (int i = 0; i < x.length; i++) {
                if (samples[i] == 0) {
                    continue;
                }

                double target = samples[i] * y[i];
                sumX += y[i];

                // For each true feature of this datum increment the
                // sufficient statistics for the "true" branch to evaluate
                // splitting on this feature.
                for (int j = 0; j < x[i].length; ++j) {
                    int index = x[i][j];
                    trueSum[index] += target;
                    trueCount[index] += samples[i];
                    featureIndex[index] = j;
                }
            }

            // Loop through features and compute the reduction
            // of squared error, which is trueCount * trueMean^2 + falseCount * falseMean^2 - count * parentMean^2

            // Initialize the information in the leaf
            node.splitScore = 0.0;
            node.splitFeature = -1;
            node.splitValue = -1;

            for (int i = 0; i < p; ++i) {
                double tc = (double) trueCount[i];
                double fc = n - tc;

                // If either side would have fewer than minimum data points, skip this feature.
                if (tc < nodeSize || fc < nodeSize) {
                    continue;
                }

                // compute penalized means
                double trueMean = trueSum[i] / tc;
                double falseMean = (sumX - trueSum[i]) / fc;

                double gain = (tc * trueMean * trueMean + fc * falseMean * falseMean) - n * node.output * node.output;
                if (gain > node.splitScore) {
                    // new best split
                    node.splitFeature = featureIndex[i];
                    node.splitValue = i;
                    node.splitScore = gain;
                    node.trueChildOutput = trueMean;
                    node.falseChildOutput = falseMean;
                }
            }

            return (node.splitFeature != -1);
        }

        /**
         * Split the node into two children nodes.
         */
        public void split(PriorityQueue<SparseBinaryTrainNode> nextSplits) {
            if (node.splitFeature < 0) {
                throw new IllegalStateException("Split a node with invalid feature.");
            }

            if (node.trueChild != null || node.falseChild != null) {
                throw new IllegalStateException("Split non-leaf node.");
            }

            int n = x.length;
            int tc = 0;
            int fc = 0;
            int[] trueSamples = new int[n];
            //int[] falseSamples = new int[n];

            for (int i = 0; i < n; i++) {
                if (samples[i] > 0) {
                    if (x[i][node.splitFeature] == (int) node.splitValue) {
                        trueSamples[i] = samples[i];
                        tc += trueSamples[i];
                        samples[i] = 0;
                    } else {
                        //falseSamples[i] = samples[i];
                        fc += samples[i];
                    }
                }
            }

            node.trueChild = new Node(node.trueChildOutput);
            node.falseChild = new Node(node.falseChildOutput);

            trueChild = new SparseBinaryTrainNode(node.trueChild, x, y, trueSamples);
            if (tc > nodeSize && trueChild.findBestSplit()) {
                if (nextSplits != null) {
                    nextSplits.add(trueChild);
                } else {
                    trueChild.split(null);
                }
            }

            falseChild = new SparseBinaryTrainNode(node.falseChild, x, y, samples);
            if (fc > nodeSize && falseChild.findBestSplit()) {
                if (nextSplits != null) {
                    nextSplits.add(falseChild);
                } else {
                    falseChild.split(null);
                }
            }
            
            importance[node.splitFeature] += node.splitScore;
            
        }
        
        /**
         * Calculate the node output for leaves.
         * @param output the output calculate functor.
         */
        public void calculateOutput(NodeOutput output) {
            if (node.trueChild == null && node.falseChild == null) {
                node.output = output.calculate(samples);
            } else {
                if (trueChild != null) {
                    trueChild.calculateOutput(output);
                }
                if (falseChild != null) {
                    falseChild.calculateOutput(output);
                }
            }
        }
    }
    
    /**
     * Constructor. Learns a regression tree with (most) given number of leaves.
     * All attributes are assumed to be numeric.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RegressionTree(double[][] x, double[] y, int maxNodes) {
        this(null, x, y, maxNodes, 5);
    }

    /**
     * Constructor. Learns a regression tree with (most) given number of leaves.
     * All attributes are assumed to be numeric.
     *
     * @param x the training instances.
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RegressionTree(double[][] x, double[] y, int maxNodes, int nodeSize) {
        this(null, x, y, maxNodes, nodeSize);
    }

    /**
     * Constructor. Learns a regression tree with (most) given number of leaves.
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RegressionTree(Attribute[] attributes, double[][] x, double[] y, int maxNodes) {
        this(attributes, x, y, maxNodes, 5);
    }

    /**
     * Constructor. Learns a regression tree with (most) given number of leaves.
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RegressionTree(Attribute[] attributes, double[][] x, double[] y, int maxNodes, int nodeSize) {
        this(attributes, x, y, maxNodes, nodeSize, x[0].length, null, null, null);
    }

    /**
     * Constructor. Learns a regression tree for random forest and gradient tree boosting.
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     * @param mtry the number of input variables to pick to split on at each
     * node. It seems that p/3 give generally good performance, where p
     * is the number of variables.
     * @param order  the index of training values in ascending order. Note
     * that only numeric attributes need be sorted.
     * @param samples the sample set of instances for stochastic learning.
     * samples[i] should be 0 or 1 to indicate if the instance is used for training.
     */
    public RegressionTree(Attribute[] attributes, double[][] x, double[] y, int maxNodes, int nodeSize, int mtry, int[][] order, int[] samples, NodeOutput output) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (mtry < 1 || mtry > x[0].length) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
        }

        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum leaves: " + maxNodes);
        }

        if (nodeSize < 2) {
            throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
        }

        if (attributes == null) {
            int p = x[0].length;
            attributes = new Attribute[p];
            for (int i = 0; i < p; i++) {
                attributes[i] = new NumericAttribute("V" + (i + 1));
            }
        }
                
        this.attributes = attributes;
        this.maxNodes = maxNodes;
        this.nodeSize = nodeSize;
        this.mtry = mtry;
        importance = new double[attributes.length];
        
        if (order != null) {
            this.order = order;
        } else {
            int n = x.length;
            int p = x[0].length;

            double[] a = new double[n];
            this.order = new int[p][];

            for (int j = 0; j < p; j++) {
                if (attributes[j] instanceof NumericAttribute) {
                    for (int i = 0; i < n; i++) {
                        a[i] = x[i][j];
                    }
                    this.order[j] = QuickSort.sort(a);
                }
            }
        }

        // Priority queue for best-first tree growing.
        PriorityQueue<TrainNode> nextSplits = new PriorityQueue<>();

        int n = 0;
        double sum = 0.0;
        if (samples == null) {
            n = y.length;
            samples = new int[n];
            for (int i = 0; i < n; i++) {
                samples[i] = 1;
                sum += y[i];
            }
        } else {
            for (int i = 0; i < y.length; i++) {
                n += samples[i];
                sum += samples[i] * y[i];
            }
        }
        
        root = new Node(sum / n);
        
        TrainNode trainRoot = new TrainNode(root, x, y, samples);
        // Now add splits to the tree until max tree size is reached
        if (trainRoot.findBestSplit()) {
            nextSplits.add(trainRoot);
        }

        // Pop best leaf from priority queue, split it, and push
        // children nodes into the queue if possible.
        for (int leaves = 1; leaves < this.maxNodes; leaves++) {
            // parent is the leaf to split
            TrainNode node = nextSplits.poll();
            if (node == null) {
                break;
            }

            node.split(nextSplits); // Split the parent node into two children nodes
        }
        
        if (output != null) {
            trainRoot.calculateOutput(output);
        }
    }

    /**
     * Constructor. Learns a regression tree on sparse binary samples.
     * @param numFeatures the number of sparse binary features.
     * @param x the training instances of sparse binary features. 
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public RegressionTree(int numFeatures, int[][] x, double[] y, int maxNodes) {
        this(numFeatures, x, y, maxNodes, 5);
    }

    /**
     * Constructor. Learns a regression tree on sparse binary samples.
     * @param numFeatures the number of sparse binary features.
     * @param x the training instances of sparse binary features.
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     */
    public RegressionTree(int numFeatures, int[][] x, double[] y, int maxNodes, int nodeSize) {
        this(numFeatures, x, y, maxNodes, nodeSize, null, null);
    }

    /**
     * Constructor. Learns a regression tree on sparse binary samples.
     * @param numFeatures the number of sparse binary features.
     * @param x the training instances. 
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     * @param samples the sample set of instances for stochastic learning.
     * samples[i] should be 0 or 1 to indicate if the instance is used for training.
     */
    public RegressionTree(int numFeatures, int[][] x, double[] y, int maxNodes, int nodeSize, int[] samples, NodeOutput output) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum number of leaves: " + maxNodes);
        }

        if (nodeSize < 2) {
            throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
        }

        this.maxNodes = maxNodes;
        this.nodeSize = nodeSize;
        this.numFeatures = numFeatures;
        this.mtry = numFeatures;
        importance = new double[numFeatures];
        
        // Priority queue for best-first tree growing.
        PriorityQueue<SparseBinaryTrainNode> nextSplits = new PriorityQueue<>();

        int n = 0;
        double sum = 0.0;
        if (samples == null) {
            n = y.length;
            samples = new int[n];
            for (int i = 0; i < n; i++) {
                samples[i] = 1;
                sum += y[i];
            }
        } else {
            for (int i = 0; i < y.length; i++) {
                n += samples[i];
                sum += samples[i] * y[i];
            }
        }
        
        root = new Node(sum / n);
        
        SparseBinaryTrainNode trainRoot = new SparseBinaryTrainNode(root, x, y, samples);
        // Now add splits to the tree until max tree size is reached
        if (trainRoot.findBestSplit()) {
            nextSplits.add(trainRoot);
        }

        // Pop best leaf from priority queue, split it, and push
        // children nodes into the queue if possible.
        for (int leaves = 1; leaves < this.maxNodes; leaves++) {
            // parent is the leaf to split
            SparseBinaryTrainNode node = nextSplits.poll();
            if (node == null) {
                break;
            }

            node.split(nextSplits); // Split the parent node into two children nodes
        }
        
        if (output != null) {
            trainRoot.calculateOutput(output);
        }
    }
    
    /**
     * Returns the variable importance. Every time a split of a node is made
     * on variable the impurity criterion for the two descendent nodes is less
     * than the parent node. Adding up the decreases for each individual
     * variable over the tree gives a simple measure of variable importance.
     *
     * @return the variable importance
     */
    public double[] importance() {
        return importance;
    }
        
    @Override
    public double predict(double[] x) {
        return root.predict(x);
    }
    
    /**
     * Predicts the dependent variable of an instance with sparse binary features.
     * @param x the instance.
     * @return the predicted value of dependent variable.
     */
    public double predict(int[] x) {
        return root.predict(x);
    }

    /**
     * Returns the maximum depth" of the tree -- the number of
     * nodes along the longest path from the root node
     * down to the farthest leaf node.*/
    public int maxDepth() {
        return maxDepth(root);
    }

    private int maxDepth(Node node) {
        if (node == null)
            return 0;

        // compute the depth of each subtree
        int lDepth = maxDepth(node.trueChild);
        int rDepth = maxDepth(node.falseChild);

        // use the larger one
        if (lDepth > rDepth)
            return (lDepth + 1);
        else
            return (rDepth + 1);
    }

    // For dot() tree traversal.
    private class DotNode {
        int parent;
        int id;
        Node node;
        DotNode(int parent, int id, Node node) {
            this.parent = parent;
            this.id = id;
            this.node = node;
        }
    }

    /**
     * Returns the graphic representation in Graphviz dot format.
     * Try http://viz-js.com/ to visualize the returned string.
     */
    public String dot() {
        StringBuilder builder = new StringBuilder();
        builder.append("digraph RegressionTree {\n node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n edge [fontname=helvetica];\n");

        int n = 0; // number of nodes processed
        Queue<DotNode> queue = new LinkedList<>();
        queue.add(new DotNode(-1, 0, root));

        while (!queue.isEmpty()) {
            // Dequeue a vertex from queue and print it
            DotNode dnode = queue.poll();
            int id = dnode.id;
            int parent = dnode.parent;
            Node node = dnode.node;

            // leaf node
            if (node.trueChild == null && node.falseChild == null) {
                builder.append(String.format(" %d [label=<%.4f>, fillcolor=\"#00000000\", shape=ellipse];\n", id, node.output));
            } else {
                Attribute attr = attributes[node.splitFeature];
                if (attr.getType() == Attribute.Type.NOMINAL) {
                    builder.append(String.format(" %d [label=<%s = %s<br/>nscore = %.4f>, fillcolor=\"#00000000\"];\n", id, attr.getName(), attr.toString(node.splitValue), node.splitScore));
                } else if (attr.getType() == Attribute.Type.NUMERIC) {
                    builder.append(String.format(" %d [label=<%s &le; %.4f<br/>score = %.4f>, fillcolor=\"#00000000\"];\n", id, attr.getName(), node.splitValue, node.splitScore));
                } else {
                    throw new IllegalStateException("Unsupported attribute type: " + attr.getType());
                }
            }

            // add edge
            if (parent >= 0) {
                builder.append(' ').append(parent).append(" -> ").append(id);
                // only draw edge label at top
                if (parent == 0) {
                    if (id == 1) {
                        builder.append(" [labeldistance=2.5, labelangle=45, headlabel=\"True\"]");
                    } else {
                        builder.append(" [labeldistance=2.5, labelangle=-45, headlabel=\"False\"]");
                    }
                }
                builder.append(";\n");
            }

            if (node.trueChild != null) {
                queue.add(new DotNode(id, ++n, node.trueChild));
            }

            if (node.falseChild != null) {
                queue.add(new DotNode(id, ++n, node.falseChild));
            }
        }

        builder.append("}");
        return builder.toString();
    }
}
