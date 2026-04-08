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
package smile.cs;

import smile.math.MathEx;
import smile.tensor.Matrix;
import smile.wavelet.HaarWavelet;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for compressed sensing algorithms: OMP, CoSaMP, and BasisPursuit.
 *
 * <p>Each test creates a synthetic k-sparse signal, takes m compressive
 * measurements via a random Gaussian matrix, and verifies that each
 * algorithm recovers the signal to within a small tolerance.
 */
public class CompressedSensingTest {

    /** Signal length (must be power of 2 for wavelet tests). */
    private static final int N = 128;
    /** Number of measurements. */
    private static final int M = 50;
    /** Target sparsity. */
    private static final int K = 5;
    /** Recovery tolerance (relative L2 error). */
    private static final double TOL = 1e-2;

    /** The k-sparse ground-truth signal. */
    private static double[] xTrue;
    /** The measurement vector y = Φ x. */
    private static double[] y;
    /** The Gaussian measurement matrix. */
    private static MeasurementMatrix mm;

    @BeforeAll
    public static void setUp() {
        MathEx.setSeed(19650218);

        // Build a K-sparse signal: K non-zero entries at random locations
        xTrue = new double[N];
        int[] locs = MathEx.permutate(N);
        for (int i = 0; i < K; i++) {
            // Amplitudes in [-2, -1] ∪ [1, 2] to avoid near-zero values
            double amp = 1.0 + MathEx.random();
            xTrue[locs[i]] = (MathEx.random() < 0.5) ? amp : -amp;
        }

        // Gaussian sensing matrix and measurements
        mm = MeasurementMatrix.gaussian(M, N);
        y  = mm.measure(xTrue);
    }

    @AfterAll
    public static void tearDown() {}

    // =========================================================================
    //  Helpers
    // =========================================================================

    /** Relative L2 recovery error ‖x_rec − x_true‖₂ / ‖x_true‖₂. */
    private static double relError(double[] xRec) {
        double[] diff = xRec.clone();
        for (int i = 0; i < diff.length; i++) diff[i] -= xTrue[i];
        return MathEx.norm(diff) / MathEx.norm(xTrue);
    }

    // =========================================================================
    //  OMP tests
    // =========================================================================

    @Test
    public void testOMP() {
        System.out.println("OMP: exact sparse recovery");
        OMP result = OMP.fit(mm.phi(), y, K);
        double err = relError(result.x());
        System.out.printf("  rel error = %.2e, iters = %d, support size = %d%n",
                err, result.iter(), result.support().length);
        assertTrue(err < TOL, "OMP relative error %e exceeds tolerance %e".formatted(err, TOL));
        assertEquals(K, result.support().length);
    }

    @Test
    public void testOMPNoisy() {
        System.out.println("OMP: noisy measurements");
        MathEx.setSeed(42);
        // Add small Gaussian noise to measurements
        double[] yn = y.clone();
        double sigma = 1e-3 * MathEx.norm(y) / Math.sqrt(M);
        for (int i = 0; i < M; i++) yn[i] += sigma * MathEx.random();

        OMP result = OMP.fit(mm.phi(), yn, K);
        double err = relError(result.x());
        System.out.printf("  rel error = %.2e (noisy)%n", err);
        // Noisy recovery is less precise; use a looser tolerance
        assertTrue(err < 0.2, "OMP (noisy) relative error %e too large".formatted(err));
    }

    @Test
    public void testOMPOptions() {
        System.out.println("OMP: Options record persistence");
        var opts = new OMP.Options(K, 1e-6);
        var props = opts.toProperties();
        var opts2 = OMP.Options.of(props);
        assertEquals(opts.sparsity(), opts2.sparsity());
        assertEquals(opts.tol(), opts2.tol(), 1e-15);
    }

    // =========================================================================
    //  CoSaMP tests
    // =========================================================================

    @Test
    public void testCoSaMP() {
        System.out.println("CoSaMP: exact sparse recovery");
        CoSaMP result = CoSaMP.fit(mm.phi(), y, K);
        double err = relError(result.x());
        System.out.printf("  rel error = %.2e, iters = %d, support size = %d%n",
                err, result.iter(), result.support().length);
        assertTrue(err < TOL, "CoSaMP relative error %e exceeds tolerance %e".formatted(err, TOL));
    }

