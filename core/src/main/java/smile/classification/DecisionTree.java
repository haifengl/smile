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
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.IntPredicate;
import smile.data.Attribute;
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.sort.QuickSort;
import smile.util.MulticoreExecutor;

/**
 * Decision tree for classification. A decision tree can be learned by
 * splitting the training set into subsets based on an attribute value
 * test. This process is repeated on each derived subset in a recursive
 * manner called recursive partitioning. The recursion is completed when
 * the subset at a node all has the same value of the target variable,
 * or when splitting no longer adds value to the predictions.
 * <p>
 * The algorithms that are used for constructing decision trees usually
 * work top-down by choosing a variable at each step that is the next best
 * variable to use in splitting the set of items. "Best" is defined by how
 * well the variable splits the set into homogeneous subsets that have
 * the same value of the target variable. Different algorithms use different
 * formulae for measuring "best". Used by the CART algorithm, Gini impurity
 * is a measure of how often a randomly chosen element from the set would
 * be incorrectly labeled if it were randomly labeled according to the
 * distribution of labels in the subset. Gini impurity can be computed by
 * summing the probability of each item being chosen times the probability
 * of a mistake in categorizing that item. It reaches its minimum (zero) when
 * all cases in the node fall into a single target category. Information gain
 * is another popular measure, used by the ID3, C4.5 and C5.0 algorithms.
 * Information gain is based on the concept of entropy used in information
 * theory. For categorical variables with different number of levels, however,
 * information gain are biased in favor of those attributes with more levels. 
 * Instead, one may employ the information gain ratio, which solves the drawback
 * of information gain. 
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
 * @see AdaBoost
 * @see GradientTreeBoost
 * @see RandomForest
 * 
 * @author Haifeng Li
 */
public class DecisionTree implements SoftClassifier<double[]> {
    private static final long serialVersionUID = 1L;

    /**
     * The attributes of independent variable.
     */
    private Attribute[] attributes;
    /**
     * Variable importance. Every time a split of a node is made on variable
     * the (GINI, information gain, etc.) impurity criterion for the two
     * descendent nodes is less than the parent node. Adding up the decreases
     * for each individual variable over the tree gives a simple measure of
     * variable importance.
     */
    private double[] importance;
    /**
     * The root of the regression tree
     */
    private Node root;
    /**
     * The splitting rule.
     */
    private SplitRule rule = SplitRule.GINI;
    /**
     * The number of classes.
     */
    private int k = 2;
    /**
     * The minimum size of leaf nodes.
     */
    private int nodeSize = 1;
    /**
     * The maximum number of leaf nodes in the tree.
     */
    private int maxNodes = 100;
    /**
     * The number of input variables to be used to determine the decision
     * at a node of the tree.
     */
    private int mtry;
    /**
     * An index of training values. Initially, order[i] is a set of indices that iterate through the
     * training values for attribute i in ascending order. During training, the array is rearranged
     * so that all values for each leaf node occupy a contiguous range, but within that range they
     * maintain the original ordering. Note that only numeric attributes will be sorted; non-numeric
     * attributes will have a null in the corresponding place in the array.
     */
    private transient int[][] order;
    /**
     * An index of training values that maps their current position in the {@link #order} arrays to
     * their original locations.
     */
    private transient int[] originalOrder;
    /**
     * A map from original class labels to the internal dense labels.
     */
    private SparseClassMap labelMap;

    /**
     * Trainer for decision tree classifiers.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {
        /**
         * The splitting rule.
         */
        private SplitRule rule = SplitRule.GINI;
        /**
         * The minimum size of leaf nodes.
         */
        private int nodeSize = 1;
        /**
         * The maximum number of leaf nodes in the tree.
         */
        private int maxNodes = 100;

        /**
         * Default constructor of maximal 100 leaf nodes in the tree.
         */
        public Trainer() {

        }

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
         * Sets the splitting rule.
         * @param rule the splitting rule.
         */
        public Trainer setSplitRule(SplitRule rule) {
            this.rule = rule;
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

        /**
         * Sets the minimum size of leaf nodes.
         * @param nodeSize the minimum size of leaf nodes..
         */
        public Trainer setNodeSize(int nodeSize) {
            if (nodeSize < 1) {
                throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
            }

            this.nodeSize = nodeSize;
            return this;
        }

        @Override
        public DecisionTree train(double[][] x, int[] y) {
            return new DecisionTree(attributes, x, y, maxNodes, nodeSize, rule);
        }
    }
    
