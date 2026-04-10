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
package smile.math.kernel;

import smile.util.SparseArray;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the smile.math.kernel package.
 *
 * @author Haifeng Li
 */
public class KernelTest {

    // Shared test vectors
    static final double[] x1 = {1.0, 0.0, 0.0};
    static final double[] x2 = {0.0, 1.0, 0.0};
    static final double[] x3 = {1.0, 1.0, 0.0};
    static final double[] zero = {0.0, 0.0, 0.0};

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    // ===== LinearKernel =====

    @Test
    public void testLinearKernel() {
        System.out.println("LinearKernel");
        LinearKernel k = new LinearKernel();
        // dot(x1, x1) = 1
        assertEquals(1.0, k.k(x1, x1), 1E-9);
        // dot(x1, x2) = 0
        assertEquals(0.0, k.k(x1, x2), 1E-9);
        // dot(x1, x3) = 1
        assertEquals(1.0, k.k(x1, x3), 1E-9);
        // symmetry
        assertEquals(k.k(x1, x2), k.k(x2, x1), 1E-9);
    }

    @Test
    public void testLinearKernelPositiveSemiDefinite() {
        System.out.println("LinearKernel PSD");
        LinearKernel k = new LinearKernel();
        // self-dot product is always non-negative
        assertTrue(k.k(x1, x1) >= 0);
        assertTrue(k.k(x2, x2) >= 0);
    }

    @Test
    public void testLinearKernelHyperparameters() {
        System.out.println("LinearKernel hyperparameters");
        LinearKernel k = new LinearKernel();
        assertEquals(0, k.hyperparameters().length);
        assertEquals(0, k.lo().length);
        assertEquals(0, k.hi().length);
        assertInstanceOf(LinearKernel.class, k.of(new double[0]));
    }

    @Test
    public void testLinearKernelToString() {
        System.out.println("LinearKernel toString");
        assertTrue(new LinearKernel().toString().contains("LinearKernel"));
    }

    // ===== GaussianKernel =====

    @Test
    public void testGaussianKernel() {
        System.out.println("GaussianKernel");
        GaussianKernel k = new GaussianKernel(1.0);
        // k(x,x) = exp(0) = 1
        assertEquals(1.0, k.k(x1, x1), 1E-9);
        assertEquals(1.0, k.k(x2, x2), 1E-9);
        // k(x1,x2): dist=sqrt(2), k = exp(-1.0)
        double dist12 = Math.sqrt(2.0);
        assertEquals(Math.exp(-0.5 * dist12 * dist12), k.k(x1, x2), 1E-9);
        // symmetry
        assertEquals(k.k(x1, x2), k.k(x2, x1), 1E-9);
        // bounded in (0, 1]
        assertTrue(k.k(x1, x2) > 0);
        assertTrue(k.k(x1, x2) <= 1);
    }

    @Test
    public void testGaussianKernelGradient() {
        System.out.println("GaussianKernel gradient");
        GaussianKernel k = new GaussianKernel(1.0);
        double[] kg = k.kg(x1, x2);
        assertEquals(2, kg.length);
        // kg[0] is the kernel value
        assertEquals(k.k(x1, x2), kg[0], 1E-9);
        // gradient should be non-negative for this case
        assertTrue(kg[1] >= 0);
    }

    @Test
    public void testGaussianKernelSigmaEffect() {
        System.out.println("GaussianKernel sigma effect");
        GaussianKernel k1 = new GaussianKernel(0.5);
        GaussianKernel k2 = new GaussianKernel(2.0);
        // Smaller sigma => values decay faster => smaller value for same distance
        assertTrue(k1.k(x1, x2) < k2.k(x1, x2));
    }

    @Test
    public void testGaussianKernelInvalidSigma() {
        assertThrows(IllegalArgumentException.class, () -> new GaussianKernel(0.0));
        assertThrows(IllegalArgumentException.class, () -> new GaussianKernel(-1.0));
    }

