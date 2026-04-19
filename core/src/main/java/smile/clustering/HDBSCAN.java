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
package smile.clustering;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import static smile.clustering.Clustering.OUTLIER;

/**
 * Hierarchical Density-Based Spatial Clustering of Applications with Noise
 * (HDBSCAN).
 * <p>
 * HDBSCAN extends DBSCAN by building a hierarchy of density-connected
 * components on the mutual-reachability graph and then selecting stable
 * clusters from the hierarchy.
 * <p>
 * This implementation follows the core pipeline in the paper and the reference
 * Python implementation:
 * <ol>
 * <li> estimate core distances with {@code minPoints}</li>
 * <li> build the mutual-reachability graph</li>
 * <li> compute a minimum spanning tree</li>
 * <li> convert to a hierarchy and perform stability-based cluster selection
 * with {@code minClusterSize}</li>
 * </ol>
 *
 * <h2>References</h2>
 * <ol>
 * <li> Campello, R. J. G. B., Moulavi, D., and Sander, J. Density-Based
 * Clustering Based on Hierarchical Density Estimates. PAKDD, 2013.</li>
 * <li> McInnes, L., Healy, J., Astels, S. hdbscan: Hierarchical density based
 * clustering. Journal of Open Source Software, 2017.</li>
 * </ol>
 *
 * @param <T> the data type.
 *
 * @author Haifeng Li
 */
public class HDBSCAN<T> extends Partitioning {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The number of neighbors for core-distance estimation. */
    private final int minPoints;
    /** The minimum cluster size used in condensing hierarchy. */
    private final int minClusterSize;
    /** Core distance of each sample. */
    private final double[] coreDistance;
    /** Stability score of selected clusters. */
    private final double[] stability;

    /**
     * Constructor.
     * @param k the number of clusters.
     * @param group the cluster labels.
     * @param minPoints the number of neighbors for core-distance estimation.
     * @param minClusterSize the minimum cluster size.
     * @param coreDistance the core distance of each point.
     * @param stability the stability scores of selected clusters.
     */
    public HDBSCAN(int k, int[] group, int minPoints, int minClusterSize, double[] coreDistance, double[] stability) {
        super(k, group);
        this.minPoints = minPoints;
        this.minClusterSize = minClusterSize;
        this.coreDistance = coreDistance;
        this.stability = stability;
    }

    /**
     * Returns the number of neighbors for core-distance estimation.
     * @return the number of neighbors for core-distance estimation.
     */
    public int minPoints() {
        return minPoints;
    }

    /**
     * Returns the minimum cluster size.
     * @return the minimum cluster size.
     */
    public int minClusterSize() {
        return minClusterSize;
    }

    /**
     * Returns the core distances.
     * @return the core distances.
     */
    public double[] coreDistance() {
        return coreDistance;
    }

    /**
     * Returns the stability scores of selected clusters.
     * @return the cluster stability.
     */
    public double[] stability() {
        return stability;
    }

    /**
     * HDBSCAN hyperparameters.
     * @param minPoints the number of neighbors for core-distance estimation.
     * @param minClusterSize the minimum cluster size used in hierarchy condensing.
     */
    public record Options(int minPoints, int minClusterSize) {
        /** Constructor. */
        public Options {
            if (minPoints < 1) {
                throw new IllegalArgumentException("Invalid minPoints: " + minPoints);
            }

            if (minClusterSize < 2) {
                throw new IllegalArgumentException("Invalid minClusterSize: " + minClusterSize);
            }
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.hdbscan.min_points", Integer.toString(minPoints));
            props.setProperty("smile.hdbscan.min_cluster_size", Integer.toString(minClusterSize));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int minPoints = Integer.parseInt(props.getProperty("smile.hdbscan.min_points", "5"));
            int minClusterSize = Integer.parseInt(props.getProperty("smile.hdbscan.min_cluster_size", "5"));
            return new Options(minPoints, minClusterSize);
        }
    }

    /**
     * Clusters the data with Euclidean distance.
     * @param data the observations.
     * @param minPoints the number of neighbors for core-distance estimation.
     * @param minClusterSize the minimum cluster size.
     * @return the model.
     */
    public static HDBSCAN<double[]> fit(double[][] data, int minPoints, int minClusterSize) {
        return fit(data, new EuclideanDistance(), new Options(minPoints, minClusterSize));
    }

