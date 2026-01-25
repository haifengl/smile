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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smile.math.MathEx;
import smile.tensor.Matrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("unused")
public class AdjacencyMatrixTest {
    Graph g1, g2, g3, g4;
    Graph g5, g6, g7, g8;

    public AdjacencyMatrixTest() {
        g1 = new AdjacencyMatrix(5, true);
        g2 = new AdjacencyMatrix(5, true);
        g3 = new AdjacencyMatrix(5, true);
        g4 = new AdjacencyMatrix(5, true);

        g2.addEdge(1, 2);
        g2.addEdge(2, 1);

        g3.addEdge(1, 2);
        g3.addEdge(2, 1);
        g3.addEdge(2, 3);
        g3.addEdge(3, 2);
        g3.addEdge(3, 1);
        g3.addEdge(1, 3);

        g4.addEdge(1, 2);
        g4.addEdge(2, 3);
        g4.addEdge(3, 4);
        g4.addEdge(4, 1);

        g5 = new AdjacencyMatrix(5, false);
        g6 = new AdjacencyMatrix(5, false);
        g7 = new AdjacencyMatrix(5, false);
        g8 = new AdjacencyMatrix(5, false);

        g6.addEdge(1, 2);

        g7.addEdge(1, 2);
        g7.addEdge(2, 3);
        g7.addEdge(3, 1);

        g8.addEdge(1, 2);
        g8.addEdge(2, 3);
        g8.addEdge(3, 4);
        g8.addEdge(4, 1);
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of hasEdge method, of class AdjacencyMatrix.
     */
    @Test
    public void testHasEdge() {
        System.out.println("hasEdge");
        assertFalse(g1.hasEdge(1, 2));
        assertFalse(g1.hasEdge(1, 1));

        assertTrue(g2.hasEdge(1, 2));
        assertTrue(g2.hasEdge(2, 1));

        assertTrue(g3.hasEdge(1, 2));
        assertTrue(g3.hasEdge(2, 1));
        assertTrue(g3.hasEdge(3, 2));
        assertTrue(g3.hasEdge(2, 3));
        assertTrue(g3.hasEdge(1, 3));
        assertTrue(g3.hasEdge(3, 1));
        assertFalse(g3.hasEdge(4, 2));

        assertFalse(g4.hasEdge(1, 4));
        g4.addEdge(1, 4);
        assertTrue(g4.hasEdge(1, 4));


        assertFalse(g5.hasEdge(1, 2));
        assertFalse(g5.hasEdge(1, 1));

        assertTrue(g6.hasEdge(1, 2));
        assertTrue(g6.hasEdge(2, 1));

        assertTrue(g7.hasEdge(1, 2));
        assertTrue(g7.hasEdge(2, 1));
        assertTrue(g7.hasEdge(3, 2));
        assertTrue(g7.hasEdge(2, 3));
        assertTrue(g7.hasEdge(1, 3));
        assertTrue(g7.hasEdge(3, 1));
        assertFalse(g7.hasEdge(4, 2));

        assertTrue(g8.hasEdge(1, 4));
    }

    /**
     * Test of getWeight method, of class AdjacencyMatrix.
     */
    @Test
    public void testGetWeight() {
        System.out.println("getWeight");
        assertEquals(0.0, g1.getWeight(1, 2), 1E-10);
        assertEquals(0.0, g1.getWeight(1, 1), 1E-10);

        assertEquals(1.0, g2.getWeight(1, 2), 1E-10);
        assertEquals(1.0, g2.getWeight(2, 1), 1E-10);

        assertEquals(1.0, g3.getWeight(1, 2), 1E-10);
        assertEquals(1.0, g3.getWeight(2, 1), 1E-10);
        assertEquals(1.0, g3.getWeight(3, 2), 1E-10);
        assertEquals(1.0, g3.getWeight(2, 3), 1E-10);
        assertEquals(1.0, g3.getWeight(1, 3), 1E-10);
        assertEquals(1.0, g3.getWeight(3, 1), 1E-10);
        assertEquals(0.0, g3.getWeight(4, 2), 1E-10);

        assertEquals(0.0, g4.getWeight(1, 4), 1E-10);
        g4.addEdge(1, 4);
        assertEquals(1.0, g4.getWeight(1, 4), 1E-10);


        assertEquals(0.0, g5.getWeight(1, 2), 1E-10);
        assertEquals(0.0, g5.getWeight(1, 1), 1E-10);

        assertEquals(1.0, g6.getWeight(1, 2), 1E-10);
        assertEquals(1.0, g6.getWeight(2, 1), 1E-10);

        assertEquals(1.0, g7.getWeight(1, 2), 1E-10);
        assertEquals(1.0, g7.getWeight(2, 1), 1E-10);
        assertEquals(1.0, g7.getWeight(3, 2), 1E-10);
        assertEquals(1.0, g7.getWeight(2, 3), 1E-10);
        assertEquals(1.0, g7.getWeight(1, 3), 1E-10);
        assertEquals(1.0, g7.getWeight(3, 1), 1E-10);
        assertEquals(0.0, g7.getWeight(4, 2), 1E-10);

        assertEquals(1.0, g8.getWeight(1, 4), 1E-10);
    }

    /**
     * Test of setWeight method, of class AdjacencyMatrix.
     */
    @Test
    public void testSetWeight() {
        System.out.println("setWeight");
        g4.setWeight(1, 4, 5.7);
        assertEquals(5.7, g4.getWeight(1, 4), 1E-10);
        assertEquals(1.0, g4.getWeight(4, 1), 1E-10);

        g8.setWeight(1, 4, 5.7);
        assertEquals(5.7, g8.getWeight(1, 4), 1E-10);
        assertEquals(5.7, g8.getWeight(4, 1), 1E-10);
    }

    /**
     * Test of removeEdge method, of class AdjacencyMatrix.
     */
    @Test
    public void testRemoveEdge() {
        System.out.println("removeEdge");
        g4.addEdge(1, 4);
        g4.removeEdge(4, 1);
        assertEquals(1.0, g8.getWeight(1, 4), 1E-10);
        assertEquals(0.0, g4.getWeight(4, 1), 1E-10);

        g8.removeEdge(1, 4);
        assertEquals(0, g8.getWeight(1, 4), 1E-10);
        assertEquals(0, g8.getWeight(4, 1), 1E-10);
    }

    /**
     * Test of getDegree method, of class AdjacencyMatrix.
     */
    @Test
    public void testGetDegree() {
        System.out.println("getDegree");
        assertEquals(0, g1.getDegree(1));

        assertEquals(2, g2.getDegree(1));
        g2.addEdge(1, 1);
        assertEquals(4, g2.getDegree(1));

        assertEquals(4, g3.getDegree(1));
        assertEquals(4, g3.getDegree(2));
        assertEquals(4, g3.getDegree(3));

        assertEquals(2, g4.getDegree(4));

        assertEquals(0, g5.getDegree(1));

        assertEquals(1, g6.getDegree(1));
        g6.addEdge(1, 1);
        assertEquals(2, g6.getDegree(1));

        assertEquals(2, g7.getDegree(1));
        assertEquals(2, g7.getDegree(2));
        assertEquals(2, g7.getDegree(3));

        assertEquals(2, g8.getDegree(4));
    }

    /**
     * Test of getIndegree method, of class AdjacencyMatrix.
     */
    @Test
    public void testGetIndegree() {
        System.out.println("getInDegree");
        assertEquals(0, g1.getInDegree(1));

        assertEquals(1, g2.getInDegree(1));
        g2.addEdge(1, 1);
        assertEquals(2, g2.getInDegree(1));

        assertEquals(2, g3.getInDegree(1));
        assertEquals(2, g3.getInDegree(2));
        assertEquals(2, g3.getInDegree(3));

        assertEquals(1, g4.getInDegree(4));

        assertEquals(0, g5.getInDegree(1));

        assertEquals(1, g6.getInDegree(1));
        g6.addEdge(1, 1);
        assertEquals(2, g6.getInDegree(1));

        assertEquals(2, g7.getInDegree(1));
        assertEquals(2, g7.getInDegree(2));
        assertEquals(2, g7.getInDegree(3));

        assertEquals(2, g8.getInDegree(4));
    }

    /**
     * Test of getOutdegree method, of class AdjacencyMatrix.
     */
    @Test
    public void testGetOutdegree() {
        System.out.println("getOutDegree");
        assertEquals(0, g1.getOutDegree(1));

        assertEquals(1, g2.getOutDegree(1));
        g2.addEdge(1, 1);
        assertEquals(2, g2.getOutDegree(1));

        assertEquals(2, g3.getOutDegree(1));
        assertEquals(2, g3.getOutDegree(2));
        assertEquals(2, g3.getOutDegree(3));

        assertEquals(1, g4.getOutDegree(4));

        assertEquals(0, g5.getOutDegree(1));

        assertEquals(1, g6.getOutDegree(1));
        g6.addEdge(1, 1);
        assertEquals(2, g6.getOutDegree(1));

        assertEquals(2, g7.getOutDegree(1));
        assertEquals(2, g7.getOutDegree(2));
        assertEquals(2, g7.getOutDegree(3));

        assertEquals(2, g8.getOutDegree(4));
    }

    @Test
    public void testToMatrix() {
        System.out.println("toMatrix digraph = false");

        AdjacencyMatrix graph = new AdjacencyMatrix(8, false);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        Matrix matrix = graph.toMatrix();

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                System.out.print(matrix.get(i, j) + " ");
            }
            System.out.println();
        }

