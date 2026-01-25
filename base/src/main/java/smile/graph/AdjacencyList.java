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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.graph;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;
import smile.sort.QuickSort;
import smile.tensor.SparseMatrix;
import smile.util.function.ArrayElementConsumer;
import smile.util.function.ArrayElementFunction;
import smile.util.SparseArray;

/**
 * An adjacency list representation of a graph.
 *
 * @author Haifeng Li
 */
public class AdjacencyList extends Graph implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;
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
        super(digraph);
        graph = new SparseArray[n];
        for (int i = 0; i < n; i++) {
            graph[i] = new SparseArray();
        }
    }

    @Override
    public String toString() {
        return String.format("AdjacencyList(%d nodes, digraph=%b)", graph.length, isDigraph());
    }

    @Override
    public int getVertexCount() {
        return graph.length;
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
        if (!isDigraph()) {
            graph[target].set(source, weight);
        }

        return this;
    }

    @Override
    public List<Edge> getEdges(int vertex) {
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

    @Override
    public AdjacencyList subgraph(int[] vertices) {
        int[] v = vertices.clone();
        Arrays.sort(v);
        
        AdjacencyList g = new AdjacencyList(v.length, isDigraph());
        for (int i = 0; i < v.length; i++) {
            List<Edge> edges = getEdges(v[i]);
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
            var edges = getEdges(i);
            int ni = edges.size();
            int[] index = new int[ni];
            double[] w = new double[ni];

            int j = 0;
            for (var edge : edges) {
                index[j] = edge.v();
                w[j++] = edge.weight();
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
            LOOP:
            for (int i = 0; i < matrix.nrow(); i++) {
                for (int j = 0; j < i; j++) {
                    if (matrix.get(i, j) != matrix.get(j, i)) {
                        symmetric = false;
                        break LOOP;
                    }
                }
            }
        }

        AdjacencyList graph = new AdjacencyList(symmetric ? matrix.nrow() : matrix.nrow() + matrix.ncol());
        if (symmetric) {
            for (int i = 0; i < matrix.nrow(); i++) {
                for (int j = 0; j < i; j++) {
                    double z = matrix.get(i, j);
                    if (z != 0.0) {
                        graph.addEdge(i, j, z);
                    }
                }
            }
        } else {
            for (int i = 0; i < matrix.nrow(); i++) {
                for (int j = 0; j < matrix.ncol(); j++) {
                    double z = matrix.get(i, j);
                    if (z != 0.0) {
                        graph.addEdge(i, matrix.nrow() + j, z);
                    }
                }
            }

        }
        return graph;
    }
}
