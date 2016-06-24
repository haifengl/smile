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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import smile.sort.PriorityQueue;

/**
 * An adjacency list representation of a graph. Multigraph is supported.
 *
 * @author Haifeng Li
 */
public class AdjacencyList implements Graph {

    /**
     * The number of vertices.
     */
    private int n;
    /**
     * Is the graph directed?
     */
    private boolean digraph;
    /**
     * Adjacency list. Non-zero values are the weights of edges.
     */
    private LinkedList<Edge>[] graph;

    /**
     * Constructor.
     *
     * @param n the number of vertices.
     */
    public AdjacencyList(int n) {
        this(n, false);
    }

    /**
     * Constructor.
     *
     * @param n the number of vertices.
     * @param digraph true if this is a directed graph.
     */
    @SuppressWarnings("unchecked")
    public AdjacencyList(int n, boolean digraph) {
        this.n = n;
        this.digraph = digraph;

        LinkedList<Edge> list = new LinkedList<>();
        graph = (LinkedList<Edge>[]) java.lang.reflect.Array.newInstance(list.getClass(), n);

        graph[0] = list;
        for (int i = 1; i < n; i++) {
            graph[i] = new LinkedList<>();
        }
    }

    @Override
    public int getNumVertices() {
        return n;
    }

    @Override
    public boolean hasEdge(int source, int target) {
        if (digraph) {
            for (Edge edge : graph[source]) {
                if (edge.v2 == target) {
                    return true;
                }
            }
        } else {
            for (Edge edge : graph[source]) {
                if ((edge.v1 == source && edge.v2 == target) || (edge.v2 == source && edge.v1 == target)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public double getWeight(int source, int target) {
        if (digraph) {
            for (Edge edge : graph[source]) {
                if (edge.v2 == target) {
                    return edge.weight;
                }
            }
        } else {
            for (Edge edge : graph[source]) {
                if ((edge.v1 == source && edge.v2 == target) || (edge.v2 == source && edge.v1 == target)) {
                    return edge.weight;
                }
            }
        }

        return 0.0;
    }

    @Override
    public AdjacencyList setWeight(int source, int target, double weight) {
        if (digraph) {
            for (Edge edge : graph[source]) {
                if (edge.v2 == target) {
                    edge.weight = weight;
                    return this;
                }
            }
        } else {
            for (Edge edge : graph[source]) {
                if ((edge.v1 == source && edge.v2 == target) || (edge.v2 == source && edge.v1 == target)) {
                    edge.weight = weight;
                    return this;
                }
            }
        }

        addEdge(source, target, weight);
        return this;
    }

    @Override
    public Collection<Edge> getEdges() {
        Collection<Edge> set = new HashSet<>();

        for (int i = 0; i < n; i++) {
            set.addAll(graph[i]);
        }

        return set;
    }

    @Override
    public Collection<Edge> getEdges(int vertex) {
        return graph[vertex];
    }

    @Override
    public Collection<Edge> getEdges(int source, int target) {
        Collection<Edge> set = new LinkedList<>();

        if (digraph) {
            for (Edge edge : graph[source]) {
                if (edge.v2 == target) {
                    set.add(edge);
                }
            }
        } else {
            for (Edge edge : graph[source]) {
                if ((edge.v1 == source && edge.v2 == target) || (edge.v2 == source && edge.v1 == target)) {
                    set.add(edge);
                }
            }
        }

        return set;
    }

    @Override
    public Edge getEdge(int source, int target) {
        if (digraph) {
            for (Edge edge : graph[source]) {
                if (edge.v2 == target) {
                    return edge;
                }
            }
        } else {
            for (Edge edge : graph[source]) {
                if ((edge.v1 == source && edge.v2 == target) || (edge.v2 == source && edge.v1 == target)) {
                    return edge;
                }
            }
        }

        return null;
    }

    @Override
    public void addEdge(int source, int target) {
        addEdge(source, target, 1.0);
    }

    @Override
    public void addEdge(int source, int target, double weight) {
        Edge edge = new Edge();
        edge.v1 = source;
        edge.v2 = target;
        edge.weight = weight;
        graph[source].add(edge);
        if (!digraph && source != target) {
            graph[target].add(edge);
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
        Iterator<Edge> iter = graph[source].iterator();

        if (digraph) {
            while (iter.hasNext()) {
                Edge e = iter.next();
                if (e.v2 == target) {
                    iter.remove();
                }
            }
        } else {
            while (iter.hasNext()) {
                Edge e = iter.next();
                if ((e.v1 == source && e.v2 == target) || (e.v2 == source && e.v1 == target)) {
                    iter.remove();
                }
            }

            iter = graph[target].iterator();
            while (iter.hasNext()) {
                Edge e = iter.next();
                if ((e.v1 == source && e.v2 == target) || (e.v2 == source && e.v1 == target)) {
                    iter.remove();
                }
            }
        }
    }

    @Override
    public void removeEdge(Edge edge) {
        Iterator<Edge> iter = graph[edge.v1].iterator();

        while (iter.hasNext()) {
            if (iter.next() == edge) {
                iter.remove();
                break;
            }
        }

        if (!digraph) {
            iter = graph[edge.v2].iterator();

            while (iter.hasNext()) {
                if (iter.next() == edge) {
                    iter.remove();
                    break;
                }
            }
        }
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
            if (hasEdge(i, vertex)) {
                degree++;
            }
        }

        return degree;
    }

    @Override
    public int getOutdegree(int vertex) {
        return graph[vertex].size();
    }

    /**
     * Depth-first search of graph.
     * @param v the start vertex.
     * @param pre the array to store the mask if vertex has been visited.
     * @param ts the array to store the reverse topological order.
     * @param count the number of vertices have been visited before this search.
     * @return the number of vertices that have been visited after this search.
     */
    private int dfsearch(int v, int[] pre, int[] ts, int count) {
        pre[v] = 0;

        for (Edge edge : graph[v]) {
            int t = edge.v2;
            if (pre[t] == -1) {
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
        for (Edge edge : graph[v]) {
            int t = edge.v2;
            if (!digraph && t == v) {
                t = edge.v1;
            }

            if (cc[t] == -1) {
                dfs(t, cc, id);
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

    /**
     * Depth-first search of graph.
     * @param v the start vertex.
     * @param cc the array to store the connected component id of vertices.
     * @param id the current component id.
     */
    private void dfs(Visitor visitor, int v, int[] cc, int id) {
        visitor.visit(v);
        cc[v] = id;
        for (Edge edge : graph[v]) {
            int t = edge.v2;
            if (!digraph && t == v) {
                t = edge.v1;
            }

            if (cc[t] == -1) {
                dfs(visitor, t, cc, id);
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
            for (Edge edge : graph[i]) {
                in[edge.v2]++;
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
            for (Edge edge : graph[t]) {
                int v = edge.v2;
                if (--in[v] == 0) {
                    queue.offer(v);
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
            for (Edge edge : graph[t]) {
                int i = edge.v2;
                if (!digraph && i == t) {
                    i = edge.v1;
                }

                if (cc[i] == -1) {
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
            for (Edge edge : graph[t]) {
                int i = edge.v2;
                if (!digraph && i == t) {
                    i = edge.v1;
                }

                if (cc[i] == -1) {
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
                for (Edge edge : graph[v]) {
                    int w = edge.v2;
                    if (!digraph && w == v) {
                        w = edge.v1;
                    }
                    
                    double p = wt[v] + edge.weight;
                    if (p < wt[w]) {
                        wt[w] = p;
                        queue.lower(w);
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
    public AdjacencyList subgraph(int[] vertices) {
        int[] v = vertices.clone();
        Arrays.sort(v);
        
        AdjacencyList g = new AdjacencyList(v.length);
        for (int i = 0; i < v.length; i++) {
            Collection<Edge> edges = getEdges(v[i]);
            for (Edge edge : edges) {
                int j = edge.v2;
                if (j == i) {
                    j = edge.v1;
                }

                j = Arrays.binarySearch(v, j);
                if (j >= 0) {
                    g.addEdge(i, j, edge.weight);
                }
            }
        }
        
        return g;
    }
    
    /**
     * Returns the adjacency matrix.
     * @return the adjacency matrix
     */
    /*
    public SparseMatrix toSparseMatrix() {
        SparseDataset matrix = new SparseDataset(n);
        
        for (LinkedList<Edge> edges : graph) {
            for (Edge edge : edges) {
                matrix.set(edge.v1, edge.v2, edge.weight);
            }
        }
        
        return matrix.toSparseMatrix();
    }
    */
    
    /**
     * Converts the sparse matrix to a graph. If the matrix is structurally
     * symmetric, it is taken as the adjacency matrix of an undirected graph,
     * where two vertices i and j are connected if the (i, j)-th entry of
     * the matrix is nonzero. Rectangular or structurally asymmetric
     * matrices are treated as bipartite graphs.
     * 
     * @return a graph
     */
    /*
    public static AdjacencyList fromSparseMatrix(SparseMatrix matrix) {
        boolean symmetric = false;
        
        if (matrix.nrows() == matrix.ncols()) {
            symmetric = true;
            for (int i = 0; i < matrix.nrows(); i++) {
                for (int j = 0; j < i; j++) {
                    if (matrix.get(i, j) != matrix.get(j, i)) {
                        symmetric = false;
                        break;
                    }
                }

                if (!symmetric) {
                    break;
                }
            }
        }
        
        if (symmetric) {
            AdjacencyList graph = new AdjacencyList(matrix.nrows());
            
            for (int i = 0; i < matrix.nrows(); i++) {
                for (int j = 0; j < i; j++) {
                    double z = matrix.get(i, j);
                    if (z != 0.0) {
                        graph.addEdge(i, j, z);
                    }
                }
            }
            
            return graph;
        } else {
            AdjacencyList graph = new AdjacencyList(matrix.nrows() + matrix.ncols());
            
            for (int i = 0; i < matrix.nrows(); i++) {
                for (int j = 0; j < matrix.ncols(); j++) {
                    double z = matrix.get(i, j);
                    if (z != 0.0) {
                        graph.addEdge(i, matrix.nrows() + j, z);
                    }
                }
            }
            
            return graph;            
        }
    }
    */
}
