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
package smile.neighbor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smile.math.Math;
import smile.math.distance.Metric;
import smile.sort.DoubleHeapSelect;

/**
 * Cover tree is a data structure for generic nearest neighbor search, which
 * is especially efficient in spaces with small intrinsic dimension. The cover
 * tree has a theoretical bound that is based on the dataset's doubling constant.
 * The bound on search time is O(c<sup>12</sup> log node) where c is the expansion
 * constant of the dataset.
 * <p>
 * By default, the query object (reference equality) is excluded from the neighborhood.
 * You may change this behavior with <code>setIdenticalExcluded</code>. Note that
 * you may observe weird behavior with String objects. JVM will pool the string literal
 * objects. So the below variables
 * <code>
 *     String a = "ABC";
 *     String b = "ABC";
 *     String c = "AB" + "C";
 * </code>
 * are actually equal in reference test <code>a == b == c</code>. With toy data that you
 * type explicitly in the code, this will cause problems. Fortunately, the data would be
 * read from secondary storage in production.
 * </p>
 *
 * <h2>References</h2>
 * <ol>
 * <li> Alina Beygelzimer, Sham Kakade, and John Langford. Cover Trees for Nearest Neighbor. ICML 2006. </li>
 * </ol>
 *
 * @param <E> the type of data objects in the tree.
 *
 * @author Haifeng Li
 */
public class CoverTree<E> implements NearestNeighborSearch<E, E>, KNNSearch<E, E>, RNNSearch<E, E> {
    private static final Logger logger = LoggerFactory.getLogger(CoverTree.class);

    /**
     * The dataset to build the cover tree.
     */
    private E[] data;
    /**
     * The distance/metric function for nearest neighbor search.
     */
    private Metric<E> distance;
    /**
     * The root node.
     */
    private Node root;
    /**
     * The base of our expansion constant. In other words the 2 in 2^i used
     * in covering tree and separation invariants of a cover tree. In
     * paper it's suggested the separation invariant is relaxed in batch
     * construction.
     */
    private double base = 1.3;
    /**
     * if we have base 2 then this can be viewed as 1/ln(2), which can be
     * used later on to do invLogBase*ln(d) instead of ln(d)/ln(2), to get log2(d),
     * in getScale method.
     */
    private double invLogBase = 1.0 / Math.log(base);
    /**
     * Whether to exclude query object self from the neighborhood.
     */
    private boolean identicalExcluded = true;

    /**
     * Node in the cover tree.
     */
    class Node {

        /** Index of the data point in the dataset. */
        int idx;
        /** The maximum distance to any grandchild. */
        double maxDist;
        /** The distance to the parent node. */
        double parentDist;
        /** The children of the node. */
        ArrayList<Node> children;
        /**
         * The min i that makes base^i &lt;= maxDist.
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

        /** Returns the instance represented by the node.
         * @return the instance represented by the node.
         */
        E getObject() {
            return data[idx];
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
        int idx;
        /**
         * The last distance is to the current reference point
         * (potential current parent). The previous ones are
         * to reference points that were previously looked at
         * (all potential ancestors).
         */
        ArrayList<Double> dist;

        /**
         * Constructor.
         */
        DistanceSet() {
            dist = new ArrayList<>();
        }

        /**
         * Constructor.
         */
        DistanceSet(int idx) {
            this.idx = idx;
            dist = new ArrayList<>();
        }

        /**
         * Returns the instance represent by this DistanceNode.
         * @return the instance represented by this node.
         */
        E getObject() {
            return data[idx];
        }
    }

    /**
     * A Node and its distance to the current query node.
     */
    class DistanceNode implements Comparable<DistanceNode> {

        /** The distance of the node's point to the query point. */
        double dist;
        /** The node. */
        Node node;

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
            return (int) Math.signum(dist - o.dist);
        }
    }

    /**
     * Constructor.
     * @param dataset the data set for nearest neighbor search.
     * @param distance a metric distance measure for nearest neighbor search.
     */
    public CoverTree(E[] dataset, Metric<E> distance) {
        this(dataset, distance, 1.3);
    }