    @Test
    public void testCoSaMPLargeSparsity() {
        System.out.println("CoSaMP: over-estimated sparsity");
        // Using k = 2K should still recover correctly
        CoSaMP result = CoSaMP.fit(mm.phi(), y, 2 * K);
        double err = relError(result.x());
        System.out.printf("  rel error = %.2e (k=2K)%n", err);
        assertTrue(err < TOL, "CoSaMP (k=2K) relative error %e exceeds tolerance".formatted(err));
    }

    @Test
    public void testCoSaMPOptions() {
        System.out.println("CoSaMP: Options record persistence");
        var opts = new CoSaMP.Options(K, 50, 1e-6);
        var props = opts.toProperties();
        var opts2 = CoSaMP.Options.of(props);
        assertEquals(opts.sparsity(), opts2.sparsity());
        assertEquals(opts.maxIter(),  opts2.maxIter());
        assertEquals(opts.tol(),      opts2.tol(), 1e-15);
    }

    @Test
    public void testCoSaMPHelpers() {
        System.out.println("CoSaMP: helper methods");

        // largestIndices
        double[] v = {0.1, 3.0, -5.0, 2.0, -1.0};
        int[] top3 = CoSaMP.largestIndices(v, 3);
        assertArrayEquals(new int[]{1, 2, 3}, top3,
                "largestIndices should return {1,2,3} (indices of |v|=3,5,2)");

        // union
        int[] a = {0, 2, 4};
        int[] b = {1, 2, 3};
        int[] u = CoSaMP.union(a, b);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, u);
    }

    // =========================================================================
    //  BasisPursuit tests
    // =========================================================================

    @Test
    public void testBasisPursuit() {
        System.out.println("BasisPursuit: exact basis pursuit (epsilon=0)");
        // Use a problem where m > n/2 so A A^T is well-conditioned
        int n = 32, m = 20, k = 3;
        MathEx.setSeed(7777);
        double[] xSmall = new double[n];
        int[] locs = MathEx.permutate(n);
        for (int i = 0; i < k; i++) xSmall[locs[i]] = (i + 1) * (MathEx.random() < 0.5 ? 1 : -1);

        MeasurementMatrix mmSmall = MeasurementMatrix.gaussian(m, n);
        double[] ySmall = mmSmall.measure(xSmall);

        // Generous iteration budget for a robust test
        var opts = new BasisPursuit.Options(0.0, 10.0, 1e-10, 300, 50, 1e-4);
        BasisPursuit result = BasisPursuit.fit(mmSmall.phi(), ySmall, opts);

        // Verify A x ≈ y (feasibility)
        double[] Ax = BasisPursuit.matvec(mmSmall.phi(), result.x(), m, n, false);
        double[] res = new double[m];
        for (int i = 0; i < m; i++) res[i] = Ax[i] - ySmall[i];
        double feasErr = MathEx.norm(res) / MathEx.norm(ySmall);
        System.out.printf("  BP feasibility error = %.2e, ‖x‖₁ = %.4f, outer iters = %d%n",
                feasErr, MathEx.norm1(result.x()), result.iter());
        assertTrue(feasErr < 0.05,
                "BP feasibility error %e exceeds 0.05".formatted(feasErr));
    }

    @Test
    public void testBasisPursuitDenoising() {
        System.out.println("BasisPursuit: BPDN (epsilon > 0)");
        int n = 32, m = 16, k = 3;
        MathEx.setSeed(8888);
        double[] xSmall = new double[n];
        int[] locs = MathEx.permutate(n);
        for (int i = 0; i < k; i++) xSmall[locs[i]] = (i + 1.0) * (MathEx.random() < 0.5 ? 1 : -1);

        MeasurementMatrix mmSmall = MeasurementMatrix.gaussian(m, n);
        double[] ySmall = mmSmall.measure(xSmall);
        // Add noise
        for (int i = 0; i < m; i++) ySmall[i] += 0.01 * MathEx.random();
        double epsilon = 0.05 * MathEx.norm(ySmall);

        var opts = new BasisPursuit.Options(epsilon, 10.0, 1e-8, 100, 30, 1e-3);
        BasisPursuit result = BasisPursuit.fit(mmSmall.phi(), ySmall, opts);

        // Verify the residual constraint is approximately satisfied
        double[] Ax = BasisPursuit.matvec(mmSmall.phi(), result.x(), m, n, false);
        double[] res = new double[m];
        for (int i = 0; i < m; i++) res[i] = Ax[i] - ySmall[i];
        double residNorm = MathEx.norm(res);
        System.out.printf("  BPDN ‖Ax−y‖₂ = %.4f, epsilon = %.4f%n", residNorm, epsilon);
        assertTrue(residNorm <= epsilon * 2.0,
                "BPDN residual %e > 2*epsilon %e".formatted(residNorm, epsilon * 2.0));
    }

    @Test
    public void testBasisPursuitOptions() {
        System.out.println("BasisPursuit: Options record persistence");
        var opts = new BasisPursuit.Options(0.01, 10.0, 1e-8, 200, 50, 1e-3);
        var props = opts.toProperties();
        var opts2 = BasisPursuit.Options.of(props);
        assertEquals(opts.epsilon(),   opts2.epsilon(),   1e-15);
        assertEquals(opts.mu(),        opts2.mu(),        1e-15);
        assertEquals(opts.cgtol(),     opts2.cgtol(),     1e-15);
        assertEquals(opts.cgMaxIter(), opts2.cgMaxIter());
        assertEquals(opts.maxIter(),   opts2.maxIter());
        assertEquals(opts.tol(),       opts2.tol(),       1e-15);
    }

    // =========================================================================
    //  MeasurementMatrix tests
    // =========================================================================

    @Test
    public void testMeasurementMatrixGaussian() {
        System.out.println("MeasurementMatrix: Gaussian");
        MathEx.setSeed(11111);
        MeasurementMatrix g = MeasurementMatrix.gaussian(M, N);
        assertEquals(M, g.nrow());
        assertEquals(N, g.ncol());

        // Column norms should be close to 1/√m · √m = 1 in expectation
        // (each entry N(0, 1/m), so column norm ≈ √(m · 1/m) = 1)
        double[] x = new double[N];
        x[0] = 1.0;
        double[] yTest = g.measure(x);
        assertEquals(M, yTest.length);
    }

    @Test
    public void testMeasurementMatrixBernoulli() {
        System.out.println("MeasurementMatrix: Bernoulli");
        MathEx.setSeed(22222);
        MeasurementMatrix b = MeasurementMatrix.bernoulli(M, N);
        assertEquals(M, b.nrow());
        assertEquals(N, b.ncol());
    }

    @Test
    public void testMeasurementMatrixPartial() {
        System.out.println("MeasurementMatrix: partial identity");
        MathEx.setSeed(33333);
        MeasurementMatrix p = MeasurementMatrix.partial(M, N);
        assertEquals(M, p.nrow());
        assertEquals(N, p.ncol());

        // Measurement of e_j should return 1 at the row that samples index j
        // and 0 elsewhere — spot-check a few entries
        double[] x = new double[N];
        x[5] = 1.0;
        double[] yTest = p.measure(x);
        // Only one row can be non-zero (the row that samples column 5)
        int nonZero = 0;
        for (double v : yTest) if (Math.abs(v) > 1e-12) nonZero++;
        assertTrue(nonZero <= 1);
    }

    @Test
    public void testMeasurementMatrixAdjoint() {
        System.out.println("MeasurementMatrix: adjoint consistency (measure / backProject)");
        MathEx.setSeed(44444);
        MeasurementMatrix g = MeasurementMatrix.gaussian(M, N);

        double[] x = new double[N];
        double[] yv = new double[M];
        MathEx.setSeed(55555);
        for (int i = 0; i < N; i++) x[i] = MathEx.random() - 0.5;
        for (int i = 0; i < M; i++) yv[i] = MathEx.random() - 0.5;

        // ⟨Φx, y⟩ should equal ⟨x, Φ^T y⟩
        double[] Ax   = g.measure(x);
        double[] Aty  = g.backProject(yv);
        double lhs = MathEx.dot(Ax, yv);
        double rhs = MathEx.dot(x, Aty);
        assertEquals(lhs, rhs, 1e-10, "<Φx,y> != <x,Φ^Ty>: lhs=%e, rhs=%e".formatted(lhs, rhs));
    }

    @Test
    public void testMeasurementMatrixWavelet() {
        System.out.println("MeasurementMatrix: wavelet compound operator adjoint");
        MathEx.setSeed(66666);
        // Use a small power-of-2 dimension for the Haar wavelet
        int nw = 64, mw = 25;
        MeasurementMatrix wm = MeasurementMatrix.gaussian(mw, nw)
                                                .withWavelet(new HaarWavelet());

        var A = wm.toMatrix();
        assertEquals(mw, A.nrow());
        assertEquals(nw, A.ncol());

        // Adjoint check: ⟨As, y⟩ = ⟨s, A^T y⟩
        double[] s = new double[nw];
        double[] yv = new double[mw];
        for (int i = 0; i < nw; i++) s[i] = MathEx.random() - 0.5;
        for (int i = 0; i < mw; i++) yv[i] = MathEx.random() - 0.5;

        double[] As  = BasisPursuit.matvec(A, s, mw, nw, false);
        double[] Aty = BasisPursuit.matvec(A, yv, mw, nw, true);
        double lhs = MathEx.dot(As, yv);
        double rhs = MathEx.dot(s, Aty);
        assertEquals(lhs, rhs, 1e-8,
                "Wavelet matrix adjoint failed: <As,y>=%e, <s,A^Ty>=%e".formatted(lhs, rhs));
    }

    // =========================================================================
    //  Invalid-argument / edge-case tests
    // =========================================================================

    @Test
    public void testOMPYLengthMismatch() {
        assertThrows(IllegalArgumentException.class, () ->
                OMP.fit(mm.phi(), new double[M + 1], K));
    }

    @Test
    public void testCoSaMPYLengthMismatch() {
        assertThrows(IllegalArgumentException.class, () ->
                CoSaMP.fit(mm.phi(), new double[M + 1], K));
    }

    @Test
    public void testBasisPursuitYLengthMismatch() {
        assertThrows(IllegalArgumentException.class, () ->
                BasisPursuit.fit(mm.phi(), new double[M + 1]));
    }

    @Test
    public void testOMPOptionsValidation() {
        assertThrows(IllegalArgumentException.class, () -> new OMP.Options(0, 1e-6));
        assertThrows(IllegalArgumentException.class, () -> new OMP.Options(-1, 1e-6));
        assertThrows(IllegalArgumentException.class, () -> new OMP.Options(5, -1e-6));
    }

    @Test
    public void testCoSaMPOptionsValidation() {
        assertThrows(IllegalArgumentException.class, () -> new CoSaMP.Options(0, 50, 1e-6));
        assertThrows(IllegalArgumentException.class, () -> new CoSaMP.Options(5, 0, 1e-6));
        assertThrows(IllegalArgumentException.class, () -> new CoSaMP.Options(5, 50, -1.0));
    }

    @Test
    public void testBasisPursuitOptionsValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new BasisPursuit.Options(-1.0, 10.0, 1e-8, 200, 50, 1e-3));
        assertThrows(IllegalArgumentException.class, () ->
                new BasisPursuit.Options(0.0, 0.5, 1e-8, 200, 50, 1e-3));   // mu ≤ 1
        assertThrows(IllegalArgumentException.class, () ->
                new BasisPursuit.Options(0.0, 10.0, 0.0, 200, 50, 1e-3));   // cgtol ≤ 0
        assertThrows(IllegalArgumentException.class, () ->
                new BasisPursuit.Options(0.0, 10.0, 1e-8, 0, 50, 1e-3));    // cgMaxIter ≤ 0
        assertThrows(IllegalArgumentException.class, () ->
                new BasisPursuit.Options(0.0, 10.0, 1e-8, 200, 0, 1e-3));   // maxIter ≤ 0
        assertThrows(IllegalArgumentException.class, () ->
                new BasisPursuit.Options(0.0, 10.0, 1e-8, 200, 50, 0.0));   // tol ≤ 0
    }

    @Test
    public void testPartialRowsValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                MeasurementMatrix.partial(new int[]{0, 5, N}, N));   // N is out of range
        assertThrows(IllegalArgumentException.class, () ->
                MeasurementMatrix.partial(new int[]{-1, 3}, N));
    }

    // =========================================================================
    //  Zero-signal / zero-measurement edge cases
    // =========================================================================

    @Test
    public void testOMPZeroMeasurement() {
        OMP result = OMP.fit(mm.phi(), new double[M], K);
        // zero y → zero x
        for (double v : result.x()) assertEquals(0.0, v, 1e-30);
        assertEquals(0, result.support().length);
        assertEquals(0, result.iter());
    }

    @Test
    public void testCoSaMPZeroMeasurement() {
        CoSaMP result = CoSaMP.fit(mm.phi(), new double[M], K);
        for (double v : result.x()) assertEquals(0.0, v, 1e-30);
        assertEquals(0, result.support().length);
        assertEquals(0, result.iter());
    }

    // =========================================================================
    //  Default-options overloads
    // =========================================================================

    @Test
    public void testBasisPursuitDefaultOptions() {
        // BasisPursuit.fit(A, y) uses new Options() — should not throw
        int n = 16, m = 10, k = 2;
        MathEx.setSeed(9999);
        double[] xSmall = new double[n];
        int[] locs = MathEx.permutate(n);
        for (int i = 0; i < k; i++) xSmall[locs[i]] = (i + 1.0);
        MeasurementMatrix mmSmall = MeasurementMatrix.gaussian(m, n);
        double[] ySmall = mmSmall.measure(xSmall);
        assertDoesNotThrow(() -> BasisPursuit.fit(mmSmall.phi(), ySmall));
    }

    // =========================================================================
    //  CoSaMP noisy measurements
    // =========================================================================

    @Test
    public void testCoSaMPNoisy() {
        System.out.println("CoSaMP: noisy measurements");
        MathEx.setSeed(1234);
        double[] yn = y.clone();
        double sigma = 1e-3 * MathEx.norm(y) / Math.sqrt(M);
        for (int i = 0; i < M; i++) yn[i] += sigma * MathEx.random();
        CoSaMP result = CoSaMP.fit(mm.phi(), yn, K);
        double err = relError(result.x());
        System.out.printf("  CoSaMP (noisy) rel error = %.2e%n", err);
        assertTrue(err < 0.2, "CoSaMP (noisy) relative error %e too large".formatted(err));
    }

    // =========================================================================
    //  OMP with explicit Options
    // =========================================================================

    @Test
    public void testOMPExplicitOptions() {
        var opts = new OMP.Options(K, 1e-8);
        OMP result = OMP.fit(mm.phi(), y, opts);
        double err = relError(result.x());
        assertTrue(err < TOL);
    }

    // =========================================================================
    //  MeasurementMatrix — additional coverage
    // =========================================================================

    @Test
    public void testMeasurementMatrixPartialExplicitRows() {
        MathEx.setSeed(55555);
        int[] rows = {0, 3, 7, 12, 20};
        MeasurementMatrix p = MeasurementMatrix.partial(rows, N);
        assertEquals(rows.length, p.nrow());
        assertEquals(N, p.ncol());
        // Measurement of e_3 should appear only at the row mapping column 3
        double[] x3 = new double[N];
        x3[3] = 1.0;
        double[] yTest = p.measure(x3);
        // row index 1 maps column 3 → entry at position 1 should be 1
        assertEquals(1.0, yTest[1], 1e-12);
        for (int i = 0; i < rows.length; i++) {
            if (i != 1) assertEquals(0.0, yTest[i], 1e-12);
        }
    }

    @Test
    public void testMeasurementMatrixBackProjectSparse() {
        System.out.println("MeasurementMatrix: backProjectSparse");
        MathEx.setSeed(66666);
        int nw = 64, mw = 20;
        MeasurementMatrix wm = MeasurementMatrix.gaussian(mw, nw)
                                                .withWavelet(new HaarWavelet());
        double[] yv = new double[mw];
        for (int i = 0; i < mw; i++) yv[i] = MathEx.random() - 0.5;

        double[] v = wm.backProjectSparse(yv);
        assertEquals(nw, v.length);

        // Adjoint check with measureSparse:  <measureSparse(s), y> = <s, backProjectSparse(y)>
        double[] s = new double[nw];
        for (int i = 0; i < nw; i++) s[i] = MathEx.random() - 0.5;
        double[] As = wm.measureSparse(s);
        double lhs = MathEx.dot(As, yv);
        double rhs = MathEx.dot(s, v);
        assertEquals(lhs, rhs, 1e-8,
                "backProjectSparse adjoint failed: lhs=%e rhs=%e".formatted(lhs, rhs));
    }

    @Test
    public void testMeasurementMatrixBernoulliRIP() {
        // Bernoulli matrix: measure a 1-sparse signal and verify it's non-zero
        MathEx.setSeed(7070);
        MeasurementMatrix b = MeasurementMatrix.bernoulli(M, N);
        double[] x1 = new double[N];
        x1[42] = 3.7;
        double[] yb = b.measure(x1);
        double energy = 0;
        for (double v : yb) energy += v * v;
        assertTrue(energy > 0, "Bernoulli measurement of non-zero signal should be non-zero");
    }

    @Test
    public void testMeasurementMatrixToMatrixNoWavelet() {
        // withWavelet(null) → toMatrix() should return the plain phi
        MeasurementMatrix g = MeasurementMatrix.gaussian(M, N);
        Matrix mat = g.toMatrix();
        assertEquals(g.phi(), mat);
    }

    // =========================================================================
    //  CoSaMP helper edge cases
    // =========================================================================

    @Test
    public void testLargestIndicesKLargerThanN() {
        double[] v = {0.5, 1.0, 0.3};
        // k > length → should clamp to length
        int[] idx = CoSaMP.largestIndices(v, 10);
        assertEquals(3, idx.length);
        assertArrayEquals(new int[]{0, 1, 2}, idx);
    }

    @Test
    public void testUnionEmptyArrays() {
        int[] u1 = CoSaMP.union(new int[0], new int[]{1, 3});
        assertArrayEquals(new int[]{1, 3}, u1);
        int[] u2 = CoSaMP.union(new int[]{2, 4}, new int[0]);
        assertArrayEquals(new int[]{2, 4}, u2);
        int[] u3 = CoSaMP.union(new int[0], new int[0]);
        assertEquals(0, u3.length);
    }

    @Test
    public void testUnionNoDuplicates() {
        int[] a = {0, 2, 4, 6};
        int[] b = {1, 2, 5, 6};
        int[] u = CoSaMP.union(a, b);
        assertArrayEquals(new int[]{0, 1, 2, 4, 5, 6}, u);
    }

    // =========================================================================
    //  BasisPursuit iter field reflects outer iterations
    // =========================================================================

    @Test
    public void testBasisPursuitIterCount() {
        int n = 16, m = 10, k = 2;
        MathEx.setSeed(1111);
        double[] xSmall = new double[n];
        int[] locs = MathEx.permutate(n);
        for (int i = 0; i < k; i++) xSmall[locs[i]] = (i + 1.0);
        MeasurementMatrix mmSmall = MeasurementMatrix.gaussian(m, n);
        double[] ySmall = mmSmall.measure(xSmall);

        var opts = new BasisPursuit.Options(0.0, 10.0, 1e-8, 200, 30, 1e-3);
        BasisPursuit result = BasisPursuit.fit(mmSmall.phi(), ySmall, opts);
        // iter should be ≤ maxIter (30) and ≥ 0
        assertTrue(result.iter() >= 0 && result.iter() <= 30,
                "iter %d not in [0, 30]".formatted(result.iter()));
    }

    @Test
    public void testOMPWithWavelet() {
        System.out.println("OMP: sparse recovery in wavelet domain");
        MathEx.setSeed(77777);
        int nw = 64, mw = 25, kw = 4;

        // Build a k-sparse signal in the wavelet coefficient domain
        double[] sSparse = new double[nw];
        int[] locs = MathEx.permutate(nw);
        for (int i = 0; i < kw; i++) sSparse[locs[i]] = 1.0 + MathEx.random();

        // Synthesise signal: x = IDWT(s)
        double[] xSig = sSparse.clone();
        new HaarWavelet().inverse(xSig);

        // Build compound measurement matrix A = Φ Ψ
        MeasurementMatrix wm = MeasurementMatrix.gaussian(mw, nw)
                                                .withWavelet(new HaarWavelet());
        double[] yMeas = wm.measureSparse(sSparse);   // y = Φ x = Φ IDWT(s)

        // Recover sparse coefficients s via OMP on A
        OMP result = OMP.fit(wm.toMatrix(), yMeas, kw);
        double[] sRec = result.x();

        double norm = MathEx.norm(sSparse);
        double[] diff = sRec.clone();
        for (int i = 0; i < nw; i++) diff[i] -= sSparse[i];
        double err = MathEx.norm(diff) / norm;
        System.out.printf("  OMP+Haar rel error = %.2e%n", err);
        assertTrue(err < 0.15, "OMP+wavelet rel error %e too large".formatted(err));
    }
}

