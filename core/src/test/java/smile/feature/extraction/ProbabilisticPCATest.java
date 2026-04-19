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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@link ProbabilisticPCA}.
 */
public class ProbabilisticPCATest {
    private static final double TOLERANCE = 1E-8;

    @Test
    public void testGivenThreeFeatureDataWhenFittingProbabilisticPcaThenLatentProjectionIsFiniteAndCenteredMeanMapsToZero() {
        // Given
        double[][] data = {
                {1.0, 2.0, 1.0},
                {2.0, 1.0, 4.0},
                {3.0, 4.0, 2.0},
                {4.0, 3.0, 5.0}
        };

        // When
        ProbabilisticPCA ppca = ProbabilisticPCA.fit(data, 1);
        double[] projectedCenter = ppca.apply(new double[] {
                ppca.center().get(0),
                ppca.center().get(1),
                ppca.center().get(2)
        });
        double[] projectedSample = ppca.apply(data[0]);

        // Then
        assertEquals(3, ppca.center().size());
        assertEquals(3, ppca.loadings().nrow());
        assertEquals(1, ppca.loadings().ncol());
        assertEquals(1, ppca.projection.nrow());
        assertEquals(3, ppca.projection.ncol());
        assertTrue(Double.isFinite(ppca.variance()));
        assertTrue(ppca.variance() >= 0.0);
        assertEquals(0.0, projectedCenter[0], TOLERANCE);
        assertTrue(Double.isFinite(projectedSample[0]));
    }

    @Test
    public void testGivenInvalidPrincipalComponentCountsWhenFittingProbabilisticPcaThenInputIsRejected() {
        // Given
        double[][] data = {
                {1.0, 2.0, 1.0},
                {2.0, 1.0, 4.0},
                {3.0, 4.0, 2.0},
                {4.0, 3.0, 5.0}
        };

        // When / Then
        assertEquals("Invalid number of principal components: 0",
                assertThrows(IllegalArgumentException.class, () -> ProbabilisticPCA.fit(data, 0)).getMessage());
        assertEquals("Invalid number of principal components: 3",
                assertThrows(IllegalArgumentException.class, () -> ProbabilisticPCA.fit(data, 3)).getMessage());
        assertEquals("Invalid number of principal components: 4",
                assertThrows(IllegalArgumentException.class, () -> ProbabilisticPCA.fit(data, 4)).getMessage());
    }
}

