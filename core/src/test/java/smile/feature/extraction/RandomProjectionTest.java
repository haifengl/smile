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

import smile.math.MathEx;
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

    @Test
    public void testGivenInvalidDimensionsForDenseProjectionWhenConstructingThenExceptionIsThrown() {
        // n < 2
        assertThrows(IllegalArgumentException.class, () -> RandomProjection.of(1, 1));
        // p < 1
        assertThrows(IllegalArgumentException.class, () -> RandomProjection.of(10, 0));
        // p > n
        assertThrows(IllegalArgumentException.class, () -> RandomProjection.of(5, 6));
    }

    @Test
    public void testGivenInvalidDimensionsForSparseProjectionWhenConstructingThenExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class, () -> RandomProjection.sparse(1, 1));
        assertThrows(IllegalArgumentException.class, () -> RandomProjection.sparse(10, 0));
        assertThrows(IllegalArgumentException.class, () -> RandomProjection.sparse(5, 6));
    }

    @Test
    public void testGivenDenseRandomProjectionWhenApplyingToVectorThenOutputDimensionIsCorrect() {
        // Given
        int n = 50, p = 10;
        RandomProjection rp = RandomProjection.of(n, p);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = i * 0.1;

        // When
        double[] y = rp.apply(x);

        // Then
        assertEquals(p, y.length);
        for (double v : y) assertTrue(Double.isFinite(v));
    }

    @Test
    public void testGivenDenseRandomProjectionWhenApplyingToMatrixThenOutputShapeIsCorrect() {
        // Given
        int n = 20, p = 5, m = 8;
        RandomProjection rp = RandomProjection.of(n, p);
        double[][] X = new double[m][n];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                X[i][j] = MathEx.random();

        // When
        double[][] Y = rp.apply(X);

        // Then
        assertEquals(m, Y.length);
        assertEquals(p, Y[0].length);
    }

    @Test
    public void testGivenSparseRandomProjectionWhenApplyingToVectorThenValuesAreFinite() {
        // Given
        int n = 30, p = 6;
        RandomProjection rp = RandomProjection.sparse(n, p);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = i - 15.0;

        // When
        double[] y = rp.apply(x);

        // Then
        assertEquals(p, y.length);
        for (double v : y) assertTrue(Double.isFinite(v));
    }
}