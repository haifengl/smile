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
import java.util.PriorityQueue;
import java.util.concurrent.Callable;

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
public class DecisionTree implements Classifier<double[]> {
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
     * The maximum number of leaf nodes in the tree.
     */
    private int J = 100;
    /**
     * The number of input variables to be used to determine the decision
     * at a node of the tree.
     */
    private int M;
    /**
     * The index of training values in ascending order. Note that only numeric
     * attributes will be sorted.
     */
    private transient int[][] order;

    /**
     * Trainer for decision tree classifiers.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {
        /**
         * The splitting rule.
         */
        private SplitRule rule = SplitRule.GINI;
        /**
         * The maximum number of leaf nodes in the tree.
         */
        private int J = 100;

        /**
         * Default constructor of maximal 100 leaf nodes in the tree.
         */
        public Trainer() {

        }

        /**
         * Constructor.
         * 
         * @param J the maximum number of leaf nodes in the tree.
         */
        public Trainer(int J) {
            if (J < 2) {
                throw new IllegalArgumentException("Invalid number of leaf nodes: " + J);
            }
            
            this.J = J;
        }
        
        /**
         * Constructor.
         * 
         * @param attributes the attributes of independent variable.
         * @param J the maximum number of leaf nodes in the tree.
         */
        public Trainer(Attribute[] attributes, int J) {
            super(attributes);
            
            if (J < 2) {
                throw new IllegalArgumentException("Invalid number of leaf nodes: " + J);
            }
            
            this.J = J;
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
         * @param J the maximum number of leaf nodes in the tree.
         */
        public Trainer setMaximumLeafNodes(int J) {
            if (J < 2) {
                throw new IllegalArgumentException("Invalid number of leaf nodes: " + J);
            }
            
            this.J = J;
            return this;
        }
        
        @Override
        public DecisionTree train(double[][] x, int[] y) {
            return new DecisionTree(attributes, x, y, J, rule);
        }
    }
    
    /**
     * The criterion to choose variable to split instances.
     */
    public static enum SplitRule {
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
        ENTROPY
    }
    
    /**
     * Classification tree node.
     */
    class Node {

        /**
         * Predicted class label for this node.
         */
        int output = -1;
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
         * Children node.
         */
        Node trueChild = null;
        /**
         * Children node.
         */
        Node falseChild = null;
        /**
         * Predicted output for children node.
         */
        int trueChildOutput = -1;
        /**
         * Predicted output for children node.
         */
        int falseChildOutput = -1;

        /**
         * Constructor.
         */
        public Node() {
        }

        /**
         * Constructor.
         */
        public Node(int output) {
            this.output = output;
        }