    /**
     * The criterion to choose variable to split instances.
     */
    public enum SplitRule {
        /**
         * Used by the CART algorithm, Gini impurity is a measure of how often
         * a randomly chosen element from the set would be incorrectly labeled
         * if it were randomly labeled according to the distribution of labels
         * in the subset. Gini impurity can be computed by summing the
         * probability of each item being chosen times the probability
         * of a mistake in categorizing that item. It reaches its minimum
         * (zero) when all cases in the node fall into a single target category.
         */
        GINI,
        /**
         * Used by the ID3, C4.5 and C5.0 tree generation algorithms.
         */
        ENTROPY,
        /**
         * Classification error.
         */
        CLASSIFICATION_ERROR
    }
    
    /**
     * Classification tree node.
     */
    class Node implements Serializable {

        /**
         * Predicted class label for this node. This is the "sparse" version of this label,
         * as provided in the original training data.
         */
        int output = -1;
        /**
         * Counts of samples assigned to this node.
         */
        int[] count = null;
        /**
         * The split feature for this node.
         */
        int splitFeature = -1;
        /**
         * The split value.
         */
        double splitValue = Double.NaN;
        /**
         * Reduction in splitting criterion.
         */
        double splitScore = 0.0;
        /**
         * The node's impurity.
         */
        double impurity = 0.0;
        /**
         * Children node.
         */
        Node trueChild = null;
        /**
         * Children node.
         */
        Node falseChild = null;

        /**
         * Constructor.
         */
        public Node() {
        }

        /**
         * Constructor.
         */
        public Node(int output, int[] count) {
            this.output = output;
            this.count = count;
            impurity = impurity(count, Math.sum(count));
        }

        private void markAsLeaf() {
            this.splitFeature = -1;
            this.splitValue = Double.NaN;
            this.splitScore = 0.0;
            this.trueChild = null;
            this.falseChild = null;
        }

