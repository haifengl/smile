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

package smile.graph;

import java.util.*;
import java.util.stream.DoubleStream;
import smile.math.MathEx;
import smile.math.matrix.IMatrix;
import smile.util.ArrayElementConsumer;
import smile.util.ArrayElementFunction;
import smile.util.PriorityQueue;

/**
 * A graph is an abstract representation of a set of objects where some pairs
 * of the objects are connected by links. The interconnected objects are
 * represented by mathematical abstractions called vertices, and the links
 * that connect some pairs of vertices are called edges. The edges may be
 * directed (asymmetric) or undirected (symmetric). A graph is a weighted graph
 * if a number (weight) is assigned to each edge. Such weights might represent,
 * for example, costs, lengths or capacities, etc., depending on the problem.
 *
 * @author Haifeng Li
 */
public abstract class Graph {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Graph.class);
    /**
     * Is the graph directed?
     */
    private final boolean digraph;

    /**
     * Graph edge.
     * @param u the vertex id. For directed graph,
     *          this is the tail of arc.
     * @param v the other vertex id. For directed graph,
     *          this is the head of arc.
     * @param weight the weight of edge. For unweighted graph,
     *               this is always 1.
     */
    public record Edge(int u, int v, double weight) {
        /**
         * Constructor of unweighted edge.
         * @param u the vertex id.
         * @param v the other vertex id.
         */
        public Edge(int u, int v) {
            this(u, v, 1.0);
        }
    }

    /**
     * Constructor.
     * @param digraph true if this is a directed graph.
     */
    public Graph(boolean digraph) {
        this.digraph = digraph;
    }

    /**
     * Return true if the graph is directed.
     * @return true if the graph is directed.
     */
    public boolean isDigraph() {
        return digraph;
    }

    /**
     * Returns the (dense or sparse) matrix representation of the graph.
     * @return the matrix representation of the graph.
     */
    public abstract IMatrix toMatrix();

    /**
     * Returns a subgraph containing all given vertices.
     * @param vertices the vertices to be included in subgraph.
     * @return a subgraph containing all given vertices
     */
    public abstract Graph subgraph(int[] vertices);
    
    /**
     * Returns the number of vertices.
     * @return the number of vertices.
     */
    public abstract int getNumVertices();

    /**
     * Returns true if and only if this graph contains an edge going
     * from the source vertex to the target vertex. In undirected graphs the
     * same result is obtained when source and target are inverted.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return true if this graph contains the specified edge.
     */
    public abstract boolean hasEdge(int source, int target);

    /**
     * Returns the weight assigned to a given edge. Unweighted graphs always
     * return 1.0.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return the edge weight
     */
    public abstract double getWeight(int source, int target);

    /**
     * Sets the weight assigned to a given edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @param weight the edge weight
     * @return this graph.
     */
    public abstract Graph setWeight(int source, int target, double weight);

    /**
     * Returns the edges from the specified vertex. If no edges are
     * touching the specified vertex returns an empty set.
     *
     * @param vertex the id of vertex for which a set of touching edges is to be
     * returned.
     * @return the edges touching the specified vertex.
     */
    public abstract Collection<Edge> getEdges(int vertex);

    /**
     * Performs an action for each edge of a vertex.
     * @param vertex the vertex id.
     * @param action a non-interfering action to perform on the edges.
     */
    public abstract void forEachEdge(int vertex, ArrayElementConsumer action);

    /**
     * Returns a stream consisting of the results of applying the given
     * function to the edge weights of a vertex.
     * @param vertex the vertex id.
     * @param mapper a non-interfering, stateless function to map each
     *               edge weight of a vertex.
     * @return the stream of the new values of edge weights.
     */
    public abstract DoubleStream mapEdges(int vertex, ArrayElementFunction mapper);

    /**
     * Updates the edge weights of a vertex.
     * @param vertex the vertex id.
     * @param mapper a function to map each edge weight to new value.
     */
    public abstract void updateEdges(int vertex, ArrayElementFunction mapper);

    /**
     * Creates a new edge in this graph, going from the source vertex to the
     * target vertex, and returns the created edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    public Graph addEdge(int source, int target) {
        return addEdge(source, target, 1.0);
    }

    /**
     * Creates a new edge in this graph, going from the source vertex to the
     * target vertex, and returns the created edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @param weight the weight of edge.
     */
    public Graph addEdge(int source, int target, double weight) {
        return setWeight(source, target, weight);
    }

    /**
     * Adds a set of edges to the graph.
     *
     * @param edges edges to be added to this graph.
     */
    public Graph addEdges(Collection<Edge> edges) {
        for (Edge edge : edges) {
            setWeight(edge.u, edge.v, edge.weight);
        }
        return this;
    }

    /**
     * Removes a set of edges from the graph.
     *
     * @param edges edges to be removed from this graph.
     */
    public Graph removeEdges(Collection<Edge> edges) {
        for (Edge edge : edges) {
            removeEdge(edge.u, edge.v);
        }
        return this;
    }

    /**
     * In a simple graph, removes and returns the edge going from the specified source
     * vertex to the specified target vertex.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    public Graph removeEdge(int source, int target) {
        return setWeight(source, target, 0.0);
    }

    /**
     * Returns the degree of the specified vertex. A degree of a vertex in an
     * undirected graph is the number of edges touching that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    public int getDegree(int vertex) {
        return digraph ? getInDegree(vertex) + getOutDegree(vertex) : getOutDegree(vertex);
    }

    /**
     * Returns the in-degree of the specified vertex. An in-degree of a vertex in a
     * directed graph is the number of edges head to that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    public abstract int getInDegree(int vertex);

    /**
     * Returns the out-degree of the specified vertex. An out-degree of a vertex in a
     * directed graph is the number of edges from that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    public abstract int getOutDegree(int vertex);

    /**
     * Reverse topological sort digraph by depth-first search of graph.
     * @param v the start vertex.
     * @param visited the flag if vertex has been visited.
     * @param order the array to store the reverse topological order.
     * @param count the number of vertices have been visited before this search.
     *              It will be updated after this search.
     */
    private void dfsort(int v, boolean[] visited, int[] order, int[] count) {
        visited[v] = true;

        forEachEdge(v, (u, w) -> {
            if (!visited[u]) {
                dfsort(u, visited, order, count);
            }
        });

        order[count[0]++] = v;
    }

    /**
     * Reverse topological sort digraph by depth-first search of graph.
     *
     * @return the vertices in the reverse topological order.
     */
    public int[] dfsort() {
        if (!digraph) {
            throw new UnsupportedOperationException("Topological sort is only meaningful for digraph.");
        }

        int n = getNumVertices();
        boolean[] visited = new boolean[n];
        int[] order = new int[n];
        Arrays.fill(order, -1);

        int[] count = new int[1];
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                dfsort(i, visited, order, count);
            }
        }

        return order;
    }

    /**
     * Depth-first search connected components of graph.
     * @param v the start vertex.
     * @param cc the array to store the connected component id of each vertex.
     * @param id the current component id.
     */
    private void dfcc(int v, int[] cc, int id) {
        cc[v] = id;
        forEachEdge(v, (u, w) -> {
            if (cc[u] == -1) {
                dfcc(u, cc, id);
            }
        });
    }

    /**
     * Returns the connected components by depth-first search.
     *
     * @return a two-dimensional array of which each row is the vertices
     *         in the same connected component.
     */
    public int[][] dfcc() {
        int n = getNumVertices();
        int[] cc = new int[n];
        Arrays.fill(cc, -1);

        int id = 0;
        for (int i = 0; i < n; i++) {
            if (cc[i] == -1) {
                dfcc(i, cc, id++);
            }
        }

        return connectedComponents(id, cc);
    }

    /**
     * Return the connected components.
     * @param numComponents the number of connected components.
     * @param cc the component id of each vertex.
     * @return the connected components.
     */
    private int[][] connectedComponents(int numComponents, int[] cc) {
        int n = cc.length;
        int[] size = new int[numComponents];
        for (int c : cc) {
            size[c]++;
        }

        int[][] components = new int[numComponents][];
        for (int i = 0; i < numComponents; i++) {
            components[i] = new int[size[i]];
            for (int j = 0, k = 0; j < n; j++) {
                if (cc[j] == i) {
                    components[i][k++] = j;
                }
            }
            Arrays.sort(components[i]);
        }

        return components;
    }

    /**
     * Depth-first search of graph.
     * @param v the start vertex.
     * @param visited the flag if vertex has been visited.
     */
    private void dfs(Visitor visitor, int v, boolean[] visited) {
        visitor.visit(v);
        visited[v] = true;
        forEachEdge(v, (u, w) -> {
            if (!visited[u]) dfs(visitor, u, visited);
        });
    }

    /**
     * DFS search on graph and performs some operation defined in visitor
     * on each vertex during traveling.
     * @param visitor the visitor functor.
     */
    public void dfs(Visitor visitor) {
        int n = getNumVertices();
        boolean[] visited = new boolean[n];

        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                dfs(visitor, i, visited);
            }
        }
    }

    /**
     * Topological sort digraph by breadth-first search of graph.
     *
     * @return the vertices in the topological order.
     */
    public int[] bfsort() {
        if (!digraph) {
            throw new UnsupportedOperationException("Topological sort is only meaningful for digraph.");
        }

        int n = getNumVertices();
        int[] in = new int[n];
        int[] ts = new int[n];
        for (int i = 0; i < n; i++) {
            ts[i] = -1;
            forEachEdge(i, (j, w) -> in[j]++);
        }

        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (in[i] == 0) {
                queue.offer(i);
            }
        }

        for (int i = 0; !queue.isEmpty(); i++) {
            int u = queue.poll();
            ts[i] = u;
            forEachEdge(u, (v, w) -> {
                if (--in[v] == 0) queue.offer(v);
            });
        }

        return ts;
    }

    /**
     * Breadth-first search connected components of graph.
     * @param v the start vertex.
     * @param cc the array to store the connected component id of each vertex.
     * @param id the current component id.
     */
    private void bfcc(int v, int[] cc, int id) {
        cc[v] = id;
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(v);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            forEachEdge(u, (i, w) -> {
                if (cc[i] == -1) {
                    queue.offer(i);
                    cc[i] = id;
                }
            });
        }
    }

    /**
     * Returns the connected components by breadth-first search.
     *
     * @return a two-dimensional array of which each row is the vertices
     *         in the same connected component.
     */
    public int[][] bfcc() {
        int n = getNumVertices();
        int[] cc = new int[n];
        Arrays.fill(cc, -1);

        int id = 0;
        for (int i = 0; i < n; i++) {
            if (cc[i] == -1) {
                bfcc(i, cc, id++);
            }
        }

        return connectedComponents(id, cc);
    }

    /**
     * Breadth-first search of graph.
     * @param visitor the visitor functor.
     * @param v the start vertex.
     * @param visited the flag if vertex has been visited.
     * @param queue a queue of vertices to visit.
     */
    private void bfs(Visitor visitor, int v, boolean[] visited, Queue<Integer> queue) {
        visitor.visit(v);
        visited[v] = true;
        queue.offer(v);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            forEachEdge(u, (i, w) -> {
                if (!visited[i]) {
                    visitor.visit(i);
                    queue.offer(i);
                    visited[i] = true;
                }
            });
        }
    }

    /**
     * BFS search on graph and performs some operation defined in visitor
     * on each vertex during traveling.
     * @param visitor the visitor functor.
     */
    public void bfs(Visitor visitor) {
        int n = getNumVertices();
        boolean[] visited = new boolean[n];

        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                bfs(visitor, i, visited, queue);
            }
        }
    }

    /**
     * Calculate the shortest path from a source to all other vertices in the
     * graph by Dijkstra algorithm.
     *
     * @param s the source vertex.
     * @return The distance to all vertices from the source.
     */
    public double[] dijkstra(int s) {
        return dijkstra(s, true);
    }
    
    /**
     * Calculate the shortest path from a source to all other vertices in the
     * graph by Dijkstra algorithm.
     * @param s The source vertex.
     * @param weighted Ignore edge weights if false.
     * @return The distance to all vertices from the source. If weighted is false,
     *         it is the length of the shortest path to other vertices.
     */
    public double[] dijkstra(int s, boolean weighted) {
        int n = getNumVertices();
        double[] wt = new double[n];
        Arrays.fill(wt, Double.POSITIVE_INFINITY);

        PriorityQueue queue = new PriorityQueue(wt);
        for (int v = 0; v < n; v++) {
            queue.insert(v);
        }

        wt[s] = 0.0;
        queue.lower(s);

        while (!queue.isEmpty()) {
            int v = queue.poll();
            if (!Double.isInfinite(wt[v])) {
                forEachEdge(v, (u, weight) -> {
                    double p = wt[v] + (weighted ? weight : 1);
                    if (p < wt[u]) {
                        wt[u] = p;
                        queue.lower(u);
                    }
                });
            }
        }
        
        return wt;
    }

    /**
     * Calculates the all pair shortest-path by Dijkstra algorithm.
     *
     * @return the length of shortest-path between vertices.
     */
    public double[][] dijkstra() {
        int n = getNumVertices();
        double[][] wt = new double[n][];
        for (int i = 0; i < n; i++) {
            wt[i] = dijkstra(i);
        }
        return wt;
    }

    /**
     * Returns the minimum spanning tree (MST) for a weighted undirected
     * graph by Prim's algorithm. MST is a subset of the edges that forms
     * a tree that includes every vertex, where the total weight of all
     * the edges in the tree is minimized. 
     * @return the minimum spanning tree.
     */
    public List<Edge> prim() {
        if (digraph) {
            throw new UnsupportedOperationException("Call Prim's algorithm on a digraph.");
        }

        int n = getNumVertices();
        if (n < 2) {
            throw new UnsupportedOperationException("Cannot construct MST with fewer than 2 vertices.");
        }

        // Tracks whether a node is included in the MST
        boolean[] inMST = new boolean[n];

        // Stores the minimum edge weight to add a node to the MST
        double[] minEdgeWeight = new double[n];
        Arrays.fill(minEdgeWeight, Double.MAX_VALUE);

        // Stores the parent of each node in the MST
        int[] parent = new int[n];
        Arrays.fill(parent, -1);

        // Total weight of the MST
        double totalWeight = 0.0;

        // Start the MST from node 0
        minEdgeWeight[0] = 0.0;

        // Iterate to include all nodes in the MST
        for (int i = 0; i < n; i++) {
            // Find the vertex with the smallest edge weight not yet included in the MST
            int u = -1;
            double minWeight = Double.MAX_VALUE;

            for (int v = 0; v < n; v++) {
                if (!inMST[v] && minEdgeWeight[v] < minWeight) {
                    minWeight = minEdgeWeight[v];
                    u = v;
                }
            }

            if (u == -1) {
                throw new RuntimeException("Failed to construct MST");
            }

            // Include this vertex in the MST
            inMST[u] = true;
            totalWeight += minWeight;

            // Update the edge weights for the remaining vertices
            final int p = u;
            forEachEdge(u, (v, weight) -> {
                if (!inMST[v]) {
                    if (weight < minEdgeWeight[v]) {
                        minEdgeWeight[v] = weight;
                        parent[v] = p; // Update the parent for this vertex
                    }
                }
            });
        }

        logger.info("MST weight = {}", totalWeight);
        List<Edge> mst = new ArrayList<>();
        for (int v = 1; v < n; v++) {
            if (parent[v] != -1) {
                int u = parent[v];
                mst.add(new Edge(v, u, minEdgeWeight[v]));
            }
        }
        return mst;
    }

    /**
     * Returns the TSP tour with Held-Karp algorithm.
     * @return the TSP tour.
     */
    public int[] heldKarp() {
        int n = getNumVertices();
        if (n < 2) {
            throw new UnsupportedOperationException("Cannot construct TSP with fewer than 2 vertices.");
        }

        if (n > 31) {
            throw new UnsupportedOperationException("Held-Karp cannot run with more than 31 vertices.");
        }

        // DP table: dp[mask][i] stores the shortest path to visit all nodes in mask
        // ending at node i
        int p = 1 << n;
        double[][] dp = new double[p][n];
        for (var row : dp) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }

        // Base case: cost of starting at node 0 and visiting only node 0 is 0
        dp[1][0] = 0.0;

        // Iterate through all possible subsets of nodes (represented by bitmasks)
        for (int mask = 1; mask < p; mask++) {
            for (int u = 0; u < n; u++) {
                if ((mask & (1 << u)) == 0) continue; // u is not in the subset represented by mask
                for (int v = 0; v < n; v++) {
                    if ((mask & (1 << v)) != 0) continue; // v is already in the subset
                    int nextMask = mask | (1 << v); // Add v to the subset
                    dp[nextMask][v] = Math.min(dp[nextMask][v], dp[mask][u] + getWeight(u, v));
                }
            }
        }

        // Reconstruct the optimal tour by backtracking
        int endMask = p - 1;
        int lastNode = 0;
        double bestCost = Double.POSITIVE_INFINITY;
        // Find the last node of the optimal tour (minimum cost returning to node 0)
        for (int u = 1; u < n; u++) {
            double cost = dp[endMask][u] + getWeight(u, 0);
            if (cost < bestCost) {
                bestCost = cost;
                lastNode = u;
            }
        }

        logger.info("Held-Karp TSP cost = {}", bestCost);

        // Backtracking to find the tour
        int mask = endMask;
        int index = 1; // The tour always starts with node 0.
        int[] tour = new int[n+1];
        while (mask != 0) {
            System.out.println(index + " " + lastNode + " " + mask);
            tour[index++] = lastNode;
            int prevMask = mask ^ (1 << lastNode);
            for (int u = 0; u < n; u++) {
                if (dp[mask][lastNode] == dp[prevMask][u] + getWeight(u, lastNode)) {
                    lastNode = u;
                    break;
                }
            }
            mask = prevMask;
        }

        MathEx.reverse(tour);
        return tour;
    }
}
