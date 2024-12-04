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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.DoubleStream;
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
public interface Graph {
    /**
     * Graph edge.
     * @param u the vertex id. For directed graph,
     *          this is the tail of arc.
     * @param v the other vertex id. For directed graph,
     *          this is the head of arc.
     * @param weight the weight of edge. For unweighted graph,
     *               this is always 1.
     */
    record Edge(int u, int v, double weight) {
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
     * Returns the number of vertices.
     * @return the number of vertices.
     */
    int getNumVertices();

    /**
     * Return true if the graph is directed.
     * @return true if the graph is directed.
     */
    boolean isDigraph();

    /**
     * Returns true if and only if this graph contains an edge going
     * from the source vertex to the target vertex. In undirected graphs the
     * same result is obtained when source and target are inverted.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return true if this graph contains the specified edge.
     */
    boolean hasEdge(int source, int target);

    /**
     * Returns the weight assigned to a given edge. Unweighted graphs always
     * return 1.0.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return the edge weight
     */
    double getWeight(int source, int target);

    /**
     * Sets the weight assigned to a given edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @param weight the edge weight
     * @return this graph.
     */
    Graph setWeight(int source, int target, double weight);

    /**
     * Returns the edges from the specified vertex. If no edges are
     * touching the specified vertex returns an empty set.
     *
     * @param vertex the id of vertex for which a set of touching edges is to be
     * returned.
     * @return the edges touching the specified vertex.
     */
    Collection<Edge> getEdges(int vertex);

    /**
     * Performs an action for each edge of a vertex.
     * @param vertex the vertex id.
     * @param action a non-interfering action to perform on the edges.
     */
    void forEachEdge(int vertex, ArrayElementConsumer action);

    /**
     * Returns a stream consisting of the results of applying the given
     * function to the edge weights of a vertex.
     * @param vertex the vertex id.
     * @param mapper a non-interfering, stateless function to map each
     *               edge weight of a vertex.
     * @return the stream of the new values of edge weights.
     */
    DoubleStream mapEdges(int vertex, ArrayElementFunction mapper);

    /**
     * Updates the edge weights of a vertex.
     * @param vertex the vertex id.
     * @param mapper a function to map each edge weight to new value.
     */
    void updateEdges(int vertex, ArrayElementFunction mapper);

    /**
     * Creates a new edge in this graph, going from the source vertex to the
     * target vertex, and returns the created edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    default Graph addEdge(int source, int target) {
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
    default Graph addEdge(int source, int target, double weight) {
        return setWeight(source, target, weight);
    }

    /**
     * Adds a set of edges to the graph.
     *
     * @param edges edges to be added to this graph.
     */
    default Graph addEdges(Collection<Edge> edges) {
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
    default Graph removeEdges(Collection<Edge> edges) {
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
    default Graph removeEdge(int source, int target) {
        return setWeight(source, target, 0.0);
    }

    /**
     * Returns the degree of the specified vertex. A degree of a vertex in an
     * undirected graph is the number of edges touching that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    default int getDegree(int vertex) {
        return isDigraph() ? getInDegree(vertex) + getOutDegree(vertex) : getOutDegree(vertex);
    }

    /**
     * Returns the in-degree of the specified vertex. An in-degree of a vertex in a
     * directed graph is the number of edges head to that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    int getInDegree(int vertex);

    /**
     * Returns the out-degree of the specified vertex. An out-degree of a vertex in a
     * directed graph is the number of edges from that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    int getOutDegree(int vertex);
    
    /**
     * Reverse topological sort digraph by depth-first search of graph.
     *
     * @return an array of vertex IDs in the reverse topological order.
     */
    int[] sortdfs();

    /**
     * Depth-first search connected components of graph.
     *
     * @return a two-dimensional array of which each row is the vertices in the
     * same connected component.
     */
    int[][] dfs();

    /**
     * DFS search on graph and performs some operation defined in visitor
     * on each vertex during traveling.
     * @param visitor the visitor functor.
     */
    void dfs(Visitor visitor);

    /**
     * Topological sort digraph by breadth-first search of graph.
     *
     * @return an array of vertex IDs in the topological order.
     */
    default int[] bfsort() {
        if (!isDigraph()) {
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
     *
     * @return a two-dimensional array of which each row is the vertices in the
     * same connected component.
     */
    int[][] bfs();

    /**
     * BFS search on graph and performs some operation defined in visitor
     * on each vertex during traveling.
     * @param visitor the visitor functor.
     */
    void bfs(Visitor visitor);

    /**
     * Returns a subgraph containing all given vertices.
     * @param vertices the vertices to be included in subgraph.
     * @return a subgraph containing all given vertices
     */
    Graph subgraph(int[] vertices);
    
    /**
     * Calculate the shortest path from a source to all other vertices in the
     * graph by Dijkstra algorithm.
     *
     * @param s the source vertex.
     * @return The distance to all vertices from the source.
     */
    default double[] dijkstra(int s) {
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
    default double[] dijkstra(int s, boolean weighted) {
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
    default double[][] dijkstra() {
        int n = getNumVertices();
        double[][] wt = new double[n][];
        for (int i = 0; i < n; i++) {
            wt[i] = dijkstra(i);
        }
        return wt;
    }

    /**
     * Returns the (dense or sparse) matrix representation of the graph.
     * @return the matrix representation of the graph.
     */
    IMatrix toMatrix();
}