        /**
         * Evaluate the regression tree over an instance.
         */
        public int predict(double[] x) {
            if (isLeaf()) {
                return output;
            } else {
                if (attributes[splitFeature].getType() == Attribute.Type.NOMINAL) {
                    if (x[splitFeature] == splitValue) {
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
        public int predict(double[] x, double[] posteriori) {
            if (isLeaf()) {
                setPosteriori(posteriori);
                return output;
            } else {
                if (attributes[splitFeature].getType() == Attribute.Type.NOMINAL) {
                    if (x[splitFeature] == splitValue) {
                        return trueChild.predict(x, posteriori);
                    } else {
                        return falseChild.predict(x, posteriori);
                    }
                } else if (attributes[splitFeature].getType() == Attribute.Type.NUMERIC) {
                    if (x[splitFeature] <= splitValue) {
                        return trueChild.predict(x, posteriori);
                    } else {
                        return falseChild.predict(x, posteriori);
                    }
                } else {
                    throw new IllegalStateException("Unsupported attribute type: " + attributes[splitFeature].getType());
                }
            }
        }

        /**
         * Fills an array with this node's posterior probabilities.
         */
        void setPosteriori(double[] posteriori) {
            if (posteriori.length < labelMap.maxSparseLabel() + 1) {
                throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected at least: %d", posteriori.length, labelMap.maxSparseLabel() + 1));
            } else if (posteriori.length != k) {
                Arrays.fill(posteriori, 0.0);
            }

            int totalCount = Math.sum(count);
            if (labelMap.isIdentity()) {
                computePosteriori(count, totalCount, posteriori);
            } else {
                double[] densePosteriori = new double[k];
                computePosteriori(count, totalCount, densePosteriori);
                for (int i = 0; i < k; i++) {
                    posteriori[labelMap.denseLabelToSparseLabel(i)] = densePosteriori[i];
                }
            }
        }

        boolean isLeaf() {
            return falseChild == null;
        }
    }

    /**
     * Classification tree node for training purpose.
     */
    class TrainNode implements Comparable<TrainNode> {
        /**
         * The associated regression tree node.
         */
        Node node;
        /**
         * Training dataset.
         */
        double[][] x;
        /**
         * class labels.
         */
        int[] y;
        /**
         * The samples for training this node. Note that samples[i] is the
         * number of sampling of dataset[i]. 0 means that the datum is not
         * included and values of greater than 1 are possible because of
         * sampling with replacement.
         */
        int[] samples;
        /**
         * The lower bound (inclusive) in the order array of the samples belonging to this node.
         */
        int low;
        /**
         * The upper bound (exclusive) in the order array of the samples belonging to this node.
         */
        int high;

        /**
         * Constructor.
         */
        public TrainNode(Node node, double[][] x, int[] y, int[] samples, int low, int high) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.samples = samples;
            this.low = low;
            this.high = high;
        }

        @Override
        public int compareTo(TrainNode a) {
            return (int) Math.signum(a.node.splitScore - node.splitScore);
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
             * The lower bound (inclusive) in the order array of the samples belonging to this node.
             */
            int low;
            /**
             * The upper bound (exclusive) in the order array of the samples belonging to this node.
             */
            int high;
            /**
             * The variable for this task to attempt to split on.
             */
            int j;

            SplitTask(int n, int low, int high, int j) {
                this.n = n;
                this.low = low;
                this.high = high;
                this.j = j;
            }

            @Override
            public Node call() {
                // An array to store sample count in each class for false child node.
                int[] falseCount = new int[k];
                return findBestSplit(n, low, high, falseCount, j);
            }
        }

        /**
         * Finds the best attribute to split on at the current node. Returns
         * true if a split exists to reduce squared error, false otherwise.
         */
        public boolean findBestSplit() {
            int label = -1;
            boolean pure = true;
            for (int i = 0; i < k; i++) {
                if (node.count[i] > 0) {
                    if (label == -1) {
                        label = i;
                    } else {
                        pure = false;
                        break;
                    }
                }
            }

            // Since all instances have same label, stop splitting.
            if (pure) {
                return false;
            }

            int n = Math.sum(node.count);
            if (n <= nodeSize) {
                return false;
            }

            int p = attributes.length;
            if (mtry < p) {
                int[] variables = new int[p];
                for (int i = 0; i < p; i++) {
                    variables[i] = i;
                }
                Math.permutate(variables);
                int[] falseCount = new int[k];

                // Random forest already runs on parallel.
                for (int j = 0; j < mtry; j++) {
                    Node split = findBestSplit(n, low, high, falseCount, variables[j]);
                    if (split.splitScore > node.splitScore) {
                        node.splitFeature = split.splitFeature;
                        node.splitValue = split.splitValue;
                        node.splitScore = split.splitScore;
                    }
                }
            } else {

                List<SplitTask> tasks = new ArrayList<>(mtry);
                for (int j = 0; j < p; j++) {
                    tasks.add(new SplitTask(n, low, high, j));
                }

                try {
                    for (Node split : MulticoreExecutor.run(tasks)) {
                        if (split.splitScore > node.splitScore) {
                            node.splitFeature = split.splitFeature;
                            node.splitValue = split.splitValue;
                            node.splitScore = split.splitScore;
                        }
                    }
                } catch (Exception ex) {
                    int[] falseCount = new int[k];
                    for (int j = 0; j < p; j++) {
                        Node split = findBestSplit(n, low, high, falseCount, j);
                        if (split.splitScore > node.splitScore) {
                            node.splitFeature = split.splitFeature;
                            node.splitValue = split.splitValue;
                            node.splitScore = split.splitScore;
                        }
                    }
                }
            }

            return (node.splitFeature != -1);
        }
        
        /**
         * Finds the best split cutoff for attribute j at the current node.
         * @param n the number of instances in this node.
         * @param low the lower bound (inclusive) of the samples belonging to this node.
         * @param high the upp bound (exclusive) of the samples belonging to this node.
         * @param falseCount an array to store sample count in each class for false child node.
         * @param j the attribute to split on.
         */
        public Node findBestSplit(int n, int low, int high, int[] falseCount, int j) {
            Node splitNode = new Node();

            if (attributes[j].getType() == Attribute.Type.NOMINAL) {
                int m = ((NominalAttribute) attributes[j]).size();
                int[][] trueCount = new int[m][k];

                for (int i = low; i < high; i++) {
                    int o = originalOrder[i];
                    trueCount[(int) x[o][j]][y[o]] += samples[o];
                }

                for (int l = 0; l < m; l++) {
                    int tc = Math.sum(trueCount[l]);
                    int fc = n - tc;

                    // If either side is empty, skip this feature.
                    if (tc < nodeSize || fc < nodeSize) {
                        continue;
                    }

                    for (int q = 0; q < k; q++) {
                        falseCount[q] = node.count[q] - trueCount[l][q];
                    }

                    double gain = node.impurity - (double) tc / n * impurity(trueCount[l], tc) - (double) fc / n * impurity(falseCount, fc);

                    if (gain > splitNode.splitScore) {
                        // new best split
                        splitNode.splitFeature = j;
                        splitNode.splitValue = l;
                        splitNode.splitScore = gain;
                    }
                }
            } else if (attributes[j].getType() == Attribute.Type.NUMERIC) {
                int[] trueCount = new int[k];
                int[] variableOrder = order[j];
                double prevx = Double.NaN;
                int prevy = -1;

                for (int i = low; i < high; i++) {
                    int o = variableOrder[i];
                    if (Double.isNaN(prevx) || x[o][j] == prevx || y[o] == prevy) {
                        prevx = x[o][j];
                        prevy = y[o];
                        trueCount[y[o]] += samples[o];
                        continue;
                    }

                    int tc = Math.sum(trueCount);
                    int fc = n - tc;

                    // If either side is empty, skip this feature.
                    if (tc < nodeSize || fc < nodeSize) {
                        prevx = x[o][j];
                        prevy = y[o];
                        trueCount[y[o]] += samples[o];
                        continue;
                    }

                    for (int l = 0; l < k; l++) {
                        falseCount[l] = node.count[l] - trueCount[l];
                    }

                    double gain = node.impurity - (double) tc / n * impurity(trueCount, tc) - (double) fc / n * impurity(falseCount, fc);

                    if (gain > splitNode.splitScore) {
                        // new best split
                        splitNode.splitFeature = j;
                        splitNode.splitValue = (x[o][j] + prevx) / 2;
                        splitNode.splitScore = gain;
                    }

                    prevx = x[o][j];
                    prevy = y[o];
                    trueCount[y[o]] += samples[o];
                }
            } else {
                throw new IllegalStateException("Unsupported attribute type: " + attributes[j].getType());
            }

            return splitNode;
        }
        
        /**
         * Split the node into two children nodes. Returns true if split success.
         */
        public boolean split(PriorityQueue<TrainNode> nextSplits) {
            if (node.splitFeature < 0) {
                throw new IllegalStateException("Split a node with invalid feature.");
            }

            int tc = 0;
            int fc = 0;

            int[] trueChildCount = new int[k];
            int[] falseChildCount = new int[k];
            int split = low;
            IntPredicate goesLeft;
            if (attributes[node.splitFeature].getType() == Attribute.Type.NOMINAL) {
                goesLeft = new IntPredicate() {
                    public boolean test(int o) { return x[o][node.splitFeature] == node.splitValue; }
                    };
            } else if (attributes[node.splitFeature].getType() == Attribute.Type.NUMERIC) {
                goesLeft = new IntPredicate() {
                    public boolean test(int o) { return x[o][node.splitFeature] <= node.splitValue; }
                    };
            } else {
                throw new IllegalStateException("Unsupported attribute type: " + attributes[node.splitFeature].getType());
            }

            for (int i = low; i < high; i++) {
                int o = originalOrder[i];
                int yi = y[o];
                int s = samples[o];
                if (goesLeft.test(o)) {
                    tc += s;
                    trueChildCount[yi] += s;
                    split++;
                } else {
                    fc += s;
                    falseChildCount[yi] += s;
                }
            }

            if (tc < nodeSize || fc < nodeSize) {
                node.markAsLeaf();
                return false;
            }

            int trueChildOutput = labelMap.denseLabelToSparseLabel(Math.whichMax(trueChildCount));
            int falseChildOutput = labelMap.denseLabelToSparseLabel(Math.whichMax(falseChildCount));

            node.trueChild = new Node(trueChildOutput, trueChildCount);
            node.falseChild = new Node(falseChildOutput, falseChildCount);

            int[] buffer = new int[high - split];
            partitionOrder(low, split, high, goesLeft, buffer);

            int leaves = 0;
            TrainNode trueChild = new TrainNode(node.trueChild, x, y, samples, low, split);         
            if (tc > nodeSize && trueChild.findBestSplit()) {
                if (nextSplits != null) {
                    nextSplits.add(trueChild);
                } else {
                    if(trueChild.split(null) == false) {
                        leaves++;
                    }
                }
            } else {
                leaves++;
            }

            TrainNode falseChild = new TrainNode(node.falseChild, x, y, samples, split, high);
            if (fc > nodeSize && falseChild.findBestSplit()) {
                if (nextSplits != null) {
                    nextSplits.add(falseChild);
                } else {
                    if(falseChild.split(null) == false) {
                        leaves++;
                    }
                }
            } else {
                leaves++;
            }

            // Prune meaningless branches
            if (leaves == 2) {// both left and right child is leaf node
                if (node.trueChild.output == node.falseChild.output) {// found meaningless branch
                    node.markAsLeaf();
                    return false;
                }
            }

            importance[node.splitFeature] += node.splitScore;

            if (nextSplits == null) {
                // We're doing depth-first splitting, so this node is definitely an interior node
                // and its count array can be deleted. For best-first splitting, these are
                // cleared in pruneRedundantLeaves.
                node.count = null;
            }

            return true;
        }
    }

    /**
     * Returns the impurity of a node.
     * @param count the sample count in each class.
     * @param n the number of samples in the node.
     * @return  the impurity of a node
     */
    private double impurity(int[] count, int n) {
        double impurity = 0.0;

        switch (rule) {
            case GINI:
                double squared_sum = 0;
                for (int i = 0; i < count.length; i++) {
                    if (count[i] > 0) {
                        squared_sum += (double) count[i] * count[i];
                    }
                }
                impurity = 1 - squared_sum / ((double) n * n);
                break;

            case ENTROPY:
                for (int i = 0; i < count.length; i++) {
                    if (count[i] > 0) {
                        double p = (double) count[i] / n;
                        impurity -= p * Math.log2(p);
                    }
                }
                break;
            case CLASSIFICATION_ERROR:
                impurity = Math.abs(1 - Math.max(count) / (double)n);
                break;
        }

        return impurity;
    }
    
    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves. All attributes are assumed to be numeric.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public DecisionTree(double[][] x, int[] y, int maxNodes) {
        this(null, x, y, maxNodes);
    }

    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves. All attributes are assumed to be numeric.
     *
     * @param x the training instances.
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param rule the splitting rule.
     */
    public DecisionTree(double[][] x, int[] y, int maxNodes, SplitRule rule) {
        this(null, x, y, maxNodes, 1, rule);
    }

    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves. All attributes are assumed to be numeric.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the minimum size of leaf nodes.
     * @param rule the splitting rule.
     */
    public DecisionTree(double[][] x, int[] y, int maxNodes, int nodeSize, SplitRule rule) {
        this(null, x, y, maxNodes, nodeSize, rule);
    }
    
    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves.
     * 
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     */
    public DecisionTree(Attribute[] attributes, double[][] x, int[] y, int maxNodes) {
        this(attributes, x, y, maxNodes, SplitRule.GINI);
    }

    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param rule the splitting rule.
     */
    public DecisionTree(Attribute[] attributes, double[][] x, int[] y, int maxNodes, SplitRule rule) {
        this(attributes, x, y, maxNodes, 1, x[0].length, rule, null, null);
    }

    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves.
     * 
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param rule the splitting rule.
     */
    public DecisionTree(Attribute[] attributes, double[][] x, int[] y, int maxNodes, int nodeSize, SplitRule rule) {
        this(attributes, x, y, maxNodes, nodeSize, x[0].length, rule, null, null);
    }

    /**
     * Constructor. Learns a classification tree for AdaBoost and Random Forest.
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param mtry the number of input variables to pick to split on at each
     * node. It seems that sqrt(p) give generally good performance, where p
     * is the number of variables.
     * @param rule the splitting rule.
     * @param order the index of training values in ascending order. Note
     * that only numeric attributes need be sorted.
     * @param samples the sample set of instances for stochastic learning.
     * samples[i] is the number of sampling for instance i.
     */
    public DecisionTree(Attribute[] attributes, double[][] x, int[] y, int maxNodes, int nodeSize, int mtry, SplitRule rule, int[] samples, int[][] order) {
        this(attributes, x, y, maxNodes, nodeSize, mtry, rule, samples, order, null);
    }

    /**
     * Constructor. Learns a classification tree for AdaBoost and Random Forest.
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param mtry the number of input variables to pick to split on at each
     * node. It seems that sqrt(p) give generally good performance, where p
     * is the number of variables.
     * @param rule the splitting rule.
     * @param order the index of training values in ascending order. Note
     * that only numeric attributes need be sorted.
     * @param samples the sample set of instances for stochastic learning.
     * samples[i] is the number of sampling for instance i.
     * @param order an ordering of the values in x
     * @param labelMap if non-null, the values in y are assumed to have been converted from sparse
     * to dense using this map, and values returned by predict() should be reverse-mapped
     * through it.
     */
    DecisionTree(Attribute[] attributes, double[][] x, int[] y, int maxNodes, int nodeSize, int mtry, SplitRule rule, int[] samples, int[][] order, SparseClassMap labelMap) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (mtry < 1 || mtry > x[0].length) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
        }

        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum leaves: " + maxNodes);
        }

