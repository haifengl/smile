// Graph Data Structure

import java.util.*;
import smile.graph.*;

Graph graph = new AdjacencyList(8);
graph.addEdge(0, 2);
graph.addEdge(1, 7);
graph.addEdge(2, 6);
graph.addEdge(7, 4);
graph.addEdge(3, 4);
graph.addEdge(3, 5);
graph.addEdge(5, 4);

// Create a DOT graph with name 'SmileGraph' with 4 node labels.
// Since we have 8 nodes, the rest of nodes will use their integer
// ID as label.
String[] label = {"a", "b", "c", "d"};
graph.dot("SmileGraph", label);

// Graph Traversal
graph.dfs(i -> IO.println("Visiting vertex " + i));

//--- CELL ---
// Compute connected components with BFS
graph.bfcc();
// Compute connected components with DFS
graph.dfcc();

//--- CELL ---
// Topological Sort
graph = new AdjacencyList(13, true);
graph.addEdge(8, 7);
graph.addEdge(7, 6);
graph.addEdge(0, 1);
graph.addEdge(0, 2);
graph.addEdge(0, 3);
graph.addEdge(0, 5);
graph.addEdge(0, 6);
graph.addEdge(2, 3);
graph.addEdge(3, 4);
graph.addEdge(3, 5);
graph.addEdge(6, 4);
graph.addEdge(6, 9);
graph.addEdge(4, 9);
graph.addEdge(9, 10);
graph.addEdge(9, 11);
graph.addEdge(9, 12);
graph.addEdge(11, 12);

graph.dfsort(); // topological sort with DFS
graph.bfsort(); // topological sort with BFS

//--- CELL ---
// Minimum Spanning Tree
graph = new AdjacencyMatrix(6);
graph.addEdge(0, 1, 0.41);
graph.addEdge(1, 2, 0.51);
graph.addEdge(2, 3, 0.50);
graph.addEdge(4, 3, 0.36);
graph.addEdge(3, 5, 0.38);
graph.addEdge(3, 0, 0.45);
graph.addEdge(0, 5, 0.29);
graph.addEdge(5, 4, 0.21);
graph.addEdge(1, 4, 0.32);
graph.addEdge(4, 2, 0.32);
graph.addEdge(5, 1, 0.29);

List<Graph.Edge> mst = new ArrayList<>();
double cost = graph.prim(mst);
mst.forEach(edge -> IO.println(edge));

//--- CELL ---
// Shortest Path
graph = new AdjacencyMatrix(6, true);
graph.addEdge(0, 1, 0.41);
graph.addEdge(1, 2, 0.51);
graph.addEdge(2, 3, 0.50);
graph.addEdge(4, 3, 0.36);
graph.addEdge(3, 5, 0.38);
graph.addEdge(3, 0, 0.45);
graph.addEdge(0, 5, 0.29);
graph.addEdge(5, 4, 0.21);
graph.addEdge(1, 4, 0.32);
graph.addEdge(4, 2, 0.32);
graph.addEdge(5, 1, 0.29);

double[][] distances = graph.dijkstra();

//--- CELL ---
// Travelling Salesman Problem
graph = new AdjacencyMatrix(6);
graph.addEdge(0, 1, 0.41);
graph.addEdge(1, 2, 0.51);
graph.addEdge(2, 3, 0.50);
graph.addEdge(4, 3, 0.36);
graph.addEdge(3, 5, 0.38);
graph.addEdge(3, 0, 0.45);
graph.addEdge(0, 5, 0.29);
graph.addEdge(5, 4, 0.21);
graph.addEdge(1, 4, 0.32);
graph.addEdge(4, 2, 0.32);
graph.addEdge(5, 1, 0.29);

// TSP is an NP-hard problem. Smile provides the Held-Karp algorithm
// to compute the optimal solution of TSP. It is an efficient dynamic
// programming algorithm, significantly better than the superexponential
// performance of a brute-force algorithm.
int[] tour = graph.heldKarp();

// Although the Held-Karp algorithm is significantly better than
// a brute-force algorithm, it is still too slow to apply to large
// graphs. To process TSPs containing thousands of cities, Smile
// provides a branch-and-bound algorithm.
tour = graph.tsp(); // Branch-and-bound algorithm

// For even large TSP problems, Smile provides several heuristic and
// approximate algorithms, which quickly yield good solutions.
tour = graph.nearestInsertion(); // Heuristic algorithm

// We can improve the result further with the 2-Opt algorithm,
// which is a simple local search algorithm.
double cost = graph.opt2(tour, 3); // 2-Opt algorithm

// For tighter bound, Smile also implements the Christofides-Serdyukov
// algorithm to yield a solution that is at most 1.5 times longer than
// the optimal solution in the worst case.
tour = graph.christofides();

//--- CELL ---
// Maximum Flow Problem
var network = new AdjacencyMatrix(6, true);
network.addEdge(0, 1, 2);
network.addEdge(0, 2, 9);
network.addEdge(1, 2, 1);
network.addEdge(1, 3, 0);
network.addEdge(1, 4, 0);
network.addEdge(2, 4, 7);
network.addEdge(3, 5, 7);
network.addEdge(4, 5, 4);

double[][] flow = new double[6][6];
double maxFlow = network.pushRelabel(flow, 0, 5);