    @Test
    public void testGaussianKernelOf() {
        System.out.println("GaussianKernel of");
        GaussianKernel k = new GaussianKernel(1.0);
        GaussianKernel k2 = k.of(new double[]{2.0});
        assertEquals(2.0, k2.scale(), 1E-9);
    }

    @Test
    public void testGaussianKernelMatrix() {
        System.out.println("GaussianKernel matrix");
        GaussianKernel k = new GaussianKernel(1.0);
        double[][] pts = {x1, x2, x3};
        var K = k.K(pts);
        // Diagonal should be 1
        assertEquals(1.0, K.get(0, 0), 1E-9);
        assertEquals(1.0, K.get(1, 1), 1E-9);
        // Symmetry
        assertEquals(K.get(0, 1), K.get(1, 0), 1E-9);
    }

    // ===== LaplacianKernel =====

    @Test
    public void testLaplacianKernel() {
        System.out.println("LaplacianKernel");
        LaplacianKernel k = new LaplacianKernel(1.0);
        // k(x,x) = exp(0) = 1
        assertEquals(1.0, k.k(x1, x1), 1E-9);
        // k(x1,x2): dist=sqrt(2), k = exp(-sqrt(2))
        double dist12 = Math.sqrt(2.0);
        assertEquals(Math.exp(-dist12), k.k(x1, x2), 1E-9);
        // symmetry
        assertEquals(k.k(x1, x2), k.k(x2, x1), 1E-9);
        // bounded in (0, 1]
        assertTrue(k.k(x1, x2) > 0);
        assertTrue(k.k(x1, x2) <= 1);
    }

    @Test
    public void testLaplacianKernelGradient() {
        System.out.println("LaplacianKernel gradient");
        LaplacianKernel k = new LaplacianKernel(1.0);
        double[] kg = k.kg(x1, x2);
        assertEquals(2, kg.length);
        assertEquals(k.k(x1, x2), kg[0], 1E-9);
        assertTrue(kg[1] >= 0);
    }

    @Test
    public void testLaplacianKernelInvalidSigma() {
        assertThrows(IllegalArgumentException.class, () -> new LaplacianKernel(-0.5));
    }

    // ===== MaternKernel =====

    @Test
    public void testMaternKernelNu05() {
        System.out.println("MaternKernel nu=0.5");
        MaternKernel k = new MaternKernel(1.0, 0.5);
        // nu=0.5 is equivalent to Laplacian
        assertEquals(1.0, k.k(x1, x1), 1E-9);
        double dist12 = Math.sqrt(2.0);
        assertEquals(Math.exp(-dist12), k.k(x1, x2), 1E-9);
    }

    @Test
    public void testMaternKernelNu15() {
        System.out.println("MaternKernel nu=1.5");
        MaternKernel k = new MaternKernel(1.0, 1.5);
        assertEquals(1.0, k.k(x1, x1), 1E-9);
        double dist = Math.sqrt(2.0);
        double d = Math.sqrt(3) * dist;
        double expected = (1.0 + d) * Math.exp(-d);
        assertEquals(expected, k.k(x1, x2), 1E-9);
    }

    @Test
    public void testMaternKernelNu25() {
        System.out.println("MaternKernel nu=2.5");
        MaternKernel k = new MaternKernel(1.0, 2.5);
        assertEquals(1.0, k.k(x1, x1), 1E-9);
        double dist = Math.sqrt(2.0);
        double d = Math.sqrt(5) * dist;
        double expected = (1.0 + d + d*d/3) * Math.exp(-d);
        assertEquals(expected, k.k(x1, x2), 1E-9);
    }

    @Test
    public void testMaternKernelNuInf() {
        System.out.println("MaternKernel nu=Inf (equivalent to Gaussian)");
        MaternKernel mk = new MaternKernel(1.0, Double.POSITIVE_INFINITY);
        GaussianKernel gk = new GaussianKernel(1.0);
        assertEquals(gk.k(x1, x2), mk.k(x1, x2), 1E-9);
    }

