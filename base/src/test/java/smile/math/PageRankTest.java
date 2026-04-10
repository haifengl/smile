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
package smile.math;

import smile.tensor.DenseMatrix;
import smile.tensor.Vector;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the PageRank algorithm.
 *
 * @author Haifeng Li
 */
public class PageRankTest {

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    /**
     * Classic 4-node PageRank example:
     * Node 1 -> {2, 3}, Node 2 -> {4}, Node 3 -> {4}, Node 4 -> {2}
     * Stochastic transition matrix (column-stochastic).
     */
    @Test
    public void testSimpleGraph() {
        System.out.println("PageRank simple graph");
        // Column-stochastic matrix: each column sums to 1
        // Nodes: 0(A), 1(B), 2(C), 3(D)
        // B->C, C->B, D->B, D->C, A->B, A->C, A->D
        // Using a simple 3-node cycle: 0->1, 1->2, 2->0
        double[][] data = {
            {0.0, 0.0, 1.0},
            {1.0, 0.0, 0.0},
            {0.0, 1.0, 0.0}
        };
        DenseMatrix A = DenseMatrix.of(data);
        Vector rank = PageRank.of(A);

        // For a perfectly symmetric cycle, all ranks should be equal
        assertEquals(3, rank.size());
        assertEquals(rank.get(0), rank.get(1), 0.01);
        assertEquals(rank.get(1), rank.get(2), 0.01);

        // Ranks should sum to approximately 1
        double sum = 0;
        for (int i = 0; i < rank.size(); i++) sum += rank.get(i);
        assertEquals(1.0, sum, 0.01);
    }

    /**
     * Test that a dangling node (no outgoing links) is handled via teleportation.
     */
    @Test
    public void testWithTeleportation() {
        System.out.println("PageRank with custom damping");
        // 2-node graph: 0->1, 1->0
        double[][] data = {
            {0.0, 1.0},
            {1.0, 0.0}
        };
        DenseMatrix A = DenseMatrix.of(data);
        Vector v = A.vector(2);
        v.fill(0.5);

        Vector rank = PageRank.of(A, v, 0.85, 1E-7, 100);
        assertEquals(2, rank.size());
        // Both nodes should have equal rank in a symmetric 2-cycle
        assertEquals(rank.get(0), rank.get(1), 1E-5);
        double sum = 0;
        for (int i = 0; i < rank.size(); i++) sum += rank.get(i);
        assertEquals(1.0, sum, 0.01);
    }

    /**
     * Test that a node with more incoming links gets higher rank.
     */
    @Test
    public void testRankOrdering() {
        System.out.println("PageRank rank ordering");
        // Node 0 is pointed to by all others (hub node)
        // 0 -> {1}, 1 -> {0}, 2 -> {0}, 3 -> {0}
        double[][] data = {
            {0.0,  1.0, 0.0, 0.0},
            {1.0/3, 0.0, 1.0, 1.0},
            {1.0/3, 0.0, 0.0, 0.0},
            {1.0/3, 0.0, 0.0, 0.0}
        };
        DenseMatrix A = DenseMatrix.of(data);
        Vector rank = PageRank.of(A);

        // Node 0 should have highest rank (most incoming links)
        double r0 = rank.get(0);
        double r2 = rank.get(2);
        double r3 = rank.get(3);
        assertTrue(r0 > r2, "Node 0 should outrank node 2");
        assertTrue(r0 > r3, "Node 0 should outrank node 3");
    }

    @Test
    public void testInvalidArgs() {
        System.out.println("PageRank invalid arguments");
        double[][] data = {{1.0}};
        DenseMatrix A = DenseMatrix.of(data);
        Vector v = A.vector(1);
        v.fill(1.0);

        assertThrows(IllegalArgumentException.class, () ->
            PageRank.of(A, v, 0.85, -1.0, 100));
        assertThrows(IllegalArgumentException.class, () ->
            PageRank.of(A, v, 0.85, 1E-7, 0));
    }
}

