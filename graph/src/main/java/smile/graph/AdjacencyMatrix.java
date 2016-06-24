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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import smile.sort.PriorityQueue;
import smile.math.Math;

/**
 * An adjacency matrix representation of a graph. Only simple graph is supported.
 *
 * @author Haifeng Li
 */
public class AdjacencyMatrix implements Graph {

    /**
     * The number of vertices.
     */
    private int n;
    /**
     * Is the graph directed?
     */
    private boolean digraph;
    /**
     * Adjacency matrix. Non-zero values are the weights of edges.
     */
    private double[][] graph;

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
        this.n = n;
        this.digraph = digraph;
        graph = new double[n][n];
    }

    @Override
    public int getNumVertices() {
        return n;
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

        if (digraph) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (graph[i][j] != 0.0) {
                        Edge edge = new Edge();
                        edge.v1 = i;
                        edge.v2 = j;
                        edge.weight = graph[i][j];
                        set.add(edge);
                    }
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                for (int j = i; j < n; j++) {
                    if (graph[i][j] != 0.0) {
                        Edge edge = new Edge();
                        edge.v1 = i;
                        edge.v2 = j;
                        edge.weight = graph[i][j];
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
        for (int j = 0; j < n; j++) {
            if (graph[vertex][j] != 0.0) {
                Edge edge = new Edge();
                edge.v1 = vertex;
                edge.v2 = j;
                edge.weight = graph[vertex][j];
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

        Edge edge = new Edge();
        edge.v1 = source;
        edge.v2 = target;
        edge.weight = graph[source][target];
        return edge;
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
        Iterator<Edge> iter = edges.iterator();
        while (iter.hasNext()) {
            removeEdge(iter.next());
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

        for (int i = 0; i < n; i++) {
            if (graph[i][vertex] != 0.0) {
                degree++;
            }
        }

        return degree;
    }

    @Override
    public int getOutdegree(int vertex) {
        int degree = 0;

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
        double[] wt = new double[n];
        Arrays.fill(wt, Double.POSITIVE_INFINITY);

        PriorityQueue queue = new PriorityQueue(wt);
        for (int v = 0; v < n; v++) {
            queue.insert(v);
        }

        wt[s] = 0.0;
        queue.lower(s);

        while (!queue.empty()) {
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
    public double[][] dijkstra() {
        double[][] wt = new double[n][];
        for (int i = 0; i < n; i++) {
            wt[i] = dijkstra(i);
        }
        return wt;
    }

    @Override
    public AdjacencyMatrix subgraph(int[] vertices) {
        int[] v = vertices.clone();
        Arrays.sort(v);
        
        AdjacencyMatrix g = new AdjacencyMatrix(v.length);
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
        return Math.clone(graph);
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
        int minHeight = 2*n;
        for (int v = 0; v < n; v++) {
            if (graph[u][v] - flow[u][v] > 0) {
                minHeight = Math.min(minHeight, height[v]);
                height[u] = minHeight + 1;
            }
        }
    }

    private void discharge(double[][] flow, double[] excess, int[] height, int[] seen, int u) {
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
     * Push-relabel algorithm for maximum flow
     */
    public double pushRelabel(double[][] flow, int source, int sink) {
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
}
