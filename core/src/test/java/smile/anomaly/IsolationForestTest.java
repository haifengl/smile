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
package smile.anomaly;

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class IsolationForestTest {
    @BeforeEach
    public void setUp() {
        MathEx.setSeed(20260418);
    }

    @Test
    public void givenSyntheticData_whenScoring_thenOutlierHasHigherScore() {
        // Given
        double[][] data = {
                {0.00, 0.01}, {0.10, -0.10}, {-0.05, 0.05}, {0.02, -0.03},
                {0.08, 0.02}, {-0.07, -0.01}, {0.01, 0.09}, {-0.09, 0.00}
        };
        IsolationForest model = IsolationForest.fit(data, new IsolationForest.Options(128, 0, 0.75, 0));

        // When
        double inlierScore = model.score(new double[]{0.02, 0.02});
        double outlierScore = model.score(new double[]{6.0, -6.0});

        // Then
        assertTrue(inlierScore > 0.0 && inlierScore <= 1.0);
        assertTrue(outlierScore > 0.0 && outlierScore <= 1.0);
        assertTrue(outlierScore > inlierScore);
    }

    @Test
    public void givenModel_whenSerializing_thenRoundTripIsReadable() throws Exception {
        // Given
        double[][] data = {
                {0.0, 0.0}, {0.1, 0.0}, {0.0, 0.1}, {-0.1, 0.0}, {0.0, -0.1}
        };
        IsolationForest model = IsolationForest.fit(data);

        // When
        Path temp = Write.object(model);
        Object restored = Read.object(temp);

        // Then
        assertNotNull(restored);
        assertInstanceOf(IsolationForest.class, restored);
    }

    @Test
    public void givenOptions_whenRoundTripToProperties_thenValuesPreserved() {
        // Given
        IsolationForest.Options options = new IsolationForest.Options(200, 15, 0.6, 1);

        // When
        Properties props = options.toProperties();
        IsolationForest.Options restored = IsolationForest.Options.of(props);

        // Then
        assertEquals(options, restored);
    }

    @Test
    public void givenInvalidInputs_whenFittingOrScoring_thenThrowsIllegalArgumentException() {
        // Given
        double[][] data = {{0.0, 0.0}, {1.0, 1.0}};
        IsolationForest model = IsolationForest.fit(data);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> IsolationForest.fit(new double[][]{{0.0, 0.0}}));
        assertThrows(IllegalArgumentException.class, () -> IsolationForest.fit(new double[][]{{0.0, 0.0}, {1.0}}));
        assertThrows(IllegalArgumentException.class, () -> IsolationForest.fit(data, new IsolationForest.Options(10, 0, 0.7, 2)));
        assertThrows(IllegalArgumentException.class, () -> model.score(new double[]{1.0}));
        assertThrows(IllegalArgumentException.class, () -> model.score((double[]) null));
    }

    @Test
    public void givenTreesAccessor_whenCalled_thenReturnsDefensiveCopy() {
        // Given
        double[][] data = {{0.0, 0.0}, {1.0, 1.0}, {0.5, 0.5}};
        IsolationForest model = IsolationForest.fit(data);

        // When
        IsolationTree[] trees1 = model.trees();
        IsolationTree[] trees2 = model.trees();

        // Then
        assertNotSame(trees1, trees2);
        assertEquals(model.size(), trees1.length);
        assertEquals(model.size(), trees2.length);
    }

    @Test
    public void givenExtensionLevel_whenFitting_thenModelUsesRequestedSemantics() {
        // Given
        double[][] data = {
                {0.0, 0.0, 0.0},
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        };

        // When
        IsolationForest standard = IsolationForest.fit(data, new IsolationForest.Options(32, 0, 0.75, 0));
        IsolationForest extended = IsolationForest.fit(data, new IsolationForest.Options(32, 0, 0.75, 1));

        // Then
        assertEquals(0, standard.extensionLevel());
        assertEquals(1, extended.extensionLevel());
    }

    @Test
    public void givenThreshold_whenPredict_thenClassifiesCorrectly() {
        // Given
        double[][] data = {
                {0.00, 0.01}, {0.10, -0.10}, {-0.05, 0.05}, {0.02, -0.03},
                {0.08, 0.02}, {-0.07, -0.01}, {0.01, 0.09}, {-0.09, 0.00}
        };
        IsolationForest model = IsolationForest.fit(data, new IsolationForest.Options(128, 0, 0.75, 0));

        // When / Then – a far-away point should be predicted anomalous at a lenient threshold
        assertFalse(model.predict(new double[]{0.02, 0.02}, 0.8));
        assertTrue(model.predict(new double[]{6.0, -6.0}, 0.5));
    }

    @Test
    public void givenBatchData_whenScoringBatch_thenResultsMatchSingleScores() {
        // Given
        double[][] data = {
                {0.0, 0.0}, {1.0, 0.0}, {0.0, 1.0}, {-1.0, 0.0}, {0.0, -1.0},
                {0.5, 0.5}, {-0.5, -0.5}, {0.5, -0.5}
        };
        IsolationForest model = IsolationForest.fit(data, new IsolationForest.Options(64, 0, 0.75, 0));
        double[][] samples = {{0.1, 0.1}, {5.0, 5.0}};

        // When
        double[] batch = model.score(samples);

        // Then
        assertEquals(2, batch.length);
        assertEquals(model.score(samples[0]), batch[0], 1e-12);
        assertEquals(model.score(samples[1]), batch[1], 1e-12);
    }
}