        if (nodeSize < 1) {
            throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
        }

        // class label set.
        if (labelMap == null) {
            labelMap = new SparseClassMap(y);
            y = labelMap.sparseLabelsToDenseLabels(y);
        }
        this.labelMap = labelMap;
        k = labelMap.numberOfClasses();
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
                
        this.attributes = attributes;
        this.mtry = mtry;
        this.nodeSize = nodeSize;
        this.maxNodes = maxNodes;
        this.rule = rule;
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

        int n = y.length;
        int[] count = new int[k];
        if (samples == null) {
            samples = new int[n];
            for (int i = 0; i < n; i++) {
                samples[i] = 1;
                count[y[i]]++;
            }
            makeCompressedOrder(samples, order != null, true);
        } else {
            boolean allPresent = true;
            for (int i = 0; i < n; i++) {
                count[y[i]] += samples[i];
                if (samples[i] == 0) {
                    allPresent = false;
                }
            }
            makeCompressedOrder(samples, order != null, allPresent);
        }

        root = new Node(labelMap.denseLabelToSparseLabel(Math.whichMax(count)), count);

        TrainNode trainRoot = new TrainNode(root, x, y, samples, 0, originalOrder.length);
        if(maxNodes == Integer.MAX_VALUE) {// depth-first split
            if (trainRoot.findBestSplit()) {
                trainRoot.split(null);
            }
        } else {// best-first split
            // Priority queue for best-first tree growing.
            PriorityQueue<TrainNode> nextSplits = new PriorityQueue<>();

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
                if(!node.split(nextSplits)) { // Split the parent node into two children nodes
                    leaves--;
                }
            }
            pruneRedundantLeaves(root);
        }

