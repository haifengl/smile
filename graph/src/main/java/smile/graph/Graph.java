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

package smile.graph;

import java.util.Collection;

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
public interface Graph
{
    /**
     * Graph edge.
     */
    public static class Edge {
        /**
         * The id of one vertex connected by this edge. For directed graph,
         * this is the tail of arc.
         */
        public int v1;
        /**
         * The id of the other vertex connected by this edge. For directed graph,
         * this is the head of arc.
         */
        public int v2;
        /**
         * The weight of edge. For unweighted graph, this is always 1.
         */
        public double weight;
    };

    /**
     * Returns the number vertices.
     */
    public int getNumVertices();

    /**
     * Returns <tt>true</tt> if and only if this graph contains an edge going
     * from the source vertex to the target vertex. In undirected graphs the
     * same result is obtained when source and target are inverted.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return <tt>true</tt> if this graph contains the specified edge.
     */
    public boolean hasEdge(int source, int target);

    /**
     * Returns the weight assigned to a given edge. Unweighted graphs always
     * return 1.0. For multi-graph, the return value is ill-defined.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @return the edge weight
     */
    public double getWeight(int source, int target);

    /**
     * Sets the weight assigned to a given edge. For multi-graph, the operation
     * is ill-defined.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     * @param weight the edge weight
     */
    public Graph setWeight(int source, int target, double weight);

    /**
     * Returns a set of the edges contained in this graph.
     */
    public Collection<Edge> getEdges();

    /**
     * Returns a set of all edges from the specified vertex. If no edges are
     * touching the specified vertex returns an empty set.
     *
     * @param vertex the id of vertex for which a set of touching edges is to be
     * returned.
     * @return a set of all edges touching the specified vertex.
     */
    public Collection<Edge> getEdges(int vertex);

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
    public Collection<Edge> getEdges(int source, int target);

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
    public Edge getEdge(int source, int target);

    /**
     * Creates a new edge in this graph, going from the source vertex to the
     * target vertex, and returns the created edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    public void addEdge(int source, int target);

    /**
     * Creates a new edge in this graph, going from the source vertex to the
     * target vertex, and returns the created edge.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    public void addEdge(int source, int target, double weight);

    /**
     * Removes a set of edges from the graph.
     *
     * @param edges edges to be removed from this graph.
     */
    public void removeEdges(Collection<Edge> edges);

    /**
     * In a simple graph, removes and returns the edge going from the specified source
     * vertex to the specified target vertex.
     *
     * @param source the id of source vertex of the edge.
     * @param target the id of target vertex of the edge.
     */
    public void removeEdge(int source, int target);

    /**
     * Removes the specified edge from the graph.* Returns <tt>true</tt> if the
     * graph contained the specified edge.
     *
     * @param edge edge to be removed from this graph, if present.
     */
    public void removeEdge(Edge edge);

    /**
     * Returns the degree of the specified vertex. A degree of a vertex in an
     * undirected graph is the number of edges touching that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    public int getDegree(int vertex);

    /**
     * Returns the in-degree of the specified vertex. A in-degree of a vertex in an
     * directed graph is the number of edges head to that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    public int getIndegree(int vertex);

    /**
     * Returns the out-degree of the specified vertex. A out-degree of a vertex in an
     * directed graph is the number of edges from that vertex.
     *
     * @param vertex the id of vertex.
     * @return the degree of the specified vertex.
     */
    public int getOutdegree(int vertex);
    
    /**
     * Reverse topological sort digraph by depth-first search of graph.
     *
     * @return an array of vertex IDs in the reverse topological order.
     */
    public int[] sortdfs();

    /**
     * Depth-first search connected components of graph.
     *
     * @return a two-dimensional array of which each row is the vertices in the
     * same connected component.
     */
    public int[][] dfs();

    /**
     * DFS search on graph and performs some operation defined in visitor
     * on each vertex during traveling.
     * @param vistor the visitor functor.
     */
    public void dfs(Visitor vistor);

    /**
     * Topological sort digraph by breadth-first search of graph.
     *
     * @return an array of vertex IDs in the topological order.
     */
    public int[] sortbfs();

    /**
     * Breadth-first search connected components of graph.
     *
     * @return a two-dimensional array of which each row is the vertices in the
     * same connected component.
     */
    public int[][] bfs();

    /**
     * BFS search on graph and performs some operation defined in visitor
     * on each vertex during traveling.
     * @param vistor the visitor functor.
     */
    public void bfs(Visitor vistor);

    /**
     * Returns a subgraph containing all given vertices.
     * @param vertices the vertices to be included in subgraph.
     * @return a subgraph containing all given vertices
     */
    public Graph subgraph(int[] vertices);
    
    /**
     * Calculate the shortest path from a source to all other vertices in the
     * graph by Dijkstra algorithm.
     *
     * @param s the source vertex.
     * @return the length of shortest path to other vertices.
     */
    public double[] dijkstra(int s);

    /**
     * Calculates the all pair shortest path by Dijkstra algorithm.
     *
     * @return the length of shortest path between vertices.
     */
    public double[][] dijkstra();
}