    /**
     * Constructor.
     * @param dataset the data set for nearest neighbor search.
     * @param distance a metric distance measure for nearest neighbor search.
     * @param base the base of the expansion constant.
     */
    public CoverTree(E[] dataset, Metric<E> distance, double base) {
        if (dataset.length == 0) {
            throw new IllegalArgumentException("Empty dataset");
        }

        this.data = dataset;
        this.distance = distance;

        this.base = base;
        invLogBase = 1.0 / Math.log(base);
        buildCoverTree();
    }

    @Override
    public String toString() {
        return String.format("Cover Tree (%s)", distance);
    }

    /**
     * Set if exclude query object self from the neighborhood.
     */
    public CoverTree<E> setIdenticalExcluded(boolean excluded) {
        identicalExcluded = excluded;
        return this;
    }

    /**
     * Get whether if query object self be excluded from the neighborhood.
     */
    public boolean isIdenticalExcluded() {
        return identicalExcluded;
    }

    /**
     * Builds the cover tree.
     */
    private void buildCoverTree() {
        ArrayList<DistanceSet> pointSet = new ArrayList<>();
        ArrayList<DistanceSet> consumedSet = new ArrayList<>();

        E point = data[0];
        int idx = 0;
        double maxDist = -1;

        for (int i = 1; i < data.length; i++) {
            DistanceSet set = new DistanceSet(i);
            double dist = distance.d(point, data[i]);
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
            Node leaf = newLeaf(p);
            return leaf;
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
                        distSplit(pointSet, newPointSet, set.getObject(), maxScale); // O(|point_saet|)
                        // putting points closer to newPoint into newPointSet (and removing them from far)
                        distSplit(far, newPointSet, set.getObject(), maxScale); // O(|far|)

                        Node newChild = batchInsert(set.idx, nextScale, topScale, newPointSet, newConsumedSet);
                        newChild.parentDist = newDist;

                        children.add(newChild);

                        // putting the unused points from newPointSet back into
                        // pointSet and far
                        double fmax = getCoverRadius(maxScale);
                        for (int i = 0; i < newPointSet.size(); i++) { // O(|newPointSet|)
                            set = newPointSet.get(i);
                            set.dist.remove(set.dist.size() - 1);
                            if (set.dist.get(set.dist.size() - 1) <= fmax) {
                                pointSet.add(set);
                            } else {
                                far.add(set);
                            }
                        }

                        // putting the points consumed while recursing for newPoint into consumedSet
                        for (int i = 0; i < newConsumedSet.size(); i++) { // O(|newPointSet|)
                            set = newConsumedSet.get(i);
                            set.dist.remove(set.dist.size() - 1);
                            consumedSet.add(set);
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
     * @param s 	the level/scale
     * @return 		base^s
     */
    private double getCoverRadius(int s) {
        return Math.pow(base, s);
    }

    /**
     * Find the scale/level of a given value, i.e. the "i" in base^i.
     *
     * @param d 	the value whose scale/level is to be determined.
     * @return 		the scale/level of the given value.
     */
    private int getScale(double d) {
        return (int) Math.ceil(invLogBase * Math.log(d));
    }

    /**
     * Create a new leaf node for a given point p.
     * @param idx the index of the instance this leaf node represents.
     */
    private Node newLeaf(int idx) {
        Node leaf = new Node(idx, 0.0, 0.0, null, 100);
        return leaf;
    }

    /**
     * Returns the max distance of the reference point p in current node to
     * it's children nodes.
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
        for (int i = 0; i < pointSet.size(); i++) {
            DistanceSet n = pointSet.get(i);
            if (n.dist.get(n.dist.size() - 1) <= fmax) {
                newSet.add(n);
            } else {
                farSet.add(n);
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
    private void distSplit(ArrayList<DistanceSet> pointSet, ArrayList<DistanceSet> newPointSet, E newPoint, int maxScale) {
        double fmax = getCoverRadius(maxScale);
        ArrayList<DistanceSet> newSet = new ArrayList<>();
        for (int i = 0; i < pointSet.size(); i++) {
            DistanceSet n = pointSet.get(i);
            double newDist = distance.d(newPoint, n.getObject());
            if (newDist <= fmax) {
                pointSet.get(i).dist.add(newDist);
                newPointSet.add(n);
            } else {
                newSet.add(n);
            }
        }

        pointSet.clear();
        pointSet.addAll(newSet);
    }

    @Override
    public Neighbor<E, E> nearest(E q) {
        return knn(q, 1)[0];
    }

    @Override
    public Neighbor<E, E>[] knn(E q, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (k > data.length) {
            throw new IllegalArgumentException("Neighbor array length is larger than the dataset size");
        }

        E e = root.getObject();
        double d = distance.d(e, q);

        // Neighbor array of length 1.
        Neighbor<E, E> n1 = new Neighbor<>(e, e, root.idx, d);
        @SuppressWarnings("unchecked")
        Neighbor<E, E>[] a1 = (Neighbor<E, E>[]) java.lang.reflect.Array.newInstance(n1.getClass(), 1);

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
        if (!identicalExcluded || root.getObject() != q) {
            heap.add(d);
            emptyHeap = false;
        }

        while (!currentCoverSet.isEmpty()) {
            ArrayList<DistanceNode> nextCoverSet = new ArrayList<>();
            for (int i = 0; i < currentCoverSet.size(); i++) {
                DistanceNode par = currentCoverSet.get(i);
                Node parent = currentCoverSet.get(i).node;
                for (int c = 0; c < parent.children.size(); c++) {
                    Node child = parent.children.get(c);
                    if (c == 0) {
                        d = par.dist;
                    } else {
                        d = distance.d(child.getObject(), q);
                    }

                    double upperBound = emptyHeap ? Double.POSITIVE_INFINITY : heap.peek();
                    if (d <= (upperBound + child.maxDist)) {
                        if (c > 0 && d < upperBound) {
                            if (!identicalExcluded || child.getObject() != q) {
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

        ArrayList<Neighbor<E, E>> list = new ArrayList<>();
        double upperBound = heap.peek();
        for (int i = 0; i < zeroSet.size(); i++) {
            DistanceNode ds = zeroSet.get(i);
            if (ds.dist <= upperBound) {
                if (!identicalExcluded || ds.node.getObject() != q) {
                    e = ds.node.getObject();
                    list.add(new Neighbor<>(e, e, ds.node.idx, ds.dist));
                }
            }
        }

        Neighbor<E, E>[] neighbors = list.toArray(a1);
        if (neighbors.length < k) {
            logger.warn(String.format("CoverTree.knn(%d) returns only %d neighbors", k, neighbors.length));
        }

        Arrays.sort(neighbors);

        Math.reverse(neighbors);

        if (neighbors.length > k) {
            neighbors = Arrays.copyOf(neighbors, k);
        }

        return neighbors;
    }

    @Override
    public void range(E q, double radius, List<Neighbor<E, E>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        ArrayList<DistanceNode> currentCoverSet = new ArrayList<>();
        ArrayList<DistanceNode> zeroSet = new ArrayList<>();

        double d = distance.d(root.getObject(), q);
        currentCoverSet.add(new DistanceNode(d, root));

        while (!currentCoverSet.isEmpty()) {
            ArrayList<DistanceNode> nextCoverSet = new ArrayList<>();
            for (int i = 0; i < currentCoverSet.size(); i++) {
                DistanceNode par = currentCoverSet.get(i);
                Node parent = currentCoverSet.get(i).node;
                for (int c = 0; c < parent.children.size(); c++) {
                    Node child = parent.children.get(c);
                    if (c == 0) {
                        d = par.dist;
                    } else {
                        d = distance.d(child.getObject(), q);
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

        for (int i = 0; i < zeroSet.size(); i++) {
            DistanceNode ds = zeroSet.get(i);
            if (!identicalExcluded || ds.node.getObject() != q) {
                neighbors.add(new Neighbor<>(ds.node.getObject(), ds.node.getObject(), ds.node.idx, ds.dist));
            }
        }
    }
}
