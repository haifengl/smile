/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.projection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
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

        DenseMatrix p = instance.getProjection();
        DenseMatrix t = p.aat();

        System.out.println(p.toString(true));
        assertTrue(MathEx.equals(Matrix.eye(40).data(), t.data(), 1E-10));
    }

    @Test
    public void testSparseRandomProjection() {
        System.out.println("sparse random projection");
        RandomProjection instance = RandomProjection.sparse(128, 40);

        DenseMatrix p = instance.getProjection();
        DenseMatrix t = p.aat();

        System.out.println(p.toString(true));
    }
}