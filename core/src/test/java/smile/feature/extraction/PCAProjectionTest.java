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
import smile.datasets.USArrests;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void testGivenVarianceThresholdOf1WhenSelectingProjectionThenAllComponentsAreKept() throws Exception {
        // Given
        double[][] data = new USArrests().x();
        PCA pca = PCA.fit(data);

        // When — p=1.0 must not throw even if floating-point cumsum falls just below 1.0
        PCA full = pca.getProjection(1.0);

        // Then
        assertEquals(4, full.projection.nrow());
        assertEquals(4, full.projection.ncol());
    }

    @Test
    public void testGivenInvalidVariancePercentageWhenSelectingProjectionThenInputIsRejected() throws Exception {
        // Given
        double[][] data = new USArrests().x();
        PCA pca = PCA.fit(data);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> pca.getProjection(0.0));
        assertThrows(IllegalArgumentException.class, () -> pca.getProjection(1.1));
        assertThrows(IllegalArgumentException.class, () -> pca.getProjection(-0.5));
    }

    @Test
    public void testGivenFittedPcaWhenAccessingCenterAndVarianceThenMetadataIsConsistent() throws Exception {
        // Given
        double[][] data = new USArrests().x();

        // When
        PCA pca = PCA.fit(data).getProjection(4);

        // Then
        assertEquals(4, pca.center().size());
        assertEquals(4, pca.variance().size());
        assertEquals(4, pca.varianceProportion().size());
        assertEquals(4, pca.cumulativeVarianceProportion().size());

        // Proportions sum to 1.0
        double propSum = 0.0;
        for (int i = 0; i < 4; i++) propSum += pca.varianceProportion().get(i);
        assertEquals(1.0, propSum, 1E-9);

        // Cumulative is non-decreasing and ends at 1.0
        double[] cum = pca.cumulativeVarianceProportion().toArray(new double[0]);
        assertEquals(1.0, cum[cum.length - 1], 1E-9);
        for (int i = 1; i < cum.length; i++) {
            assertTrue(cum[i] >= cum[i - 1]);
        }
    }

    @Test
    public void testGivenInvalidComponentCountWhenSelectingProjectionByCountThenInputIsRejected() throws Exception {
        // Given
        double[][] data = new USArrests().x();
        PCA pca = PCA.fit(data).getProjection(4);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> pca.getProjection(0));
        assertThrows(IllegalArgumentException.class, () -> pca.getProjection(5));
    }

    @Test
    public void testGivenCorPcaWhenSelectingProjectionByThresholdThenProjectionIsValid() throws Exception {
        // Given
        double[][] data = new USArrests().x();
        PCA pca = PCA.cor(data).getProjection(4);

        // When
        PCA projected = pca.getProjection(0.9);

        // Then — correlation PCA on 4 features: first 2 components capture ~87% variance
        assertTrue(projected.projection.nrow() >= 1);
        assertTrue(projected.projection.nrow() <= 4);
        double[] projectedPoint = projected.apply(data[0]);
        for (double v : projectedPoint) {
            assertTrue(Double.isFinite(v));
        }
    }
}