    @Test
    public void testMaternKernelInvalidNu() {
        assertThrows(IllegalArgumentException.class, () -> new MaternKernel(1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new MaternKernel(1.0, 3.0));
    }

    @Test
    public void testMaternKernelGradient() {
        System.out.println("MaternKernel gradient");
        for (double nu : new double[]{0.5, 1.5, 2.5, Double.POSITIVE_INFINITY}) {
            MaternKernel k = new MaternKernel(1.0, nu);
            double[] kg = k.kg(x1, x2);
            assertEquals(2, kg.length);
            assertEquals(k.k(x1, x2), kg[0], 1E-9, "nu=" + nu);
        }
    }

    @Test
    public void testMaternKernelSymmetry() {
        System.out.println("MaternKernel symmetry");
        MaternKernel k = new MaternKernel(1.5, 1.5);
        assertEquals(k.k(x1, x2), k.k(x2, x1), 1E-9);
    }

    // ===== PolynomialKernel =====

    @Test
    public void testPolynomialKernel() {
        System.out.println("PolynomialKernel");
        PolynomialKernel k = new PolynomialKernel(2, 1.0, 0.0);
        // k(x1,x1) = (1*1 + 0)^2 = 1
        assertEquals(1.0, k.k(x1, x1), 1E-9);
        // k(x1,x2) = (0+0)^2 = 0
        assertEquals(0.0, k.k(x1, x2), 1E-9);
        // k(x1,x3) = (1*1+0)^2 = 1
        assertEquals(1.0, k.k(x1, x3), 1E-9);
    }

    @Test
    public void testPolynomialKernelWithOffset() {
        System.out.println("PolynomialKernel with offset");
        PolynomialKernel k = new PolynomialKernel(2, 1.0, 1.0);
        // k(x1,x1) = (1*1+1)^2 = 4
        assertEquals(4.0, k.k(x1, x1), 1E-9);
        // k(x1,x2) = (0+1)^2 = 1
        assertEquals(1.0, k.k(x1, x2), 1E-9);
    }

    @Test
    public void testPolynomialKernelGradient() {
        System.out.println("PolynomialKernel gradient");
        PolynomialKernel k = new PolynomialKernel(3, 0.5, 1.0);
        double[] kg = k.kg(x1, x3);
        assertEquals(3, kg.length);
        assertEquals(k.k(x1, x3), kg[0], 1E-9);
    }

    @Test
    public void testPolynomialKernelInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new PolynomialKernel(0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PolynomialKernel(2, 1.0, -1.0));
    }

    @Test
    public void testPolynomialKernelOf() {
        System.out.println("PolynomialKernel of");
        PolynomialKernel k = new PolynomialKernel(2, 1.0, 0.5);
        PolynomialKernel k2 = k.of(new double[]{2.0, 1.0});
        assertEquals(2.0, k2.scale(), 1E-9);
        assertEquals(1.0, k2.offset(), 1E-9);
    }

    // ===== HyperbolicTangentKernel =====

    @Test
    public void testHyperbolicTangentKernel() {
        System.out.println("HyperbolicTangentKernel");
        HyperbolicTangentKernel k = new HyperbolicTangentKernel(1.0, 0.0);
        // k(x1,x1) = tanh(1*1 + 0) = tanh(1)
        assertEquals(Math.tanh(1.0), k.k(x1, x1), 1E-9);
        // k(x1,x2) = tanh(0)=0
        assertEquals(0.0, k.k(x1, x2), 1E-9);
        // symmetry
        assertEquals(k.k(x1, x3), k.k(x3, x1), 1E-9);
    }

    @Test
    public void testHyperbolicTangentKernelGradient() {
        System.out.println("HyperbolicTangentKernel gradient");
        HyperbolicTangentKernel k = new HyperbolicTangentKernel(0.5, 0.1);
        double[] kg = k.kg(x1, x3);
        assertEquals(3, kg.length);
        assertEquals(k.k(x1, x3), kg[0], 1E-9);
    }

    // ===== ThinPlateSplineKernel =====