        assertEquals(1.0, matrix.get(0, 2), 1E-10);
        assertEquals(1.0, matrix.get(1, 7), 1E-10);
        assertEquals(1.0, matrix.get(2, 6), 1E-10);
        assertEquals(1.0, matrix.get(7, 4), 1E-10);
        assertEquals(1.0, matrix.get(3, 4), 1E-10);
        assertEquals(1.0, matrix.get(3, 5), 1E-10);
        assertEquals(1.0, matrix.get(5, 4), 1E-10);

        // Graph is undirected.
        assertEquals(1.0, matrix.get(2, 0), 1E-10);
        assertEquals(1.0, matrix.get(7, 1), 1E-10);
        assertEquals(1.0, matrix.get(6, 2), 1E-10);
        assertEquals(1.0, matrix.get(4, 7), 1E-10);
        assertEquals(1.0, matrix.get(4, 3), 1E-10);
        assertEquals(1.0, matrix.get(5, 3), 1E-10);
        assertEquals(1.0, matrix.get(4, 5), 1E-10);
    }

    @Test
    public void testToMatrixDigraph() {
        System.out.println("toMatrix digraph = true");

        AdjacencyMatrix graph = new AdjacencyMatrix(8, true);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        Matrix matrix = graph.toMatrix();

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                System.out.print(matrix.get(i, j) + " ");
            }
            System.out.println();
        }

        assertEquals(1.0, matrix.get(0, 2), 1E-10);
        assertEquals(1.0, matrix.get(1, 7), 1E-10);
        assertEquals(1.0, matrix.get(2, 6), 1E-10);
        assertEquals(1.0, matrix.get(7, 4), 1E-10);
        assertEquals(1.0, matrix.get(3, 4), 1E-10);
        assertEquals(1.0, matrix.get(3, 5), 1E-10);
        assertEquals(1.0, matrix.get(5, 4), 1E-10);

        // Graph is directed.
        assertEquals(0.0, matrix.get(2, 0), 1E-10);
        assertEquals(0.0, matrix.get(7, 1), 1E-10);
        assertEquals(0.0, matrix.get(6, 2), 1E-10);
        assertEquals(0.0, matrix.get(4, 7), 1E-10);
        assertEquals(0.0, matrix.get(4, 3), 1E-10);
        assertEquals(0.0, matrix.get(5, 3), 1E-10);
        assertEquals(0.0, matrix.get(4, 5), 1E-10);
    }

    /**
     * Test of subgraph method, of class AdjacencyMatrix.
     */
    @Test
    public void testSubgraph() {
        System.out.println("subgraph digraph = false");

        AdjacencyMatrix graph = new AdjacencyMatrix(8, false);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        int[] v = {1, 3, 7};
        AdjacencyMatrix sub = graph.subgraph(v);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                System.out.print(sub.getWeight(i, j) + " ");
            }
            System.out.println();
        }

        assertEquals(1.0, sub.getWeight(0, 2), 1E-10);
        assertEquals(1.0, sub.getWeight(2, 0), 1E-10);
    }

    /**
     * Test of subgraph method, of class AdjacencyMatrix.
     */
    @Test
    public void testSubgraphDigraph() {
        System.out.println("subgraph digraph = true");

        AdjacencyMatrix graph = new AdjacencyMatrix(8, true);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        int[] v = {1, 3, 7};
        AdjacencyMatrix sub = graph.subgraph(v);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                System.out.print(sub.getWeight(i, j) + " ");
            }
            System.out.println();
        }

        assertEquals(1.0, sub.getWeight(0, 2), 1E-10);
    }

    /**
     * Test of dfs method, of class AdjacencyMatrix.
     */
    @Test
    public void testDfsort() {
        System.out.println("dfs sort");
        int[] ts = {1,10,12,11,9,4,5,3,2,6,0,7,8};

        Graph graph = new AdjacencyMatrix(13, true);
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

        assertArrayEquals(ts, graph.dfsort());
    }

    /**
     * Test of dfs method, of class AdjacencyMatrix.
     */
    @Test
    public void testDfcc() {
        System.out.println("dfs connected component");
        int[] size = {3, 5};
        int[] id = {0, 1, 0, 1, 1, 1, 0, 1};
        int[][] cc = {{0, 2, 6}, {1, 3, 4, 5, 7}};

        Graph graph = new AdjacencyMatrix(8);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        int[][] cc2 = graph.dfcc();
        assertTrue(Arrays.deepEquals(cc, cc2));
    }

    /**
     * Test of bfs method, of class AdjacencyMatrix.
     */
    @Test
    public void testBfsort() {
        System.out.println("bfs sort");
        int[] ts = {0, 8, 1, 2, 7, 3, 6, 5, 4, 9, 10, 11, 12};

        Graph graph = new AdjacencyMatrix(13, true);
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

        assertArrayEquals(ts, graph.bfsort());
    }

    /**
     * Test of bfs method, of class AdjacencyMatrix.
     */
    @Test
    public void testBfcc() {
        System.out.println("bfs connected component");
        int[] size = {3, 5};
        int[] id = {0, 1, 0, 1, 1, 1, 0, 1};
        int[][] cc = {{0, 2, 6}, {1, 3, 4, 5, 7}};

        Graph graph = new AdjacencyMatrix(8);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        int[][] cc2 = graph.bfcc();
        assertTrue(Arrays.deepEquals(cc, cc2));
    }

    /**
     * Test of dijkstra method, of class AdjacencyMatrix.
     */
    @Test
    public void testDijkstra() {
        System.out.println("Dijkstra");
        double[][] wt = {
            {0.00, 0.41, 0.82, 0.86, 0.50, 0.29},
            {1.13, 0.00, 0.51, 0.68, 0.32, 1.06},
            {0.95, 1.17, 0.00, 0.50, 1.09, 0.88},
            {0.45, 0.67, 0.91, 0.00, 0.59, 0.38},
            {0.81, 1.03, 0.32, 0.36, 0.00, 0.74},
            {1.02, 0.29, 0.53, 0.57, 0.21, 0.00},
        };

        Graph graph = new AdjacencyMatrix(6, true);
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

        double[][] wt2 = graph.dijkstra();
        
        assertTrue(MathEx.equals(wt, wt2));
    }

    /**
     * Test of pushRelabel method, of class AdjacencyMatrix.
     */
    @Test
    public void testPushRelabel() {
        System.out.println("Push-Relabel");
        double[][] results = {
            { 0,   1,   3,   0,   0,   0},   
            {-1,   0,   1,   0,   0,   0},   
            {-3,  -1,   0,   0,   4,   0},   
            { 0,   0,   0,   0,   0,   0},   
            { 0,   0,  -4,   0,   0,   4},   
            { 0,   0,   0,   0,  -4,   0}
        };

        AdjacencyMatrix graph = new AdjacencyMatrix(6, true);
        graph.addEdge(0, 1, 2);
        graph.addEdge(0, 2, 9);
        graph.addEdge(1, 2, 1);
        graph.addEdge(1, 3, 0);
        graph.addEdge(1, 4, 0);
        graph.addEdge(2, 4, 7);
        graph.addEdge(3, 5, 7);
        graph.addEdge(4, 5, 4);

        double[][] flow = new double[6][6];
        double maxFlow = graph.pushRelabel(flow, 0, 5);
        
        assertEquals(4.0, maxFlow, 1E-1);
        assertTrue(MathEx.equals(flow, results));
    }

    @Test
    public void testPrim() {
        System.out.println("Prim's algorithm");

        Graph graph = new AdjacencyList(6);
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
        assertEquals(1.47, cost, 1E-5);
        assertEquals(5, mst.size());
        assertEquals(1, mst.getFirst().u());
        assertEquals(5, mst.getFirst().v());
        assertEquals(0.29, mst.getFirst().weight(), 1E-5);
        assertEquals(5, mst.getLast().u());
        assertEquals(0, mst.getLast().v());
        assertEquals(0.29, mst.getLast().weight(), 1E-5);
    }

    @Test
    public void testHeldKarp() {
        System.out.println("Held-Karp algorithm");

        Graph graph = new AdjacencyList(6);
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

        int[] tour = graph.heldKarp();
        assertEquals(2.17, graph.getPathDistance(tour), 1E-4);
        assertEquals(7, tour.length);
        assertEquals(0, tour[0]);
        assertEquals(5, tour[1]);
        assertEquals(1, tour[2]);
        assertEquals(4, tour[3]);
        assertEquals(2, tour[4]);
        assertEquals(3, tour[5]);
        assertEquals(0, tour[6]);
    }

    @Test
    public void testTsp() {
        System.out.println("TSP with branch and bound");

        Graph graph = new AdjacencyList(6);
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

        int[] tour = graph.tsp();
        assertEquals(2.17, graph.getPathDistance(tour), 1E-4);
        assertEquals(7, tour.length);
        assertEquals(0, tour[0]);
        assertEquals(5, tour[1]);
        assertEquals(1, tour[2]);
        assertEquals(4, tour[3]);
        assertEquals(2, tour[4]);
        assertEquals(3, tour[5]);
        assertEquals(0, tour[6]);
    }

    @Test
    public void testNearestInsertion() {
        System.out.println("Nearest Insertion");

        Graph graph = new AdjacencyList(6);
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

        int[] tour = graph.nearestInsertion();
        assertEquals(7, tour.length);
        assertEquals(2.27, graph.getPathDistance(tour), 1E-4);
        assertEquals(0, tour[0]);
        assertEquals(1, tour[1]);
        assertEquals(2, tour[2]);
        assertEquals(4, tour[3]);
        assertEquals(3, tour[4]);
        assertEquals(5, tour[5]);
        assertEquals(0, tour[6]);

        double cost = graph.opt2(tour, 3);
        System.out.println(Arrays.toString(tour));
        assertEquals(2.17, cost, 1E-4);
        assertEquals(0, tour[0]);
        assertEquals(3, tour[1]);
        assertEquals(2, tour[2]);
        assertEquals(4, tour[3]);
        assertEquals(1, tour[4]);
        assertEquals(5, tour[5]);
        assertEquals(0, tour[6]);
    }

    @Test
    public void testFarthestInsertion() {
        System.out.println("Farthest Insertion");

        Graph graph = new AdjacencyList(6);
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

        int[] tour = graph.farthestInsertion();
        assertEquals(7, tour.length);
        assertEquals(2.17, graph.getPathDistance(tour), 1E-4);
        assertEquals(0, tour[0]);
        assertEquals(5, tour[1]);
        assertEquals(1, tour[2]);
        assertEquals(4, tour[3]);
        assertEquals(2, tour[4]);
        assertEquals(3, tour[5]);
        assertEquals(0, tour[6]);
    }

    @Test
    public void testArbitraryInsertion() {
        System.out.println("Arbitrary Insertion");

        Graph graph = new AdjacencyList(6);
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

        int[] tour = graph.arbitraryInsertion();
        assertEquals(7, tour.length);
        assertEquals(2.17, graph.getPathDistance(tour), 1E-4);
        assertEquals(0, tour[0]);
        assertEquals(3, tour[1]);
        assertEquals(2, tour[2]);
        assertEquals(4, tour[3]);
        assertEquals(1, tour[4]);
        assertEquals(5, tour[5]);
        assertEquals(0, tour[6]);
    }

    @Test
    public void testChristofides() {
        System.out.println("Christofides algorithm");

        Graph graph = new AdjacencyList(6);
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

        int[] tour = graph.christofides();
        assertEquals(7, tour.length);
        assertEquals(2.28, graph.getPathDistance(tour), 1E-4);
        assertEquals(0, tour[0]);
        assertEquals(5, tour[1]);
        assertEquals(4, tour[2]);
        assertEquals(3, tour[3]);
        assertEquals(2, tour[4]);
        assertEquals(1, tour[5]);
        assertEquals(0, tour[6]);
    }
}
