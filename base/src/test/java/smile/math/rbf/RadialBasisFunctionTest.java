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
package smile.math.rbf;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the smile.math.rbf package.
 *
 * @author Haifeng Li
 */
public class RadialBasisFunctionTest {

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

    // ===== GaussianRadialBasis =====

    @Test
    public void testGaussianAtZero() {
        System.out.println("GaussianRadialBasis at r=0");
        GaussianRadialBasis rbf = new GaussianRadialBasis(1.0);
        // φ(0) = exp(0) = 1
        assertEquals(1.0, rbf.f(0.0), 1E-12);
    }

    @Test
    public void testGaussianKnownValue() {
        System.out.println("GaussianRadialBasis known value");
        double r0 = 2.0;
        GaussianRadialBasis rbf = new GaussianRadialBasis(r0);
        double r = 1.0;
        // φ(r) = exp(-0.5 * (r/r0)^2) = exp(-0.5 * 0.25) = exp(-0.125)
        assertEquals(Math.exp(-0.5 * (r / r0) * (r / r0)), rbf.f(r), 1E-12);
    }

    @Test
    public void testGaussianDecaysToZero() {
        System.out.println("GaussianRadialBasis decays to zero");
        GaussianRadialBasis rbf = new GaussianRadialBasis(1.0);
        // For large r, φ(r) → 0
        assertTrue(rbf.f(100.0) < 1e-10);
        assertTrue(rbf.f(1000.0) < 1e-100);
    }

    @Test
    public void testGaussianMonotonicallyDecreasing() {
        System.out.println("GaussianRadialBasis monotonically decreasing");
        GaussianRadialBasis rbf = new GaussianRadialBasis(1.0);
        double prev = rbf.f(0.0);
        for (double r = 0.1; r <= 5.0; r += 0.1) {
            double curr = rbf.f(r);
            assertTrue(curr < prev, "Not decreasing at r=" + r);
            prev = curr;
        }
    }

    @Test
    public void testGaussianBounded() {
        System.out.println("GaussianRadialBasis bounded in (0,1]");
        GaussianRadialBasis rbf = new GaussianRadialBasis(1.5);
        for (double r = 0.0; r <= 10.0; r += 0.5) {
            double v = rbf.f(r);
            assertTrue(v > 0, "Not positive at r=" + r);
            assertTrue(v <= 1.0, "Exceeds 1 at r=" + r);
        }
    }

    @Test
    public void testGaussianScaleAccessor() {
        System.out.println("GaussianRadialBasis scale accessor");
        GaussianRadialBasis rbf = new GaussianRadialBasis(3.14);
        assertEquals(3.14, rbf.scale(), 1E-12);
    }

    @Test
    public void testGaussianDefaultScale() {
        System.out.println("GaussianRadialBasis default scale");
        GaussianRadialBasis rbf = new GaussianRadialBasis();
        assertEquals(1.0, rbf.scale(), 1E-12);
        assertEquals(1.0, rbf.f(0.0), 1E-12);
    }

    @Test
    public void testGaussianInvalidScale() {
        System.out.println("GaussianRadialBasis invalid scale");
        assertThrows(IllegalArgumentException.class, () -> new GaussianRadialBasis(0.0));
        assertThrows(IllegalArgumentException.class, () -> new GaussianRadialBasis(-1.0));
    }

    @Test
    public void testGaussianToString() {
        System.out.println("GaussianRadialBasis toString");
        String s = new GaussianRadialBasis(1.5).toString();
        assertTrue(s.contains("Gaussian"), "Missing 'Gaussian': " + s);
        assertTrue(s.contains("1.5"), "Missing scale value: " + s);
    }

    @Test
    public void testGaussianScaleEffect() {
        System.out.println("GaussianRadialBasis scale effect");
        GaussianRadialBasis small = new GaussianRadialBasis(0.5);
        GaussianRadialBasis large = new GaussianRadialBasis(2.0);
        // For fixed r > 0, smaller scale → faster decay → smaller value
        assertTrue(small.f(1.0) < large.f(1.0));
    }

    // ===== MultiquadricRadialBasis =====

