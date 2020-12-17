/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.projection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.matrix.Matrix;

/**
 *
 * @author Haifeng Li
 */
public class RandomProjectionTest {

    public RandomProjectionTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testRandomProjection() {
        System.out.println("regular random projection");
        RandomProjection instance = RandomProjection.of(128, 40);

        Matrix p = instance.projection();
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

        Matrix p = instance.projection();
        System.out.println(p.toString(true));
    }
}