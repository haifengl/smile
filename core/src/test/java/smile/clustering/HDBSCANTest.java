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

import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class HDBSCANTest {

    @Test
    public void testTwoBlobsWithNoise() {
        // Given
        double[][] x = {
                {-0.10, 0.00}, {0.05, 0.02}, {0.00, -0.08}, {0.08, 0.12}, {-0.06, 0.10},
                {4.90, 5.10}, {5.10, 4.95}, {4.95, 4.85}, {5.12, 5.08}, {4.82, 5.02},
                {10.0, 0.0}, {-6.0, -6.0}
        };

        // When
        HDBSCAN<double[]> model = HDBSCAN.fit(x, 3, 3);

        // Then
        assertTrue(model.k() >= 2);
        int[] group = model.group();
        assertEquals(group[0], group[1]);
        assertEquals(group[0], group[2]);
        assertEquals(group[5], group[6]);
        assertEquals(group[5], group[7]);
        assertNotEquals(group[0], group[5]);
        assertEquals(Clustering.OUTLIER, group[10]);
        assertEquals(Clustering.OUTLIER, group[11]);
    }

    @Test
    public void testCoreDistanceAndStabilityShapes() {
        // Given
        double[][] x = {
                {0.0, 0.0}, {0.1, 0.0}, {0.0, 0.1}, {5.0, 5.0}, {5.1, 5.1}, {5.0, 5.1}
        };

        // When
        HDBSCAN<double[]> model = HDBSCAN.fit(x, 2, 2);

        // Then
        assertEquals(x.length, model.coreDistance().length);
        assertEquals(model.k(), model.stability().length);
        assertTrue(Arrays.stream(model.coreDistance()).allMatch(d -> d >= 0.0 && Double.isFinite(d)));
    }

    @Test
    public void testInvalidArguments() {
        // Given
        double[][] x = {{0.0, 0.0}, {1.0, 1.0}};

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> HDBSCAN.fit(new double[][] {{0.0, 0.0}}, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> HDBSCAN.fit(x, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> HDBSCAN.fit(x, 2, 1));
    }

    @Test
    public void testOptionsRoundTrip() {
        // Given
        HDBSCAN.Options options = new HDBSCAN.Options(7, 11);

        // When
        Properties props = options.toProperties();
        HDBSCAN.Options copy = HDBSCAN.Options.of(props);

        // Then
        assertEquals(7, copy.minPoints());
        assertEquals(11, copy.minClusterSize());
    }
}
