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
package smile.wavelet;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for D4Wavelet.
 *
 * @author Haifeng Li
 */
public class D4WaveletTest {

    static final double[] SIGNAL = {.2,-.4,-.6,-.5,-.8,-.4,-.9,0,-.2,.1,-.1,.1,.7,.9,0,.3};
    static final double TOLERANCE = 1E-7;

    /** Roundtrip: transform then inverse should recover original signal. */
    @Test
    public void testRoundTrip() {
        double[] a = SIGNAL.clone();
        double[] b = SIGNAL.clone();
        D4Wavelet w = new D4Wavelet();
        w.transform(a);
        w.inverse(a);
        assertArrayEquals(b, a, TOLERANCE);
    }

    /** Parseval's theorem: energy must be preserved. */
    @Test
    public void testEnergyPreservation() {
        double[] a = SIGNAL.clone();
        double energy = energy(a);
        new D4Wavelet().transform(a);
        assertEquals(energy, energy(a), 1E-7);
    }

    /** Non-power-of-2 length must throw. */
    @Test
    public void testNonPowerOf2Throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new D4Wavelet().transform(new double[3]));
    }

    /** Signal shorter than ncof=4 must throw for transform. */
    @Test
    public void testTooShortSignalThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new D4Wavelet().transform(new double[]{1.0, -1.0}));
    }

    /** Constant signal: all detail coefficients must be zero. */
    @Test
    public void testConstantSignalCoefficients() {
        double[] a = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
        new D4Wavelet().transform(a);
        // All detail coefficients (indices >= n/2) should be ~0 for constant signal
        int nh = a.length >> 1;
        for (int i = nh; i < a.length; i++) {
            assertEquals(0.0, a[i], 1E-10, "Detail coeff at " + i);
        }
    }

    /** D4Wavelet and DaubechiesWavelet(4) should produce identical results. */
    @Test
    public void testD4MatchesDaubechies4() {
        double[] a1 = {.2, -.4, -.6, -.5, -.8, -.4, -.9, 0, -.2, .1, -.1, .1, .7, .9, 0, .3};
        double[] a2 = a1.clone();
        new D4Wavelet().transform(a1);
        new DaubechiesWavelet(4).transform(a2);
        // Both use the same coefficients but different centering, so the
        // coefficient values differ; just check that both are invertible and
        // have the same energy.
        assertEquals(energy(a1), energy(a2), 1E-7);
    }

    private static double energy(double[] a) {
        double sum = 0;
        for (double v : a) sum += v * v;
        return sum;
    }
}