    @Test
    public void testMultiquadricAtZero() {
        System.out.println("MultiquadricRadialBasis at r=0");
        MultiquadricRadialBasis rbf = new MultiquadricRadialBasis(1.0);
        // φ(0) = sqrt(0 + r0^2) = r0
        assertEquals(1.0, rbf.f(0.0), 1E-12);
    }

    @Test
    public void testMultiquadricKnownValue() {
        System.out.println("MultiquadricRadialBasis known value");
        double r0 = 2.0;
        MultiquadricRadialBasis rbf = new MultiquadricRadialBasis(r0);
        double r = 3.0;
        // φ(r) = sqrt(9 + 4) = sqrt(13)
        assertEquals(Math.sqrt(r * r + r0 * r0), rbf.f(r), 1E-12);
    }

    @Test
    public void testMultiquadricMonotonicallyIncreasing() {
        System.out.println("MultiquadricRadialBasis monotonically increasing");
        MultiquadricRadialBasis rbf = new MultiquadricRadialBasis(1.0);
        double prev = rbf.f(0.0);
        for (double r = 0.1; r <= 5.0; r += 0.1) {
            double curr = rbf.f(r);
            assertTrue(curr > prev, "Not increasing at r=" + r);
            prev = curr;
        }
    }

    @Test
    public void testMultiquadricPositive() {
        System.out.println("MultiquadricRadialBasis always positive");
        MultiquadricRadialBasis rbf = new MultiquadricRadialBasis(1.0);
        for (double r = 0.0; r <= 10.0; r += 0.5) {
            assertTrue(rbf.f(r) > 0, "Not positive at r=" + r);
        }
    }

    @Test
    public void testMultiquadricScaleAccessor() {
        System.out.println("MultiquadricRadialBasis scale accessor");
        MultiquadricRadialBasis rbf = new MultiquadricRadialBasis(2.5);
        assertEquals(2.5, rbf.scale(), 1E-12);
    }

    @Test
    public void testMultiquadricDefaultScale() {
        System.out.println("MultiquadricRadialBasis default scale");
        MultiquadricRadialBasis rbf = new MultiquadricRadialBasis();
        assertEquals(1.0, rbf.scale(), 1E-12);
        assertEquals(1.0, rbf.f(0.0), 1E-12);
    }

    @Test
    public void testMultiquadricInvalidScale() {
        System.out.println("MultiquadricRadialBasis invalid scale");
        assertThrows(IllegalArgumentException.class, () -> new MultiquadricRadialBasis(0.0));
        assertThrows(IllegalArgumentException.class, () -> new MultiquadricRadialBasis(-2.0));
    }

    @Test
    public void testMultiquadricToString() {
        System.out.println("MultiquadricRadialBasis toString");
        String s = new MultiquadricRadialBasis(2.0).toString();
        assertTrue(s.contains("Multiquadric"), "Missing 'Multiquadric': " + s);
        // toString should show r0=2.0, not r0^2=4.0
        assertTrue(s.contains("2.0"), "Should show r0 value: " + s);
        assertFalse(s.contains("4.0"), "Should NOT show r0^2: " + s);
    }

    // ===== InverseMultiquadricRadialBasis =====

    @Test
    public void testInverseMultiquadricAtZero() {
        System.out.println("InverseMultiquadricRadialBasis at r=0");
        InverseMultiquadricRadialBasis rbf = new InverseMultiquadricRadialBasis(1.0);
        // φ(0) = 1/sqrt(0 + 1) = 1
        assertEquals(1.0, rbf.f(0.0), 1E-12);
    }

    @Test
    public void testInverseMultiquadricKnownValue() {
        System.out.println("InverseMultiquadricRadialBasis known value");
        double r0 = 2.0;
        InverseMultiquadricRadialBasis rbf = new InverseMultiquadricRadialBasis(r0);
        double r = 3.0;
        // φ(r) = 1/sqrt(9 + 4) = 1/sqrt(13)
        assertEquals(1.0 / Math.sqrt(r * r + r0 * r0), rbf.f(r), 1E-12);
    }

    @Test
    public void testInverseMultiquadricDecaysToZero() {
        System.out.println("InverseMultiquadricRadialBasis decays to zero");
        InverseMultiquadricRadialBasis rbf = new InverseMultiquadricRadialBasis(1.0);
        assertTrue(rbf.f(1000.0) < 1e-3);
        assertTrue(rbf.f(1000.0) > 0);
    }

