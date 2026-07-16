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
package smile.math.distance;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class CorrelationDistanceTest {

    public CorrelationDistanceTest() {
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
     * Test of Pearson correlation distance.
     */
    @Test
    public void testPearsonDistance() {
        System.out.println("Pearson distance");
        double[] x = {1.0, 2.0, 3.0, 4.0};
        double[] y = {4.0, 3.0, 2.0, 1.0};
        double[] z = {4.0, 2.0, 3.0, 1.0};
        double[] w = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] v = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        CorrelationDistance cor = new CorrelationDistance();
        assertEquals(0.0, cor.d(x, x), 1E-5);
        assertEquals(2.0, cor.d(x, y), 1E-5);
        assertEquals(0.2, cor.d(y, z), 1E-5);
        assertEquals(0.5313153, cor.d(w, v), 1E-7);
    }

    /**
     * Test of Spearman correlation distance.
     */
    @Test
    public void testSpearmanDistance() {
        System.out.println("Spearman distance");
        double[] x = {1.0, 2.0, 3.0, 4.0};
        double[] y = {4.0, 3.0, 2.0, 1.0};
        CorrelationDistance spearman = new CorrelationDistance("spearman");
        // Perfect reverse ranking => Spearman cor = -1 => distance = 2
        assertEquals(2.0, spearman.d(x, y), 1E-5);
        // Same vector => distance = 0
        assertEquals(0.0, spearman.d(x, x), 1E-5);
    }

    /**
     * Test of Kendall correlation distance.
     */
    @Test
    public void testKendallDistance() {
        System.out.println("Kendall distance");
        double[] x = {1.0, 2.0, 3.0, 4.0};
        double[] y = {4.0, 3.0, 2.0, 1.0};
        CorrelationDistance kendall = new CorrelationDistance("kendall");
        // Perfect reverse ranking => Kendall tau = -1 => distance = 2
        assertEquals(2.0, kendall.d(x, y), 1E-5);
        assertEquals(0.0, kendall.d(x, x), 1E-5);
    }

    @Test
    public void testInvalidMethod() {
        System.out.println("invalid method");
        assertThrows(IllegalArgumentException.class, () -> new CorrelationDistance("invalid"));
    }

    @Test
    public void testSymmetry() {
        System.out.println("symmetry");
        double[] x = {1.0, 3.0, 5.0, 2.0};
        double[] y = {2.0, 1.0, 4.0, 3.0};
        CorrelationDistance cor = new CorrelationDistance();
        assertEquals(cor.d(x, y), cor.d(y, x), 1E-9);
    }

    @Test
    public void testToString() {
        System.out.println("toString");
        assertTrue(new CorrelationDistance("pearson").toString().contains("pearson"));
        assertTrue(new CorrelationDistance("spearman").toString().contains("spearman"));
        assertTrue(new CorrelationDistance("kendall").toString().contains("kendall"));
    }
}