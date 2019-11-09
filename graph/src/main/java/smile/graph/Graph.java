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

package smile.graph;

import java.util.Collection;
import smile.math.matrix.Matrix;

/**
 * A graph is an abstract representation of a set of objects where some pairs
 * of the objects are connected by links. The interconnected objects are
 * represented by mathematical abstractions called vertices, and the links
 * that connect some pairs of vertices are called edges. The edges may be
 * directed (asymmetric) or undirected (symmetric). A graph is a weighted graph
 * if a number (weight) is assigned to each edge. Such weights might represent,
 * for example, costs, lengths or capacities, etc. depending on the problem.
 *
 * @author Haifeng Li
 */
public interface Graph {
    /**
     * Graph edge.
     */
    class Edge {
        /**
         * The id of one vertex connected by this edge. For directed graph,
         * this is the tail of arc.
         */
        public final int v1;
        /**
         * The id of the other vertex connected by this edge. For directed graph,
         * this is the head of arc.
         */
        public final int v2;
        /**
         * The weight of edge. For unweighted graph, this is always 1.
         */
        public double weight;

        /**
         * Constructor.
         * @param v1 the vertex id.
         * @param v2 the other vertex id.
         * @param weight the weight of edge.
         */
        public Edge(int v1, int v2, double weight) {
            this.v1 = v1;
            this.v2 = v2;
            this.weight = weight;
        }
    }

    /**
     * Returns the number vertices.
     */
    int getNumVertices();

    /**
     * Returns <tt>true</tt> if and only if this graph contains an edge going
     * from the source vertex to the target vertex. In undirected graphs the
     * same result is obtained when source and target are inverted.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return <tt>true</tt> if this graph contains the specified edge.
     */
    boolean hasEdge(int source, int target);

    /**
     * Returns the weight assigned to a given edge. Unweighted graphs always
     * return 1.0. For multi-graph, the return value is ill-defined.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return the edge weight
     */
    double getWeight(int source, int target);

    /**
     * Sets the weight assigned to a given edge. For multi-graph, the operation
     * is ill-defined.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @param weight the edge weight
     */
    Graph setWeight(int source, int target, double weight);

    /**
     * Returns a set of the edges contained in this graph.
     */
    Collection<Edge> getEdges();

    /**
     * Returns a set of all edges from the specified vertex. If no edges are
     * touching the specified vertex returns an empty set.
     *
     * @param vertex the id of vertex for which a set of touching edges is to be
     * returned.
     * @return a set of all edges touching the specified vertex.
     */
    Collection<Edge> getEdges(int vertex);

    /**
     * Returns a set of all edges connecting source vertex to target vertex if
     * such vertices exist in this graph. If both vertices
     * exist but no edges found, returns an empty set.
     * <p>
     * In undirected graphs, some of the returned edges may have their source
     * and target vertices in the opposite order.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return a set of all edges connecting source vertex to target vertex.
     */
    Collection<Edge> getEdges(int source, int target);

    /**
     * Returns an edge connecting source vertex to target vertex if such edge
     * exist in this graph. Otherwise returns <code> null</code>.
     * <p>
     * In undirected graphs, the returned edge may have its source and target
     * vertices in the opposite order.
     * <p>
     * For multi-graph, the return value is ill-defined.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return an edge connecting source vertex to target vertex if there are
     * connected. Otherwise null.
     */
    Edge getEdge(int source, int target);

    /**
     * Creates a new edge in this graph, going from the source vertex to the
     * target vertex, and returns the created edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    void addEdge(int source, int target);

    /**
     * Creates a new edge in this graph, going from the source vertex to the
     * target vertex, and returns the created edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    void addEdge(int source, int target, double weight);

    /**
     * Removes a set of edges from the graph.
     *
     * @param edges edges to be removed from this graph.
     */
    void removeEdges(Collection<Edge> edges);

    /**
     * In a simple graph, removes and returns the edge going from the specified source
     * vertex to the specified target vertex.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    void removeEdge(int source, int target);

    /**
     * Removes the specified edge from the graph.* Returns <tt>true</tt> if the
     * graph contained the specified edge.
     *
     * @param edge edge to be removed from this graph, if present.
     */
    void removeEdge(Edge edge);

    /**
     * Returns the degree of the specified vertex. A degree of a vertex in an
     * undirected graph is the number of edges touching that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    int getDegree(int vertex);

    /**
     * Returns the in-degree of the specified vertex. A in-degree of a vertex in an
     * directed graph is the number of edges head to that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    int getIndegree(int vertex);

    /**
     * Returns the out-degree of the specified vertex. A out-degree of a vertex in an
     * directed graph is the number of edges from that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    int getOutdegree(int vertex);
    
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
     * @param vistor the visitor functor.
     */
    void dfs(Visitor vistor);

    /**
     * Topological sort digraph by breadth-first search of graph.
     *
     * @return an array of vertex IDs in the topological order.
     */
    int[] sortbfs();

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
     * @param vistor the visitor functor.
     */
    void bfs(Visitor vistor);

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
     * @return the length of shortest path to other vertices.
     */
    double[] dijkstra(int s);

    /**
     * Calculates the all pair shortest path by Dijkstra algorithm.
     *
     * @return the length of shortest path between vertices.
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
     */
    Matrix toMatrix();
}


