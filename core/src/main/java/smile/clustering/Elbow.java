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

import java.nio.DoubleBuffer;
import java.util.Arrays;
import smile.math.MathEx;

/**
 * Elbow method to select the number of clusters of K-Means. K-Means is
 * fitted for each k in {@code [minK, maxK]}, and the elbow of the resulting
 * distortion curve is detected automatically: the point where adding more
 * clusters stops paying off.
 * <p>
 * Following the Kneedle algorithm, the distortion curve is normalized to
 * the unit square, and the elbow is the point farthest below the line
 * between the points of {@code minK} and {@code maxK}. This approximates
 * the point of maximum curvature. The sensitivity threshold of the original
 * paper, which only matters for curves with several local maxima, is
 * omitted for simplicity.
 * <p>
 * The elbow is a heuristic of diminishing returns, not an estimate of the
 * true number of clusters. It is best used as an additional check next to
 * a principled criterion such as BIC ({@link XMeans}, {@link GMeans}), or
 * to discretize data without a natural cluster structure, e.g. splitting
 * continuous weather measurements into a few categories such as dry, damp
 * and wet, where the question is how many levels are worth the extra
 * granularity. Be aware of its limitations:
 * <ul>
 * <li>Distortion curves look similar for well-separated clusters, uniform
 *     noise and a single Gaussian, so the elbow cannot tell them apart.</li>
 * <li>The result may change with {@code maxK}, because the curve is
 *     normalized by its observed range. Keep the range fixed when
 *     comparing results.</li>
 * <li>It never concludes that the data has no clusters.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li>V. Satopää, J. Albrecht, D. Irwin, and B. Raghavan. Finding a
 *     "Kneedle" in a Haystack: Detecting Knee Points in System Behavior.
 *     ICDCS Workshops, 2011.</li>
 * <li>E. Schubert. Stop using the elbow criterion for k-means and how to
 *     choose the number of clusters instead. ACM SIGKDD Explorations
 *     25(1), 36-42, 2023.</li>
 * </ol>
 *
 * @author Mikita Tsybulka
 */
public final class Elbow {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Elbow.class);

    /** The number of K-Means runs per k, of which the best one is kept. */
    private static final int RUNS = 5;
    /** The maximum number of K-Means iterations. */
    private static final int MAX_ITER = 100;

    /** Constructor. */
    private Elbow() {

    }

    /**
     * Selects the number of clusters of K-Means by the elbow method.
     *
     * @param data the input data of which each row is an observation.
     * @param minK the minimum number of clusters, at least 2.
     * @param maxK the maximum number of clusters, at least {@code minK + 2}
     *             and at most the number of distinct observations.
     * @return the selected number of clusters.
     */
    public static int fit(double[][] data, int minK, int maxK) {
        KMeans.validateData(data, false);
        if (minK < 2) {
            throw new IllegalArgumentException("Invalid minimum number of clusters: " + minK);
        }
        if (maxK < minK + 2) {
            throw new IllegalArgumentException(String.format(
                    "At least 3 values of k are required: minK = %d, maxK = %d", minK, maxK));
        }
        long distinct = Arrays.stream(data).map(DoubleBuffer::wrap).distinct().limit(maxK).count();
        if (maxK > distinct) {
            throw new IllegalArgumentException(String.format(
                    "Maximum number of clusters %d cannot be greater than the number of distinct observations %d.",
                    maxK, distinct));
        }

        // The BBD-tree only depends on the data, so build it once
        // instead of in every K-Means call.
        BBDTree bbd = new BBDTree(data);
        double[] distortions = new double[maxK - minK + 1];
        for (int k = minK; k <= maxK; k++) {
            var options = new Clustering.Options(k, MAX_ITER);
            var model = Clustering.run(RUNS, () -> KMeans.fit(bbd, data, options));
            distortions[k - minK] = model.distortion();
            logger.info("k = {}: distortion = {}", k, distortions[k - minK]);
        }

        int k = minK + knee(distortions);
        logger.info("Elbow method selected k = {}", k);
        return k;
    }

    /**
     * Returns the index of the elbow of a distortion curve, which is convex
     * and decreasing, with the values for consecutive numbers of clusters.
     * After normalizing the curve to the unit square, the elbow is the point
     * farthest below the line from (0, 1) to (1, 0), which connects the
     * first and the last points when the curve is monotone.
     *
     * @param distortions the distortions for consecutive numbers of clusters.
     * @return the index of the elbow.
     */
    public static int knee(double[] distortions) {
        int n = distortions.length;
        if (n < 3) {
            throw new IllegalArgumentException("At least 3 points are required: " + n);
        }
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(distortions[i])) {
                throw new IllegalArgumentException(String.format("Invalid distortion at %d: %s", i, distortions[i]));
            }
        }

        double min = MathEx.min(distortions);
        double range = MathEx.max(distortions) - min;
        if (range == 0.0) {
            return 0;
        }

        // In the unit square, the line from (0, 1) to (1, 0) is x + y = 1,
        // so 1 - x - y is proportional to the distance below the line.
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = 1.0 - (distortions[i] - min) / range - (double) i / (n - 1);
        }

        return MathEx.whichMax(d);
    }
}
