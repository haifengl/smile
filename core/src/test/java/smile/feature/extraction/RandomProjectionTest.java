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

package smile.feature.extraction;

import smile.math.matrix.Matrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RandomProjectionTest {

    public RandomProjectionTest() {
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
    public void testRandomProjection() {
        System.out.println("regular random projection");
        RandomProjection instance = RandomProjection.of(128, 40);

        Matrix p = instance.projection;
        Matrix t = p.aat();

        System.out.println(p.toString(true));
        for (int i = 0; i < 40; i++) {
            assertEquals(1.0, t.get(i, i), 1E-10);
            for (int j = 0; j < 40; j++) {
                if (i != j) {
                    assertEquals(0.0, t.get(i, j), 1E-10);
                }
            }
        }
    }

    @Test
    public void testSparseRandomProjection() {
        System.out.println("sparse random projection");
        RandomProjection instance = RandomProjection.sparse(128, 40);

        Matrix p = instance.projection;
        System.out.println(p.toString(true));
    }
}