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

import java.util.List;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
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

    @Test
    public void testGivenDataFrameWhenFittingProbabilisticPcaThenResultMatchesArrayOverload() {
        // Given
        StructType schema = new StructType(
                new StructField("x1", DataTypes.DoubleType),
                new StructField("x2", DataTypes.DoubleType),
                new StructField("x3", DataTypes.DoubleType)
        );
        double[][] raw = {
                {1.0, 2.0, 1.0},
                {2.0, 1.0, 4.0},
                {3.0, 4.0, 2.0},
                {4.0, 3.0, 5.0}
        };
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{raw[0][0], raw[0][1], raw[0][2]}),
                Tuple.of(schema, new Object[]{raw[1][0], raw[1][1], raw[1][2]}),
                Tuple.of(schema, new Object[]{raw[2][0], raw[2][1], raw[2][2]}),
                Tuple.of(schema, new Object[]{raw[3][0], raw[3][1], raw[3][2]})
        ));

        // When
        ProbabilisticPCA fromArray = ProbabilisticPCA.fit(raw, 1);
        ProbabilisticPCA fromFrame = ProbabilisticPCA.fit(data, 1, "x1", "x2", "x3");

        // Then — both paths produce identical noise variance and center
        assertEquals(fromArray.variance(), fromFrame.variance(), TOLERANCE);
        for (int i = 0; i < 3; i++) {
            assertEquals(fromArray.center().get(i), fromFrame.center().get(i), TOLERANCE);
        }
    }

    @Test
    public void testGivenMultiplePrincipalComponentsWhenFittingPpCaThenProjectionDimensionMatches() {
        // Given
        double[][] data = new double[20][5];
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 5; j++) {
                data[i][j] = (i + 1) * (j + 1) * 0.1 + (i % 3) * 0.01;
            }
        }

        // When
        ProbabilisticPCA ppca2 = ProbabilisticPCA.fit(data, 2);
        ProbabilisticPCA ppca3 = ProbabilisticPCA.fit(data, 3);

        // Then
        assertEquals(2, ppca2.projection.nrow());
        assertEquals(5, ppca2.projection.ncol());
        assertEquals(5, ppca2.center().size());
        assertEquals(5, ppca2.loadings().nrow());
        assertEquals(2, ppca2.loadings().ncol());

        assertEquals(3, ppca3.projection.nrow());
        assertEquals(5, ppca3.projection.ncol());

        // Noise variance may be numerically near-zero; allow tiny negative margin
        assertTrue(ppca2.variance() >= -1E-9);

        // Projected center should map to zero (PPCA is mean-centered)
        double[] mu2 = ppca2.center().toArray(new double[0]);
        double[] projCenter2 = ppca2.apply(mu2);
        for (double v : projCenter2) {
            assertEquals(0.0, v, TOLERANCE);
        }
    }
}

