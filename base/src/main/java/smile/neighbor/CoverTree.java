/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smile.math.MathEx;
import smile.math.distance.Metric;
import smile.sort.DoubleHeapSelect;
import smile.util.IntDoubleHashMap;

/**
 * Cover tree is a data structure for generic nearest neighbor search, which
 * is especially efficient in spaces with small intrinsic dimension. The cover
 * tree has a theoretical bound that is based on the dataset's doubling constant.
 * The bound on search time is O(c<sup>12</sup> log node) where c is the expansion
 * constant of the dataset.
 * <p>
 * By default, the query object (reference equality) is excluded from the
 * neighborhood. You may change this behavior with
 * <code>setIdenticalExcluded</code>. Note that you may observe weird behavior
 * with String objects. JVM will pool the string literal objects. So the below
 * variables
 * <code>
 *     String a = "ABC";
 *     String b = "ABC";
 *     String c = "AB" + "C";
 * </code>
 * are actually equal in reference test <code>a == b == c</code>. With toy data
 * that you type explicitly in the code, this will cause problems. Fortunately,
 * the data would be generally read from secondary storage in production.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Alina Beygelzimer, Sham Kakade, and John Langford. Cover Trees for Nearest Neighbor. ICML 2006. </li>
 * </ol>
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public class CoverTree<K, V> implements KNNSearch<K, V>, RNNSearch<K, V>, Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CoverTree.class);

    /**
     * The object keys.
     */
    private final List<K> keys;
    /**
     * The data objects.
     */
    private final List<V> data;
    /**
     * The distance/metric function for nearest neighbor search.
     */
    private final Metric<K> distance;
    /**
     * The root node.
     */
    private Node root;
    /**
     * The base of our expansion constant. In other words the 2 in 2^i used
     * in covering tree and separation invariants of a cover tree. In
     * paper, it's suggested the separation invariant is relaxed in batch
     * construction.
     */
    private final double base;
    /**
     * If we have base 2 then this can be viewed as 1/ln(2), which can be
     * used later on to do invLogBase*ln(d) instead of ln(d)/ln(2), to get log2(d),
     * in getScale method.
     */
    private final double invLogBase;
    /**
     * Cache of cover radii: scale -&gt; base^scale. Avoids repeated Math.pow
     * calls during both build and search. Uses a primitive int-&gt;double map
     * to avoid autoboxing overhead.
     */
    private final IntDoubleHashMap coverRadiusCache = new IntDoubleHashMap();

    /**
     * Node in the cover tree.
     */
    class Node implements Serializable {

        /** Index of the data point in the dataset. */
        final int idx;
        /** The maximum distance to any grandchild. */
        double maxDist;
        /** The distance to the parent node. */
        double parentDist;
        /**
         * The children of the node. {@code null} for leaf nodes.
         */
        ArrayList<Node> children;
        /**
         * The min i that makes base<sup>i</sup> {@code <= maxDist}.
         * Essentially, it is an upper bound on the distance to any child.
         */
        int scale;

        /** Constructor. */
        Node(int idx) {
            this.idx = idx;
        }

        /**
         * Constructor.
         * @param idx the index of the object this node is associated with.
         * @param maxDist the distance of the furthest descendant.
         * @param parentDist the distance of the node to its parent.
         * @param children children of the node in a stack.
         * @param scale the scale/level of the node in the tree.
         */
        Node(int idx, double maxDist, double parentDist, ArrayList<Node> children, int scale) {
            this.idx = idx;
            this.maxDist = maxDist;
            this.parentDist = parentDist;
            this.children = children;
            this.scale = scale;
        }

        /** Returns the data key represented by the node.
         * @return the data key represented by the node.
         */
        K getKey() {
            return keys.get(idx);
        }

        /** Returns the data object represented by the node.
         * @return the data object represented by the node.
         */
        V getValue() {
            return data.get(idx);
        }

        /** Returns whether if the node is a leaf or not.
         * @return true if the node is a leaf node.
         */
        boolean isLeaf() {
            return children == null;
        }
    }

    /**
     * A point's distance stack to ancestor reference points accumulated
     * during batch construction. Uses a plain {@code double[]} array as a
     * stack to avoid the overhead of {@code DoubleArrayList} bounds-checks
     * and potential resizing on every push/pop.
     */
    class DistanceSet {

        /** The index of the instance represented by this node. */
        final int idx;
        /**
         * Stack of distances to ancestor reference points.
         * {@code distStack[distTop-1]} is the distance to the current
         * reference point (the potential current parent).
         */
        double[] distStack;
        /** Number of valid entries in {@code distStack}. */
        int distTop;

        /**
         * Constructor.
         */
        DistanceSet(int idx) {
            this.idx = idx;
            distStack = new double[8];
            distTop = 0;
        }

        /** Push a distance onto the stack. */
        void push(double d) {
            if (distTop == distStack.length) {
                distStack = Arrays.copyOf(distStack, distStack.length * 2);
            }
            distStack[distTop++] = d;
        }

        /** Pop the top distance off the stack and return it. */
        double pop() {
            return distStack[--distTop];
        }

        /** Peek at the top distance without removing it. */
        double peek() {
            return distStack[distTop - 1];
        }

        /** Returns the data key represented by the DistanceSet.
         * @return the data key represented by the node.
         */
        K getKey() {
            return keys.get(idx);
        }

        /** Returns the data object represented by the DistanceSet.
         * @return the data object represented by the node.
         */
        V getValue() {
            return data.get(idx);
        }
    }

    /**
     * Constructor.
     * @param keys the data keys.
     * @param data the data objects.
     * @param distance a metric distance measure for nearest neighbor search.
     */
    public CoverTree(K[] keys, V[] data, Metric<K> distance) {
        this(keys, data, distance, 1.3);
    }

    /**
     * Constructor.
     * @param keys the data keys.
     * @param data the data objects.
     * @param distance a metric distance measure for nearest neighbor search.
     */
    public CoverTree(List<K> keys, List<V> data, Metric<K> distance) {
        this(keys, data, distance, 1.3);
    }

    /**
     * Constructor.
     * @param keys the data keys.
     * @param data the data objects.
     * @param distance a metric distance measure for nearest neighbor search.
     * @param base the base of the expansion constant.
     */
    public CoverTree(K[] keys, V[] data, Metric<K> distance, double base) {
        this(Arrays.asList(keys), Arrays.asList(data), distance, base);
    }

    /**
     * Constructor.
     * @param keys the data keys.
     * @param data the data objects.
     * @param distance a metric distance measure for nearest neighbor search.
     * @param base the base of the expansion constant.
     */
    public CoverTree(List<K> keys, List<V> data, Metric<K> distance, double base) {
        if (keys.size() != data.size()) {
            throw new IllegalArgumentException("Different size of keys and data objects");
        }

        this.keys = keys;
        this.data = data;
        this.distance = distance;

        this.base = base;
        this.invLogBase = 1.0 / Math.log(base);
        buildCoverTree();
    }

    /**
     * Return a cover tree of the data.
     * @param data the data objects, which are also used as key.
     * @param distance a metric distance measure for nearest neighbor search.
     * @param <T> the type of keys and values.
     * @return Cover tree.
     */
    public static <T> CoverTree<T, T> of(T[] data, Metric<T> distance) {
        return new CoverTree<>(data, data, distance);
    }

    /**
     * Return a cover tree of the data.
     * @param data the data objects, which are also used as key.
     * @param distance a metric distance measure for nearest neighbor search.
     * @param base the base of the expansion constant.
     * @param <T> the type of keys and values.
     * @return Cover tree.
     */
    public static <T> CoverTree<T, T> of(T[] data, Metric<T> distance, double base) {
        return new CoverTree<>(data, data, distance, base);
    }

    /**
     * Return a cover tree of the data.
     * @param data the data objects, which are also used as key.
     * @param distance a metric distance measure for nearest neighbor search.
     * @param <T> the type of keys and values.
     * @return Cover tree.
     */
    public static <T> CoverTree<T, T> of(List<T> data, Metric<T> distance) {
        return new CoverTree<>(data, data, distance);
    }

    /**
     * Return a cover tree of the data.
     * @param data the data objects, which are also used as key.
     * @param distance a metric distance measure for nearest neighbor search.
     * @param base the base of the expansion constant.
     * @param <T> the type of keys and values.
     * @return Cover tree.
     */
    public static <T> CoverTree<T, T> of(List<T> data, Metric<T> distance, double base) {
        return new CoverTree<>(data, data, distance, base);
    }

    @Override
    public String toString() {
        return String.format("Cover Tree (%s)", distance);
    }

    /**
     * Returns the number of data points in the cover tree.
     * @return the number of data points.
     */
    public int size() {
        return keys.size();
    }

    /**
     * Builds the cover tree.
     */
    private void buildCoverTree() {
        ArrayList<DistanceSet> pointSet = new ArrayList<>();
        ArrayList<DistanceSet> consumedSet = new ArrayList<>();

        K point = keys.getFirst();
        int n = keys.size();
        double maxDist = -1;

        for (int i = 1; i < n; i++) {
            DistanceSet set = new DistanceSet(i);
            double dist = distance.d(point, keys.get(i));
            set.push(dist);
            pointSet.add(set);
            if (dist > maxDist) {
                maxDist = dist;
            }
        }

        root = batchInsert(0, getScale(maxDist), getScale(maxDist), pointSet, consumedSet);
    }

    /**
     * Creates a cover tree recursively using batch insert method.
     *
     * @param p the index of the instance from which to create the
     * first node. All other points will be inserted beneath this node
     * for p.
     * @param maxScale the current scale/level where the node is to be
     * created (Also determines the radius of the cover balls created at
     * this level).
     * @param topScale the max scale in the whole tree.
     * @param pointSet the set of unprocessed points from which child nodes
     * need to be created.
     * @param consumedSet the set of processed points from which child
     * nodes have already been created. This would be used to find the
     * radius of the cover ball of p.
     * @return the node of cover tree created with p.
     */
    private Node batchInsert(int p, int maxScale, int topScale, ArrayList<DistanceSet> pointSet, ArrayList<DistanceSet> consumedSet) {
        if (pointSet.isEmpty()) {
            return newLeaf(p);
        } else {
            double maxDist = maxDist(pointSet); // O(|pointSet|) the max dist in pointSet to point "p".
            int nextScale = Math.min(maxScale - 1, getScale(maxDist));
            if (nextScale == Integer.MIN_VALUE) { // We have points with distance 0. if maxDist is 0.
                ArrayList<Node> children = new ArrayList<>();
                Node leaf = newLeaf(p);
                children.add(leaf);
                while (!pointSet.isEmpty()) {
                    DistanceSet set = pointSet.removeLast();
                    leaf = newLeaf(set.idx);
                    children.add(leaf);
                    consumedSet.add(set);
                }
                Node node = new Node(p); // make a new node out of p and assign it the children.
                node.scale = 100; // A magic number meant to be larger than all scales.
                node.maxDist = 0; // since all points have distance 0 to p
                node.children = children;
                return node;
            } else {
                ArrayList<DistanceSet> far = new ArrayList<>();
                split(pointSet, far, maxScale); // O(|pointSet|)

                Node child = batchInsert(p, nextScale, topScale, pointSet, consumedSet);

                if (pointSet.isEmpty()) {
                    // not creating any node in this recursive call
                    pointSet.addAll(far); // pointSet=far;
                    return child;
                } else {
                    ArrayList<Node> children = new ArrayList<>();
                    children.add(child);
                    ArrayList<DistanceSet> newPointSet = new ArrayList<>();
                    ArrayList<DistanceSet> newConsumedSet = new ArrayList<>();

                    while (!pointSet.isEmpty()) { // O(|pointSet| * .size())
                        DistanceSet set = pointSet.removeLast();
                        double newDist = set.peek();
                        consumedSet.add(set);

                        // putting points closer to newPoint into newPointSet (and removing them from pointSet)
                        distSplit(pointSet, newPointSet, set.getKey(), maxScale); // O(|pointSet|)
                        // putting points closer to newPoint into newPointSet (and removing them from far)
                        distSplit(far, newPointSet, set.getKey(), maxScale); // O(|far|)

                        Node newChild = batchInsert(set.idx, nextScale, topScale, newPointSet, newConsumedSet);
                        newChild.parentDist = newDist;

                        children.add(newChild);

                        // putting the unused points from newPointSet back into
                        // pointSet and far
                        double fmax = getCoverRadius(maxScale);
                        for (DistanceSet ds : newPointSet) { // O(|newPointSet|)
                            ds.pop();
                            if (ds.peek() <= fmax) {
                                pointSet.add(ds);
                            } else {
                                far.add(ds);
                            }
                        }

                        // putting the points consumed while recursing for newPoint into consumedSet
                        for (DistanceSet ds : newConsumedSet) { // O(|newConsumedSet|)
                            ds.pop();
                            consumedSet.add(ds);
                        }

                        newPointSet.clear();
                        newConsumedSet.clear();
                    }

                    pointSet.addAll(far); // pointSet=far;

                    Node node = new Node(p);
                    node.scale = topScale - maxScale;
                    node.maxDist = maxDist(consumedSet);
                    node.children = children;
                    return node;
                }
            }
        }
    }

    /**
     * Returns the distance/value of a given scale/level, i.e. the value of
     * base^i (e.g. 2^i). Results are cached in a primitive int-&gt;double map
     * to avoid repeated {@code Math.pow} calls and autoboxing overhead.
     *
     * @param s the level/scale
     * @return base^s
     */
    private double getCoverRadius(int s) {
        double v = coverRadiusCache.get(s);
        if (Double.isNaN(v)) {
            v = Math.pow(base, s);
            coverRadiusCache.put(s, v);
        }
        return v;
    }

    /**
     * Find the scale/level of a given value, i.e. the "i" in base^i.
     *
     * @param d the value whose scale/level is to be determined.
     * @return the scale/level of the given value.
     */
    private int getScale(double d) {
        return (int) Math.ceil(invLogBase * Math.log(d));
    }

    /**
     * Create a new leaf node for a given point p.
     * @param idx the index of the instance this leaf node represents.
     */
    private Node newLeaf(int idx) {
        return new Node(idx, 0.0, 0.0, null, 100);
    }

    /**
     * Returns the max distance (top of distance stack) among all entries.
     * @param v the list of DistanceSet objects.
     * @return the distance of the furthest child.
     */
    private double maxDist(ArrayList<DistanceSet> v) {
        double max = 0.0;
        for (DistanceSet n : v) {
            double d = n.peek();
            if (d > max) max = d;
        }
        return max;
    }

    /**
     * Splits a given pointSet into near and far based on the given
     * scale/level. All points with distance {@code > base^maxScale} are moved
     * to the far set. The operation is done in-place to avoid creating a
     * temporary list.
     *
     * @param pointSet the supplied set from which all far points
     * would be removed.
     * @param farSet the set in which all far points having distance
     * {@code > base^maxScale} would be put into.
     * @param maxScale the given scale based on which the distances
     * of points are judged to be far or near.
     */
    private void split(ArrayList<DistanceSet> pointSet, ArrayList<DistanceSet> farSet, int maxScale) {
        double fmax = getCoverRadius(maxScale);
        int write = 0;
        for (int i = 0; i < pointSet.size(); i++) {
            DistanceSet ds = pointSet.get(i);
            if (ds.peek() <= fmax) {
                pointSet.set(write++, ds);
            } else {
                farSet.add(ds);
            }
        }
        pointSet.subList(write, pointSet.size()).clear();
    }

    /**
     * Moves all the points in pointSet covered by (the ball of) newPoint
     * into newPointSet, based on the given scale/level. The operation is
     * done in-place to avoid creating a temporary list.
     *
     * @param pointSet the supplied set of instances from which
     * all points covered by newPoint will be removed.
     * @param newPointSet the set in which all points covered by
     * newPoint will be put into.
     * @param newPoint the given new point.
     * @param maxScale the scale based on which distances are
     * judged (radius of cover ball is calculated).
     */
    private void distSplit(ArrayList<DistanceSet> pointSet, ArrayList<DistanceSet> newPointSet, K newPoint, int maxScale) {
        double fmax = getCoverRadius(maxScale);
        int write = 0;
        for (int i = 0; i < pointSet.size(); i++) {
            DistanceSet ds = pointSet.get(i);
            double newDist = distance.d(newPoint, ds.getKey());
            if (newDist <= fmax) {
                ds.push(newDist);
                newPointSet.add(ds);
            } else {
                pointSet.set(write++, ds);
            }
        }
        pointSet.subList(write, pointSet.size()).clear();
    }

    @Override
    public Neighbor<K, V>[] search(K q, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (k > data.size()) {
            throw new IllegalArgumentException("Neighbor array length is larger than the dataset size");
        }

        K e = root.getKey();
        double d = distance.d(e, q);

        // Neighbor array of length 1.
        Neighbor<K, V> n1 = new Neighbor<>(e, root.getValue(), root.idx, d);
        @SuppressWarnings("unchecked")
        Neighbor<K, V>[] a1 = (Neighbor<K, V>[]) java.lang.reflect.Array.newInstance(n1.getClass(), 1);

        //if root is the only node
        if (root.children == null) {
            a1[0] = n1;
            return a1;
        }

        // Use parallel arrays of (distance, node) pairs to avoid DistanceNode
        // object allocation on every level expansion.
        int initCap = 64;
        double[] curDist = new double[initCap];
        // Object[] instead of Node[] to avoid generic array creation restriction
        Object[] curNodes = new Object[initCap];
        int curSize = 0;

        double[] nextDist = new double[initCap];
        Object[] nextNodes = new Object[initCap];
        int nextSize = 0;

        double[] zeroDist = new double[initCap];
        Object[] zeroNodes = new Object[initCap];
        int zeroSize = 0;

        curDist[0] = d;
        curNodes[0] = root;
        curSize = 1;

        DoubleHeapSelect heap = new DoubleHeapSelect(k);
        heap.add(Double.MAX_VALUE);

        boolean emptyHeap = true;
        if (root.getKey() != q) {
            heap.add(d);
            emptyHeap = false;
        }

        while (curSize > 0) {
            nextSize = 0;
            for (int pi = 0; pi < curSize; pi++) {
                @SuppressWarnings("unchecked")
                Node parent = (Node) curNodes[pi];
                double parDist = curDist[pi];
                ArrayList<Node> ch = parent.children;
                int nc = ch.size();
                for (int c = 0; c < nc; c++) {
                    Node child = ch.get(c);
                    double cd;
                    if (c == 0) {
                        cd = parDist;
                    } else {
                        cd = distance.d(child.getKey(), q);
                    }

                    double upperBound = emptyHeap ? Double.MAX_VALUE : heap.peek();
                    if (cd <= (upperBound + child.maxDist)) {
                        if (c > 0 && cd < upperBound) {
                            if (child.getKey() != q) {
                                heap.add(cd);
                                emptyHeap = false;
                            }
                        }

                        if (child.children != null) {
                            if (nextSize == nextDist.length) {
                                nextDist = Arrays.copyOf(nextDist, nextDist.length * 2);
                                nextNodes = Arrays.copyOf(nextNodes, nextNodes.length * 2);
                            }
                            nextDist[nextSize] = cd;
                            nextNodes[nextSize] = child;
                            nextSize++;
                        } else if (cd <= upperBound) {
                            if (zeroSize == zeroDist.length) {
                                zeroDist = Arrays.copyOf(zeroDist, zeroDist.length * 2);
                                zeroNodes = Arrays.copyOf(zeroNodes, zeroNodes.length * 2);
                            }
                            zeroDist[zeroSize] = cd;
                            zeroNodes[zeroSize] = child;
                            zeroSize++;
                        }
                    }
                }
            }
            // swap cur/next without allocation
            double[] tmpD = curDist; curDist = nextDist; nextDist = tmpD;
            Object[] tmpN = curNodes; curNodes = nextNodes; nextNodes = tmpN;
            curSize = nextSize;
        }

        ArrayList<Neighbor<K, V>> list = new ArrayList<>();
        double upperBound = heap.peek();
        for (int i = 0; i < zeroSize; i++) {
            @SuppressWarnings("unchecked")
            Node zn = (Node) zeroNodes[i];
            double zd = zeroDist[i];
            if (zd <= upperBound && zn.getKey() != q) {
                list.add(new Neighbor<>(zn.getKey(), zn.getValue(), zn.idx, zd));
            }
        }

        Neighbor<K, V>[] neighbors = list.toArray(a1);
        if (neighbors.length < k) {
            logger.warn("CoverTree.knn({}) returns only {} neighbors", k, neighbors.length);
        }

        Arrays.sort(neighbors);

        if (neighbors.length > k) {
            neighbors = Arrays.copyOf(neighbors, k);
        }

        MathEx.reverse(neighbors);

        return neighbors;
    }

    @Override
    public void search(K q, double radius, List<Neighbor<K, V>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        int initCap = 64;
        double[] curDist = new double[initCap];
        Object[] curNodes = new Object[initCap];
        int curSize = 0;

        double[] nextDist = new double[initCap];
        Object[] nextNodes = new Object[initCap];
        int nextSize = 0;

        double d = distance.d(root.getKey(), q);
        curDist[0] = d;
        curNodes[0] = root;
        curSize = 1;

        while (curSize > 0) {
            nextSize = 0;
            for (int pi = 0; pi < curSize; pi++) {
                @SuppressWarnings("unchecked")
                Node parent = (Node) curNodes[pi];
                double parDist = curDist[pi];
                ArrayList<Node> ch = parent.children;
                int nc = ch.size();
                for (int c = 0; c < nc; c++) {
                    Node child = ch.get(c);
                    double cd;
                    if (c == 0) {
                        cd = parDist;
                    } else {
                        cd = distance.d(child.getKey(), q);
                    }

                    if (cd <= (radius + child.maxDist)) {
                        if (child.children != null) {
                            if (nextSize == nextDist.length) {
                                nextDist = Arrays.copyOf(nextDist, nextDist.length * 2);
                                nextNodes = Arrays.copyOf(nextNodes, nextNodes.length * 2);
                            }
                            nextDist[nextSize] = cd;
                            nextNodes[nextSize] = child;
                            nextSize++;
                        } else if (MathEx.le(cd, radius)) {
                            if (child.getKey() != q) {
                                neighbors.add(new Neighbor<>(child.getKey(), child.getValue(), child.idx, cd));
                            }
                        }
                    }
                }
            }
            // swap cur/next without allocation
            double[] tmpD = curDist; curDist = nextDist; nextDist = tmpD;
            Object[] tmpN = curNodes; curNodes = nextNodes; nextNodes = tmpN;
            curSize = nextSize;
        }
    }
}