    @Test
    public void testInverseMultiquadricMonotonicallyDecreasing() {
        System.out.println("InverseMultiquadricRadialBasis monotonically decreasing");
        InverseMultiquadricRadialBasis rbf = new InverseMultiquadricRadialBasis(1.0);
        double prev = rbf.f(0.0);
        for (double r = 0.1; r <= 5.0; r += 0.1) {
            double curr = rbf.f(r);
            assertTrue(curr < prev, "Not decreasing at r=" + r);
            prev = curr;
        }
    }

    @Test
    public void testInverseMultiquadricBounded() {
        System.out.println("InverseMultiquadricRadialBasis bounded in (0,1]");
        InverseMultiquadricRadialBasis rbf = new InverseMultiquadricRadialBasis(1.5);
        for (double r = 0.0; r <= 10.0; r += 0.5) {
            double v = rbf.f(r);
            assertTrue(v > 0, "Not positive at r=" + r);
            assertTrue(v <= 1.0, "Exceeds 1 at r=" + r);
        }
    }

    @Test
    public void testInverseMultiquadricScaleAccessor() {
        System.out.println("InverseMultiquadricRadialBasis scale accessor");
        InverseMultiquadricRadialBasis rbf = new InverseMultiquadricRadialBasis(3.0);
        assertEquals(3.0, rbf.scale(), 1E-12);
    }

    @Test
    public void testInverseMultiquadricDefaultScale() {
        System.out.println("InverseMultiquadricRadialBasis default scale");
        InverseMultiquadricRadialBasis rbf = new InverseMultiquadricRadialBasis();
        assertEquals(1.0, rbf.scale(), 1E-12);
    }

    @Test
    public void testInverseMultiquadricInvalidScale() {
        System.out.println("InverseMultiquadricRadialBasis invalid scale");
        assertThrows(IllegalArgumentException.class, () -> new InverseMultiquadricRadialBasis(0.0));
        assertThrows(IllegalArgumentException.class, () -> new InverseMultiquadricRadialBasis(-1.5));
    }

    @Test
    public void testInverseMultiquadricToString() {
        System.out.println("InverseMultiquadricRadialBasis toString");
        String s = new InverseMultiquadricRadialBasis(2.0).toString();
        assertTrue(s.contains("InverseMultiquadric"), "Missing 'InverseMultiquadric': " + s);
        // toString should show r0=2.0, not r0^2=4.0
        assertTrue(s.contains("2.0"), "Should show r0 value: " + s);
        assertFalse(s.contains("4.0"), "Should NOT show r0^2: " + s);
    }

    @Test
    public void testMultiquadricInverseRelationship() {
        System.out.println("Multiquadric vs InverseMultiquadric relationship");
        double r0 = 1.5;
        MultiquadricRadialBasis mq = new MultiquadricRadialBasis(r0);
        InverseMultiquadricRadialBasis imq = new InverseMultiquadricRadialBasis(r0);
        for (double r = 0.0; r <= 5.0; r += 0.5) {
            assertEquals(1.0 / mq.f(r), imq.f(r), 1E-12,
                    "IMQ should be 1/MQ at r=" + r);
        }
    }

    // ===== ThinPlateRadialBasis =====

    @Test
    public void testThinPlateAtZero() {
        System.out.println("ThinPlateRadialBasis at r=0");
        ThinPlateRadialBasis rbf = new ThinPlateRadialBasis(1.0);
        // φ(0) = 0 (limit value)
        assertEquals(0.0, rbf.f(0.0), 1E-12);
        assertFalse(Double.isNaN(rbf.f(0.0)));
    }

    @Test
    public void testThinPlateAtSmallPositive() {
        System.out.println("ThinPlateRadialBasis at small positive r");
        ThinPlateRadialBasis rbf = new ThinPlateRadialBasis(1.0);
        // φ(r) = r^2 * log(r/r0); for r < r0: log is negative, φ < 0
        assertTrue(rbf.f(0.5) < 0.0);
    }

    @Test
    public void testThinPlateAtScalePoint() {
        System.out.println("ThinPlateRadialBasis at r=r0");
        ThinPlateRadialBasis rbf = new ThinPlateRadialBasis(2.0);
        // φ(r0) = r0^2 * log(1) = 0
        assertEquals(0.0, rbf.f(2.0), 1E-12);
    }