    /**
     * Clusters the data with Euclidean distance.
     * @param data the observations.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static HDBSCAN<double[]> fit(double[][] data, Options options) {
        return fit(data, new EuclideanDistance(), options);
    }

    /**
     * Clusters the data.
     * @param data the observations.
     * @param distance the distance function.
     * @param minPoints the number of neighbors for core-distance estimation.
     * @param minClusterSize the minimum cluster size.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> HDBSCAN<T> fit(T[] data, Distance<T> distance, int minPoints, int minClusterSize) {
        return fit(data, distance, new Options(minPoints, minClusterSize));
    }

    /**
     * Clusters the data.
     * @param data the observations.
     * @param distance the distance function.
     * @param options the hyperparameters.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> HDBSCAN<T> fit(T[] data, Distance<T> distance, Options options) {
        if (data.length < 2) {
            throw new IllegalArgumentException("Invalid data size: " + data.length);
        }

        int minPoints = options.minPoints;
        int minClusterSize = options.minClusterSize;

        int n = data.length;
        double[][] pairwise = pairwiseDistances(data, distance);
        double[] core = coreDistances(pairwise, minPoints);
        Edge[] mst = mutualReachabilityMST(pairwise, core);
        Dendrogram dendrogram = hierarchyFromMST(n, mst);
        Selection selection = selectClusters(dendrogram, minClusterSize);
        int[] group = label(selection.selected, dendrogram.nodes, n);
        int k = 0;
        for (int yi : group) {
            if (yi != OUTLIER && yi + 1 > k) {
                k = yi + 1;
            }
        }

        return new HDBSCAN<>(k, group, minPoints, minClusterSize, core, selection.stability);
    }

    private static <T> double[][] pairwiseDistances(T[] data, Distance<T> distance) {
        int n = data.length;
        double[][] pairwise = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = distance.d(data[i], data[j]);
                if (!Double.isFinite(d) || d < 0.0) {
                    throw new IllegalArgumentException("Invalid distance at (" + i + ", " + j + "): " + d);
                }
                pairwise[i][j] = d;
                pairwise[j][i] = d;
            }
        }
        return pairwise;
    }

    private static double[] coreDistances(double[][] pairwise, int minPoints) {
        int n = pairwise.length;
        int kth = Math.max(0, minPoints - 1);
        double[] core = new double[n];
        for (int i = 0; i < n; i++) {
            if (kth == 0) {
                core[i] = 0.0;
                continue;
            }

            double[] row = new double[n - 1];
            int p = 0;
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    row[p++] = pairwise[i][j];
                }
            }
            Arrays.sort(row);
            int index = Math.min(kth - 1, row.length - 1);
            core[i] = row[index];
        }
        return core;
    }

    private static Edge[] mutualReachabilityMST(double[][] pairwise, double[] core) {
        int n = pairwise.length;
        boolean[] inTree = new boolean[n];
        double[] best = new double[n];
        int[] parent = new int[n];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Arrays.fill(parent, -1);

        best[0] = 0.0;
        Edge[] mst = new Edge[n - 1];
        int m = 0;
        for (int it = 0; it < n; it++) {
            int u = -1;
            double min = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (!inTree[i] && best[i] < min) {
                    min = best[i];
                    u = i;
                }
            }

            inTree[u] = true;
            if (parent[u] >= 0) {
                mst[m++] = new Edge(parent[u], u, best[u]);
            }

            for (int v = 0; v < n; v++) {
                if (!inTree[v] && u != v) {
                    double mr = Math.max(Math.max(core[u], core[v]), pairwise[u][v]);
                    if (mr < best[v]) {
                        best[v] = mr;
                        parent[v] = u;
                    }
                }
            }
        }

        Arrays.sort(mst);
        return mst;
    }

    private static Dendrogram hierarchyFromMST(int n, Edge[] mst) {
        int total = 2 * n - 1;
        Node[] nodes = new Node[total];
        for (int i = 0; i < n; i++) {
            nodes[i] = Node.leaf(i);
        }

        IntDisjointSet uf = new IntDisjointSet(n);
        int[] componentNode = new int[n];
        for (int i = 0; i < n; i++) {
            componentNode[i] = i;
        }

        int next = n;
        for (Edge e : mst) {
            int ru = uf.find(e.u);
            int rv = uf.find(e.v);
            if (ru == rv) {
                continue;
            }

            int left = componentNode[ru];
            int right = componentNode[rv];
            double lambda = e.weight <= 0.0 ? Double.POSITIVE_INFINITY : 1.0 / e.weight;
            nodes[next] = Node.internal(next, left, right, lambda, nodes[left].size + nodes[right].size);
            nodes[left].parent = next;
            nodes[right].parent = next;

            int root = uf.union(ru, rv);
            componentNode[root] = next;
            next++;
        }

        int root = next - 1;
        return new Dendrogram(nodes, root, n);
    }

    private static Selection selectClusters(Dendrogram tree, int minClusterSize) {
        List<Integer> selected = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        select(tree.root, 0.0, tree, minClusterSize, selected, scores);
        double[] stability = new double[scores.size()];
        for (int i = 0; i < scores.size(); i++) {
            stability[i] = scores.get(i);
        }
        return new Selection(selected.stream().mapToInt(i -> i).toArray(), stability);
    }

    private static double select(int id, double parentLambda, Dendrogram tree, int minClusterSize,
                                 List<Integer> selected, List<Double> selectedScores) {
        Node node = tree.nodes[id];
        if (node.size < minClusterSize) {
            return 0.0;
        }

        if (node.isLeaf()) {
            return 0.0;
        }

        double left = select(node.left, node.lambda, tree, minClusterSize, selected, selectedScores);
        double right = select(node.right, node.lambda, tree, minClusterSize, selected, selectedScores);
        double children = left + right;
        double own = (node.lambda - parentLambda) * node.size;

        if (own >= children) {
            removeDescendants(id, tree, selected, selectedScores);
            selected.add(id);
            selectedScores.add(own);
            return own;
        } else {
            return children;
        }
    }

    private static void removeDescendants(int id, Dendrogram tree, List<Integer> selected, List<Double> selectedScores) {
        for (int i = selected.size() - 1; i >= 0; i--) {
            if (isDescendant(selected.get(i), id, tree)) {
                selected.remove(i);
                selectedScores.remove(i);
            }
        }
    }

    private static boolean isDescendant(int node, int ancestor, Dendrogram tree) {
        int p = tree.nodes[node].parent;
        while (p >= 0) {
            if (p == ancestor) {
                return true;
            }
            p = tree.nodes[p].parent;
        }
        return false;
    }

    private static int[] label(int[] selected, Node[] nodes, int n) {
        int[] group = new int[n];
        Arrays.fill(group, OUTLIER);
        for (int c = 0; c < selected.length; c++) {
            assign(selected[c], c, nodes, group);
        }
        return group;
    }

    private static void assign(int nodeId, int label, Node[] nodes, int[] group) {
        Node node = nodes[nodeId];
        if (node.isLeaf()) {
            group[node.id] = label;
            return;
        }

        assign(node.left, label, nodes, group);
        assign(node.right, label, nodes, group);
    }

    private static final class IntDisjointSet {
        private final int[] parent;
        private final int[] rank;

        IntDisjointSet(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        int union(int x, int y) {
            int rx = find(x);
            int ry = find(y);
            if (rx == ry) {
                return rx;
            }

            if (rank[rx] < rank[ry]) {
                parent[rx] = ry;
                return ry;
            } else if (rank[rx] > rank[ry]) {
                parent[ry] = rx;
                return rx;
            } else {
                parent[ry] = rx;
                rank[rx]++;
                return rx;
            }
        }
    }

    private record Edge(int u, int v, double weight) implements Comparable<Edge> {
        @Override
        public int compareTo(Edge o) {
            return Double.compare(weight, o.weight);
        }
    }

    private static final class Node {
        final int id;
        final int left;
        final int right;
        final int size;
        final double lambda;
        int parent = -1;

        private Node(int id, int left, int right, int size, double lambda) {
            this.id = id;
            this.left = left;
            this.right = right;
            this.size = size;
            this.lambda = lambda;
        }

        static Node leaf(int id) {
            return new Node(id, -1, -1, 1, Double.POSITIVE_INFINITY);
        }

        static Node internal(int id, int left, int right, double lambda, int size) {
            return new Node(id, left, right, size, lambda);
        }

        boolean isLeaf() {
            return left < 0;
        }
    }

    private record Dendrogram(Node[] nodes, int root, int n) {}

    private record Selection(int[] selected, double[] stability) {}
}
