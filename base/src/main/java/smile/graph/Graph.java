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
package smile.graph;

import java.util.*;
import java.util.stream.DoubleStream;
import smile.math.MathEx;
import smile.sort.Sort;
import smile.tensor.Matrix;
import smile.util.function.ArrayElementConsumer;
import smile.util.function.ArrayElementFunction;
import smile.util.PriorityQueue;
import smile.util.Strings;

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
    public record Edge(int u, int v, double weight) implements Comparable<Edge> {
        /**
         * Constructor of unweighted edge.
         * @param u the vertex id.
         * @param v the other vertex id.
         */
        public Edge(int u, int v) {
            this(u, v, 1.0);
        }

        @Override
        public int compareTo(Edge o) {
            return Double.compare(weight, o.weight);
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
     * Returns the graphic representation in Graphviz dot format.
     * Try <a href="http://viz-js.com/">http://viz-js.com/</a>
     * to visualize the returned string.
     * @return the graphic representation in Graphviz dot format.
     */
    public String dot() {
        return dot(null, null);
    }

    /**
     * Returns the graphic representation in Graphviz dot format.
     * Try <a href="http://viz-js.com/">http://viz-js.com/</a>
     * to visualize the returned string.
     * @param name the graph name.
     * @param label the label of nodes.
     * @return the graphic representation in Graphviz dot format.
     */
    public String dot(String name, String[] label) {
        StringBuilder builder = new StringBuilder();
        builder.append(digraph ? "digraph " : "graph ");
        if (name != null) builder.append(name);
        builder.append(" {\n");
        builder.append("  node [shape=box, style=\"rounded\", color=\"black\", fontname=helvetica];\n");
        builder.append("  edge [fontname=helvetica];\n");

        if (label != null) {
            for (int i = 0; i < label.length; i++) {
                builder.append(String.format("  %d [label=\"%s\"];\n", i, label[i]));
            }
        }

        int n = getVertexCount();
        String edge = digraph ? "->" : "--";
        for (int i = 0; i < n; i++) {
            int u = i;
            forEachEdge(i, (v, w) -> {
                if (digraph || v >= u) {
                    builder.append(String.format("  %d %s %d [label=\"%s\"];\n", u, edge, v, Strings.format(w)));
                }
            });
        }

        builder.append("}");
        return builder.toString();
    }

    /**
     * Returns the (dense or sparse) matrix representation of the graph.
     * @return the matrix representation of the graph.
     */
    public abstract Matrix toMatrix();

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
    public abstract int getVertexCount();

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
     * return 1.0. If the edge doesn't exist, it returns zero. For minimization
     * problems such as TSP, use getDistance as it will return infinity instead.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return the edge weight
     */
    public abstract double getWeight(int source, int target);

    /**
     * Returns the distance between two vertices.
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return the distance between two vertices.
     */
    public double getDistance(int source, int target) {
        double weight = getWeight(source, target);
        return weight == 0 ? Double.POSITIVE_INFINITY : weight;
    }

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
    public abstract List<Edge> getEdges(int vertex);

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
     * @return this graph.
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
     * @return this graph.
     */
    public Graph addEdge(int source, int target, double weight) {
        return setWeight(source, target, weight);
    }

    /**
     * Adds a set of edges to the graph.
     *
     * @param edges edges to be added to this graph.
     * @return this graph.
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
     * @return this graph.
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
     * @return this graph.
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
            throw new UnsupportedOperationException("Topological sort cannot be applied on undirected graph.");
        }

        int n = getVertexCount();
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
        if (digraph) {
            throw new UnsupportedOperationException("Connected components algorithm cannot be applied on digraph");
        }

        int n = getVertexCount();
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
    private void dfs(VertexVisitor visitor, int v, boolean[] visited) {
        visitor.accept(v);
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
    public void dfs(VertexVisitor visitor) {
        int n = getVertexCount();
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
            throw new UnsupportedOperationException("Topological sort cannot be applied on undirected graph.");
        }

        int n = getVertexCount();
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
        if (digraph) {
            throw new UnsupportedOperationException("Connected components algorithm cannot be applied on digraph");
        }

        int n = getVertexCount();
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
    private void bfs(VertexVisitor visitor, int v, boolean[] visited, Queue<Integer> queue) {
        visitor.accept(v);
        visited[v] = true;
        queue.offer(v);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            forEachEdge(u, (i, w) -> {
                if (!visited[i]) {
                    visitor.accept(i);
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
    public void bfs(VertexVisitor visitor) {
        int n = getVertexCount();
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
        int n = getVertexCount();
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
        int n = getVertexCount();
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
     * @param mst an output container to hold edges in the MST.
     * @return the cost of minimum spanning tree.
     */
    public double prim(List<Edge> mst) {
        if (digraph) {
            throw new UnsupportedOperationException("Prim's algorithm cannot be applied on a digraph.");
        }

        int n = getVertexCount();
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

        if (mst != null) {
            for (int v = 1; v < n; v++) {
                if (parent[v] != -1) {
                    int u = parent[v];
                    mst.add(new Edge(v, u, minEdgeWeight[v]));
                }
            }
        }
        return totalWeight;
    }

    /**
     * Returns the distance of path.
     * @param path the path.
     * @return the distance.
     */
    public double getPathDistance(int[] path) {
        double distance = 0.0;
        for (int i = 0; i < path.length-1; i++) {
            distance += getDistance(path[i], path[i+1]);
        }
        return distance;
    }

    /**
     * Replace edges path[i]->path[i+1] and path[j]->path[j+1]
     * with path[i]->path[j] and path[i+1]->path[j+1]
     */
    private void swapEdges(int[] path, int i, int j) {
        i += 1;
        while (i < j) {
            Sort.swap(path, i, j);
            i++;
            j--;
        }
    }

    /**
     * A search node in TSP branch and bound algorithm.
     */
    private record TspNode(int[] path, double lowerBound, double cost) implements Comparable<TspNode> {
        /**
         * Returns the level of search tree, i.e. the length of partial path.
         * @return the level of search tree.
         */
        public int level() {
            return path.length;
        }

        @Override
        public int compareTo(TspNode o) {
            return Double.compare(lowerBound, o.lowerBound);
        }
    }

    /**
     * Returns the MST cost of vertices not in the path.
     * @param inPath the flag if a node is in the partial path.
     * @return the MST cost.
     */
    private double mstLowerBound(boolean[] inPath) {
        int n = getVertexCount();

        // Tracks whether a node is included in the MST
        boolean[] inMST = new boolean[n];
        // Stores the minimum edge weight to add a node to the MST
        double[] minEdgeWeight = new double[n];
        Arrays.fill(minEdgeWeight, Double.MAX_VALUE);

        // Total weight of the MST
        double totalWeight = 0.0;

        // Find the start node
        for (int i = 0; i < n; i++) {
            if (!inPath[i]) {
                minEdgeWeight[i] = 0.0;
                break;
            }
        }

        // Iterate to include all nodes in the MST
        for (int i = 0; i < n; i++) {
            // Find the vertex with the smallest edge weight not yet included in the MST
            int u = -1;
            double minWeight = Double.MAX_VALUE;

            for (int v = 0; v < n; v++) {
                if (!inMST[v] && !inPath[v] && minEdgeWeight[v] < minWeight) {
                    minWeight = minEdgeWeight[v];
                    u = v;
                }
            }

            if (u == -1) break; // All reachable nodes are visited

            // Include this vertex in the MST
            inMST[u] = true;
            totalWeight += minWeight;

            // Update the edge weights for the remaining vertices
            final int p = u;
            forEachEdge(u, (v, weight) -> {
                if (!inMST[v] && !inPath[v]) {
                    minEdgeWeight[v] = Math.min(minEdgeWeight[v], weight);
                }
            });
        }

        // add the edge back to node 0
        forEachEdge(0, (v, weight) -> {
            if (inMST[v]) {
                minEdgeWeight[0] = Math.min(minEdgeWeight[0], weight);
            }
        });

        return totalWeight + minEdgeWeight[0];
    }

    /**
     * Returns the optimal travelling salesman problem (TSP) tour with
     * branch and bound algorithm.
     * @return the optimal TSP tour.
     */
    public int[] tsp() {
        int n = getVertexCount();
        if (n < 2) {
            throw new UnsupportedOperationException("Cannot construct TSP with fewer than 2 vertices.");
        }

        // Initialize the best cost with nearest insertion
        int[] tour = nearestInsertion();
        double bestCost = getPathDistance(tour);

        // Push the initial node into the priority queue
        java.util.PriorityQueue<TspNode> pq = new java.util.PriorityQueue<>();
        pq.offer(new TspNode(new int[1], 0.0, 0.0));

        // Perform Branch and Bound with Best-First Search
        while (!pq.isEmpty()) {
            var current = pq.poll();

            // Skip nodes with bounds worse than the current best solution
            if (current.lowerBound >= bestCost) continue;

            // If we reach the last level, check the complete path
            if (current.level() == n) {
                double cost = current.cost + getDistance(current.path[n-1], 0); // Return to start
                if (cost < bestCost) {
                    bestCost = cost;
                    System.arraycopy(current.path, 0, tour, 0, n);
                }
                continue;
            }

            boolean[] inPath = new boolean[n];
            for (var node : current.path) {
                inPath[node] = true;
            }
            // The cost of checking all remaining branches is cheaper compared
            // estimating MST lower bound if there are fewer than 5 nodes.
            double mst = (n - current.level() < 5) ? 0 : mstLowerBound(inPath);

            // Explore all possible next nodes
            final double currentBest = bestCost;
            forEachEdge(current.path[current.level() - 1], (v, weight) -> {
                if (inPath[v]) return;

                double nextCost = current.cost + weight;
                double nextLowerBound = nextCost + mst;

                // Prune branches with higher bounds
                if (nextLowerBound < currentBest) {
                    int[] nextPath = Arrays.copyOfRange(current.path, 0, current.level() + 1);
                    nextPath[current.level()] = v;
                    pq.offer(new TspNode(nextPath, nextLowerBound, nextCost));
                }
            });
        }

        logger.info("Branch and bound TSP cost = {}", bestCost);
        return tour;
    }

    /**
     * Returns the optimal TSP tour with Held-Karp algorithm.
     * It works on graph up to 31 vertices.
     * @return the optimal TSP tour.
     */
    public int[] heldKarp() {
        int n = getVertexCount();
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
                    dp[nextMask][v] = Math.min(dp[nextMask][v], dp[mask][u] + getDistance(u, v));
                }
            }
        }

        // Reconstruct the optimal tour by backtracking
        int endMask = p - 1;
        int lastNode = 0;
        double bestCost = Double.POSITIVE_INFINITY;
        // Find the last node of the optimal tour (minimum cost returning to node 0)
        for (int u = 1; u < n; u++) {
            double cost = dp[endMask][u] + getDistance(u, 0);
            if (cost < bestCost) {
                bestCost = cost;
                lastNode = u;
            }
        }

        // Backtracking to find the tour
        int mask = endMask;
        int index = 1; // The tour always starts with node 0.
        int[] tour = new int[n+1];
        while (mask != 0) {
            tour[index++] = lastNode;
            int prevMask = mask ^ (1 << lastNode);
            for (int u = 0; u < n; u++) {
                if (dp[mask][lastNode] == dp[prevMask][u] + getDistance(u, lastNode)) {
                    lastNode = u;
                    break;
                }
            }
            mask = prevMask;
        }

        logger.info("Held-Karp TSP cost = {}", bestCost);
        MathEx.reverse(tour);
        return tour;
    }

    /**
     * Returns the insertion position that causes minimum increase of TSP tour distance.
     * @param node the node to insert.
     * @param tour the tour path.
     * @param length the length of tour path.
     * @return the insertion position.
     */
    private int getInsertPosition(int node, int[] tour, int length) {
        int insertIndex = 1;
        double minIncrease = Double.MAX_VALUE;
        for (int i = 0; i < length; i++) {
            int node1 = tour[i];
            int node2 = tour[(i+1) % length];
            double increase = getDistance(node1, node) + getDistance(node, node2) - getDistance(node1, node2);
            if (increase < minIncrease) {
                minIncrease = increase;
                insertIndex = i + 1;
            }
        }
        return insertIndex;
    }

    /**
     * Returns the approximate solution to TSP with the
     * nearest insertion heuristic.
     * @return the approximate solution to TSP.
     */
    public int[] nearestInsertion() {
        int n = getVertexCount();
        if (n < 2) {
            throw new UnsupportedOperationException("Cannot construct TSP with fewer than 2 vertices.");
        }

        int[] tour = new int[n+1];
        double[] dist = new double[n];
        boolean[] visited = new boolean[n];
        visited[0] = true;
        Arrays.fill(dist, Double.POSITIVE_INFINITY);

        forEachEdge(0, (i, weight) -> dist[i] = weight);

        int nearestNode = MathEx.whichMin(dist);
        tour[1] = nearestNode;
        visited[nearestNode] = true;

        for (int length = 2; length < n; length++) {
            forEachEdge(nearestNode, (i, weight) -> {
                if (!visited[i]) {
                    dist[i] = Math.min(dist[i], weight);
                }
            });

            nearestNode = -1;
            double minDistance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (!visited[i]) {
                    if (dist[i] < minDistance) {
                        minDistance = dist[i];
                        nearestNode = i;
                    }
                }
            }

            // insert at the position that minimizes the increase in tour length
            int insertIndex = getInsertPosition(nearestNode, tour, length);
            System.arraycopy(tour, insertIndex, tour, insertIndex+1, length-insertIndex);
            tour[insertIndex] = nearestNode;
            visited[nearestNode] = true;
        }

        return tour;
    }

    /**
     * Returns the approximate solution to TSP with the
     * farthest insertion heuristic.
     * @return the approximate solution to TSP.
     */
    public int[] farthestInsertion() {
        int n = getVertexCount();
        if (n < 2) {
            throw new UnsupportedOperationException("Cannot construct TSP with fewer than 2 vertices.");
        }

        int[] tour = new int[n+1];
        double[] dist = new double[n];
        boolean[] visited = new boolean[n];
        visited[0] = true;

        forEachEdge(0, (i, weight) -> dist[i] = weight);

        int farthestNode = MathEx.whichMax(dist);
        tour[1] = farthestNode;
        visited[farthestNode] = true;

        for (int length = 2; length < n; length++) {
            forEachEdge(farthestNode, (i, weight) -> {
                if (!visited[i]) {
                    dist[i] = Math.max(dist[i], weight);
                }
            });

            farthestNode = -1;
            double maxDistance = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (!visited[i]) {
                    if (dist[i] > maxDistance) {
                        maxDistance = dist[i];
                        farthestNode = i;
                    }
                }
            }

            // insert at the position that minimizes the increase in tour length
            int insertIndex = getInsertPosition(farthestNode, tour, length);
            System.arraycopy(tour, insertIndex, tour, insertIndex+1, length-insertIndex);
            tour[insertIndex] = farthestNode;
            visited[farthestNode] = true;
        }

        return tour;
    }

    /**
     * Returns the approximate solution to TSP with the
     * arbitrary insertion heuristic.
     * @return the approximate solution to TSP.
     */
    public int[] arbitraryInsertion() {
        int n = getVertexCount();
        if (n < 2) {
            throw new UnsupportedOperationException("Cannot construct TSP with fewer than 2 vertices.");
        }

        int[] tour = new int[n+1];
        tour[1] = 1;

        for (int v = 2; v < n; v++) {
            // insert at the position that minimizes the increase in tour length
            int insertIndex = getInsertPosition(v, tour, v);
            System.arraycopy(tour, insertIndex, tour, insertIndex+1, v-insertIndex);
            tour[insertIndex] = v;
        }

        return tour;
    }

    /**
     * Improves an existing TSP tour with the 2-opt heuristic. The method
     * reconnects pairs of non-adjacent edges until no more pairs can be
     * swapped to further improve the solution.
     * @param tour an existing TSP tour. It may be revised with a better tour
     *             of lower cost.
     * @param maxIter the maximum number of iterations of the outer loop.
     * @return the improved tour cost.
     */
    public double opt2(int[] tour, int maxIter) {
        int n = getVertexCount();
        if (tour.length != n+1) {
            throw new IllegalArgumentException("Invalid tour length: " + tour.length);
        }

        double cost = getPathDistance(tour);
        boolean improved = true;
        for (int iter = 0; improved && iter < maxIter; iter++) {
            improved = false;
            for (int i = 0; i < n - 2; i++) {
                for (int j = i + 2; j < n; j++) {
                    double d1 = getWeight(tour[i], tour[j]);
                    double d2 = getWeight(tour[i+1], tour[(j+1)%n]);
                    if (d1 != 0 && d2 != 0) {
                        double delta = d1 + d2 - getWeight(tour[i], tour[i+1]) - getWeight(tour[j], tour[(j+1)%n]);

                        // If the length of the path is reduced, do a 2-opt swap
                        if (delta < 0) {
                            swapEdges(tour, i, j);
                            cost += delta;
                            improved = true;
                        }
                    }
                }
            }
        }
        return cost;
    }

    /**
     * Returns the approximate solution to TSP with Christofides algorithm.
     * @return the approximate solution to TSP.
     */
    public int[] christofides() {
        int n = getVertexCount();
        if (n < 2) {
            throw new UnsupportedOperationException("Cannot construct TSP with fewer than 2 vertices.");
        }

        // Step 1: Find a Minimum Spanning Tree (MST)
        List<Edge> mst = new ArrayList<>();
        prim(mst);

        // Step 2: Find vertices with odd degree in MST
        int[] degree = new int[n];
        for (var edge : mst) {
            degree[edge.u()]++;
            degree[edge.v()]++;
        }

        List<Integer> oddDegreeVertices = new ArrayList<>();
        for (int i = 0; i < n; ++i) {
            if (degree[i] % 2 != 0) {
                oddDegreeVertices.add(i);
            }
        }

        // Step 3: Perform Minimum Weight Perfect Matching on odd-degree vertices
        int m = oddDegreeVertices.size();
        List<Edge> matching = new ArrayList<>();
        boolean[] matched = new boolean[m];

        for (int i = 0; i < m; i++) {
            if (matched[i]) continue;

            int bestPartner = -1;
            double minWeight = Double.POSITIVE_INFINITY;

            for (int j = i + 1; j < m; j++) {
                if (matched[j]) continue;

                double weight = getDistance(oddDegreeVertices.get(i), oddDegreeVertices.get(j));
                if (weight < minWeight) {
                    minWeight = weight;
                    bestPartner = j;
                }
            }

            if (bestPartner != -1) {
                matching.add(new Edge(oddDegreeVertices.get(i), oddDegreeVertices.get(bestPartner)));
                matched[i] = true;
                matched[bestPartner] = true;
            }
        }

        // Step 4: Combine MST edges and matching edges to form a multigraph
        int[][] multigraph = new int[n][matching.size() + n - 1];
        int index = 0;
        for (var edge : mst) {
            multigraph[edge.u()][index] = edge.v();
            multigraph[edge.v()][index++] = edge.u();
        }
        for (var edge : matching) {
            multigraph[edge.u()][index] = edge.v();
            multigraph[edge.v()][index++] = edge.u();
        }
        assert index == matching.size() + n - 1;

        // Step 5: Find an Eulerian Circuit
        List<Integer> circuit = new ArrayList<>();
        Stack<Integer> stack = new Stack<>();
        boolean[][] visited = new boolean[n][n];

        stack.push(0);
        while (!stack.isEmpty()) {
            int u = stack.peek();

            boolean found = false;
            for (var v : multigraph[u]) {
                if (!visited[u][v]) {
                    stack.push(v);
                    visited[u][v] = true;
                    visited[v][u] = true;
                    found = true;
                    break;
                }
            }

            if (!found) {
                circuit.add(u);
                stack.pop();
            }
        }

        // Step 6: Shortcut the Eulerian Circuit to form a Hamiltonian Path
        boolean[] inPath = new boolean[n];
        int[] tour = new int[n+1];

        index = 0;
        for (var v : circuit) {
            if (!inPath[v]) {
                tour[index++] = v;
                inPath[v] = true;
            }
        }
        assert index == n;

        return tour;
    }
}
