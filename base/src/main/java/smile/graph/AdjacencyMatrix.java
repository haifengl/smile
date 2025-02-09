/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.util.function.ArrayElementConsumer;
import smile.util.function.ArrayElementFunction;

/**
 * An adjacency matrix representation of a graph. Only simple graph is supported.
 *
 * @author Haifeng Li
 */
public class AdjacencyMatrix extends Graph implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

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
        super(digraph);
        graph = new double[n][n];
    }

    @Override
    public String toString() {
        return String.format("AdjacencyMatrix(%d nodes, digraph=%b)", graph.length, isDigraph());
    }

    @Override
    public int getVertexCount() {
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
        if (!isDigraph()) {
            graph[target][source] = weight;
        }
        return this;
    }

    @Override
    public List<Edge> getEdges(int vertex) {
        List<Edge> set = new ArrayList<>();
        int n = graph.length;
        double[] row = graph[vertex];
        for (int j = 0; j < n; j++) {
            if (row[j] != 0.0) {
                Edge edge = new Edge(vertex, j, row[j]);
                set.add(edge);
            }
        }
        return set;
    }

    /**
     * Returns the stream of vertex neighbors.
     *
     * @param weights the edge weights.
     * @return the stream of vertex neighbors.
     */
    private IntStream edges(double[] weights) {
        return IntStream.range(0, weights.length).filter(i -> weights[i] != 0);
    }

    @Override
    public void forEachEdge(int vertex, ArrayElementConsumer action) {
        double[] weights = graph[vertex];
        edges(weights).forEach(i -> action.apply(i, weights[i]));
    }

    @Override
    public DoubleStream mapEdges(int vertex, ArrayElementFunction mapper) {
        double[] weights = graph[vertex];
        return edges(weights).mapToDouble(i -> mapper.apply(i, weights[i]));
    }

    @Override
    public void updateEdges(int vertex, ArrayElementFunction mapper) {
        double[] weights = graph[vertex];
        edges(weights).forEach(i -> weights[i] = mapper.apply(i, weights[i]));
    }

    @Override
    public int getInDegree(int vertex) {
        int degree = 0;

        for (double[] edges : graph) {
            if (edges[vertex] != 0.0) {
                degree++;
            }
        }

        return degree;
    }

    @Override
    public int getOutDegree(int vertex) {
        int degree = 0;
        int n = graph.length;

        for (int j = 0; j < n; j++) {
            if (graph[vertex][j] != 0.0) {
                degree++;
            }
        }

        return degree;
    }

    @Override
    public AdjacencyMatrix subgraph(int[] vertices) {
        int[] v = vertices.clone();
        Arrays.sort(v);
        
        AdjacencyMatrix g = new AdjacencyMatrix(v.length, isDigraph());
        for (int i = 0; i < v.length; i++) {
            for (int j = 0; j < v.length; j++) {
                g.graph[i][j] = graph[v[i]][v[j]];
            }
        }
        
        return g;
    }

    @Override
    public Matrix toMatrix() {
        return Matrix.of(graph);
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
}