    @Test
    public void testThinPlateSplineKernel() {
        System.out.println("ThinPlateSplineKernel");
        ThinPlateSplineKernel k = new ThinPlateSplineKernel(1.0);
        // k(x,x): dist=0 -> should return 0 (not NaN)
        assertEquals(0.0, k.k(x1, x1), 1E-9);
        assertFalse(Double.isNaN(k.k(x1, x1)));
        // k(x1,x2): dist=sqrt(2), d=sqrt(2), k = 2*log(sqrt(2)) = log(2)
        double dist12 = Math.sqrt(2.0);
        double expected = dist12 * dist12 * Math.log(dist12);
        assertEquals(expected, k.k(x1, x2), 1E-9);
    }

    @Test
    public void testThinPlateSplineKernelZeroDistGradient() {
        System.out.println("ThinPlateSplineKernel zero dist gradient");
        ThinPlateSplineKernel k = new ThinPlateSplineKernel(1.0);
        double[] kg = k.kg(x1, x1);
        assertEquals(2, kg.length);
        assertEquals(0.0, kg[0], 1E-9);
        assertFalse(Double.isNaN(kg[0]));
        assertFalse(Double.isNaN(kg[1]));
    }

    @Test
    public void testThinPlateSplineKernelGradient() {
        System.out.println("ThinPlateSplineKernel gradient");
        ThinPlateSplineKernel k = new ThinPlateSplineKernel(1.0);
        double[] kg = k.kg(x1, x2);
        assertEquals(2, kg.length);
        assertEquals(k.k(x1, x2), kg[0], 1E-9);
        assertFalse(Double.isNaN(kg[1]));
    }

    @Test
    public void testThinPlateSplineKernelInvalidSigma() {
        assertThrows(IllegalArgumentException.class, () -> new ThinPlateSplineKernel(-1.0));
    }

    // ===== PearsonKernel =====

    @Test
    public void testPearsonKernel() {
        System.out.println("PearsonKernel");
        PearsonKernel k = new PearsonKernel(1.0, 1.0);
        // k(x,x): dist²=0, k = (1 + C*0)^(-omega) = 1
        assertEquals(1.0, k.k(x1, x1), 1E-9);
        // symmetry
        assertEquals(k.k(x1, x2), k.k(x2, x1), 1E-9);
        // bounded in (0,1]
        assertTrue(k.k(x1, x2) > 0);
        assertTrue(k.k(x1, x2) <= 1.0);
    }

    @Test
    public void testPearsonKernelGradient() {
        System.out.println("PearsonKernel gradient");
        PearsonKernel k = new PearsonKernel(1.0, 2.0);
        double[] kg = k.kg(x1, x2);
        assertEquals(2, kg.length);
        assertEquals(k.k(x1, x2), kg[0], 1E-9);
    }

    @Test
    public void testPearsonKernelOf() {
        System.out.println("PearsonKernel of");
        PearsonKernel k = new PearsonKernel(1.0, 2.0);
        PearsonKernel k2 = k.of(new double[]{2.0});
        assertEquals(2.0, k2.sigma(), 1E-9);
        assertEquals(2.0, k2.omega(), 1E-9);
    }

