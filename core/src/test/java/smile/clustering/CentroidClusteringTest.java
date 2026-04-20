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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CentroidClustering record methods.
 */
class CentroidClusteringTest {

    /** Two tightly separated 2-D clusters with known geometry. */
    private static final double[][] DATA = {
            {0.0, 0.0}, {0.0, 1.0},   // cluster near (0, 0.5)
            {10.0, 0.0}, {10.0, 1.0}  // cluster near (10, 0.5)
    };

    private CentroidClustering<double[], double[]> model;

    @BeforeEach
    void setUp() {
        MathEx.setSeed(42);
        model = KMeans.fit(DATA, 2, 100);
    }

    @Test
    void givenKMeansFit_whenK_thenReturnsNumberOfClusters() {
        assertEquals(2, model.k());
    }

    @Test
    void givenPerfectClusters_whenRadius_thenReturnsRMSDistanceToCentroid() {
        // Each cluster has two points at distance 0.5 from the centroid.
        // mean squared distance = 0.25, so RMS radius = sqrt(0.25) = 0.5.
        assertEquals(0.5, model.radius(0), 1E-10);
        assertEquals(0.5, model.radius(1), 1E-10);
    }

    @Test
    void givenTrainedModel_whenDistortion_thenReturnsNonNegativeFiniteValue() {
        double d = model.distortion();
        assertTrue(Double.isFinite(d));
        assertTrue(d >= 0.0);
    }

    @Test
    void givenTrainedModel_whenPredict_thenClosePointsShareLabel() {
        // Points close to (0,0) should predict the same cluster.
        int nearOrigin = model.predict(new double[]{0.1, 0.1});
        assertEquals(model.group(0), nearOrigin,
                "Point near (0,0) should predict the same cluster as training point (0,0)");

        // Points close to (10,1) should predict the same cluster.
        int nearTen = model.predict(new double[]{9.9, 0.9});
        assertEquals(model.group(2), nearTen,
                "Point near (10,1) should predict the same cluster as training point (10,0)");

        assertNotEquals(nearOrigin, nearTen,
                "Points from different clusters should predict different labels");
    }

    @Test
    void givenTwoModelsWithDifferentDistortions_whenCompareTo_thenOrdersByDistortion() {
        // A model with 4 clusters (each point its own) has distortion 0.
        MathEx.setSeed(42);
        CentroidClustering<double[], double[]> fine = KMeans.fit(DATA, 4, 100);
        CentroidClustering<double[], double[]> coarse = KMeans.fit(DATA, 2, 100);

        // 4 clusters should have smaller-or-equal distortion than 2 clusters.
        assertTrue(fine.distortion() <= coarse.distortion());
        assertTrue(fine.compareTo(coarse) <= 0);
        assertTrue(coarse.compareTo(fine) >= 0);
    }

    @Test
    void givenTrainedModel_whenToString_thenContainsNameAndTotalLine() {
        String s = model.toString();
        assertTrue(s.contains("K-Means"), "toString should contain the algorithm name");
        assertTrue(s.contains("Total"), "toString should contain a 'Total' summary row");
    }

    @Test
    void givenTrainedModel_whenSizeAndGroup_thenSumToDataLength() {
        int total = 0;
        for (int i = 0; i < model.k(); i++) {
            total += model.size(i);
        }
        assertEquals(DATA.length, total, "Sum of cluster sizes should equal the data size");

        int[] group = model.group();
        assertEquals(DATA.length, group.length);
        for (int g : group) {
            assertTrue(g >= 0 && g < model.k(), "Each label must be in [0, k)");
        }
    }
}
