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
package smile.clustering;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Clustering.run() and Clustering.Options.
 */
class ClusteringUnitTest {

    private static final double[][] DATA = {
            {0.0}, {0.1}, {5.0}, {5.1}
    };

    // ── Clustering.run ────────────────────────────────────────────────────────

    @Test
    void givenMultipleRuns_whenRun_thenReturnsBestResult() {
        MathEx.setSeed(42);
        CentroidClustering<double[], double[]> best =
                Clustering.run(5, () -> KMeans.fit(DATA, 2, 100));

        assertNotNull(best);
        assertTrue(Double.isFinite(best.distortion()));
        assertTrue(best.distortion() >= 0.0);
        assertEquals(2, best.k());
    }

    @Test
    void givenRunEqualsOne_whenRun_thenReturnsSingleResult() {
        MathEx.setSeed(42);
        CentroidClustering<double[], double[]> result =
                Clustering.run(1, () -> KMeans.fit(DATA, 2, 100));

        assertNotNull(result);
    }

    @Test
    void givenZeroRuns_whenRun_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Clustering.run(0, () -> KMeans.fit(DATA, 2, 10)));
    }

    @Test
    void givenNegativeRuns_whenRun_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Clustering.run(-1, () -> KMeans.fit(DATA, 2, 10)));
    }

    // ── Clustering.Options validation ─────────────────────────────────────────

    @Test
    void givenKLessThanTwo_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Clustering.Options(1, 100));
        assertThrows(IllegalArgumentException.class, () -> new Clustering.Options(0, 100));
    }

    @Test
    void givenNonPositiveMaxIter_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Clustering.Options(2, 0));
        assertThrows(IllegalArgumentException.class, () -> new Clustering.Options(2, -1));
    }

    @Test
    void givenNegativeTol_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Clustering.Options(2, 100, -1E-4, null));
    }

    @Test
    void givenZeroTol_whenConstructingOptions_thenSucceeds() {
        // tol = 0.0 is a valid (exact convergence) setting
        assertDoesNotThrow(() -> new Clustering.Options(2, 100, 0.0, null));
    }

    // ── Clustering.Options round-trip ─────────────────────────────────────────

    @Test
    void givenOptions_whenRoundTripped_thenAllValuesPreserved() {
        Clustering.Options opts = new Clustering.Options(7, 250, 1E-6, null);
        Properties props = opts.toProperties();
        Clustering.Options restored = Clustering.Options.of(props);

        assertEquals(7, restored.k());
        assertEquals(250, restored.maxIter());
        assertEquals(1E-6, restored.tol(), 1E-15);
        assertNull(restored.controller());
    }

    @Test
    void givenMissingProperties_whenRestoringOptions_thenDefaultsAreApplied() {
        Clustering.Options restored = Clustering.Options.of(new Properties());

        assertEquals(2, restored.k());
        assertEquals(100, restored.maxIter());
        assertEquals(1E-4, restored.tol(), 1E-15);
    }
}