    @Test
    public void testPearsonKernelInvalid() {
        System.out.println("PearsonKernel invalid");
        assertThrows(IllegalArgumentException.class, () -> new PearsonKernel(0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new PearsonKernel(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new PearsonKernel(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PearsonKernel(1.0, -1.0));
    }

    // ===== HellingerKernel =====

    @Test
    public void testHellingerKernel() {
        System.out.println("HellingerKernel");
        HellingerKernel k = new HellingerKernel();
        double[] p = {0.25, 0.25, 0.25, 0.25};
        double[] q = {0.25, 0.25, 0.25, 0.25};
        // Same distribution: sum(sqrt(pi*qi)) = sum(0.25) = 1
        assertEquals(1.0, k.k(p, q), 1E-9);
        // Non-negative
        double[] r = {1.0, 0.0, 0.0, 0.0};
        double[] s = {0.0, 1.0, 0.0, 0.0};
        assertEquals(0.0, k.k(r, s), 1E-9);
    }

    @Test
    public void testHellingerKernelOf() {
        System.out.println("HellingerKernel of()");
        HellingerKernel k = new HellingerKernel();
        // Bug fix: of() should return HellingerKernel, not LinearKernel
        MercerKernel<double[]> k2 = k.of(new double[0]);
        assertInstanceOf(HellingerKernel.class, k2);
    }

    @Test
    public void testHellingerKernelLengthMismatch() {
        HellingerKernel k = new HellingerKernel();
        assertThrows(IllegalArgumentException.class,
                () -> k.k(new double[]{0.5, 0.5}, new double[]{0.5, 0.5, 0.0}));
    }

    // ===== SumKernel =====

    @Test
    public void testSumKernel() {
        System.out.println("SumKernel");
        GaussianKernel gk = new GaussianKernel(1.0);
        LinearKernel lk = new LinearKernel();
        SumKernel<double[]> sk = new SumKernel<>(gk, lk);
        double expected = gk.k(x1, x2) + lk.k(x1, x2);
        assertEquals(expected, sk.k(x1, x2), 1E-9);
        // symmetry
        assertEquals(sk.k(x1, x2), sk.k(x2, x1), 1E-9);
    }

    @Test
    public void testSumKernelGradient() {
        System.out.println("SumKernel gradient");
        GaussianKernel gk = new GaussianKernel(1.0);
        LinearKernel lk = new LinearKernel();
        SumKernel<double[]> sk = new SumKernel<>(gk, lk);
        double[] kg = sk.kg(x1, x2);
        // kg[0] should be the sum kernel value
        assertEquals(sk.k(x1, x2), kg[0], 1E-9);
        // length = (gk.kg.length - 1) + (lk.kg.length - 1) + 1 = 2 + 0 + 1 = 3... but lk has len 1
        // gk gradient has 2 elements, lk has 1 element => sum has 2+1-1=2 elements
        assertEquals(2, kg.length);
    }

    @Test
    public void testSumKernelHyperparameters() {
        System.out.println("SumKernel hyperparameters");
        GaussianKernel gk = new GaussianKernel(1.0);
        LaplacianKernel lk = new LaplacianKernel(2.0);
        SumKernel<double[]> sk = new SumKernel<>(gk, lk);
        double[] hp = sk.hyperparameters();
        assertEquals(2, hp.length);
        assertEquals(1.0, hp[0], 1E-9);
        assertEquals(2.0, hp[1], 1E-9);
    }

    @Test
    public void testSumKernelOf() {
        System.out.println("SumKernel of");
        GaussianKernel gk = new GaussianKernel(1.0);
        LaplacianKernel lk = new LaplacianKernel(2.0);
        SumKernel<double[]> sk = new SumKernel<>(gk, lk);
        MercerKernel<double[]> sk2 = sk.of(new double[]{3.0, 4.0});
        // new kernel should have updated parameters
        assertNotNull(sk2);
        double[] hp = sk2.hyperparameters();
        assertEquals(3.0, hp[0], 1E-9);
        assertEquals(4.0, hp[1], 1E-9);
    }

    // ===== ProductKernel =====

    @Test
    public void testProductKernel() {
        System.out.println("ProductKernel");
        GaussianKernel gk = new GaussianKernel(1.0);
        LinearKernel lk = new LinearKernel();
        ProductKernel<double[]> pk = new ProductKernel<>(gk, lk);
        double expected = gk.k(x1, x3) * lk.k(x1, x3);
        assertEquals(expected, pk.k(x1, x3), 1E-9);
        // symmetry
        assertEquals(pk.k(x1, x3), pk.k(x3, x1), 1E-9);
    }

    @Test
    public void testProductKernelGradientValue() {
        System.out.println("ProductKernel gradient value (bug fix)");
        GaussianKernel gk = new GaussianKernel(1.0);
        LaplacianKernel lk = new LaplacianKernel(1.0);
        ProductKernel<double[]> pk = new ProductKernel<>(gk, lk);
        double[] kg = pk.kg(x1, x3);
        // kg[0] must be gk.k * lk.k (product), NOT gk.k + lk.k (sum)
        double expected = gk.k(x1, x3) * lk.k(x1, x3);
        assertEquals(expected, kg[0], 1E-9);
    }

    @Test
    public void testProductKernelHyperparameters() {
        System.out.println("ProductKernel hyperparameters");
        GaussianKernel gk = new GaussianKernel(1.0);
        LaplacianKernel lk = new LaplacianKernel(2.0);
        ProductKernel<double[]> pk = new ProductKernel<>(gk, lk);
        double[] hp = pk.hyperparameters();
        assertEquals(2, hp.length);
        assertEquals(1.0, hp[0], 1E-9);
        assertEquals(2.0, hp[1], 1E-9);
    }

    // ===== Sparse kernels =====

    @Test
    public void testSparseGaussianKernel() {
        System.out.println("SparseGaussianKernel");
        SparseGaussianKernel k = new SparseGaussianKernel(1.0);
        SparseArray s1 = new SparseArray();
        s1.append(0, 1.0);
        SparseArray s2 = new SparseArray();
        s2.append(1, 1.0);

        // Same array: k(s1,s1)=1
        assertEquals(1.0, k.k(s1, s1), 1E-9);
        // symmetry
        assertEquals(k.k(s1, s2), k.k(s2, s1), 1E-9);
        // bounded
        assertTrue(k.k(s1, s2) > 0);
        assertTrue(k.k(s1, s2) <= 1);
    }

    @Test
    public void testSparseLaplacianKernel() {
        System.out.println("SparseLaplacianKernel");
        SparseLaplacianKernel k = new SparseLaplacianKernel(1.0);
        SparseArray s1 = new SparseArray();
        s1.append(0, 1.0);
        SparseArray s2 = new SparseArray();
        s2.append(0, 1.0);
        // Same: k=1
        assertEquals(1.0, k.k(s1, s2), 1E-9);
    }

    @Test
    public void testSparseLinearKernel() {
        System.out.println("SparseLinearKernel");
        SparseLinearKernel k = new SparseLinearKernel();
        SparseArray s1 = new SparseArray();
        s1.append(0, 3.0);
        s1.append(1, 4.0);
        SparseArray s2 = new SparseArray();
        s2.append(0, 1.0);
        s2.append(1, 2.0);
        // dot = 3*1 + 4*2 = 11
        assertEquals(11.0, k.k(s1, s2), 1E-9);
    }

    @Test
    public void testSparsePolynomialKernel() {
        System.out.println("SparsePolynomialKernel");
        SparsePolynomialKernel k = new SparsePolynomialKernel(2, 1.0, 1.0);
        SparseArray s1 = new SparseArray();
        s1.append(0, 1.0);
        // k(s1,s1) = (1*1 + 1)^2 = 4
        assertEquals(4.0, k.k(s1, s1), 1E-9);
    }

    @Test
    public void testSparseMaternKernel() {
        System.out.println("SparseMaternKernel");
        SparseMaternKernel k = new SparseMaternKernel(1.0, 1.5);
        SparseArray s1 = new SparseArray();
        s1.append(0, 1.0);
        // Same array: dist=0, k=1
        assertEquals(1.0, k.k(s1, s1), 1E-9);
    }

    @Test
    public void testSparseThinPlateSplineKernel() {
        System.out.println("SparseThinPlateSplineKernel");
        SparseThinPlateSplineKernel k = new SparseThinPlateSplineKernel(1.0);
        SparseArray s1 = new SparseArray();
        s1.append(0, 1.0);
        // Same array: dist=0, k should be 0 (not NaN)
        assertEquals(0.0, k.k(s1, s1), 1E-9);
        assertFalse(Double.isNaN(k.k(s1, s1)));
    }

    // ===== Binary sparse kernels =====

    @Test
    public void testBinarySparseGaussianKernel() {
        System.out.println("BinarySparseGaussianKernel");
        BinarySparseGaussianKernel k = new BinarySparseGaussianKernel(1.0);
        int[] b1 = {0, 2};
        int[] b2 = {1, 3};
        assertEquals(1.0, k.k(b1, b1), 1E-9);
        assertEquals(k.k(b1, b2), k.k(b2, b1), 1E-9);
        assertTrue(k.k(b1, b2) > 0);
    }

    @Test
    public void testBinarySparseLinearKernel() {
        System.out.println("BinarySparseLinearKernel");
        BinarySparseLinearKernel k = new BinarySparseLinearKernel();
        int[] b1 = {0, 1, 2};
        int[] b2 = {1, 2, 3};
        // intersection size = 2
        assertEquals(2.0, k.k(b1, b2), 1E-9);
        assertEquals(k.k(b1, b2), k.k(b2, b1), 1E-9);
    }

    @Test
    public void testBinarySparsePolynomialKernel() {
        System.out.println("BinarySparsePolynomialKernel");
        BinarySparsePolynomialKernel k = new BinarySparsePolynomialKernel(2, 1.0, 1.0);
        int[] b1 = {0, 1};
        // dot(b1,b1) = 2; k = (1*2+1)^2 = 9
        assertEquals(9.0, k.k(b1, b1), 1E-9);
    }

    @Test
    public void testBinarySparseLaplacianKernel() {
        System.out.println("BinarySparseLaplacianKernel");
        BinarySparseLaplacianKernel k = new BinarySparseLaplacianKernel(1.0);
        int[] b1 = {0, 1, 2};
        assertEquals(1.0, k.k(b1, b1), 1E-9);
    }

    @Test
    public void testBinarySparseMaternKernel() {
        System.out.println("BinarySparseMaternKernel");
        BinarySparseMaternKernel k = new BinarySparseMaternKernel(1.0, 1.5);
        int[] b1 = {0, 1};
        assertEquals(1.0, k.k(b1, b1), 1E-9);
    }

    @Test
    public void testBinarySparseThinPlateSplineKernel() {
        System.out.println("BinarySparseThinPlateSplineKernel");
        BinarySparseThinPlateSplineKernel k = new BinarySparseThinPlateSplineKernel(1.0);
        int[] b1 = {0, 1};
        // Same array: dist=0, k should be 0 (not NaN)
        assertEquals(0.0, k.k(b1, b1), 1E-9);
        assertFalse(Double.isNaN(k.k(b1, b1)));
    }

    @Test
    public void testBinarySparseHyperbolicTangentKernel() {
        System.out.println("BinarySparseHyperbolicTangentKernel");
        BinarySparseHyperbolicTangentKernel k = new BinarySparseHyperbolicTangentKernel(0.1, 0.0);
        int[] b1 = {0, 1, 2};
        int[] b2 = {0, 1, 2};
        assertEquals(k.k(b1, b2), k.k(b2, b1), 1E-9);
    }

    // ===== Positive-Semi-Definite (kernel matrix diagonal >= 0) =====

    @Test
    public void testKernelMatrixDiagonalNonNegative() {
        System.out.println("kernel matrix diagonal non-negative");
        double[][] pts = {x1, x2, x3};
        List<MercerKernel<double[]>> kernels = List.of(
            new GaussianKernel(1.0),
            new LaplacianKernel(1.0),
            new MaternKernel(1.0, 1.5),
            new MaternKernel(1.0, 2.5),
            new PolynomialKernel(2, 1.0, 1.0),
            new LinearKernel(),
            new ThinPlateSplineKernel(1.0)
        );
        for (MercerKernel<double[]> k : kernels) {
            var K = k.K(pts);
            for (int i = 0; i < pts.length; i++) {
                assertTrue(K.get(i, i) >= 0, k.toString() + " diagonal[" + i + "] < 0");
            }
        }
    }

    @Test
    public void testKernelSymmetry() {
        System.out.println("kernel symmetry");
        double[][] pts = {x1, x2, x3};
        List<MercerKernel<double[]>> kernels = List.of(
            new GaussianKernel(1.0),
            new LaplacianKernel(1.0),
            new MaternKernel(1.0, 1.5),
            new PolynomialKernel(2, 1.0, 1.0),
            new LinearKernel(),
            new ThinPlateSplineKernel(1.0)
        );
        for (MercerKernel<double[]> k : kernels) {
            for (int i = 0; i < pts.length; i++) {
                for (int j = 0; j < pts.length; j++) {
                    assertEquals(k.k(pts[i], pts[j]), k.k(pts[j], pts[i]), 1E-9,
                            k.toString() + " not symmetric at [" + i + "," + j + "]");
                }
            }
        }
    }

    // ===== MercerKernel.of() parsing =====

    @Test
    public void testParse() {
        System.out.println("parse");
        MercerKernel.of("linear()");
        MercerKernel.of("polynomial(2, 0.1, 0.0)");
        MercerKernel.of("gaussian(0.1)");
        MercerKernel.of("matern(0.1, 1.5)");
        MercerKernel.of("laplacian(0.1)");
        MercerKernel.of("tanh(0.1, 0.0)");
        MercerKernel.of("tps(0.1)");
        MercerKernel.of("pearson(0.1, 1.0)");
        MercerKernel.of("hellinger");
    }

    @Test
    public void testParseToString() {
        System.out.println("parse toString round-trip");
        MercerKernel.of(new LinearKernel().toString());
        MercerKernel.of(new PolynomialKernel(2, 0.1, 0.0).toString());
        MercerKernel.of(new GaussianKernel(0.1).toString());
        MercerKernel.of(new MaternKernel(0.1, 1.5).toString());
        MercerKernel.of(new LaplacianKernel(0.1).toString());
        MercerKernel.of(new HyperbolicTangentKernel(0.1, 0.0).toString());
        MercerKernel.of(new ThinPlateSplineKernel(0.1).toString());
        MercerKernel.of(new PearsonKernel(0.1, 1.0).toString());
        MercerKernel.of(new HellingerKernel().toString());
    }

    @Test
    public void testParseValues() {
        System.out.println("parse values");
        GaussianKernel gk = (GaussianKernel) MercerKernel.of("gaussian(2.5)");
        assertEquals(2.5, gk.scale(), 1E-9);

        LaplacianKernel lk = (LaplacianKernel) MercerKernel.of("laplacian(3.0)");
        assertEquals(3.0, lk.scale(), 1E-9);

        PolynomialKernel pk = (PolynomialKernel) MercerKernel.of("polynomial(3, 0.5, 1.0)");
        assertEquals(3, pk.degree());
        assertEquals(0.5, pk.scale(), 1E-6);
        assertEquals(1.0, pk.offset(), 1E-6);
    }

    @Test
    public void testParseUnknown() {
        assertThrows(IllegalArgumentException.class, () -> MercerKernel.of("unknown(1.0)"));
    }

    @Test
    public void testSparseParse() {
        System.out.println("sparse parse");
        MercerKernel.sparse("linear()");
        MercerKernel.sparse("gaussian(1.0)");
        MercerKernel.sparse("laplacian(1.0)");
        MercerKernel.sparse("polynomial(2, 1.0, 0.0)");
        MercerKernel.sparse("matern(1.0, 1.5)");
        MercerKernel.sparse("tanh(0.5, 0.0)");
        MercerKernel.sparse("tps(1.0)");
    }

    @Test
    public void testBinaryParse() {
        System.out.println("binary parse");
        MercerKernel.binary("linear()");
        MercerKernel.binary("gaussian(1.0)");
        MercerKernel.binary("laplacian(1.0)");
        MercerKernel.binary("polynomial(2, 1.0, 0.0)");
        MercerKernel.binary("matern(1.0, 1.5)");
        MercerKernel.binary("tanh(0.5, 0.0)");
        MercerKernel.binary("tps(1.0)");
    }

    @Test
    public void testParseUnknownSparse() {
        assertThrows(IllegalArgumentException.class, () -> MercerKernel.sparse("unknown(1.0)"));
    }

    @Test
    public void testParseUnknownBinary() {
        assertThrows(IllegalArgumentException.class, () -> MercerKernel.binary("unknown(1.0)"));
    }
}

