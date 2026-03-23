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

import smile.math.MathEx;
import smile.datasets.USPS;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class NearestNeighborGraphTest {
    double[][] x;

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
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testRandom() {
        System.out.println("Random");

        var graph = NearestNeighborGraph.random(x, MathEx::distance, 7);
        int[][] neighbors = graph.neighbors();
        double[][] distances = graph.distances();
        assertEquals(x.length, neighbors.length);
        assertEquals(x.length, distances.length);
        assertEquals(distances[0][0], MathEx.distance(x[0], x[neighbors[0][0]]), 1E-7);
        assertEquals(distances[100][2], MathEx.distance(x[100], x[neighbors[100][2]]), 1E-7);
        for (int i = 0; i < x.length; i++) {
            assertEquals(7, neighbors[i].length);
            assertEquals(7, distances[i].length);
        }
    }

    @Test
    public void testDescentRandom() {
        System.out.println("Descent with random initialization");

        var graph = NearestNeighborGraph.descent(x, MathEx::distance, 7);
        int[][] neighbors = graph.neighbors();
        double[][] distances = graph.distances();
        assertEquals(x.length, neighbors.length);
        assertEquals(x.length, distances.length);
        assertEquals(distances[0][0], MathEx.distance(x[0], x[neighbors[0][0]]), 1E-7);
        assertEquals(distances[100][2], MathEx.distance(x[100], x[neighbors[100][2]]), 1E-7);
        for (int i = 0; i < x.length; i++) {
            assertEquals(7, neighbors[i].length);
            assertEquals(7, distances[i].length);
        }
    }

    @Test
    public void testDescentForest() {
        System.out.println("Descent with Forest");

        var graph = NearestNeighborGraph.descent(x, 7);
        int[][] neighbors = graph.neighbors();
        double[][] distances = graph.distances();
        assertEquals(x.length, neighbors.length);
        assertEquals(x.length, distances.length);
        assertEquals(distances[0][0], MathEx.distance(x[0], x[neighbors[0][0]]), 1E-7);
        assertEquals(distances[100][2], MathEx.distance(x[100], x[neighbors[100][2]]), 1E-7);
        for (int i = 0; i < x.length; i++) {
            assertEquals(7, neighbors[i].length);
            assertEquals(7, distances[i].length);
        }
    }
}
