/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
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
import smile.util.DoubleArrayList;

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
    private final double invLogBase ;

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
        /** The children of the node. */
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
     * A point's distance to the current reference point p.
     */
    class DistanceSet {

        /** The index of the instance represented by this node. */
        final int idx;
        /**
         * The last distance is to the current reference point
         * (potential current parent). The previous ones are
         * to reference points that were previously looked at
         * (all potential ancestors).
         */
        final DoubleArrayList dist;

        /**
         * Constructor.
         */
        DistanceSet(int idx) {
            this.idx = idx;
            dist = new DoubleArrayList();
        }

        /** Returns the data key represented by the DistanceNode.
         * @return the data key represented by the node.
         */
        K getKey() {
            return keys.get(idx);
        }

        /** Returns the data object represented by the DistanceNode.
         * @return the data object represented by the node.
         */
        V getValue() {
            return data.get(idx);
        }
    }

    /**
     * A Node and its distance to the current query node.
     */
    class DistanceNode implements Comparable<DistanceNode> {

        /** The distance of the node's point to the query point. */
        final double dist;
        /** The node. */
        final Node node;

        /**
         * Constructor.
         * @param dist the distance of the node to the query.
         * @param node the node.
         */
        DistanceNode(double dist, Node node) {
            this.dist = dist;
            this.node = node;
        }

        @Override
        public int compareTo(DistanceNode o) {
            return Double.compare(dist, o.dist);
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
     * Builds the cover tree.
     */
    private void buildCoverTree() {
        ArrayList<DistanceSet> pointSet = new ArrayList<>();
        ArrayList<DistanceSet> consumedSet = new ArrayList<>();

        K point = keys.get(0);
        int idx = 0;
        int n = keys.size();
        double maxDist = -1;

        for (int i = 1; i < n; i++) {
            DistanceSet set = new DistanceSet(i);
            double dist = distance.d(point, keys.get(i));
            set.dist.add(dist);
            pointSet.add(set);
            if (dist > maxDist) {
                maxDist = dist;
            }
        }

        root = batchInsert(idx, getScale(maxDist), getScale(maxDist), pointSet, consumedSet);
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
            double maxDist = max(pointSet); // O(|pointSet|) the max dist in pointSet to point "p".
            int nextScale = Math.min(maxScale - 1, getScale(maxDist));
            if (nextScale == Integer.MIN_VALUE) { // We have points with distance 0. if maxDist is 0.
                ArrayList<Node> children = new ArrayList<>();
                Node leaf = newLeaf(p);
                children.add(leaf);
                while (!pointSet.isEmpty()) {
                    DistanceSet set = pointSet.get(pointSet.size() - 1);
                    pointSet.remove(pointSet.size() - 1);
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
                        DistanceSet set = pointSet.get(pointSet.size() - 1);
                        pointSet.remove(pointSet.size() - 1);
                        double newDist = set.dist.get(set.dist.size() - 1);
                        consumedSet.add(set);

                        // putting points closer to newPoint into newPointSet (and removing them from pointSet)
                        distSplit(pointSet, newPointSet, set.getKey(), maxScale); // O(|point_saet|)
                        // putting points closer to newPoint into newPointSet (and removing them from far)
                        distSplit(far, newPointSet, set.getKey(), maxScale); // O(|far|)

                        Node newChild = batchInsert(set.idx, nextScale, topScale, newPointSet, newConsumedSet);
                        newChild.parentDist = newDist;

                        children.add(newChild);

                        // putting the unused points from newPointSet back into
                        // pointSet and far
                        double fmax = getCoverRadius(maxScale);
                        for (DistanceSet ds : newPointSet) { // O(|newPointSet|)
                            ds.dist.remove(ds.dist.size() - 1);
                            if (ds.dist.get(ds.dist.size() - 1) <= fmax) {
                                pointSet.add(ds);
                            } else {
                                far.add(ds);
                            }
                        }

                        // putting the points consumed while recursing for newPoint into consumedSet
                        for (DistanceSet ds : newConsumedSet) { // O(|newPointSet|)
                            ds.dist.remove(ds.dist.size() - 1);
                            consumedSet.add(ds);
                        }

                        newPointSet.clear();
                        newConsumedSet.clear();
                    }

                    pointSet.addAll(far); // pointSet=far;

                    Node node = new Node(p);
                    node.scale = topScale - maxScale;
                    node.maxDist = max(consumedSet);
                    node.children = children;
                    return node;
                }
            }
        }
    }

    /**
     * Returns the distance/value of a given scale/level, i.e. the value of
     * base^i (e.g. 2^i).
     *
     * @param s the level/scale
     * @return base^s
     */
    private double getCoverRadius(int s) {
        return Math.pow(base, s);
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
     * Returns the max distance of the reference point p in current node to
     * its children nodes.
     * @param v the stack of DistanceNode objects.
     * @return the distance of the furthest child.
     */
    private double max(ArrayList<DistanceSet> v) {
        double max = 0.0;
        for (DistanceSet n : v) {
            if (max < n.dist.get(n.dist.size() - 1)) {
                max = n.dist.get(n.dist.size() - 1);
            }
        }
        return max;
    }

    /**
     * Splits a given pointSet into near and far based on the given
     * scale/level. All points with distance > base^maxScale would be moved
     * to far set. In other words, all those points that are not covered by the
     * next child ball of a point p (ball made of the same point p but of
     * smaller radius at the next lower level) are removed from the supplied
     * current pointSet and put into farSet.
     *
     * @param pointSet the supplied set from which all far points
     * would be removed.
     * @param farSet the set in which all far points having distance
     * > base^maxScale would be put into.
     * @param maxScale the given scale based on which the distances
     * of points are judged to be far or near.
     */
    private void split(ArrayList<DistanceSet> pointSet, ArrayList<DistanceSet> farSet, int maxScale) {
        double fmax = getCoverRadius(maxScale);
        ArrayList<DistanceSet> newSet = new ArrayList<>();
        for (DistanceSet ds : pointSet) {
            if (ds.dist.get(ds.dist.size() - 1) <= fmax) {
                newSet.add(ds);
            } else {
                farSet.add(ds);
            }
        }

        pointSet.clear();
        pointSet.addAll(newSet);
    }

    /**
     * Moves all the points in pointSet covered by (the ball of) newPoint
     * into newPointSet, based on the given scale/level.
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
        ArrayList<DistanceSet> newSet = new ArrayList<>();
        for (DistanceSet ds : pointSet) {
            double newDist = distance.d(newPoint, ds.getKey());
            if (newDist <= fmax) {
                ds.dist.add(newDist);
                newPointSet.add(ds);
            } else {
                newSet.add(ds);
            }
        }

        pointSet.clear();
        pointSet.addAll(newSet);
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

        ArrayList<DistanceNode> currentCoverSet = new ArrayList<>();
        ArrayList<DistanceNode> zeroSet = new ArrayList<>();

        currentCoverSet.add(new DistanceNode(d, root));

        DoubleHeapSelect heap = new DoubleHeapSelect(k);
        heap.add(Double.MAX_VALUE);

        boolean emptyHeap = true;
        if (root.getKey() != q) {
            heap.add(d);
            emptyHeap = false;
        }

        while (!currentCoverSet.isEmpty()) {
            ArrayList<DistanceNode> nextCoverSet = new ArrayList<>();
            for (DistanceNode par : currentCoverSet) {
                Node parent = par.node;
                for (int c = 0; c < parent.children.size(); c++) {
                    Node child = parent.children.get(c);
                    if (c == 0) {
                        d = par.dist;
                    } else {
                        d = distance.d(child.getKey(), q);
                    }

                    double upperBound = emptyHeap ? Double.MAX_VALUE : heap.peek();
                    if (d <= (upperBound + child.maxDist)) {
                        if (c > 0 && d < upperBound) {
                            if (child.getKey() != q) {
                                heap.add(d);
                            }
                        }

                        if (child.children != null) {
                            nextCoverSet.add(new DistanceNode(d, child));
                        } else if (d <= upperBound) {
                            zeroSet.add(new DistanceNode(d, child));
                        }
                    }
                }
            }
            currentCoverSet = nextCoverSet;
        }

        ArrayList<Neighbor<K, V>> list = new ArrayList<>();
        double upperBound = heap.peek();
        for (DistanceNode ds : zeroSet) {
            if (ds.dist <= upperBound) {
                if (ds.node.getKey() != q) {
                    e = ds.node.getKey();
                    list.add(new Neighbor<>(e, ds.node.getValue(), ds.node.idx, ds.dist));
                }
            }
        }

        Neighbor<K, V>[] neighbors = list.toArray(a1);
        if (neighbors.length < k) {
            logger.warn(String.format("CoverTree.knn(%d) returns only %d neighbors", k, neighbors.length));
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

        ArrayList<DistanceNode> currentCoverSet = new ArrayList<>();
        ArrayList<DistanceNode> zeroSet = new ArrayList<>();

        double d = distance.d(root.getKey(), q);
        currentCoverSet.add(new DistanceNode(d, root));

        while (!currentCoverSet.isEmpty()) {
            ArrayList<DistanceNode> nextCoverSet = new ArrayList<>();
            for (DistanceNode par : currentCoverSet) {
                Node parent = par.node;
                for (int c = 0; c < parent.children.size(); c++) {
                    Node child = parent.children.get(c);
                    if (c == 0) {
                        d = par.dist;
                    } else {
                        d = distance.d(child.getKey(), q);
                    }

                    if (d <= (radius + child.maxDist)) {
                        if (child.children != null) {
                            nextCoverSet.add(new DistanceNode(d, child));
                        } else if (d <= radius) {
                            zeroSet.add(new DistanceNode(d, child));
                        }
                    }
                }
            }
            currentCoverSet = nextCoverSet;
        }

        for (DistanceNode ds : zeroSet) {
            if (ds.node.getKey() != q) {
                neighbors.add(new Neighbor<>(ds.node.getKey(), ds.node.getValue(), ds.node.idx, ds.dist));
            }
        }
    }
}