    @Test
    public void testThinPlateKnownValue() {
        System.out.println("ThinPlateRadialBasis known value");
        double r0 = 1.0;
        ThinPlateRadialBasis rbf = new ThinPlateRadialBasis(r0);
        double r = Math.E;
        // φ(e) = e^2 * log(e/1) = e^2 * 1 = e^2
        assertEquals(Math.E * Math.E, rbf.f(r), 1E-10);
    }

    @Test
    public void testThinPlatePositiveForLargeR() {
        System.out.println("ThinPlateRadialBasis positive for r > r0");
        ThinPlateRadialBasis rbf = new ThinPlateRadialBasis(1.0);
        for (double r = 1.1; r <= 10.0; r += 0.5) {
            assertTrue(rbf.f(r) > 0, "Not positive for r=" + r);
        }
    }

    @Test
    public void testThinPlateScaleAccessor() {
        System.out.println("ThinPlateRadialBasis scale accessor");
        ThinPlateRadialBasis rbf = new ThinPlateRadialBasis(2.5);
        assertEquals(2.5, rbf.scale(), 1E-12);
    }

    @Test
    public void testThinPlateDefaultScale() {
        System.out.println("ThinPlateRadialBasis default scale");
        ThinPlateRadialBasis rbf = new ThinPlateRadialBasis();
        assertEquals(1.0, rbf.scale(), 1E-12);
    }

    @Test
    public void testThinPlateInvalidScale() {
        System.out.println("ThinPlateRadialBasis invalid scale");
        assertThrows(IllegalArgumentException.class, () -> new ThinPlateRadialBasis(0.0));
        assertThrows(IllegalArgumentException.class, () -> new ThinPlateRadialBasis(-1.0));
    }

    @Test
    public void testThinPlateToString() {
        System.out.println("ThinPlateRadialBasis toString");
        String s = new ThinPlateRadialBasis(1.5).toString();
        assertTrue(s.contains("ThinPlate"), "Missing 'ThinPlate': " + s);
        assertTrue(s.contains("1.5"), "Missing scale value: " + s);
    }

    // ===== InverseQuadraticRadialBasis =====

    @Test
    public void testInverseQuadraticAtZero() {
        System.out.println("InverseQuadraticRadialBasis at r=0");
        InverseQuadraticRadialBasis rbf = new InverseQuadraticRadialBasis(1.0);
        // φ(0) = 1/(1+0) = 1
        assertEquals(1.0, rbf.f(0.0), 1E-12);
    }

    @Test
    public void testInverseQuadraticKnownValue() {
        System.out.println("InverseQuadraticRadialBasis known value");
        double r0 = 2.0;
        InverseQuadraticRadialBasis rbf = new InverseQuadraticRadialBasis(r0);
        double r = 2.0;
        // φ(r) = 1/(1+(r/r0)^2) = 1/(1+1) = 0.5
        assertEquals(0.5, rbf.f(r), 1E-12);
    }

    @Test
    public void testInverseQuadraticDecaysToZero() {
        System.out.println("InverseQuadraticRadialBasis decays to zero");
        InverseQuadraticRadialBasis rbf = new InverseQuadraticRadialBasis(1.0);
        assertTrue(rbf.f(1000.0) < 1e-6);
        assertTrue(rbf.f(1000.0) > 0);
    }

    @Test
    public void testInverseQuadraticMonotonicallyDecreasing() {
        System.out.println("InverseQuadraticRadialBasis monotonically decreasing");
        InverseQuadraticRadialBasis rbf = new InverseQuadraticRadialBasis(1.0);
        double prev = rbf.f(0.0);
        for (double r = 0.1; r <= 5.0; r += 0.1) {
            double curr = rbf.f(r);
            assertTrue(curr < prev, "Not decreasing at r=" + r);
            prev = curr;
        }
    }

    @Test
    public void testInverseQuadraticBounded() {
        System.out.println("InverseQuadraticRadialBasis bounded in (0,1]");
        InverseQuadraticRadialBasis rbf = new InverseQuadraticRadialBasis(1.0);
        for (double r = 0.0; r <= 10.0; r += 0.5) {
            double v = rbf.f(r);
            assertTrue(v > 0, "Not positive at r=" + r);
            assertTrue(v <= 1.0, "Exceeds 1 at r=" + r);
        }
    }