        this.order = null;
        this.originalOrder = null;
    }

    /**
     * Modifies {@link #order} so that it includes only elements where samples != 0. Populates
     * {@link #originalOrder} to be an ascending index of elements where samples != 0.
     * @param samples how many times each element occurs in the training set.
     * @param mustCopyOrder whether {@link #order} variable must be copied, even if the copy
     *        contains no changes.
     * @param allPresent whether every element in samples is non-zero.
     */
    private void makeCompressedOrder(int[] samples, boolean mustCopyOrder, boolean allPresent) {
        int p = order.length;
        int n = samples.length;
        if (allPresent && mustCopyOrder) {
            int[][] orderCopy = new int[p][];
            for (int i = 0; i < p; i++) {
                if (order[i] != null) {
                    orderCopy[i] = order[i].clone();
                }
            }
            order = orderCopy;
        }

        if (!allPresent) {
            int[][] compressedOrder;
            if (mustCopyOrder) {
                compressedOrder = new int[p][];
            } else {
                // Rewrite in place.
                compressedOrder = order;
            }
            int presentCount = 0;
            for (int i = 0; i < n; i++) {
                if (samples[i] != 0) {
                    presentCount++;
                }
            }
            for (int i = 0; i < p; i++) {
                if (order[i] != null) {
                    int[] variableOrder = order[i];
                    int[] compressedVariableOrder = new int[presentCount];
                    int k = 0;
                    for (int j = 0; j < n; j++) {
                        if (samples[variableOrder[j]] != 0) {
                            compressedVariableOrder[k++] = variableOrder[j];
                        }
                    }
                    compressedOrder[i] = compressedVariableOrder;
                }
            }
            order = compressedOrder;

            originalOrder = new int[presentCount];
            int k = 0;
            for (int i = 0; i < n; i++) {
                if (samples[i] != 0) {
                    originalOrder[k++] = i;
                }
            }
        } else {
            originalOrder = new int[n];
            for (int i = 0; i < n; i++) {
                originalOrder[i] = i;
            }
        }
    }

    /**
     * Modifies {@link #order} and {@link #originalOrder} by partitioning the range from low
     * (inclusive) to high (exclusive) so that all elements i for which goesLeft(i) is true come
     * before all elements for which it is false, but element ordering is otherwise preserved. The
     * number of true values returned by goesLeft must equal split-low.
     * @param low the low bound of the segment of the order arrays which will be partitioned.
     * @param split where the partition's split point will end up.
     * @param high the high bound of the segment of the order arrays which will be partitioned.
     * @param goesLeft whether an element goes to the left side or the right side of the
     *        partition.
     * @param buffer scratch space large enough to hold all elements for which goesLeft is false.
     */
    private void partitionOrder(int low, int split, int high, IntPredicate goesLeft, int[] buffer) {
        for (int[] variableOrder : order) {
            if (variableOrder != null) {
                partitionArray(variableOrder, low, split, high, goesLeft, buffer);
            }
        }
        partitionArray(originalOrder, low, split, high, goesLeft, buffer);
    }

    /**
     * Modifies an array in-place by partitioning the range from low (inclusive) to high (exclusive)
     * so that all elements i for which goesLeft(i) is true come before all elements for which it is
     * false, but element ordering is otherwise preserved. The number of true values returned by
     * goesLeft must equal split-low. buffer is scratch space large enough (i.e., at least
     * high-split long) to hold all elements for which goesLeft is false.
     */
    private void partitionArray(int[] a, int low, int split, int high, IntPredicate goesLeft, int[] buffer) {
        int j = low;
        int k = 0;
        for (int i = low; i < high; i++) {
            if (goesLeft.test(a[i])) {
                a[j++] = a[i];
            } else {
                buffer[k++] = a[i];
            }
        }
        System.arraycopy(buffer, 0, a, split, k);
    }

    /**
     * Prunes redundant leaves from the tree. In some cases, a node is split into two leaves that
     * get assigned the same label, so this recursively combines leaves when it notices this
     * situation.
     */
    private void pruneRedundantLeaves(Node node) {
        if (node.isLeaf()) {
            return;
        }

        // The children might not be leaves now, but might collapse into leaves given the chance.
        pruneRedundantLeaves(node.trueChild);
        pruneRedundantLeaves(node.falseChild);

        if (node.trueChild.isLeaf() && node.falseChild.isLeaf() &&
            node.trueChild.output == node.falseChild.output) {
            node.trueChild = null;
            node.falseChild = null;
            importance[node.splitFeature] -= node.splitScore;
        } else {
            // This is an interior node, and will remain so. Its count array is dead weight.
            node.count = null;
        }
    }

    /**
     * Turns a set of counts into posterior probabilities.
     */
    private void computePosteriori(int[] count, int totalCount, double[] posteriori) {
        // Apply add-k smoothing to the counts to get the posterior probability estimates.
        double scale = 1. / ((double) totalCount + k);
        for (int i = 0; i < k; i++) {
            posteriori[i] = (count[i] + 1) * scale;
        }
    }

    /**
     * Returns the variable importance. Every time a split of a node is made
     * on variable the (GINI, information gain, etc.) impurity criterion for
     * the two descendent nodes is less than the parent node. Adding up the
     * decreases for each individual variable over the tree gives a simple
     * measure of variable importance.
     *
     * @return the variable importance
     */
    public double[] importance() {
        return importance;
    }
    
    @Override
    public int predict(double[] x) {
        return root.predict(x);
    }

    /**
     * Predicts the class label of an instance and also calculate a posteriori
     * probabilities. The posteriori estimation is based on sample distribution
     * in the leaf node. It is not accurate at all when be used in a single tree.
     * It is mainly used by RandomForest in an ensemble way.
     */
    @Override
    public int predict(double[] x, double[] posteriori) {
        return root.predict(x, posteriori);
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
        builder.append("digraph DecisionTree {\n node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n edge [fontname=helvetica];\n");

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
            if (node.isLeaf()) {
                builder.append(String.format(" %d [label=<class = %d>, fillcolor=\"#00000000\", shape=ellipse];\n", id, node.output));
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

    /**
     * Adds a string representation of each node in this subtree to "lines", in false-child-first
     * postorder. Returns a class count for all the leaves in the subtree.
     */
    private int[] subtreeToLines(Node node, Node parent, int depth, BigInteger number, List<String> lines) {
        int[] count;
        if (!node.isLeaf()) {
            BigInteger trueNumber = number.shiftLeft(1);
            BigInteger falseNumber = trueNumber.add(BigInteger.ONE);
            int[] falseCount = subtreeToLines(node.falseChild, node, depth + 1, falseNumber, lines);
            int[] trueCount = subtreeToLines(node.trueChild, node, depth + 1, trueNumber, lines);
            count = new int[k];
            for (int i = 0; i < k; i++) {
                count[i] = trueCount[i] + falseCount[i];
            }
        } else {
            count = node.count;
        }

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            line.append(" ");
        }
        line.append(number).append(") ");

        if (parent == null) {
            line.append("root ");
        } else {
            boolean onTrueBranch = node == parent.trueChild;
            Attribute splitFeature = attributes[parent.splitFeature];
            String condition;
            String value;
            if (splitFeature.getType() == Attribute.Type.NOMINAL) {
                String[] labels = ((NominalAttribute) splitFeature).values();
                condition = "=";
                if (onTrueBranch) {
                    value = labels[(int) parent.splitValue];
                } else {
                    List<String> falseValues = new ArrayList<>(labels.length - 1);
                    for (int i = 0; i < labels.length; i++) {
                        if (i != (int) parent.splitValue) {
                            falseValues.add(labels[i]);
                        }
                    }
                    value = String.join(",", falseValues);
                }
            } else {
                condition = onTrueBranch ? "<=" : ">";
                value = String.format("%g", parent.splitValue);
            }
            line.append(splitFeature.getName()).append(condition).append(value).append(" ");
        }

        int totalCount = Math.sum(count);
        line.append(totalCount).append(" ");
        line.append(String.format("%.5g", totalCount * node.impurity)).append(" ");
        line.append(node.output).append(" (");
        double[] posteriori = new double[k];
        computePosteriori(count, totalCount, posteriori);
        if (!labelMap.isIdentity()) {
            double[] sparsePosteriori = new double[labelMap.maxSparseLabel() + 1];
            for (int i = 0; i < k; i++) {
                sparsePosteriori[labelMap.denseLabelToSparseLabel(i)] = posteriori[i];
            }
            posteriori = sparsePosteriori;
        }

        for (int i = 0; i < posteriori.length; i++) {
            if (i != 0) {
                    line.append(" ");
            }
            line.append(String.format("%.5g", posteriori[i]));
        }
        line.append(")");
        if (node.isLeaf()) {
                line.append(" *");
        }
        line.append("\n");
        lines.add(line.toString());
        return count;
    }

    /**
     * Returns a text representation of the tree in the same format produced by R's "rpart"
     * package.
     */
    public String toRpart() {
        // We're going to build up the lines in reverse order: the false-child-first postorder turns
        // into a true-child-first preorder, which is what's needed.
        List<String> lines = new ArrayList<>();
        int[] treeCount = subtreeToLines(getRoot(), null, 0, BigInteger.ONE, lines);
        lines.add("* denotes terminal node\n");
        lines.add("node), split, n, loss, yval, (yprob)\n");
        lines.add("n= " + Math.sum(treeCount) + "\n");
        Collections.reverse(lines);
        return String.join("", lines);
    }

    /**
     * Returs the root node.
     * @return root node.
     */
    public Node getRoot() {
        return root;
    }
}
