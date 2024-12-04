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
import java.util.stream.DoubleStream;
import smile.math.matrix.SparseMatrix;
import smile.sort.QuickSort;
import smile.util.ArrayElementConsumer;
import smile.util.ArrayElementFunction;
import smile.util.PriorityQueue;
import smile.util.SparseArray;

/**
 * An adjacency list representation of a graph. Multigraph is supported.
 *
 * @author Haifeng Li
 */
public class AdjacencyList implements Graph, Serializable {
    @Serial
    private static final long serialVersionUID = 3L;
    /**
     * Is the graph directed?
     */
    private final boolean digraph;
    /**
     * Adjacency list. Non-zero values are the weights of edges.
     */
    private final SparseArray[] graph;

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
    public AdjacencyList(int n, boolean digraph) {
        this.digraph = digraph;
        graph = new SparseArray[n];
        for (int i = 0; i < n; i++) {
            graph[i] = new SparseArray();
        }
    }

    @Override
    public int getNumVertices() {
        return graph.length;
    }

    @Override
    public boolean isDigraph() {
        return digraph;
    }

    @Override
    public boolean hasEdge(int source, int target) {
        return graph[source].get(target) != 0.0;
    }

    @Override
    public double getWeight(int source, int target) {
        return graph[source].get(target);
    }

    @Override
    public AdjacencyList setWeight(int source, int target, double weight) {
        graph[source].set(target, weight);
        if (!digraph) {
            graph[target].set(source, weight);
        }

        return this;
    }

    @Override
    public Collection<Edge> getEdges(int vertex) {
        return graph[vertex].stream().map(e -> new Edge(vertex, e.index(), e.value())).toList();
    }

    @Override
    public void forEachEdge(int vertex, ArrayElementConsumer action) {
        graph[vertex].forEach(action);
    }

    @Override
    public DoubleStream mapEdges(int vertex, ArrayElementFunction mapper) {
        return graph[vertex].map(mapper);
    }

    @Override
    public void updateEdges(int vertex, ArrayElementFunction mapper) {
        graph[vertex].update(mapper);
    }

    @Override
    public int getInDegree(int vertex) {
        int degree = 0;
        int n = graph.length;

        for (int i = 0; i < n; i++) {
            if (hasEdge(i, vertex)) {
                degree++;
            }
        }

        return degree;
    }

    @Override
    public int getOutDegree(int vertex) {
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

        for (var edge : graph[v]) {
            int t = edge.index();
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
        for (var edge : graph[v]) {
            int t = edge.index();
            if (cc[t] == -1) {
                dfs(t, cc, id);
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

    /**
     * Depth-first search of graph.
     * @param v the start vertex.
     * @param cc the array to store the connected component id of vertices.
     * @param id the current component id.
     */
    private void dfs(Visitor visitor, int v, int[] cc, int id) {
        visitor.visit(v);
        cc[v] = id;
        for (var edge : graph[v]) {
            int t = edge.index();
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

        int n = graph.length;
        int[] in = new int[n];
        int[] ts = new int[n];
        for (int i = 0; i < n; i++) {
            ts[i] = -1;
            for (var edge : graph[i]) {
                in[edge.index()]++;
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
            for (var edge : graph[t]) {
                int v = edge.index();
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
            for (var edge : graph[t]) {
                int i = edge.index();
                if (cc[i] == -1) {
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
        while (!queue.isEmpty()) {
            int t = queue.poll();
            for (var edge : graph[t]) {
                int i = edge.index();
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
    public AdjacencyList subgraph(int[] vertices) {
        int[] v = vertices.clone();
        Arrays.sort(v);
        
        AdjacencyList g = new AdjacencyList(v.length, digraph);
        for (int i = 0; i < v.length; i++) {
            Collection<Edge> edges = getEdges(v[i]);
            for (Edge edge : edges) {
                int j = edge.u() == v[i] ? edge.v() : edge.u();
                j = Arrays.binarySearch(v, j);
                if (j >= 0) {
                    g.addEdge(i, j, edge.weight());
                }
            }
        }
        
        return g;
    }
    
    @Override
    public SparseMatrix toMatrix() {
        int size = 0;
        int n = graph.length;
        int[] colSize = new int[n];
        int[] colIndex = new int[n + 1];
        for (int i = 0; i < n; i++) {
            var edges = graph[i];
            size += edges.size();
            colSize[i] = edges.size();
        }

        for (int i = 0; i < n; i++) {
            colIndex[i + 1] = colIndex[i] + colSize[i];
        }

        int[] rowIndex = new int[size];
        double[] x = new double[size];

        for (int i = 0; i < n; i++) {
            var edges = graph[i];
            int ni = edges.size();
            int[] index = new int[ni];
            double[] w = new double[ni];

            int j = 0;
            for (var edge : edges) {
                index[j] = edge.index();
                w[j++] = edge.value();
            }

            QuickSort.sort(index, w);

            int k = colIndex[i];
            for (j = 0; j < ni; j++, k++) {
                rowIndex[k] = index[j];
                x[k] = w[j];
            }
        }

        return new SparseMatrix(n, n, x, rowIndex, colIndex).transpose();
    }

    /**
     * Converts the sparse matrix to a graph. If the matrix is structurally
     * symmetric, it is taken as the adjacency matrix of an undirected graph,
     * where two vertices i and j are connected if the (i, j)-th entry of
     * the matrix is nonzero. Rectangular or structurally asymmetric
     * matrices are treated as bipartite graphs.
     *
     * @param matrix the matrix representation of the graph.
     * @return a graph
     */
    public static AdjacencyList of(SparseMatrix matrix) {
        boolean symmetric = false;
        
        if (matrix.nrow() == matrix.ncol()) {
            symmetric = true;
            for (int i = 0; i < matrix.nrow(); i++) {
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
            AdjacencyList graph = new AdjacencyList(matrix.nrow());
            
            for (int i = 0; i < matrix.nrow(); i++) {
                for (int j = 0; j < i; j++) {
                    double z = matrix.get(i, j);
                    if (z != 0.0) {
                        graph.addEdge(i, j, z);
                    }
                }
            }
            
            return graph;
        } else {
            AdjacencyList graph = new AdjacencyList(matrix.nrow() + matrix.ncol());
            
            for (int i = 0; i < matrix.nrow(); i++) {
                for (int j = 0; j < matrix.ncol(); j++) {
                    double z = matrix.get(i, j);
                    if (z != 0.0) {
                        graph.addEdge(i, matrix.nrow() + j, z);
                    }
                }
            }
            
            return graph;            
        }
    }
}
