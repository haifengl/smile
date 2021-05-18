/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.base.cart;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.feature.SHAP;
import smile.math.MathEx;
import smile.sort.QuickSort;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.IntStream;
import java.util.AbstractMap.SimpleEntry;

/**
 * Classification and regression tree.
 *
 * @author Haifeng Li
 */
public abstract class CART implements SHAP<Tuple>, Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CART.class);

    /** The model formula. */
    protected Formula formula;

    /** The schema of predictors. */
    protected StructType schema;

    /** The schema of response variable. */
    protected StructField response;

    /** The root of decision tree. */
    protected Node root;
    /**
     * The maximum depth of the tree.
     */
    protected int maxDepth = 20;
    /**
     * The maximum number of leaf nodes in the tree.
     */
    protected int maxNodes = 6;
    /**
     * The number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     */
    protected int nodeSize = 5;
    /**
     * The number of input variables to be used to determine the decision
     * at a node of the tree.
     */
    protected int mtry = -1;

    /**
     * Variable importance. Every time a split of a node is made on variable
     * the (GINI, information gain, etc.) impurity criterion for the two
     * descendent nodes is less than the parent node. Adding up the decreases
     * for each individual variable over the tree gives a simple measure of
     * variable importance.
     */
    protected double[] importance;

    /** The training data. */
    protected transient DataFrame x;

    /**
     * The samples for training this node. Note that samples[i] is the
     * number of sampling of dataset[i]. 0 means that the datum is not
     * included and values of greater than 1 are possible because of
     * sampling with replacement.
     */
    protected transient int[] samples;

    /**
     * An index of samples to their original locations in training dataset.
     */
    protected transient int[] index;

    /**
     * An index of training values. Initially, order[i] is a set of indices that iterate through the
     * training values for attribute i in ascending order. During training, the array is rearranged
     * so that all values for each leaf node occupy a contiguous range, but within that range they
     * maintain the original ordering. Note that only numeric attributes will be sorted; non-numeric
     * attributes will have a null in the corresponding place in the array.
     */
    protected transient int[][] order;

    /**
     * The working buffer for reordering {@link #index} array.
     */
    private transient int[] buffer;

    /** Private constructor for deserialization. */
    private CART() {

    }

    /**
     * Constructor.
     * @param formula The model formula.
     * @param schema The data schema of predictors.
     * @param response The response variable.
     * @param root The root node.
     * @param importance The feature importance.
     */
    public CART(Formula formula, StructType schema, StructField response, Node root, double[] importance) {
        this.formula = formula;
        this.schema = schema;
        this.response = response;
        this.root = root;
        this.importance = importance;
    }

    /**
     * Constructor.
     * @param x the data frame of the explanatory variable.
     * @param y the response variables.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize the minimum size of leaf nodes.
     * @param mtry the number of input variables to pick to split on at each
     *             node. It seems that sqrt(p) give generally good performance,
     *             where p is the number of variables.
     * @param samples the sample set of instances for stochastic learning.
     *               samples[i] is the number of sampling for instance i.
     * @param order the index of training values in ascending order. Note
     *              that only numeric attributes need be sorted.
     */
    public CART(DataFrame x, StructField y, int maxDepth, int maxNodes, int nodeSize, int mtry, int[] samples, int[][] order) {
        this.x = x;
        this.response = y;
        this.schema = x.schema();
        this.importance = new double[x.ncol()];
        this.maxDepth = maxDepth;
        this.maxNodes = maxNodes;
        this.nodeSize = nodeSize;
        this.mtry = mtry;

        int n = x.size();
        int p = x.ncol();

        if (mtry < 1 || mtry > p) {
            logger.debug("Invalid mtry. Use all features.");
            this.mtry = p;
        }

        if (maxDepth < 1) {
            throw new IllegalArgumentException("Invalid maximum depth: " + maxDepth);
        }

        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum leaves: " + maxNodes);
        }

        if (nodeSize < 1) {
            throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
        }

        IntStream idx;
        if (samples == null) {
            this.samples = Collections.nCopies(n, 1).parallelStream().mapToInt(i -> i).toArray();
            idx = IntStream.range(0, n);
        } else {
            this.samples = samples;
            idx = IntStream.range(0, samples.length).filter(i -> samples[i] > 0);
        }
        this.index = idx.toArray();

        buffer  = new int[index.length];

        if (order == null) {
            this.order = order(x);
        } else {
            this.order = new int[order.length][];
            for (int i = 0; i < order.length; i++) {
                if (order[i] != null) {
                    this.order[i] = Arrays.stream(order[i]).filter(o -> this.samples[o] > 0).toArray();
                }
            }
        }
    }

    /**
     * Returns the number of nodes in the tree.
     * @return the number of nodes in the tree.
     */
    public int size() {
        return size(root);
    }

    /** Returns the number of nodes of the subtree. */
    private int size(Node node) {
        if (node instanceof LeafNode) return 1;

        InternalNode parent = (InternalNode) node;
        return size(parent.trueChild) + size(parent.falseChild) + 1;
    }

    /**
     * Returns the index of ordered samples for each ordinal column.
     * @param x the predictors.
     * @return the index of ordered samples for each ordinal column.
     */
    public static int[][] order(DataFrame x) {
        int n = x.size();
        int p = x.ncol();
        StructType schema = x.schema();

        double[] a = new double[n];
        int[][] order = new int[p][];

        for (int j = 0; j < p; j++) {
            Measure measure = schema.field(j).measure;
            if (!(measure instanceof NominalScale)) {
                x.column(j).toDoubleArray(a);
                order[j] = QuickSort.sort(a);
            }
        }

        return order;
    }

    /**
     * Returns the predictors by the model formula if it is not null.
     * Otherwise return the input tuple.
     * @param x the input tuple.
     * @return the predictors.
     */
    protected Tuple predictors(Tuple x) {
        return formula == null ? x : formula.x(x);
    }

    /** Clear the workspace of building tree. */
    protected void clear() {
        this.x = null;
        this.order = null;
        this.index = null;
        this.samples = null;
        this.buffer = null;
    }

    /**
     * Split a node into two children nodes.
     *
     * @param split the split candidate.
     * @param queue the queue of splits.
     * @return true if split success.
     */
    protected boolean split(final Split split, PriorityQueue<Split> queue) {
        if (split.feature < 0) {
            throw new IllegalStateException("Split a node with invalid feature.");
        }

        if (split.depth >= maxDepth) {
            logger.debug("Reach maximum depth");
            return false;
        }

        if (split.trueCount < nodeSize || split.falseCount < nodeSize) {
            // We should not reach here as findBestSplit filters this situation out.
            logger.debug("Node size is too small after splitting");
            return false;
        }

        int[] trueSamples = Arrays.stream(index, split.lo, split.hi).filter(i -> split.predicate().test(i)).toArray();

        // cache the results of predicate.test()
        boolean[] trues = new boolean[samples.length];
        for (int i : trueSamples) trues[i] = true;

        int[] falseSamples = Arrays.stream(index, split.lo, split.hi).filter(i -> !trues[i]).toArray();
        int mid = split.lo + trueSamples.length;

        LeafNode trueChild = newNode(trueSamples);
        assert trueChild.size == split.trueCount : String.format("trueChild.size != split.trueCount: %d != %d", trueChild.size, split.trueCount);
        assert trueChild.size >= nodeSize : String.format("trueChild size is too small: %d < %d", trueChild.size, nodeSize);

        LeafNode falseChild = newNode(falseSamples);
        assert falseChild.size == split.falseCount : String.format("falseChild.size != split.falseCount: %d != %d", falseChild.size, split.falseCount);
        assert falseChild.size >= nodeSize : String.format("falseChild size is too small: %d < %d", falseChild.size, nodeSize);
        InternalNode node = split.toNode(trueChild, falseChild);

        shuffle(split.lo, mid, split.hi, trues);

        Optional<Split> trueSplit = findBestSplit(trueChild, split.lo, mid, split.unsplittable.clone());
        Optional<Split> falseSplit = findBestSplit(falseChild, mid, split.hi, split.unsplittable); // reuse parent's array

        // Prune the branch if both children are leaf nodes and of same output value.
        if (trueChild.equals(falseChild) && !trueSplit.isPresent() && !falseSplit.isPresent()) {
            return false;
        }

        if (split.parent == null) {
            this.root = node;
        } else if (split.parent.trueChild == split.leaf) {
            split.parent.trueChild = node;
        } else if (split.parent.falseChild == split.leaf) {
            split.parent.falseChild = node;
        } else {
            throw new IllegalStateException("split.parent and leaf don't match");
        }

        importance[node.feature] += node.score;
        trueSplit.ifPresent(s -> {s.parent = node; s.depth = split.depth + 1;});
        falseSplit.ifPresent(s -> {s.parent = node; s.depth = split.depth + 1;});

        if (queue == null) {
            // deep first split
            trueSplit.ifPresent(s -> split(s, null));
            falseSplit.ifPresent(s -> split(s, null));
        } else {
            // best first split
            trueSplit.ifPresent(queue::add);
            falseSplit.ifPresent(queue::add);
        }

        return true;
    }

    /**
     * Finds the best attribute to split on a set of samples. at the current node. Returns
     * <code>Optional.empty</code> if a split doesn't exists to reduce the impurity.
     * @param node the leaf node to split.
     * @param lo the inclusive lower bound of the data partition in the reordered sample index array.
     * @param hi the exclusive upper bound of the data partition in the reordered sample index array.
     * @param unsplittable unsplittable[j] is true if the column j cannot be split further in the node.
     * @return the best split candidate.
     */
    protected Optional<Split> findBestSplit(LeafNode node, int lo, int hi, boolean[] unsplittable) {
        if (node.size() < 2 * nodeSize) {
            return Optional.empty(); // one child will has less than nodeSize samples.
        }

        final double impurity = impurity(node);
        if (impurity == 0.0) {
            return Optional.empty(); // all the samples in the node have the same response
        }

        // skip the unsplittable columns
        int p = schema.length();
        int[] columns = IntStream.range(0, p).filter(i -> !unsplittable[i]).toArray();

        // random forest
        if (mtry < p) {
            MathEx.permutate(columns);
        }

        IntStream stream = Arrays.stream(columns).limit(mtry);
        Optional<Split> split = (mtry < p ? stream : stream.parallel()) // random forest is in parallel already
                .mapToObj(j -> {
                    Optional<Split> s = findBestSplit(node, j, impurity, lo, hi);
                    if (!s.isPresent()) unsplittable[j] = true;
                    return s;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Split.comparator);

        split.ifPresent(s -> s.unsplittable = unsplittable);
        return split;
    }

    /**
     * Returns the impurity of node.
     * @param node the node to calculate the impurity.
     * @return the impurity of node.
     */
    protected abstract double impurity(LeafNode node);

    /**
     * Creates a new leaf node.
     * @param nodeSamples the samples belonging to this node.
     * @return the new leaf node.
     */
    protected abstract LeafNode newNode(int[] nodeSamples);

    /**
     * Finds the best split for given column.
     * @param node the node to split.
     * @param column the column to split on.
     * @param impurity the impurity of node.
     * @param lo the lower bound of sample index in the node.
     * @param hi the upper bound of sample index in the node.
     * @return the best split.
     */
    protected abstract Optional<Split> findBestSplit(LeafNode node, int column, double impurity, int lo, int hi);

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

    /**
     * Returs the root node.
     * @return root node.
     */
    public Node root() {
        return root;
    }

    /**
     * Returns the graphic representation in Graphviz dot format.
     * Try <a href="http://viz-js.com/">http://viz-js.com/</a>
     * to visualize the returned string.
     * @return the graphic representation in Graphviz dot format.
     */
    public String dot() {
        StringBuilder builder = new StringBuilder();
        builder.append("digraph CART {\n node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n edge [fontname=helvetica];\n");

        String trueLabel  = " [labeldistance=2.5, labelangle=45, headlabel=\"True\"];\n";
        String falseLabel = " [labeldistance=2.5, labelangle=-45, headlabel=\"False\"];\n";

        Queue<SimpleEntry<Integer, Node>> queue = new LinkedList<>();
        queue.add(new SimpleEntry<>(1, root));

        while (!queue.isEmpty()) {
            // Dequeue a vertex from queue and print it
            SimpleEntry<Integer, Node> entry = queue.poll();
            int id = entry.getKey();
            Node node = entry.getValue();

            // leaf node
            builder.append(node.dot(schema, response, id));

            if (node instanceof InternalNode) {
                int tid = 2 * id;
                int fid = 2 * id + 1;
                InternalNode inode = (InternalNode) node;
                queue.add(new SimpleEntry<>(tid, inode.trueChild));
                queue.add(new SimpleEntry<>(fid, inode.falseChild));

                // add edge
                builder.append(' ').append(id).append(" -> ").append(tid).append(trueLabel);
                builder.append(' ').append(id).append(" -> ").append(fid).append(falseLabel);

                // only draw edge label at top
                if (id == 1) {
                    trueLabel = "\n";
                    falseLabel = "\n";
                }
            }
        }

        builder.append("}");
        return builder.toString();
    }

    /**
     * Shuffles {@link #index} and {@link #order} by partitioning the range
     * from low (inclusive) to high (exclusive) so that all elements i for
     * which predicate(i) is true come before all elements for which it is
     * false, but element ordering is otherwise preserved. The number of
     * true values returned by predicate must equal {@code split - low}.
     *
     * @param low the low bound of the segment of the order arrays
     *            which will be partitioned.
     * @param split where the partition's split point will end up.
     * @param high the high bound of the segment of the order arrays
     *             which will be partitioned.
     * @param predicate whether an element goes to the left side or
     *                  the right side of the partition.
     */
    private void shuffle(int low, int split, int high, boolean[] predicate) {
        Arrays.stream(order).filter(Objects::nonNull).forEach(o -> shuffle(o, low, split, high, predicate));
        shuffle(index, low, split, high, predicate);
    }

    /**
     * Shuffles an array in-place by partitioning the range from
     * low (inclusive) to high (exclusive) so that all elements i
     * for which goesLeft(i) is true come before all elements for
     * which it is false, but element ordering is otherwise preserved.
     * The number of true values returned by goesLeft must equal
     * {@code split - low}. buffer is scratch space large enough
     * (i.e., at least {@code high - split} long) to hold all
     * elements for which goesLeft is false.
     *
     * @param a the array to shuffle.
     * @param low the low bound of the segment of the order arrays
     *            which will be partitioned.
     * @param split where the partition's split point will end up.
     * @param high the high bound of the segment of the order arrays
     *             which will be partitioned.
     * @param predicate whether an element goes to the left side or
     *                  the right side of the partition.
     */
    private void shuffle(int[] a, int low, int split, int high, boolean[] predicate) {
        int k = 0;
        for (int i = low, j = low; i < high; i++) {
            if (predicate[a[i]]) {
                a[j++] = a[i];
            } else {
                buffer[k++] = a[i];
            }
        }

        assert(split + k == high);
        System.arraycopy(buffer, 0, a, split, k);
    }

    /**
     * Returns a text representation of the tree in R's rpart format.
     * A semi-graphical layout of the tree. Indentation is used to convey
     * the tree topology. Information for each node includes the node number,
     * split, size, deviance, and fitted value. For the decision tree,
     * the class probabilities are also printed.
     */
    @Override
    public String toString() {
        // Build up the lines in reverse order:
        // the false-child-first postorder turns into
        // the true-child-first preorder, which is what's needed.
        List<String> lines = new ArrayList<>();
        root.toString(schema, response, null, 0, BigInteger.ONE, lines);
        lines.add("* denotes terminal node");
        lines.add("node), split, n, loss, yval, (yprob)");
        lines.add("n=" + root.size());
        Collections.reverse(lines);
        return String.join("\n", lines);
    }

    /**
     * Returns the average of absolute SHAP values over a data frame.
     * @param data the data.
     * @return the average of absolute SHAP values.
     */
    public double[] shap(DataFrame data) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula.bind(data.schema());
        return shap(data.stream().parallel());
    }

    @Override
    public double[] shap(Tuple x) {
        int k = 1;
        Node node = root;
        while (node instanceof InternalNode) {
            node = ((InternalNode) node).trueChild;
        }
        if (node instanceof DecisionNode) {
            k = ((DecisionNode) node).count().length;
        }

        int p = schema.length();
        double[] phi = new double[p * k];
        Path m = new Path(new int[0], new double[0], new double[0], new double[0]);
        recurse(phi, predictors(x), root, m, 1, 1, -1);
        return phi;
    }

    /**
     * Recursively keep track of what proportion of all possible subsets
     * flow down into each of the leaves of the tree.
     */
    private void recurse(double[] phi, Tuple x, Node node, Path m, double pz, double po, int pi) {
        int l = m.length;
        m = m.extend(pz, po, pi);

        if (node instanceof InternalNode) {
            InternalNode split = (InternalNode) node;
            int dj = split.feature();
            Node h, c;
            if (split.branch(x)) {
                h = split.trueChild();
                c = split.falseChild();
            } else {
                h = split.falseChild();
                c = split.trueChild();
            }

            int rh = h.size();
            int rc = c.size();
            int rj = node.size();

            int k = 0;
            for (; k <= l; k++) {
                if (m.d[k] == dj) break;
            }

            double iz = 1.0;
            double io = 1.0;
            if (k <= l) {
                iz = m.z[k];
                io = m.o[k];
                m.unwind(k);
            }

            recurse(phi, x, h, m, iz * rh / rj, io, dj);
            recurse(phi, x, c, m, iz * rc /rj, 0, dj);
        } else {
            if (node instanceof DecisionNode) {
                DecisionNode leaf = ((DecisionNode) node);
                int k = leaf.count().length;
                double[] prob = new double[k];
                leaf.posteriori(prob);
                for (int i = 1; i <= l; i++) {
                    double w = m.unwoundSum(i)* (m.o[i] - m.z[i]);
                    int di = m.d[i] * k;
                    for (int j = 0; j < k; j++) {
                        phi[di + j] += w * prob[j];
                    }
                }
            } else {
                double vj = ((RegressionNode) node).output();
                for (int i = 1; i <= l; i++) {
                    double w = m.unwoundSum(i);
                    phi[m.d[i]] += w * (m.o[i] - m.z[i]) * vj;
                }
            }
        }
    }

    /**
     * The path of unique features we have split
     * on so far during SHAP recursive traverse.
     */
    private static class Path {
        /** The length of path. */
        int length;
        /** The unique feature index. */
        int[] d;
        /**
         * The fraction of zero paths (where this feature is not
         * in the non-zero index set S) that flow through this path.
         */
        double[] z;
        /**
         * The fraction of one paths (where this feature is
         * in the non-zero index set S) that flow through this path.
         */
        double[] o;
        /**
         * The proportion of sets of a given cardinality that are present.
         */
        double[] w;

        /**
         * Constructor.
         */
        Path(int[] d, double[] z, double[] o, double[] w) {
            this.length = d.length;
            this.d = d;
            this.z = z;
            this.o = o;
            this.w = w;
        }

        /**
         * To keep track of each possible subset size during the recursion,
         * grows all these subsets according to a given fraction of ones and
         * zeros.
         */
        Path extend(double pz, double po, int pi) {
            int l = length;
            Path m = new Path(
                    // Arrays.copyOf will truncate or pad with zeros.
                    Arrays.copyOf(d, l + 1),
                    Arrays.copyOf(z, l + 1),
                    Arrays.copyOf(o, l + 1),
                    Arrays.copyOf(w, l + 1)
            );

            m.d[l] = pi;
            m.z[l] = pz;
            m.o[l] = po;
            m.w[l] = l == 0 ? 1 : 0;

            for (int i = l-1; i >= 0; i--) {
                m.w[i+1] += po * m.w[i] * (i+1) / (l+1);
                m.w[i] = pz * m.w[i] * (l - i) / (l+1);
            }

            return m;
        }

        /**
         * Undo previous extensions when we split on the same feature twice,
         * and undo each extension of the path inside a leaf to compute
         * weights for each feature in the path.
         */
        void unwind(int i) {
            double po = o[i];
            double pz = z[i];
            int l = --length;

            double n = w[l];
            if (po != 0) {
                for (int j = l - 1; j >= 0; j--) {
                    double t = w[j];
                    w[j] = n * (l + 1) / ((j + 1) * po);
                    n = t - w[j] * pz * (l - j) / (l + 1);
                }
            } else {
                for (int j = l - 1; j >= 0; j--) {
                    w[j] = (w[j] * (l+1)) / (pz * (l - j));
                }
            }

            for (int j = i; j < l; j++) {
                d[j] = d[j+1];
                z[j] = z[j+1];
                o[j] = o[j+1];
            }
        }

        /**
         * Return the total permutation weight if we unwind a previous
         * extension in the decision path.
         */
        double unwoundSum(int i) {
            double po = o[i];
            double pz = z[i];
            int l = length - 1;
            double sum = 0.0;

            double n = w[l];
            if (po != 0) {
                for (int j = l - 1; j >= 0; j--) {
                    double t = n / ((j+1) * po);
                    sum += t;
                    n = w[j] - t * pz * (l - j);
                }
            } else {
                for (int j = l - 1; j >= 0; j--) {
                    sum += w[j] / (pz * (l - j));
                }
            }

            return sum * (l + 1);
        }
    }
}
