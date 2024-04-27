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

import smile.math.MathEx;
import smile.math.matrix.SparseMatrix;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("unused")
public class AdjacencyListTest {
    Graph g1, g2, g3, g4;
    Graph g5, g6, g7, g8;

    public AdjacencyListTest() {
        g1 = new AdjacencyList(5, true);
        g2 = new AdjacencyList(5, true);
        g3 = new AdjacencyList(5, true);
        g4 = new AdjacencyList(5, true);

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

        g5 = new AdjacencyList(5, false);
        g6 = new AdjacencyList(5, false);
        g7 = new AdjacencyList(5, false);
        g8 = new AdjacencyList(5, false);

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
     * Test of isConnected method, of class AdjacencyList.
     */
    @Test
    public void testIsConnected() {
        System.out.println("isConnected");
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
     * Test of getWeight method, of class AdjacencyList.
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
     * Test of setWeight method, of class AdjacencyList.
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
     * Test of removeEdge method, of class AdjacencyList.
     */
    @Test
    public void testRemoveEdge_int_int() {
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
     * Test of removeEdge method, of class AdjacencyList.
     */
    @Test
    public void testRemoveEdge_GraphEdge() {
        System.out.println("removeEdge");
        g4.addEdge(1, 4);
        g4.removeEdge(g4.getEdge(4, 1));
        assertEquals(1.0, g8.getWeight(1, 4), 1E-10);
        assertEquals(0.0, g4.getWeight(4, 1), 1E-10);

        g8.removeEdge(g8.getEdge(1, 4));
        assertEquals(0, g8.getWeight(1, 4), 1E-10);
        assertEquals(0, g8.getWeight(4, 1), 1E-10);
    }

    /**
     * Test of getDegree method, of class AdjacencyList.
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
     * Test of getIndegree method, of class AdjacencyList.
     */
    @Test
    public void testGetIndegree() {
        System.out.println("getIndegree");
        assertEquals(0, g1.getIndegree(1));

        assertEquals(1, g2.getIndegree(1));
        g2.addEdge(1, 1);
        assertEquals(2, g2.getIndegree(1));

        assertEquals(2, g3.getIndegree(1));
        assertEquals(2, g3.getIndegree(2));
        assertEquals(2, g3.getIndegree(3));

        assertEquals(1, g4.getIndegree(4));

        assertEquals(0, g5.getIndegree(1));

        assertEquals(1, g6.getIndegree(1));
        g6.addEdge(1, 1);
        assertEquals(2, g6.getIndegree(1));

        assertEquals(2, g7.getIndegree(1));
        assertEquals(2, g7.getIndegree(2));
        assertEquals(2, g7.getIndegree(3));

        assertEquals(2, g8.getIndegree(4));
    }

    /**
     * Test of getOutdegree method, of class AdjacencyList.
     */
    @Test
    public void testGetOutdegree() {
        System.out.println("getOutdegree");
        assertEquals(0, g1.getOutdegree(1));

        assertEquals(1, g2.getOutdegree(1));
        g2.addEdge(1, 1);
        assertEquals(2, g2.getOutdegree(1));

        assertEquals(2, g3.getOutdegree(1));
        assertEquals(2, g3.getOutdegree(2));
        assertEquals(2, g3.getOutdegree(3));

        assertEquals(1, g4.getOutdegree(4));

        assertEquals(0, g5.getOutdegree(1));

        assertEquals(1, g6.getOutdegree(1));
        g6.addEdge(1, 1);
        assertEquals(2, g6.getOutdegree(1));

        assertEquals(2, g7.getOutdegree(1));
        assertEquals(2, g7.getOutdegree(2));
        assertEquals(2, g7.getOutdegree(3));

        assertEquals(2, g8.getOutdegree(4));
    }

    /**
     * Test of dfs method, of class AdjacencyList.
     */
    @Test
    public void testDfs() {
        System.out.println("dfs sort");
        int[] ts = {1,10,12,11,9,4,5,3,2,6,0,7,8};

        Graph graph = new AdjacencyList(13, true);
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

        assertArrayEquals(ts, graph.sortdfs());
    }

    /**
     * Test of dfs method, of class AdjacencyList.
     */
    @Test
    public void testDfs2() {
        System.out.println("dfs connected component");
        int[] size = {3, 5};
        int[] id = {0, 1, 0, 1, 1, 1, 0, 1};
        int[][] cc = {{0, 2, 6}, {1, 3, 4, 5, 7}};

        Graph graph = new AdjacencyList(8);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        int[][] cc2 = graph.dfs();
        assertTrue(Arrays.deepEquals(cc, cc2));
    }

    /**
     * Test of toSparseMatrix method, of class AdjacencyList.
     */
    @Test
    public void testToMatrix() {
        System.out.println("toMatrix digraph = false");

        AdjacencyList graph = new AdjacencyList(8, false);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        SparseMatrix matrix = graph.toMatrix();

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

    /**
     * Test of toSparseMatrix method, of class AdjacencyList.
     */
    @Test
    public void testToMatrixDigraph() {
        System.out.println("toMatrix digraph = true");

        AdjacencyList graph = new AdjacencyList(8, true);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        SparseMatrix matrix = graph.toMatrix();

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
     * Test of subgraph method, of class AdjacencyList.
     */
    @Test
    public void testSubgraph() {
        System.out.println("subgraph digraph = false");

        AdjacencyList graph = new AdjacencyList(8, false);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        int[] v = {1, 3, 7};
        AdjacencyList sub = graph.subgraph(v);

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
     * Test of subgraph method, of class AdjacencyList.
     */
    @Test
    public void testSubgraphDigraph() {
        System.out.println("subgraph digraph = true");

        AdjacencyList graph = new AdjacencyList(8, true);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        int[] v = {1, 3, 7};
        AdjacencyList sub = graph.subgraph(v);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                System.out.print(sub.getWeight(i, j) + " ");
            }
            System.out.println();
        }

        assertEquals(1.0, sub.getWeight(0, 2), 1E-10);
    }

    /**
     * Test of bfs method, of class AdjacencyList.
     */
    @Test
    public void testBfs() {
        System.out.println("bfs sort");
        int[] ts = {0, 8, 1, 2, 7, 3, 6, 5, 4, 9, 10, 11, 12};

        Graph graph = new AdjacencyList(13, true);
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

        assertArrayEquals(ts, graph.sortbfs());
    }

    /**
     * Test of bfs method, of class AdjacencyList.
     */
    @Test
    public void testBfs2() {
        System.out.println("bfs connected component");
        int[] size = {3, 5};
        int[] id = {0, 1, 0, 1, 1, 1, 0, 1};
        int[][] cc = {{0, 2, 6}, {1, 3, 4, 5, 7}};

        Graph graph = new AdjacencyList(8);
        graph.addEdge(0, 2);
        graph.addEdge(1, 7);
        graph.addEdge(2, 6);
        graph.addEdge(7, 4);
        graph.addEdge(3, 4);
        graph.addEdge(3, 5);
        graph.addEdge(5, 4);

        int[][] cc2 = graph.bfs();
        assertTrue(Arrays.deepEquals(cc, cc2));
    }

    /**
     * Test of dijkstra method, of class AdjacencyList.
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

        Graph graph = new AdjacencyList(6, true);
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
}