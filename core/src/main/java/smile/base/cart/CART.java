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

package smile.base.cart;

import smile.data.DataFrame;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.math.MathEx;
import smile.sort.QuickSort;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import java.util.AbstractMap.SimpleEntry;

/** Classification and regression tree. */
public abstract class CART {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CART.class);

    /** The schema of data. */
    protected StructType schema;

    /** The root of decision tree. */
    protected Node root;
    /**
     * The number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     */
    protected int nodeSize = 5;
    /**
     * The maximum number of leaf nodes in the tree.
     */
    protected int maxNodes = 6;
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

    /** The dependent variable. */
    protected transient BaseVector y;

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

    /**
     * Constructor.
     * @param x the data frame of the explanatory variable.
     * @param y the response variables.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param mtry the number of input variables to pick to split on at each
     *             node. It seems that sqrt(p) give generally good performance,
     *             where p is the number of variables.
     * @param samples the sample set of instances for stochastic learning.
     *               samples[i] is the number of sampling for instance i.
     * @param order the index of training values in ascending order. Note
     *              that only numeric attributes need be sorted.
     */
    public CART(DataFrame x, BaseVector y, int nodeSize, int maxNodes, int mtry, int[] samples, int[][] order) {
        this.x = x;
        this.y = y;
        this.schema = x.schema();
        this.importance = new double[x.ncols()];
        this.nodeSize = nodeSize;
        this.maxNodes = maxNodes;
        this.mtry = mtry;

        if (mtry < 1 || mtry > schema.length()) {
            logger.warn("Invalid number of variables to split on at a node of the tree: . Use all features." + mtry);
            this.mtry = schema.length();
        }

        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum leaves: " + maxNodes);
        }

        if (nodeSize < 1) {
            throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
        }

        int n = x.size();
        int p = x.ncols();

        IntStream idx;
        if (samples == null) {
            this.samples = Collections.nCopies(n, 1).parallelStream().mapToInt(i -> i).toArray();
            idx = IntStream.range(0, n);
            this.index = idx.toArray();
        } else {
            this.samples = samples;
            idx = IntStream.range(0, samples.length).filter(i -> samples[i] > 0);
            this.index = idx.toArray();
        }

        buffer  = new int[index.length];

        if (order == null) {
            this.order = order(x);
        } else {
            this.order = new int[order.length][];
            for (int i = 0; i < order.length; i++) {
                if (order[i] != null) {
                    final int[] o = order[i];
                    this.order[i] = idx.map(j -> o[j]).toArray();
                }
            }
        }
    }

    /** Returns the index of ordered samples for each ordinal column. */
    public static int[][] order(DataFrame x) {
        int n = x.size();
        int p = x.ncols();
        StructType schema = x.schema();

        double[] a = new double[n];
        int[][] order = new int[p][];

        for (int j = 0; j < p; j++) {
            Measure measure = schema.field(j).measure;
            if (measure == null || !(measure instanceof NominalScale)) {
                x.column(j).toDoubleArray(a);
                order[j] = QuickSort.sort(a);
            }
        }

        return order;
    }

    /** Clear the workspace of building tree. */
    protected void clear() {
        this.x = null;
        this.y = null;
        this.order = null;
        this.index = null;
        this.samples = null;
        this.buffer = null;
    }

    /**
     * Split a node into two children nodes.
     * Returns a new InternalNode if split success.
     * Otherwise, return the node.
     */
    protected boolean split(final Split split, PriorityQueue<Split> queue) {
        if (split.feature < 0) {
            throw new IllegalStateException("Split a node with invalid feature.");
        }

        if (split.trueCount < nodeSize || split.falseCount < nodeSize) {
            // We should not reach here as findBestSplit filters this situation out.
            logger.debug("Node size is too small after splitting");
            return false;
        }

        int[] trueSamples = new int[split.trueCount];
        int[] falseSamples = new int[split.falseCount];
        int mid = split.lo;
        int p = 0, q = 0;
        for (int idx = split.lo; idx < split.hi; idx++) {
            int i = index[idx];
            if (split.predicate().test(i)) {
                trueSamples[p++] = i;
                mid++;
            } else {
                falseSamples[q++] = i;
            }
        }

        assert(p == trueSamples.length);
        assert(q == falseSamples.length);

        LeafNode trueChild = newNode(trueSamples);
        LeafNode falseChild = newNode(falseSamples);
        InternalNode node = split.toNode(trueChild, falseChild);

        shuffle(split.lo, mid, split.hi, split.predicate());

        Optional<Split> trueSplit = findBestSplit(trueChild, split.lo, mid, split.pure.clone());
        Optional<Split> falseSplit = findBestSplit(falseChild, mid, split.hi, split.pure); // reuse parent's pure array

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
        trueSplit.ifPresent(s -> s.parent = node);
        falseSplit.ifPresent(s -> s.parent = node);

        if (queue == null) {
            // deep first split
            trueSplit.ifPresent(s -> split(s, null));
            falseSplit.ifPresent(s -> split(s, null));
        } else {
            // best first split
            trueSplit.ifPresent(s -> queue.add(s));
            falseSplit.ifPresent(s -> queue.add(s));
        }

        return true;
    }

    /**
     * Finds the best attribute to split on a set of samples. at the current node. Returns
     * null if a split doesn't exists to reduce the impurity.
     * @param node the leaf node to split.
     * @param lo the inclusive lower bound of the data partition in the reordered sample index array.
     * @param hi the exclusive upper bound of the data partition in the reordered sample index array.
     * @param pure true if all the samples in the split have the same value in the column.
     */
    protected Optional<Split> findBestSplit(LeafNode node, int lo, int hi, boolean[] pure) {
        if (node.size() < 2 * nodeSize) {
            return Optional.empty(); // one child will has less than nodeSize samples.
        }

        final double impurity = impurity(node);
        if (impurity == 0.0) {
            return Optional.empty(); // all the samples in the node have the same response
        }

        // skip the pure columns
        int p = schema.length();
        int[] columns = IntStream.range(0, p).filter(i -> pure == null || !pure[i]).toArray();

        // random forest
        if (mtry < p) {
            MathEx.permutate(columns);
        }

        IntStream stream = Arrays.stream(columns).limit(mtry);
        Optional<Split> split = (mtry < p ? stream : stream.parallel()) // random forest is in parallel already
                .mapToObj(j -> {
                    Optional<Split> s = findBestSplit(node, j, impurity, lo, hi);
                    if (!s.isPresent()) pure[j] = true;
                    return s;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Split.comparator);

        split.ifPresent(s -> s.pure = pure);
        return split;
    }

    /** Returns the impurity of node. */
    protected abstract double impurity(LeafNode node);

    /** Creates a new leaf node. */
    protected abstract LeafNode newNode(int[] nodeSamples);

    /** Finds the best split for given column. */
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
     * Try http://viz-js.com/ to visualize the returned string.
     */
    public String dot() {
        StringBuilder builder = new StringBuilder();
        builder.append("digraph DecisionTree {\n node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n edge [fontname=helvetica];\n");

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
            builder.append(node.toDot(schema, id));

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
     * Shuffles {@link #index} and {@link #order} by partitioning the range from low
     * (inclusive) to high (exclusive) so that all elements i for which predicate(i) is true come
     * before all elements for which it is false, but element ordering is otherwise preserved. The
     * number of true values returned by predicate must equal split-low.
     * @param low the low bound of the segment of the order arrays which will be partitioned.
     * @param split where the partition's split point will end up.
     * @param high the high bound of the segment of the order arrays which will be partitioned.
     * @param predicate whether an element goes to the left side or the right side of the
     *        partition.
     */
    private void shuffle(int low, int split, int high, IntPredicate predicate) {
        Arrays.stream(order).filter(Objects::nonNull).forEach(o -> shuffle(o, low, split, high, predicate));
        shuffle(index, low, split, high, predicate);
    }

    /**
     * Shuffles an array in-place by partitioning the range from low (inclusive) to high (exclusive)
     * so that all elements i for which goesLeft(i) is true come before all elements for which it is
     * false, but element ordering is otherwise preserved. The number of true values returned by
     * goesLeft must equal split-low. buffer is scratch space large enough (i.e., at least
     * high-split long) to hold all elements for which goesLeft is false.
     */
    private void shuffle(int[] a, int low, int split, int high, IntPredicate predicate) {
        int k = 0;
        for (int i = low, j = low; i < high; i++) {
            if (predicate.test(a[i])) {
                a[j++] = a[i];
            } else {
                buffer[k++] = a[i];
            }
        }

        assert(split + k == high);
        System.arraycopy(buffer, 0, a, split, k);
    }
}
