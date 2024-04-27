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

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.util.PriorityQueue;

/**
 * An adjacency matrix representation of a graph. Only simple graph is supported.
 *
 * @author Haifeng Li
 */
public class AdjacencyMatrix implements Graph, Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Is the graph directed?
     */
    private final boolean digraph;
    /**
     * Adjacency matrix. Non-zero values are the weights of edges.
     */
    private final double[][] graph;

    /**
     * Constructor.
     * 
     * @param n the number of vertices.
     */
    public AdjacencyMatrix(int n) {
        this(n, false);
    }

    /**
     * Constructor.
     *
     * @param n the number of vertices.
     * @param digraph true if this is a directed graph.
     */
    public AdjacencyMatrix(int n, boolean digraph) {
        this.digraph = digraph;
        graph = new double[n][n];
    }

    @Override
    public int getNumVertices() {
        return graph.length;
    }

    @Override
    public boolean hasEdge(int source, int target) {
        return graph[source][target] != 0.0;
    }

    @Override
    public double getWeight(int source, int target) {
        return graph[source][target];
    }

    @Override
    public AdjacencyMatrix setWeight(int source, int target, double weight) {
        graph[source][target] = weight;
        if (!digraph) {
            graph[target][source] = weight;
        }
        return this;
    }

    @Override
    public Collection<Edge> getEdges() {
        Collection<Edge> set = new LinkedList<>();
        int n = graph.length;

        if (digraph) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (graph[i][j] != 0.0) {
                        Edge edge = new Edge(i, j, graph[i][j]);
                        set.add(edge);
                    }
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                for (int j = i; j < n; j++) {
                    if (graph[i][j] != 0.0) {
                        Edge edge = new Edge(i, j, graph[i][j]);
                        set.add(edge);
                    }
                }
            }
        }

        return set;
    }

    @Override
    public Collection<Edge> getEdges(int vertex) {
        Collection<Edge> set = new LinkedList<>();
        int n = graph.length;
        for (int j = 0; j < n; j++) {
            if (graph[vertex][j] != 0.0) {
                Edge edge = new Edge(vertex, j, graph[vertex][j]);
                set.add(edge);
            }
        }
        return set;
    }

    @Override
    public Collection<Edge> getEdges(int source, int target) {
        Collection<Edge> set = new LinkedList<>();
        Edge edge = getEdge(source, target);
        if (edge != null) {
            set.add(edge);
        }
        return set;
    }

    @Override
    public Edge getEdge(int source, int target) {
        if (graph[source][target] == 0.0) {
            return null;
        }

        return new Edge(source, target, graph[source][target]);
    }

    @Override
    public void addEdge(int source, int target) {
        if (digraph) {
            if (graph[source][target] == 0.0) {
                graph[source][target] = 1.0;
            }
        } else {
            if (graph[source][target] == 0.0) {
                graph[source][target] = 1.0;
                graph[target][source] = 1.0;
            }
        }
    }

    @Override
    public void addEdge(int source, int target, double weight) {
        if (digraph) {
            graph[source][target] = weight;
        } else {
            graph[source][target] = weight;
            graph[target][source] = weight;
        }
    }

    @Override
    public void removeEdges(Collection<Edge> edges) {
        for (Edge edge : edges) {
            removeEdge(edge);
        }
    }

    @Override
    public void removeEdge(int source, int target) {
        if (digraph) {
            graph[source][target] = 0.0;
        } else {
            graph[source][target] = 0.0;
            graph[target][source] = 0.0;
        }
    }

    @Override
    public void removeEdge(Edge edge) {
        removeEdge(edge.v1, edge.v2);
    }

    @Override
    public int getDegree(int vertex) {
        if (digraph) {
            return getIndegree(vertex) + getOutdegree(vertex);
        } else {
            return getOutdegree(vertex);
        }
    }

    @Override
    public int getIndegree(int vertex) {
        int degree = 0;

        for (double[] edges : graph) {
            if (edges[vertex] != 0.0) {
                degree++;
            }
        }

        return degree;
    }

    @Override
    public int getOutdegree(int vertex) {
        int degree = 0;
        int n = graph.length;

        for (int j = 0; j < n; j++) {
            if (graph[vertex][j] != 0.0) {
                degree++;
            }
        }

        return degree;
    }

    /**
     * Depth-first search of graph.
     * @param v the start vertex.
     * @param pre the array to store the order that vertices will be visited.
     * @param ts the array to store the reverse topological order.
     * @param count the number of vertices have been visited before this search.
     * @return the number of vertices that have been visited after this search.
     */
    private int dfsearch(int v, int[] pre, int[] ts, int count) {
        pre[v] = 0;
        int n = graph.length;
        for (int t = 0; t < n; t++) {
            if (graph[v][t] != 0.0 && pre[t] == -1) {
                count = dfsearch(t, pre, ts, count);
            }
        }

        ts[count++] = v;

        return count;
    }

    @Override
    public int[] sortdfs() {
        if (!digraph) {
            throw new UnsupportedOperationException("Topological sort is only meaningful for digraph.");
        }

        int count = 0;
        int n = graph.length;

        int[] pre = new int[n];
        int[] ts = new int[n];
        for (int i = 0; i < n; i++) {
            pre[i] = -1;
            ts[i] = -1;
        }

        for (int i = 0; i < n; i++) {
            if (pre[i] == -1) {
                count = dfsearch(i, pre, ts, count);
            }
        }

        return ts;
    }

    /**
     * Depth-first search connected components of graph.
     * @param v the start vertex.
     * @param cc the array to store the connected component id of vertices.
     * @param id the current component id.
     */
    private void dfs(int v, int[] cc, int id) {
        cc[v] = id;
        int n = graph.length;
        for (int t = 0; t < n; t++) {
            if (graph[v][t] != 0.0) {
                if (cc[t] == -1) {
                    dfs(t, cc, id);
                }
            }
        }
    }

    @Override
    public int[][] dfs() {
        int n = graph.length;
        int[] cc = new int[n];
        Arrays.fill(cc, -1);

        int id = 0;
        for (int i = 0; i < n; i++) {
            if (cc[i] == -1) {
                dfs(i, cc, id++);
            }
        }

        int[] size = new int[id];
        for (int i = 0; i < n; i++) {
            size[cc[i]]++;
        }
        
        int[][] components = new int[id][];
        for (int i = 0; i < id; i++) {
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
     * @param cc the array to store the connected component id of vertices.
     * @param id the current component id.
     */
    private void dfs(Visitor visitor, int v, int[] cc, int id) {
        visitor.visit(v);
        cc[v] = id;
        int n = graph.length;
        for (int t = 0; t < n; t++) {
            if (graph[v][t] != 0.0) {
                if (cc[t] == -1) {
                    dfs(visitor, t, cc, id);
                }
            }
        }
    }

    @Override
    public void dfs(Visitor visitor) {
        int n = graph.length;
        int[] cc = new int[n];
        Arrays.fill(cc, -1);

        int id = 0;
        for (int i = 0; i < n; i++) {
            if (cc[i] == -1) {
                dfs(visitor, i, cc, id++);
            }
        }
    }

    @Override
    public int[] sortbfs() {
        if (!digraph) {
            throw new UnsupportedOperationException("Topological sort is only meaningful for digraph.");
        }

        int n = graph.length;
        int[] in = new int[n];
        int[] ts = new int[n];
        for (int i = 0; i < n; i++) {
            ts[i] = -1;
            for (int v = 0; v < n; v++) {
                if (graph[i][v] != 0.0) {
                    in[v]++;
                }
            }
        }

        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (in[i] == 0) {
                queue.offer(i);
            }
        }

        for (int i = 0; !queue.isEmpty(); i++) {
            int t = queue.poll();
            ts[i] = t;
            for (int v = 0; v < n; v++) {
                if (graph[t][v] != 0.0) {
                    if (--in[v] == 0) {
                        queue.offer(v);
                    }
                }
            }
        }

        return ts;
    }

    /**
     * Breadth-first search connected components of graph.
     * @param v the start vertex.
     * @param cc the array to store the connected component id of vertices.
     * @param id the current component id.
     */
    private void bfs(int v, int[] cc, int id) {
        cc[v] = id;
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(v);
        int n = graph.length;
        while (!queue.isEmpty()) {
            int t = queue.poll();
            for (int i = 0; i < n; i++) {
                if (graph[t][i] != 0.0 && cc[i] == -1) {
                    queue.offer(i);
                    cc[i] = id;
                }
            }
        }
    }

    @Override
    public int[][] bfs() {
        int n = graph.length;
        int[] cc = new int[n];
        Arrays.fill(cc, -1);

        int id = 0;
        for (int i = 0; i < n; i++) {
            if (cc[i] == -1) {
                bfs(i, cc, id++);
            }
        }

        int[] size = new int[id];
        for (int i = 0; i < n; i++) {
            size[cc[i]]++;
        }

        
        int[][] components = new int[id][];
        for (int i = 0; i < id; i++) {
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
     * Breadth-first search of graph.
     * @param v the start vertex.
     * @param cc the array to store the connected component id of vertices.
     * @param id the current component id.
     */
    private void bfs(Visitor visitor, int v, int[] cc, int id) {
        visitor.visit(v);
        cc[v] = id;
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(v);
        int n = graph.length;
        while (!queue.isEmpty()) {
            int t = queue.poll();
            for (int i = 0; i < n; i++) {
                if (graph[t][i] != 0.0 && cc[i] == -1) {
                    visitor.visit(i);
                    queue.offer(i);
                    cc[i] = id;
                }
            }
        }
    }

    @Override
    public void bfs(Visitor visitor) {
        int n = graph.length;
        int[] cc = new int[n];
        Arrays.fill(cc, -1);

        int id = 0;
        for (int i = 0; i < n; i++) {
            if (cc[i] == -1) {
                bfs(visitor, i, cc, id++);
            }
        }
    }

    @Override
    public double[] dijkstra(int s) {
        return dijkstra(s, true);
    }
    
    /**
     * Calculates the shortest path by Dijkstra algorithm.
     * @param s The source vertex.
     * @param weighted True to calculate weighted path. Otherwise, the edge weights will be ignored.
     * @return The distance to all vertices from the source.
     */
    public double[] dijkstra(int s, boolean weighted) {
        int n = graph.length;
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
                for (int w = 0; w < n; w++) {
                    if (graph[v][w] != 0.0) {
                        double p = weighted ? wt[v] + graph[v][w] : wt[v] + 1;
                        if (p < wt[w]) {
                            wt[w] = p;
                            queue.lower(w);
                        }
                    }
                }
            }
        }
        
        return wt;
    }

    @Override
    public AdjacencyMatrix subgraph(int[] vertices) {
        int[] v = vertices.clone();
        Arrays.sort(v);
        
        AdjacencyMatrix g = new AdjacencyMatrix(v.length, digraph);
        for (int i = 0; i < v.length; i++) {
            for (int j = 0; j < v.length; j++) {
                g.graph[i][j] = graph[v[i]][v[j]];
            }
        }
        
        return g;
    }
    
    /**
     * Returns the adjacency matrix.
     * @return the adjacency matrix
     */
    public double[][] toArray() {
        return MathEx.clone(graph);
    }
    
    /**
     * Push-relabel algorithm for maximum flow
     */
    private void push(double[][] flow, double[] excess, int u, int v) {
        double send = Math.min(excess[u], graph[u][v] - flow[u][v]);
        flow[u][v] += send;
        flow[v][u] -= send;
        excess[u] -= send;
        excess[v] += send;
    }

    private void relabel(double[][] flow, int[] height, int u) {
        int n = graph.length;
        int minHeight = 2*n;
        for (int v = 0; v < n; v++) {
            if (graph[u][v] - flow[u][v] > 0) {
                minHeight = Math.min(minHeight, height[v]);
                height[u] = minHeight + 1;
            }
        }
    }

    private void discharge(double[][] flow, double[] excess, int[] height, int[] seen, int u) {
        int n = graph.length;
        while (excess[u] > 0) {
            if (seen[u] < n) {
                int v = seen[u];
                if ((graph[u][v] - flow[u][v] > 0) && (height[u] > height[v])) {
                    push(flow, excess, u, v);
                } else {
                    seen[u] += 1;
                }
            } else {
                relabel(flow, height, u);
                seen[u] = 0;
            }
        }
    }

    private static void moveToFront(int i, int[] array) {
        int temp = array[i];
        for (int j = i; j > 0; j--) {
            array[j] = array[j - 1];
        }
        array[0] = temp;
    }
    
    /**
     * Push-relabel algorithm for maximum flow.
     * @param flow the flow network.
     * @param source the source vertex.
     * @param sink the sink vertex.
     * @return the maximum flow between source and sink.
     */
    public double pushRelabel(double[][] flow, int source, int sink) {
        int n = graph.length;
        int[] seen = new int[n];
        int[] queue = new int[n-2];
        for (int i = 0, p = 0; i < n; i++) {
            if ((i != source) && (i != sink)) {
                queue[p++] = i;
            }
        }
        
        int[] height = new int[n];//dijkstra(sink, false);
        height[source] = n;
        
        double[] excess = new double[n];
        excess[source] = Double.MAX_VALUE;
        
        for (int i = 0; i < n; i++) {
            push(flow, excess, source, i);
        }

        int p = 0;
        while (p < n-2) {
            int u = queue[p];
            double oldHeight = height[u];
            discharge(flow, excess, height, seen, u);
            if (height[u] > oldHeight) {
                moveToFront(p, queue);
                p = 0;
            } else {
                p += 1;
            }
        }
        
        double maxflow = 0.0;
        for (int i = 0; i < n; i++) {
            maxflow += flow[source][i];
        }

        return maxflow;
    }

    @Override
    public Matrix toMatrix() {
        return Matrix.of(graph);
    }
}