        /**
         * Evaluate the regression tree over an instance.
         */
        public int predict(double[] x) {
            if (trueChild == null && falseChild == null) {
                return output;
            } else {
                if (attributes[splitFeature].type == Attribute.Type.NOMINAL) {
                    if (x[splitFeature] == splitValue) {
                        return trueChild.predict(x);
                    } else {
                        return falseChild.predict(x);
                    }
                } else if (attributes[splitFeature].type == Attribute.Type.NUMERIC) {
                    if (x[splitFeature] <= splitValue) {
                        return trueChild.predict(x);
                    } else {
                        return falseChild.predict(x);
                    }
                } else {
                    throw new IllegalStateException("Unsupported attribute type: " + attributes[splitFeature].type);
                }
            }
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
         * Constructor.
         */
        public TrainNode(Node node, double[][] x, int[] y, int[] samples) {
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
         * Finds the best attribute to split on at the current node. Returns
         * true if a split exists to reduce squared error, false otherwise.
         */
        public boolean findBestSplit() {
            int N = x.length;
            int label = -1;
            boolean pure = true;
            for (int i = 0; i < N; i++) {
                if (samples[i] > 0) {
                    if (label == -1) {
                        label = y[i];
                    } else if (y[i] != label) {
                        pure = false;
                        break;
                    }
                }
            }
            
            // Since all instances have same label, stop splitting.
            if (pure) {
                return false;
            }
            
            // Sample count in each class.
            int n = 0;
            int[] count = new int[k];
            for (int i = 0; i < N; i++) {
                if (samples[i] > 0) {
                    n += samples[i];
                    count[y[i]] += samples[i];
                }
            }

            double impurity = impurity(count, n);
            
            int p = attributes.length;
            int[] variables = new int[p];
            for (int i = 0; i < p; i++) {
                variables[i] = i;
            }
            
            if (M < p) {
                synchronized (DecisionTree.class) {
                    Math.permutate(variables);
                }

                // Random forest already runs on parallel.
                for (int j = 0; j < M; j++) {
                    Node split = findBestSplit(n, count, impurity, variables[j]);
                    if (split.splitScore > node.splitScore) {
                        node.splitFeature = split.splitFeature;
                        node.splitValue = split.splitValue;
                        node.splitScore = split.splitScore;
                        node.trueChildOutput = split.trueChildOutput;
                        node.falseChildOutput = split.falseChildOutput;
                    }
                }
            } else {

                List<SplitTask> tasks = new ArrayList<SplitTask>(M);
                for (int j = 0; j < M; j++) {
                    tasks.add(new SplitTask(n, count, impurity, variables[j]));
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
                    for (int j = 0; j < M; j++) {
                        Node split = findBestSplit(n, count, impurity, variables[j]);
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
             * The sample count in each class.
             */
            int[] count;
            /**
             * The impurity of this node.
             */
            double impurity;
            /**
             * The index of variables for this task.
             */
            int j;

            SplitTask(int n, int[] count, double impurity, int j) {
                this.n = n;
                this.count = count;
                this.impurity = impurity;                
                this.j = j;
            }

            @Override
            public Node call() {
                return findBestSplit(n, count, impurity, j);
            }
        }
        
        /**
         * Finds the best split cutoff for attribute j at the current node.
         * @param n the number instances in this node.
         * @param count the sample count in each class.
         * @param impurity the impurity of this node.
         * @param j the attribute to split on.
         */
        public Node findBestSplit(int n, int[] count, double impurity, int j) {
            int N = x.length;
            Node splitNode = new Node();

            int[] falseCount = new int[k];
            if (attributes[j].type == Attribute.Type.NOMINAL) {
                int m = ((NominalAttribute) attributes[j]).size();
                int[][] trueCount = new int[m][k];

                for (int i = 0; i < N; i++) {
                    if (samples[i] > 0) {
                        trueCount[(int) x[i][j]][y[i]] += samples[i];
                    }
                }

                for (int l = 0; l < m; l++) {
                    int tc = Math.sum(trueCount[l]);
                    int fc = n - tc;

                    // If either side is empty, skip this feature.
                    if (tc == 0 || fc == 0) {
                        continue;
                    }

                    for (int q = 0; q < k; q++) {
                        falseCount[q] = count[q] - trueCount[l][q];
                    }

                    int trueLabel = Math.whichMax(trueCount[l]);
                    int falseLabel = Math.whichMax(falseCount);
                    double gain = impurity - (double) tc / n * impurity(trueCount[l], tc) - (double) fc / n * impurity(falseCount, fc);

                    if (gain > splitNode.splitScore) {
                        // new best split
                        splitNode.splitFeature = j;
                        splitNode.splitValue = l;
                        splitNode.splitScore = gain;
                        splitNode.trueChildOutput = trueLabel;
                        splitNode.falseChildOutput = falseLabel;
                    }
                }
            } else if (attributes[j].type == Attribute.Type.NUMERIC) {
                int[] trueCount = new int[k];
                double prevx = Double.NaN;
                int prevy = -1;

                for (int i : order[j]) {
                    if (samples[i] > 0) {
                        if (Double.isNaN(prevx) || x[i][j] == prevx || y[i] == prevy) {
                            prevx = x[i][j];
                            prevy = y[i];
                            trueCount[y[i]] += samples[i];
                            continue;
                        }

                        int tc = Math.sum(trueCount);
                        int fc = n - tc;

                        // If either side is empty, continue.
                        if (tc == 0 || fc == 0) {
                            prevx = x[i][j];
                            prevy = y[i];
                            trueCount[y[i]] += samples[i];
                            continue;
                        }

                        for (int l = 0; l < k; l++) {
                            falseCount[l] = count[l] - trueCount[l];
                        }

                        int trueLabel = Math.whichMax(trueCount);
                        int falseLabel = Math.whichMax(falseCount);
                        double gain = impurity - (double) tc / n * impurity(trueCount, tc) - (double) fc / n * impurity(falseCount, fc);

                        if (gain > splitNode.splitScore) {
                            // new best split
                            splitNode.splitFeature = j;
                            splitNode.splitValue = (x[i][j] + prevx) / 2;
                            splitNode.splitScore = gain;
                            splitNode.trueChildOutput = trueLabel;
                            splitNode.falseChildOutput = falseLabel;
                        }

                        prevx = x[i][j];
                        prevy = y[i];
                        trueCount[y[i]] += samples[i];
                    }
                }
            } else {
                throw new IllegalStateException("Unsupported attribute type: " + attributes[j].type);
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

            int n = x.length;
            int tc = 0;
            int fc = 0;
            int[] trueSamples = new int[n];
            int[] falseSamples = new int[n];

            if (attributes[node.splitFeature].type == Attribute.Type.NOMINAL) {
                for (int i = 0; i < n; i++) {
                    if (samples[i] > 0) {
                        if (x[i][node.splitFeature] == node.splitValue) {
                            trueSamples[i] = samples[i];
                            tc += samples[i];
                        } else {
                            falseSamples[i] = samples[i];                            
                            fc += samples[i];
                        }
                    }
                }
            } else if (attributes[node.splitFeature].type == Attribute.Type.NUMERIC) {
                for (int i = 0; i < n; i++) {
                    if (samples[i] > 0) {
                        if (x[i][node.splitFeature] <= node.splitValue) {
                            trueSamples[i] = samples[i];
                            tc += samples[i];
                        } else {
                            falseSamples[i] = samples[i];                            
                            fc += samples[i];
                        }
                    }
                }
            } else {
                throw new IllegalStateException("Unsupported attribute type: " + attributes[node.splitFeature].type);
            }

            if (tc == 0 || fc == 0) {
                node.splitFeature = -1;
                node.splitValue = Double.NaN;
                node.splitScore = 0.0;
                return false;
            }
            
            node.trueChild = new Node(node.trueChildOutput);
            node.falseChild = new Node(node.falseChildOutput);
            
            TrainNode trueChild = new TrainNode(node.trueChild, x, y, trueSamples);
            if (trueChild.findBestSplit()) {
                if (nextSplits != null) {
                    nextSplits.add(trueChild);
                } else {
                    trueChild.split(null);
                }
            }

            TrainNode falseChild = new TrainNode(node.falseChild, x, y, falseSamples);
            if (falseChild.findBestSplit()) {
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
     * Returns the impurity of a node.
     * @param count the sample count in each class.
     * @param n the number of samples in the node.
     * @return  the impurity of a node
     */
    private double impurity(int[] count, int n) {
        double impurity = 0.0;

        switch (rule) {
            case GINI:
                impurity = 1.0;
                for (int i = 0; i < count.length; i++) {
                    if (count[i] > 0) {
                        double p = (double) count[i] / n;
                        impurity -= p * p;
                    }
                }
                break;

            case ENTROPY:
                for (int i = 0; i < count.length; i++) {
                    if (count[i] > 0) {
                        double p = (double) count[i] / n;
                        impurity -= p * Math.log2(p);
                    }
                }
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
     * @param J the maximum number of leaf nodes in the tree.
     */
    public DecisionTree(double[][] x, int[] y, int J) {
        this(null, x, y, J);
    }
    
    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves. All attributes are assumed to be numeric.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param J the maximum number of leaf nodes in the tree.
     * @param rule the splitting rule.
     */
    public DecisionTree(double[][] x, int[] y, int J, SplitRule rule) {
        this(null, x, y, J, rule);
    }
    
    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves.
     * 
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param J the maximum number of leaf nodes in the tree.
     */
    public DecisionTree(Attribute[] attributes, double[][] x, int[] y, int J) {
        this(attributes, x, y, J, SplitRule.GINI);
    }
    
    /**
     * Constructor. Learns a classification tree with (most) given number of
     * leaves.
     * 
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param J the maximum number of leaf nodes in the tree.
     * @param rule the splitting rule.
     */
    public DecisionTree(Attribute[] attributes, double[][] x, int[] y, int J, SplitRule rule) {
        this(attributes, x, y, J, null, null, rule);
    }
    
    /**
     * Constructor. Learns a classification tree for AdaBoost.
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param J the maximum number of leaf nodes in the tree.
     * @param order the index of training values in ascending order. Note
     * that only numeric attributes need be sorted.
     * @param samples the sample set of instances for stochastic learning.
     * samples[i] is the number of sampling for instance i.
     */
    DecisionTree(Attribute[] attributes, double[][] x, int[] y, int J, int[] samples, int[][] order, SplitRule rule) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (J < 2) {
            throw new IllegalArgumentException("Invalid maximum leaves: " + J);
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
                
        this.attributes = attributes;
        this.J = J;
        this.rule = rule;
        this.M = attributes.length;
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
        PriorityQueue<TrainNode> nextSplits = new PriorityQueue<TrainNode>();

        int n = y.length;
        int[] count = new int[k];
        if (samples == null) {
            samples = new int[n];
            for (int i = 0; i < n; i++) {
                samples[i] = 1;
                count[y[i]]++;
            }
        } else {
            for (int i = 0; i < n; i++) {
                count[y[i]] += samples[i];
            }
        }
        
        root = new Node(Math.whichMax(count));
        
        TrainNode trainRoot = new TrainNode(root, x, y, samples);
        // Now add splits to the tree until max tree size is reached
        if (trainRoot.findBestSplit()) {
            nextSplits.add(trainRoot);
        }

        // Pop best leaf from priority queue, split it, and push
        // children nodes into the queue if possible.
        for (int leaves = 1; leaves < this.J; leaves++) {
            // parent is the leaf to split
            TrainNode node = nextSplits.poll();
            if (node == null) {
                break;
            }

            node.split(nextSplits); // Split the parent node into two children nodes
        }
    }
    
    /**
     * Constructor. Learns a classification tree for random forest.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param M the number of input variables to pick to split on at each
     * node. It seems that dim/3 give generally good performance, where dim
     * is the number of variables.
     * @param order the index of training values in ascending order. Note
     * that only numeric attributes need be sorted.
     * @param samples the sample set of instances for stochastic learning.
     * samples[i] is the number of sampling for instance i.
     */
    DecisionTree(Attribute[] attributes, double[][] x, int[] y, int M, int[] samples, int[][] order) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (M <= 0 || M > x[0].length) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + M);
        }

        if (samples == null) {
            throw new IllegalArgumentException("Sampling array is null.");
        }
        
        this.k = Math.max(y) + 1;

        if (k < 2) {
            throw new IllegalArgumentException("Only one class or negative class labels.");
        }
        
        if (attributes == null) {
            int p = x[0].length;
            attributes = new Attribute[p];
            for (int i = 0; i < p; i++) {
                attributes[i] = new NumericAttribute("V" + (i + 1));
            }
        }
                
        this.attributes = attributes;
        this.J = Integer.MAX_VALUE;
        this.M = M;
        this.order = order;
        importance = new double[attributes.length];

        int n = y.length;
        int[] count = new int[k];
        for (int i = 0; i < n; i++) {
            count[y[i]] += samples[i];
        }
        
        root = new Node(Math.whichMax(count));
        
        TrainNode trainRoot = new TrainNode(root, x, y, samples);
        if (trainRoot.findBestSplit()) {
            trainRoot.split(null);
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
     * probabilities. Not supported.
     */
    @Override
    public int predict(double[] x, double[] posteriori) {
        throw new UnsupportedOperationException("Not supported.");
    }
}
