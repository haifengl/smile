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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct coverage for PCA projection-selection behavior.
 */
public class PCAProjectionTest {
    private static final double TOLERANCE = 1E-10;

    @Test
    public void testGivenWideMatrixWhenSelectingProjectionByVarianceThresholdThenAtLeastOneComponentIsKept() {
        // Given
        double[][] data = {
                {3.0, 0.0, 0.0},
                {-3.0, 0.0, 0.0}
        };

        // When
        PCA pca = PCA.fit(data);
        PCA projection = pca.getProjection(0.5);
        double[] projectedCenter = projection.apply(new double[] {0.0, 0.0, 0.0});
        double[] projectedSample = projection.apply(data[0]);

        // Then
        assertEquals(1, projection.projection.nrow());
        assertEquals(3, projection.projection.ncol());
        assertEquals(0.0, projectedCenter[0], TOLERANCE);
        assertTrue(Double.isFinite(projectedSample[0]));
    }
}

