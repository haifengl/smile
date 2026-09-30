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
import smile.math.distance.EuclideanDistance;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Local Outlier Factor (LOF).
 *
 * @author Haifeng Li
 */
public class LOFTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(20260418);
    }

    /**
     * Tests multi-density dataset:
     * - Cluster 1: dense cluster centered at (0, 0), radius ~0.1
     * - Cluster 2: sparse cluster centered at (10, 10), radius ~2.0
     * - An outlier near Cluster 1 at (0.8, 0.8): distance to cluster 1 center is ~1.1,
     *   which is smaller than distances inside the sparse cluster!
     * - A global distance/density method would miss this outlier or misclassify the entire
     *   sparse cluster. LOF should recognize (0.8, 0.8) as an outlier because its local density
     *   is significantly lower than its neighbors in the dense cluster.
     */
    @Test
    public void testMultiDensityClusters() {
        int n1 = 60;
        int n2 = 60;
        double[][] data = new double[n1 + n2 + 1][2];

        // Dense cluster: std = 0.05
        for (int i = 0; i < n1; i++) {
            data[i][0] = MathEx.randn() * 0.05;
            data[i][1] = MathEx.randn() * 0.05;
        }

        // Sparse cluster: std = 1.5
        for (int i = 0; i < n2; i++) {
            data[n1 + i][0] = 10.0 + MathEx.randn() * 1.5;
            data[n1 + i][1] = 10.0 + MathEx.randn() * 1.5;
        }

        // Local outlier near dense cluster
        data[n1 + n2][0] = 0.8;
        data[n1 + n2][1] = 0.8;

        LOF<double[]> lof = LOF.fit(data, 15);
        double[] scores = lof.scores();

        assertEquals(data.length, scores.length);

        // Average LOF in both clusters should be close to 1.0 (typical range 0.9 - 1.3)
        double avgDenseLof = 0.0;
        for (int i = 0; i < n1; i++) {
            avgDenseLof += scores[i];
        }
        avgDenseLof /= n1;
        assertEquals(1.0, avgDenseLof, 0.35);

        double avgSparseLof = 0.0;
        for (int i = n1; i < n1 + n2; i++) {
            avgSparseLof += scores[i];
        }
        avgSparseLof /= n2;
        assertEquals(1.0, avgSparseLof, 0.35);

        // The outlier near the dense cluster should have a markedly higher LOF score than the cluster averages
        double outlierScore = scores[n1 + n2];
        assertTrue(outlierScore > 2.0, "Local outlier should have high LOF score: " + outlierScore);
        assertTrue(outlierScore > avgDenseLof * 1.8);
        assertTrue(outlierScore > avgSparseLof * 1.8);

        // Out-of-sample query scoring
        double testInlierScore = lof.score(new double[]{0.0, 0.0});
        double testOutlierScore = lof.score(new double[]{0.85, 0.85});
        assertTrue(testInlierScore < 1.5, "Query inlier should have low LOF: " + testInlierScore);
        assertTrue(testOutlierScore > 2.0, "Query outlier should have high LOF: " + testOutlierScore);

        // Prediction with threshold
        assertTrue(lof.predict(new double[]{0.85, 0.85}, 1.8));
        assertFalse(lof.predict(new double[]{0.0, 0.0}, 1.8));
    }

    @Test
    public void testGenericMetricSpace() {
        double[][] data = {
                {0.0, 0.0}, {0.1, 0.0}, {0.0, 0.1}, {-0.1, 0.0}, {0.0, -0.1},
                {0.05, 0.05}, {-0.05, -0.05}, {0.05, -0.05}, {-0.05, 0.05},
                {5.0, 5.0} // outlier
        };

        LOF<double[]> lof = LOF.fit(data, new EuclideanDistance(), 4);
        double[] scores = lof.scores();

        assertEquals(10, scores.length);
        // The last point is a clear outlier
        assertTrue(scores[9] > scores[0]);
        assertTrue(scores[9] > 2.0);
    }

    @Test
    public void testDuplicatePoints() {
        double[][] data = {
                {0.0, 0.0}, {0.0, 0.0}, {0.0, 0.0}, // duplicates
                {0.1, 0.1}, {-0.1, -0.1},
                {10.0, 10.0}
        };

        LOF<double[]> lof = LOF.fit(data, 3);
        double[] scores = lof.scores();

        for (double s : scores) {
            assertTrue(Double.isFinite(s), "Scores should be finite even with duplicate points: " + s);
            assertTrue(s > 0.0);
        }
    }

    @Test
    public void testBatchScoring() {
        double[][] train = {
                {0.0, 0.0}, {0.1, 0.0}, {0.0, 0.1}, {-0.1, 0.0}, {0.0, -0.1},
                {0.05, 0.05}, {-0.05, -0.05}
        };

        LOF<double[]> lof = LOF.fit(train, 3);

        double[][] queries = {
                {0.0, 0.02},
                {10.0, 10.0}
        };

        double[] batchScores = lof.score(queries);
        assertEquals(2, batchScores.length);
        assertEquals(lof.score(queries[0]), batchScores[0], 1E-10);
        assertEquals(lof.score(queries[1]), batchScores[1], 1E-10);
        assertTrue(batchScores[1] > batchScores[0]);
    }

    @Test
    public void testOptionsAndSerialization() throws Exception {
        LOF.Options options = new LOF.Options(4);
        Properties props = options.toProperties();
        LOF.Options restoredOpts = LOF.Options.of(props);
        assertEquals(4, restoredOpts.k());

        double[][] data = {
                {0.0, 0.0}, {0.1, 0.0}, {0.0, 0.1}, {-0.1, 0.0}, {0.0, -0.1},
                {0.05, 0.05}, {-0.05, -0.05}
        };
        LOF<double[]> model = LOF.fit(data, options);

        Path temp = Write.object(model);
        Object restored = Read.object(temp);

        assertNotNull(restored);
        assertInstanceOf(LOF.class, restored);
        @SuppressWarnings("unchecked")
        LOF<double[]> lofRestored = (LOF<double[]>) restored;
        assertEquals(model.k(), lofRestored.k());
        assertEquals(model.score(new double[]{0.0, 0.0}), lofRestored.score(new double[]{0.0, 0.0}), 1E-10);
    }

    @Test
    public void testInvalidParameters() {
        double[][] data = {{0.0, 0.0}, {1.0, 1.0}};
        assertThrows(IllegalArgumentException.class, () -> LOF.fit(data, 0));
        assertThrows(IllegalArgumentException.class, () -> LOF.fit(data, 2)); // k must be < n
        assertThrows(IllegalArgumentException.class, () -> LOF.fit(null));
        assertThrows(IllegalArgumentException.class, () -> LOF.fit(new double[0][]));
    }
}