    @Test
    public void testInverseQuadraticScaleAccessor() {
        System.out.println("InverseQuadraticRadialBasis scale accessor");
        InverseQuadraticRadialBasis rbf = new InverseQuadraticRadialBasis(4.0);
        assertEquals(4.0, rbf.scale(), 1E-12);
    }

    @Test
    public void testInverseQuadraticDefaultScale() {
        System.out.println("InverseQuadraticRadialBasis default scale");
        InverseQuadraticRadialBasis rbf = new InverseQuadraticRadialBasis();
        assertEquals(1.0, rbf.scale(), 1E-12);
        assertEquals(1.0, rbf.f(0.0), 1E-12);
    }

    @Test
    public void testInverseQuadraticInvalidScale() {
        System.out.println("InverseQuadraticRadialBasis invalid scale");
        assertThrows(IllegalArgumentException.class, () -> new InverseQuadraticRadialBasis(0.0));
        assertThrows(IllegalArgumentException.class, () -> new InverseQuadraticRadialBasis(-0.5));
    }

    @Test
    public void testInverseQuadraticToString() {
        System.out.println("InverseQuadraticRadialBasis toString");
        String s = new InverseQuadraticRadialBasis(2.0).toString();
        assertTrue(s.contains("InverseQuadratic"), "Missing 'InverseQuadratic': " + s);
        assertTrue(s.contains("2.0"), "Missing scale: " + s);
    }

    // ===== PolyharmonicSpline =====

    @Test
    public void testPolyharmonicAtZero() {
        System.out.println("PolyharmonicSpline at r=0");
        for (int k = 1; k <= 4; k++) {
            PolyharmonicSpline rbf = new PolyharmonicSpline(k);
            assertEquals(0.0, rbf.f(0.0), 1E-12, "k=" + k);
            assertFalse(Double.isNaN(rbf.f(0.0)), "NaN at r=0 for k=" + k);
        }
    }

    @Test
    public void testPolyharmonicOddOrder() {
        System.out.println("PolyharmonicSpline odd order k");
        PolyharmonicSpline rbf = new PolyharmonicSpline(3);
        double r = 2.0;
        // φ(r) = r^3 = 8
        assertEquals(8.0, rbf.f(r), 1E-12);
    }

    @Test
    public void testPolyharmonicEvenOrder() {
        System.out.println("PolyharmonicSpline even order k");
        PolyharmonicSpline rbf = new PolyharmonicSpline(2);
        double r = Math.E;
        // φ(r) = r^2 * log(r) = e^2 * 1 = e^2
        assertEquals(Math.E * Math.E, rbf.f(r), 1E-10);
    }

    @Test
    public void testPolyharmonicOrder2EqualsThinPlate() {
        System.out.println("PolyharmonicSpline k=2 equals ThinPlateRadialBasis with r0=1");
        PolyharmonicSpline poly = new PolyharmonicSpline(2);
        ThinPlateRadialBasis tps = new ThinPlateRadialBasis(1.0);
        for (double r = 0.0; r <= 5.0; r += 0.5) {
            assertEquals(tps.f(r), poly.f(r), 1E-12, "Mismatch at r=" + r);
        }
    }

    @Test
    public void testPolyharmonicOrder1Linear() {
        System.out.println("PolyharmonicSpline k=1 is linear");
        PolyharmonicSpline rbf = new PolyharmonicSpline(1);
        assertEquals(0.0, rbf.f(0.0), 1E-12);
        assertEquals(3.0, rbf.f(3.0), 1E-12);
        assertEquals(5.0, rbf.f(5.0), 1E-12);
    }

    @Test
    public void testPolyharmonicOrder3Cubic() {
        System.out.println("PolyharmonicSpline k=3 is cubic");
        PolyharmonicSpline rbf = new PolyharmonicSpline(3);
        assertEquals(0.0, rbf.f(0.0), 1E-12);
        assertEquals(1.0, rbf.f(1.0), 1E-12);
        assertEquals(8.0, rbf.f(2.0), 1E-12);
        assertEquals(27.0, rbf.f(3.0), 1E-12);
    }

