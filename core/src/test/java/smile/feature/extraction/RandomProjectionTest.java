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
package smile.feature.extraction;

import smile.tensor.DenseMatrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RandomProjectionTest {
    @Test
    public void testGivenDenseRandomProjectionWhenCheckingOrthonormalityThenRowsAreUnitAndOrthogonal() {
        RandomProjection instance = RandomProjection.of(128, 40);

        DenseMatrix p = instance.projection;
        DenseMatrix t = p.aat();

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
    public void testGivenSparseRandomProjectionWhenCreatingProjectionThenMatrixShapeIsStable() {
        RandomProjection instance = RandomProjection.sparse(128, 40);

        DenseMatrix p = instance.projection;
        assertEquals(40, p.nrow());
        assertEquals(128, p.ncol());
    }
}