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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.graph;

import java.util.Arrays;
import java.util.HashSet;
import smile.math.MathEx;
import smile.datasets.USPS;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NearestNeighborGraph.
 *
 * @author Haifeng Li
 */
public class NearestNeighborGraphTest {
    double[][] x;
    int k = 7;

    // Small synthetic dataset: 10 points on a line (easy to reason about exact neighbors)
    static final double[][] LINE = {
        {0.0}, {1.0}, {2.0}, {3.0}, {4.0},
        {5.0}, {6.0}, {7.0}, {8.0}, {9.0}
    };

    // Wrap as Double[] for generic overloads
    static final Double[][] LINE_BOXED;
    static {
        LINE_BOXED = new Double[LINE.length][1];
        for (int i = 0; i < LINE.length; i++) {
            LINE_BOXED[i] = new Double[]{LINE[i][0]};
        }
    }

    public NearestNeighborGraphTest() throws Exception {
        MathEx.setSeed(19650218); // to get repeatable results.
        var usps = new USPS();
        x = usps.x();
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @AfterEach
    public void tearDown() {
    }

    // -----------------------------------------------------------------------
    // Accessors / record components
    // -----------------------------------------------------------------------

    @Test
    public void testAccessors() {
        System.out.println("NearestNeighborGraph accessors");
        var graph = NearestNeighborGraph.of(LINE, 3);
        assertEquals(3, graph.k());
        assertEquals(LINE.length, graph.size());
        assertEquals(LINE.length, graph.neighbors().length);
        assertEquals(LINE.length, graph.distances().length);
        assertEquals(LINE.length, graph.index().length);
        // Default index is identity
        for (int i = 0; i < LINE.length; i++) {
            assertEquals(i, graph.index()[i]);
        }
    }

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    public void testInvalidK() {
        assertThrows(IllegalArgumentException.class,
            () -> new NearestNeighborGraph(0, new int[3][2], new double[3][2], new int[3]));
    }

    @Test
    public void testMismatchedNeighborsDistances() {
        assertThrows(IllegalArgumentException.class,
            () -> new NearestNeighborGraph(2, new int[3][2], new double[4][2], new int[3]));
    }

    @Test
    public void testMismatchedNeighborsIndex() {
        assertThrows(IllegalArgumentException.class,
            () -> new NearestNeighborGraph(2, new int[3][2], new double[3][2], new int[5]));
    }

    @Test
    public void testNullNeighbors() {
        assertThrows(NullPointerException.class,
            () -> new NearestNeighborGraph(2, null, new double[3][2], new int[3]));
    }

    @Test
    public void testKTooSmallForOf() {
        assertThrows(IllegalArgumentException.class,
            () -> NearestNeighborGraph.of(LINE, 1));
    }

    @Test
    public void testKTooSmallForDescentForest() {
        assertThrows(IllegalArgumentException.class,
            () -> NearestNeighborGraph.descent(LINE, 1));
    }

    @Test
    public void testKTooSmallForDescentGeneric() {
        assertThrows(IllegalArgumentException.class,
            () -> NearestNeighborGraph.descent(LINE_BOXED,
                  (a, b) -> Math.abs(a[0] - b[0]), 1));
    }

    // -----------------------------------------------------------------------
    // Exact k-NN: of(double[][], k) — Euclidean
    // -----------------------------------------------------------------------

    @Test
    public void testExactEuclidean() {
        System.out.println("Exact k-NN (Euclidean)");
        var graph = NearestNeighborGraph.of(LINE, 3);

        assertEquals(3, graph.k());
        assertEquals(LINE.length, graph.size());
        for (int i = 0; i < LINE.length; i++) {
            assertEquals(3, graph.neighbors()[i].length);
            assertEquals(3, graph.distances()[i].length);
        }
        // Verify distances match actual Euclidean distances
        for (int i = 0; i < LINE.length; i++) {
            for (int j = 0; j < graph.k(); j++) {
                int nb = graph.neighbors()[i][j];
                double expected = MathEx.distance(LINE[i], LINE[nb]);
                assertEquals(expected, graph.distances()[i][j], 1E-10);
            }
        }
        // Vertex 5 (value 5.0): nearest neighbors should be 4 and 6 (dist 1.0), then 3 or 7 (dist 2.0)
        int[] nb5 = graph.neighbors()[5];
        var nbSet5 = new HashSet<Integer>();
        for (int n : nb5) nbSet5.add(n);
        assertTrue(nbSet5.contains(4));
        assertTrue(nbSet5.contains(6));
    }

    @Test
    public void testExactNoSelfNeighbor() {
        System.out.println("No self-neighbor in exact k-NN");
        var graph = NearestNeighborGraph.of(LINE, 3);
        for (int i = 0; i < LINE.length; i++) {
            for (int nb : graph.neighbors()[i]) {
                assertNotEquals(i, nb, "vertex " + i + " is its own neighbor");
            }
        }
    }

    @Test
    public void testExactDistancesNonNegative() {
        System.out.println("Distances are non-negative in exact k-NN");
        var graph = NearestNeighborGraph.of(LINE, 3);
        for (int i = 0; i < LINE.length; i++) {
            for (double d : graph.distances()[i]) {
                assertTrue(d >= 0.0, "negative distance at vertex " + i);
            }
        }
    }

    @Test
    public void testExactNeighborIndicesInRange() {
        System.out.println("Neighbor indices in valid range");
        var graph = NearestNeighborGraph.of(LINE, 3);
        int n = LINE.length;
        for (int i = 0; i < n; i++) {
            for (int nb : graph.neighbors()[i]) {
                assertTrue(nb >= 0 && nb < n,
                    "out-of-range neighbor " + nb + " at vertex " + i);
            }
        }
    }

    @Test
    public void testExactNoduplicateNeighbors() {
        System.out.println("No duplicate neighbors in exact k-NN");
        var graph = NearestNeighborGraph.of(LINE, 3);
        for (int i = 0; i < LINE.length; i++) {
            int[] nb = graph.neighbors()[i].clone();
            Arrays.sort(nb);
            for (int j = 1; j < nb.length; j++) {
                assertNotEquals(nb[j - 1], nb[j],
                    "duplicate neighbor at vertex " + i);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Exact k-NN: of(T[], Distance<T>, k) — generic overload
    // -----------------------------------------------------------------------

    @Test
    public void testExactGenericDistance() {
        System.out.println("Exact k-NN with generic distance");
        var graph = NearestNeighborGraph.of(LINE_BOXED, (a, b) -> {
            double d = a[0] - b[0];
            return Math.abs(d);
        }, 2);

        assertEquals(2, graph.k());
        assertEquals(LINE_BOXED.length, graph.size());
        // vertex 0 (value 0.0): nearest should be 1 (dist 1.0) and 2 (dist 2.0)
        int[] nb0 = graph.neighbors()[0];
        var nbSet0 = new HashSet<Integer>();
        for (int n : nb0) nbSet0.add(n);
        assertTrue(nbSet0.contains(1));
    }

    // -----------------------------------------------------------------------
    // k = 2 edge case
    // -----------------------------------------------------------------------

    @Test
    public void testKEquals2() {
        System.out.println("Exact k-NN with k=2");
        var graph = NearestNeighborGraph.of(LINE, 2);
        assertEquals(2, graph.k());
        for (int i = 0; i < LINE.length; i++) {
            assertEquals(2, graph.neighbors()[i].length);
            assertEquals(2, graph.distances()[i].length);
        }
    }

    // -----------------------------------------------------------------------
    // USPS dataset: existing regression tests with enhanced assertions
    // -----------------------------------------------------------------------

    @Test
    public void testRandom() {
        System.out.println("Random");

        var graph = NearestNeighborGraph.random(x, MathEx::distance, k);
        int[][] neighbors = graph.neighbors();
        double[][] distances = graph.distances();
        assertEquals(x.length, neighbors.length);
        assertEquals(x.length, distances.length);
        assertEquals(distances[0][0], MathEx.distance(x[0], x[neighbors[0][0]]), 1E-7);
        assertEquals(distances[100][2], MathEx.distance(x[100], x[neighbors[100][2]]), 1E-7);
        for (int i = 0; i < x.length; i++) {
            assertEquals(k, neighbors[i].length);
            assertEquals(k, distances[i].length);
        }
    }

    @Test
    public void testDescentRandom() {
        System.out.println("Descent with random initialization");

        var graph = NearestNeighborGraph.descent(x, MathEx::distance, k);
        int[][] neighbors = graph.neighbors();
        double[][] distances = graph.distances();
        assertEquals(x.length, neighbors.length);
        assertEquals(x.length, distances.length);
        assertEquals(distances[0][0], MathEx.distance(x[0], x[neighbors[0][0]]), 1E-7);
        assertEquals(distances[100][2], MathEx.distance(x[100], x[neighbors[100][2]]), 1E-7);
        for (int i = 0; i < x.length; i++) {
            assertEquals(k, neighbors[i].length);
            assertEquals(k, distances[i].length);
        }
    }

    @Test
    public void testDescentForest() {
        System.out.println("Descent with Forest");

        var graph = NearestNeighborGraph.descent(x, k);
        int[][] neighbors = graph.neighbors();
        double[][] distances = graph.distances();
        assertEquals(x.length, neighbors.length);
        assertEquals(x.length, distances.length);
        assertEquals(distances[0][0], MathEx.distance(x[0], x[neighbors[0][0]]), 1E-7);
        assertEquals(distances[100][2], MathEx.distance(x[100], x[neighbors[100][2]]), 1E-7);
        for (int i = 0; i < x.length; i++) {
            assertEquals(k, neighbors[i].length);
            assertEquals(k, distances[i].length);
        }
    }

    // -----------------------------------------------------------------------
    // Approximate quality: descent should find most true neighbors
    // -----------------------------------------------------------------------

    @Test
    public void testDescentApproximationQuality() {
        System.out.println("NN-Descent approximation quality vs exact");
        // Use a small slice of USPS for tractable exact comparison
        int n = 200;
        double[][] sub = Arrays.copyOf(x, n);

        var exact = NearestNeighborGraph.of(sub, k);
        var approx = NearestNeighborGraph.descent(sub, k);

        int total = 0, found = 0;
        for (int i = 0; i < n; i++) {
            var exactSet = new HashSet<Integer>();
            for (int nb : exact.neighbors()[i]) exactSet.add(nb);
            for (int nb : approx.neighbors()[i]) {
                if (exactSet.contains(nb)) found++;
            }
            total += k;
        }
        double recall = (double) found / total;
        // NN-Descent should achieve > 85% recall on this dataset
        assertTrue(recall > 0.85,
            String.format("NN-Descent recall %.2f%% is too low", recall * 100));
    }

    // -----------------------------------------------------------------------
    // graph() — AdjacencyList conversion
    // -----------------------------------------------------------------------

    @Test
    public void testGraphUndirected() {
        System.out.println("graph() undirected");
        var knn = NearestNeighborGraph.of(LINE, 3);
        AdjacencyList g = knn.graph(false);

        assertEquals(LINE.length, g.getVertexCount());
        assertFalse(g.isDigraph());
        // Every neighbor edge exists in the graph
        for (int i = 0; i < LINE.length; i++) {
            for (int j = 0; j < knn.k(); j++) {
                int nb = knn.neighbors()[i][j];
                double w  = knn.distances()[i][j];
                assertTrue(g.hasEdge(i, nb));
                assertEquals(w, g.getWeight(i, nb), 1E-10);
            }
        }
    }

    @Test
    public void testGraphDirected() {
        System.out.println("graph() directed");
        var knn = NearestNeighborGraph.of(LINE, 3);
        AdjacencyList g = knn.graph(true);

        assertEquals(LINE.length, g.getVertexCount());
        assertTrue(g.isDigraph());
        // Directed: i→nb exists, nb→i does not unless nb also lists i as neighbor
        for (int i = 0; i < LINE.length; i++) {
            for (int nb : knn.neighbors()[i]) {
                assertTrue(g.hasEdge(i, nb));
            }
        }
    }

    @Test
    public void testGraphWeightsMatchDistances() {
        System.out.println("graph() weights match distances");
        var knn = NearestNeighborGraph.of(LINE, 2);
        AdjacencyList g = knn.graph(false);

        for (int i = 0; i < LINE.length; i++) {
            for (int j = 0; j < knn.k(); j++) {
                int nb = knn.neighbors()[i][j];
                assertEquals(knn.distances()[i][j], g.getWeight(i, nb), 1E-10);
            }
        }
    }

    // -----------------------------------------------------------------------
    // largest() — connected component filtering
    // -----------------------------------------------------------------------

    @Test
    public void testLargestAlreadyConnected() {
        System.out.println("largest() on already-connected graph returns same object");
        // Dense enough that the graph is connected
        var knn = NearestNeighborGraph.of(LINE, 4);
        var largest = knn.largest(false);
        // Should return the same record (reference equality when already 1 component)
        assertSame(knn, largest);
    }

    @Test
    public void testLargestShape() {
        System.out.println("largest() result has correct shape");
        // Build a graph that might be disconnected with small k on a larger dataset
        int n = 200;
        double[][] sub = Arrays.copyOf(x, n);
        var knn = NearestNeighborGraph.of(sub, 3);
        var largest = knn.largest(false);

        int m = largest.size();
        assertTrue(m <= n);
        assertEquals(m, largest.neighbors().length);
        assertEquals(m, largest.distances().length);
        assertEquals(m, largest.index().length);
        assertEquals(knn.k(), largest.k());
        // Each row has exactly k entries
        for (int i = 0; i < m; i++) {
            assertEquals(knn.k(), largest.neighbors()[i].length);
            assertEquals(knn.k(), largest.distances()[i].length);
        }
    }

    @Test
    public void testLargestNeighborIndicesInRange() {
        System.out.println("largest() neighbor indices within [0, size)");
        int n = 200;
        double[][] sub = Arrays.copyOf(x, n);
        var knn = NearestNeighborGraph.of(sub, 3);
        var largest = knn.largest(false);

        int m = largest.size();
        for (int i = 0; i < m; i++) {
            for (int nb : largest.neighbors()[i]) {
                assertTrue(nb >= 0 && nb < m,
                    "out-of-range neighbor " + nb + " (size=" + m + ") at vertex " + i);
            }
        }
    }

    @Test
    public void testLargestIndexContainsOriginalVertices() {
        System.out.println("largest() index[] references valid original vertices");
        int n = 200;
        double[][] sub = Arrays.copyOf(x, n);
        var knn = NearestNeighborGraph.of(sub, 3);
        var largest = knn.largest(false);

        for (int origVertex : largest.index()) {
            assertTrue(origVertex >= 0 && origVertex < n,
                "index entry " + origVertex + " out of range");
        }
    }

    @Test
    public void testLargestFormsSingleComponent() {
        System.out.println("largest() result is a single connected component");
        int n = 200;
        double[][] sub = Arrays.copyOf(x, n);
        var knn = NearestNeighborGraph.of(sub, 3);
        var largest = knn.largest(false);

        AdjacencyList g = largest.graph(false);
        int[][] cc = g.bfcc();
        assertEquals(1, cc.length,
            "largest() should produce a single connected component");
    }
}