    @Test
    public void testPolyharmonicOrder4() {
        System.out.println("PolyharmonicSpline k=4");
        PolyharmonicSpline rbf = new PolyharmonicSpline(4);
        // φ(2) = 2^4 * log(2) = 16 * log(2)
        assertEquals(16.0 * Math.log(2.0), rbf.f(2.0), 1E-12);
    }

    @Test
    public void testPolyharmonicOrderAccessor() {
        System.out.println("PolyharmonicSpline order accessor");
        PolyharmonicSpline rbf = new PolyharmonicSpline(5);
        assertEquals(5, rbf.order());
    }

    @Test
    public void testPolyharmonicDefaultOrder() {
        System.out.println("PolyharmonicSpline default order is 2");
        PolyharmonicSpline rbf = new PolyharmonicSpline();
        assertEquals(2, rbf.order());
    }

    @Test
    public void testPolyharmonicInvalidOrder() {
        System.out.println("PolyharmonicSpline invalid order");
        assertThrows(IllegalArgumentException.class, () -> new PolyharmonicSpline(0));
        assertThrows(IllegalArgumentException.class, () -> new PolyharmonicSpline(-1));
    }

    @Test
    public void testPolyharmonicToString() {
        System.out.println("PolyharmonicSpline toString");
        String s = new PolyharmonicSpline(3).toString();
        assertTrue(s.contains("Polyharmonic"), "Missing 'Polyharmonic': " + s);
        assertTrue(s.contains("3"), "Missing order value: " + s);
    }

    // ===== Cross-RBF properties =====

    @Test
    public void testAllRBFsPositiveAtZero() {
        System.out.println("All compact RBFs positive at r=0");
        // Gaussian, IMQ, IQ all return 1 at r=0
        assertEquals(1.0, new GaussianRadialBasis(1.0).f(0.0), 1E-12);
        assertEquals(1.0, new InverseMultiquadricRadialBasis(1.0).f(0.0), 1E-12);
        assertEquals(1.0, new InverseQuadraticRadialBasis(1.0).f(0.0), 1E-12);
        // MQ returns r0 at r=0
        assertEquals(1.0, new MultiquadricRadialBasis(1.0).f(0.0), 1E-12);
        // ThinPlate and Polyharmonic return 0 at r=0
        assertEquals(0.0, new ThinPlateRadialBasis(1.0).f(0.0), 1E-12);
        assertEquals(0.0, new PolyharmonicSpline(2).f(0.0), 1E-12);
    }

    @Test
    public void testDecayRBFsBoundedByOne() {
        System.out.println("Decaying RBFs bounded by 1");
        RadialBasisFunction[] decayRbfs = {
            new GaussianRadialBasis(1.0),
            new InverseMultiquadricRadialBasis(1.0),
            new InverseQuadraticRadialBasis(1.0)
        };
        for (RadialBasisFunction rbf : decayRbfs) {
            for (double r = 0.0; r <= 10.0; r += 0.5) {
                double v = rbf.f(r);
                assertTrue(v <= 1.0 + 1E-12, rbf + " exceeds 1 at r=" + r);
                assertTrue(v >= 0, rbf + " negative at r=" + r);
            }
        }
    }

    @Test
    public void testFunctionInterfaceDelegate() {
        System.out.println("RadialBasisFunction Function interface delegation");
        // f() and apply() should return same value
        GaussianRadialBasis rbf = new GaussianRadialBasis(1.0);
        double r = 1.5;
        assertEquals(rbf.f(r), rbf.apply(r), 1E-12);
    }

    @Test
    public void testSerializableInterface() {
        System.out.println("RBF classes implement Serializable via Function");
        // All RBF classes extend Function which is Serializable
        assertTrue(new GaussianRadialBasis() instanceof java.io.Serializable);
        assertTrue(new MultiquadricRadialBasis() instanceof java.io.Serializable);
        assertTrue(new InverseMultiquadricRadialBasis() instanceof java.io.Serializable);
        assertTrue(new ThinPlateRadialBasis() instanceof java.io.Serializable);
        assertTrue(new InverseQuadraticRadialBasis() instanceof java.io.Serializable);
        assertTrue(new PolyharmonicSpline() instanceof java.io.Serializable);
    }
}

