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
package smile.manifold;

import java.util.Arrays;
import java.util.Properties;
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Supplemental tests for the smile.manifold package.
 * Covers:
 * <ul>
 *   <li>Input validation (bad arguments)</li>
 *   <li>Options round-trips ({@code toProperties()} / {@code of()})</li>
 *   <li>Output structural invariants (correct shape, finite values, etc.)</li>
 *   <li>Edge cases</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public class ManifoldOptionsTest {

    // -----------------------------------------------------------------------
    // Small synthetic datasets used across multiple tests
    // -----------------------------------------------------------------------

    /** A small symmetric proximity / distance matrix (5 × 5). */
    static double[][] proximity5() {
        return new double[][]{
                {0, 1, 2, 3, 4},
                {1, 0, 1, 2, 3},
                {2, 1, 0, 1, 2},
                {3, 2, 1, 0, 1},
                {4, 3, 2, 1, 0}
        };
    }

    /** A tiny grid of 2-D points (20 points on a simple plane). */
    static double[][] grid20() {
        double[][] pts = new double[20][3];
        int idx = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                pts[idx++] = new double[]{i, j, 0};
            }
        }
        return pts;
    }

    // ======================================================================
    // IsoMap
    // ======================================================================

    @Test
    public void givenKLessThan2_whenIsoMapOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new IsoMap.Options(1, 2, true));
    }

    @Test
    public void givenDLessThan2_whenIsoMapOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new IsoMap.Options(5, 1, true));
    }

    @Test
    public void givenValidOptions_whenIsoMapOptionsRoundTrip_thenValuesPreserved() {
        // Given
        IsoMap.Options opts = new IsoMap.Options(7, 3, false);
        // When
        Properties props = opts.toProperties();
        IsoMap.Options restored = IsoMap.Options.of(props);
        // Then
        assertEquals(opts.k(), restored.k());
        assertEquals(opts.d(), restored.d());
        assertEquals(opts.conformal(), restored.conformal());
    }

    @Test
    public void givenDefaultProperties_whenIsoMapOptionsOf_thenDefaultsApplied() {
        // Given – empty Properties
        IsoMap.Options opts = IsoMap.Options.of(new Properties());
        // Then defaults: k=7, d=2, conformal=true
        assertEquals(7, opts.k());
        assertEquals(2, opts.d());
        assertTrue(opts.conformal());
    }

    @Test
    public void givenSmallGrid_whenIsoMapFit_thenOutputHasCorrectShape() {
        // Given
        double[][] data = grid20();
        IsoMap.Options opts = new IsoMap.Options(4, 2, true);
        // When
        double[][] coords = IsoMap.fit(data, opts);
        // Then: coords.length == n (or largest connected component), coords[i].length == 2
        assertTrue(coords.length > 0 && coords.length <= data.length);
        assertEquals(2, coords[0].length);
        // All values must be finite
        for (double[] row : coords) {
            for (double v : row) {
                assertTrue(Double.isFinite(v), "Non-finite coordinate: " + v);
            }
        }
    }

    // ======================================================================
    // LLE
    // ======================================================================

    @Test
    public void givenKLessThan2_whenLLEOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new LLE.Options(1));
    }

    @Test
    public void givenDLessThan2_whenLLEOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new LLE.Options(5, 1));
    }

    @Test
    public void givenValidOptions_whenLLEOptionsRoundTrip_thenValuesPreserved() {
        LLE.Options opts = new LLE.Options(10, 3);
        LLE.Options restored = LLE.Options.of(opts.toProperties());
        assertEquals(opts.k(), restored.k());
        assertEquals(opts.d(), restored.d());
    }

    @Test
    public void givenDefaultProperties_whenLLEOptionsOf_thenDefaultsApplied() {
        LLE.Options opts = LLE.Options.of(new Properties());
        assertEquals(7, opts.k());
        assertEquals(2, opts.d());
    }

    @Test
    public void givenSmallGrid_whenLLEFit_thenOutputHasCorrectShape() {
        double[][] data = grid20();
        LLE.Options opts = new LLE.Options(4, 2);
        double[][] coords = LLE.fit(data, opts);
        assertTrue(coords.length > 0 && coords.length <= data.length);
        assertEquals(2, coords[0].length);
        for (double[] row : coords) {
            for (double v : row) {
                assertTrue(Double.isFinite(v));
            }
        }
    }

    // ======================================================================
    // LaplacianEigenmap
    // ======================================================================

    @Test
    public void givenKLessThan2_whenLaplacianEigenmapOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new LaplacianEigenmap.Options(1));
    }

    @Test
    public void givenDLessThan2_whenLaplacianEigenmapOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new LaplacianEigenmap.Options(5, 1, 1.0));
    }

    @Test
    public void givenValidOptions_whenLaplacianEigenmapOptionsRoundTrip_thenValuesPreserved() {
        LaplacianEigenmap.Options opts = new LaplacianEigenmap.Options(6, 2, 2.5);
        LaplacianEigenmap.Options restored = LaplacianEigenmap.Options.of(opts.toProperties());
        assertEquals(opts.k(), restored.k());
        assertEquals(opts.d(), restored.d());
        assertEquals(opts.t(), restored.t(), 1e-12);
    }

    @Test
    public void givenDefaultProperties_whenLaplacianEigenmapOptionsOf_thenDefaultsApplied() {
        LaplacianEigenmap.Options opts = LaplacianEigenmap.Options.of(new Properties());
        assertEquals(7, opts.k());
        assertEquals(2, opts.d());
        assertEquals(-1.0, opts.t(), 1e-12);
    }

    @Test
    public void givenSmallGridDiscrete_whenLaplacianEigenmapFit_thenOutputHasCorrectShape() {
        // t <= 0 → discrete weights
        double[][] data = grid20();
        LaplacianEigenmap.Options opts = new LaplacianEigenmap.Options(4, 2, -1);
        double[][] coords = LaplacianEigenmap.fit(data, opts);
        assertTrue(coords.length > 0 && coords.length <= data.length);
        assertEquals(2, coords[0].length);
    }

    @Test
    public void givenSmallGridGaussian_whenLaplacianEigenmapFit_thenOutputHasCorrectShape() {
        // t > 0 → Gaussian kernel weights
        double[][] data = grid20();
        LaplacianEigenmap.Options opts = new LaplacianEigenmap.Options(4, 2, 1.0);
        double[][] coords = LaplacianEigenmap.fit(data, opts);
        assertTrue(coords.length > 0 && coords.length <= data.length);
        assertEquals(2, coords[0].length);
    }

    // ======================================================================
    // MDS
    // ======================================================================

    @Test
    public void givenDLessThan2_whenMDSOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MDS.Options(1, false));
    }

    @Test
    public void givenNonSquareMatrix_whenMDSFit_thenThrowsIllegalArgumentException() {
        // Given: 3×4 proximity matrix
        double[][] notSquare = {{0, 1, 2, 3}, {1, 0, 1, 2}, {2, 1, 0, 1}};
        assertThrows(IllegalArgumentException.class, () -> MDS.fit(notSquare));
    }

    @Test
    public void givenValidOptions_whenMDSOptionsRoundTrip_thenValuesPreserved() {
        MDS.Options opts = new MDS.Options(3, true);
        MDS.Options restored = MDS.Options.of(opts.toProperties());
        assertEquals(opts.d(), restored.d());
        assertEquals(opts.positive(), restored.positive());
    }

    @Test
    public void givenDefaultProperties_whenMDSOptionsOf_thenDefaultsApplied() {
        MDS.Options opts = MDS.Options.of(new Properties());
        assertEquals(2, opts.d());
        assertFalse(opts.positive());
    }

    @Test
    public void givenProximityMatrix_whenMDSFit_thenOutputShapeCorrect() {
        // Given
        double[][] prox = proximity5();
        // When
        MDS mds = MDS.fit(prox);
        // Then
        assertEquals(5, mds.coordinates().length);
        assertEquals(2, mds.coordinates()[0].length);
        assertEquals(2, mds.scores().length);
        assertEquals(2, mds.proportion().length);
        // Proportions should sum to <= 1.0 (only top-d eigenvalues)
        double propSum = Arrays.stream(mds.proportion()).sum();
        assertTrue(propSum <= 1.0 + 1e-9, "Proportion sum > 1: " + propSum);
        // Eigenvalues should be non-negative
        for (double s : mds.scores()) {
            assertTrue(s >= 0, "Negative eigenvalue: " + s);
        }
    }

    @Test
    public void givenProximityMatrix_whenMDSFitPositive_thenOutputShapeCorrect() {
        double[][] prox = proximity5();
        MDS mds = MDS.fit(prox, new MDS.Options(2, true));
        assertEquals(5, mds.coordinates().length);
        assertEquals(2, mds.coordinates()[0].length);
    }

    // ======================================================================
    // IsotonicMDS
    // ======================================================================

    @Test
    public void givenDLessThan2_whenIsotonicMDSOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new IsotonicMDS.Options(1, 1e-4, 200));
    }

    @Test
    public void givenNonSquareMatrix_whenIsotonicMDSFit_thenThrowsIllegalArgumentException() {
        double[][] notSquare = {{0, 1, 2, 3}, {1, 0, 1, 2}, {2, 1, 0, 1}};
        assertThrows(IllegalArgumentException.class, () -> IsotonicMDS.fit(notSquare));
    }

    @Test
    public void givenSizeMismatch_whenIsotonicMDSFitWithInit_thenThrowsIllegalArgumentException() {
        double[][] prox = proximity5();
        double[][] wrongInit = new double[3][2]; // wrong number of rows
        assertThrows(IllegalArgumentException.class,
                () -> IsotonicMDS.fit(prox, wrongInit, new IsotonicMDS.Options()));
    }

    @Test
    public void givenValidOptions_whenIsotonicMDSOptionsRoundTrip_thenValuesPreserved() {
        IsotonicMDS.Options opts = new IsotonicMDS.Options(3, 1e-5, 300);
        IsotonicMDS.Options restored = IsotonicMDS.Options.of(opts.toProperties());
        assertEquals(opts.d(), restored.d());
        assertEquals(opts.tol(), restored.tol(), 1e-15);
        assertEquals(opts.maxIter(), restored.maxIter());
    }

    @Test
    public void givenDefaultProperties_whenIsotonicMDSOptionsOf_thenDefaultsApplied() {
        IsotonicMDS.Options opts = IsotonicMDS.Options.of(new Properties());
        assertEquals(2, opts.d());
        assertEquals(1E-4, opts.tol(), 1e-12);
        assertEquals(200, opts.maxIter());
    }

    @Test
    public void givenProximityMatrix_whenIsotonicMDSFit_thenStressIsNonNegative() {
        double[][] prox = proximity5();
        IsotonicMDS mds = IsotonicMDS.fit(prox);
        assertTrue(mds.stress() >= 0, "Stress must be non-negative");
        assertEquals(5, mds.coordinates().length);
        assertEquals(2, mds.coordinates()[0].length);
    }

    // ======================================================================
    // SammonMapping
    // ======================================================================

    @Test
    public void givenDLessThan2_whenSammonOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SammonMapping.Options(1, 0.2, 100));
    }

    @Test
    public void givenNegativeStep_whenSammonOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SammonMapping.Options(2, -0.1, 100, 1e-4, 1e-3, null));
    }

    @Test
    public void givenZeroMaxIter_whenSammonOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SammonMapping.Options(2, 0.2, 0, 1e-4, 1e-3, null));
    }

    @Test
    public void givenNonSquareMatrix_whenSammonFit_thenThrowsIllegalArgumentException() {
        double[][] notSquare = {{0, 1, 2, 3}, {1, 0, 1, 2}, {2, 1, 0, 1}};
        assertThrows(IllegalArgumentException.class, () -> SammonMapping.fit(notSquare));
    }

    @Test
    public void givenSizeMismatch_whenSammonFitWithCoords_thenThrowsIllegalArgumentException() {
        double[][] prox = proximity5();
        double[][] wrongCoords = new double[3][2];
        assertThrows(IllegalArgumentException.class,
                () -> SammonMapping.fit(prox, wrongCoords, new SammonMapping.Options(2, 0.2, 50)));
    }

    @Test
    public void givenValidOptions_whenSammonOptionsRoundTrip_thenValuesPreserved() {
        SammonMapping.Options opts = new SammonMapping.Options(3, 0.3, 200, 1e-5, 1e-4, null);
        SammonMapping.Options restored = SammonMapping.Options.of(opts.toProperties());
        assertEquals(opts.d(), restored.d());
        assertEquals(opts.step(), restored.step(), 1e-12);
        assertEquals(opts.maxIter(), restored.maxIter());
        assertEquals(opts.tol(), restored.tol(), 1e-15);
        assertEquals(opts.stepTol(), restored.stepTol(), 1e-15);
    }

    @Test
    public void givenDefaultProperties_whenSammonOptionsOf_thenDefaultsApplied() {
        SammonMapping.Options opts = SammonMapping.Options.of(new Properties());
        assertEquals(2, opts.d());
        assertEquals(0.2, opts.step(), 1e-12);
        assertEquals(100, opts.maxIter());
    }

    @Test
    public void givenProximityMatrix_whenSammonFit_thenStressIsNonNegative() {
        double[][] prox = proximity5();
        SammonMapping sm = SammonMapping.fit(prox);
        assertTrue(sm.stress() >= 0, "Stress must be non-negative");
        assertEquals(5, sm.coordinates().length);
        assertEquals(2, sm.coordinates()[0].length);
    }

    // ======================================================================
    // TSNE
    // ======================================================================

    @Test
    public void givenDLessThan2_whenTSNEOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TSNE.Options(1, 20, 200, 12, 1000));
    }

    @Test
    public void givenPerplexityLessThan2_whenTSNEOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TSNE.Options(2, 1.5, 200, 12, 1000));
    }

    @Test
    public void givenNonPositiveEta_whenTSNEOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TSNE.Options(2, 20, 0.0, 12, 1000));
    }

    @Test
    public void givenNonPositiveEarlyExaggeration_whenTSNEOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TSNE.Options(2, 20, 200, -1.0, 1000));
    }

    @Test
    public void givenMaxIterLessThan250_whenTSNEOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TSNE.Options(2, 20, 200, 12, 249));
    }

    @Test
    public void givenMomentumSwitchAtMaxIter_whenTSNEOptions_thenThrowsIllegalArgumentException() {
        // momentumSwitchIter must be < maxIter
        assertThrows(IllegalArgumentException.class,
                () -> new TSNE.Options(2, 20, 200, 12, 1000, 50, 1E-7, 0.5, 0.8, 1000, 0.01, null));
    }

    @Test
    public void givenValidOptions_whenTSNEOptionsRoundTrip_thenValuesPreserved() {
        TSNE.Options opts = new TSNE.Options(2, 30, 150, 10, 500);
        TSNE.Options restored = TSNE.Options.of(opts.toProperties());
        assertEquals(opts.d(), restored.d());
        assertEquals(opts.perplexity(), restored.perplexity(), 1e-12);
        assertEquals(opts.eta(), restored.eta(), 1e-12);
        assertEquals(opts.earlyExaggeration(), restored.earlyExaggeration(), 1e-12);
        assertEquals(opts.maxIter(), restored.maxIter());
    }

    @Test
    public void givenDefaultProperties_whenTSNEOptionsOf_thenDefaultsApplied() {
        TSNE.Options opts = TSNE.Options.of(new Properties());
        assertEquals(2, opts.d());
        assertEquals(20.0, opts.perplexity(), 1e-12);
        assertEquals(200.0, opts.eta(), 1e-12);
        assertEquals(1000, opts.maxIter());
    }

    @Test
    public void givenSmallData_whenTSNEFit_thenOutputShapeAndCostCorrect() {
        // Given: 30 points in 4-D
        MathEx.setSeed(19650218);
        int n = 30;
        double[][] X = new double[n][4];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 4; j++) {
                X[i][j] = MathEx.random();
            }
        }
        TSNE.Options opts = new TSNE.Options(2, 5, 200, 12, 300);
        // When
        TSNE result = TSNE.fit(X, opts);
        // Then
        assertEquals(n, result.coordinates().length);
        assertEquals(2, result.coordinates()[0].length);
        assertTrue(result.cost() >= 0, "Cost should be non-negative");
        // All coordinates finite
        for (double[] row : result.coordinates()) {
            for (double v : row) {
                assertTrue(Double.isFinite(v));
            }
        }
    }

    @Test
    public void givenSquareMatrix_whenTSNEFit_thenTreatedAsDistanceMatrix() {
        // When X is square, it is treated as a distance matrix (not decomposed to pdist)
        MathEx.setSeed(19650218);
        int n = 20;
        // Build a simple symmetric squared-distance matrix
        double[][] pts = new double[n][2];
        for (int i = 0; i < n; i++) {
            pts[i][0] = i * 0.1;
            pts[i][1] = 0;
        }
        double[][] D = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double dx = pts[i][0] - pts[j][0];
                D[i][j] = dx * dx;
            }
        }
        TSNE.Options opts = new TSNE.Options(2, 3, 200, 12, 300);
        TSNE result = TSNE.fit(D, opts);
        assertEquals(n, result.coordinates().length);
        assertEquals(2, result.coordinates()[0].length);
    }

    // ======================================================================
    // UMAP
    // ======================================================================

    @Test
    public void givenKLessThan2_whenUMAPOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new UMAP.Options(1, 2, 200, 1.0, 0.1, 1.0, 5, 1.0, 1.0));
    }

    @Test
    public void givenDLessThan2_whenUMAPOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new UMAP.Options(15, 1, 200, 1.0, 0.1, 1.0, 5, 1.0, 1.0));
    }

    @Test
    public void givenValidOptions_whenUMAPOptionsRoundTrip_thenValuesPreserved() {
        UMAP.Options opts = new UMAP.Options(10, 2, 200, 1.0, 0.1, 1.0, 5, 1.0, 1.0);
        UMAP.Options restored = UMAP.Options.of(opts.toProperties());
        assertEquals(opts.k(), restored.k());
        assertEquals(opts.d(), restored.d());
        assertEquals(opts.epochs(), restored.epochs());
        assertEquals(opts.learningRate(), restored.learningRate(), 1e-12);
        assertEquals(opts.minDist(), restored.minDist(), 1e-12);
        assertEquals(opts.spread(), restored.spread(), 1e-12);
        assertEquals(opts.negativeSamples(), restored.negativeSamples());
        assertEquals(opts.repulsionStrength(), restored.repulsionStrength(), 1e-12);
    }

    @Test
    public void givenDefaultProperties_whenUMAPOptionsOf_thenDefaultsApplied() {
        UMAP.Options opts = UMAP.Options.of(new Properties());
        assertEquals(15, opts.k());
        assertEquals(2, opts.d());
    }

    @Test
    public void givenSmallGrid_whenUMAPFit_thenOutputHasCorrectShape() {
        MathEx.setSeed(19650218);
        double[][] data = grid20();
        UMAP.Options opts = new UMAP.Options(4, 2, 100, 1.0, 0.1, 1.0, 5, 1.0, 1.0);
        double[][] coords = UMAP.fit(data, opts);
        // UMAP may return fewer points if the graph is disconnected
        assertTrue(coords.length > 0 && coords.length <= data.length);
        assertEquals(2, coords[0].length);
        for (double[] row : coords) {
            for (double v : row) {
                assertTrue(Double.isFinite(v));
            }
        }
    }

    // ======================================================================
    // KPCA
    // ======================================================================

    @Test
    public void givenDLessThan2_whenKPCAOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new KPCA.Options(1, 1e-7));
    }

    @Test
    public void givenValidOptions_whenKPCAOptionsRoundTrip_thenValuesPreserved() {
        KPCA.Options opts = new KPCA.Options(3, 1e-6);
        KPCA.Options restored = KPCA.Options.of(opts.toProperties());
        assertEquals(opts.d(), restored.d());
        assertEquals(opts.threshold(), restored.threshold(), 1e-15);
    }

    @Test
    public void givenDefaultProperties_whenKPCAOptionsOf_thenDefaultsApplied() {
        KPCA.Options opts = KPCA.Options.of(new Properties());
        assertEquals(2, opts.d());
    }
}



