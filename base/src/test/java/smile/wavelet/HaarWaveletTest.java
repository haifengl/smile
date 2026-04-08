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
 * Tests for HaarWavelet.
 *
 * @author Haifeng Li
 */
public class HaarWaveletTest {

    static final double[] SIGNAL = {.2,-.4,-.6,-.5,-.8,-.4,-.9,0,-.2,.1,-.1,.1,.7,.9,0,.3};
    static final double TOLERANCE = 1E-7;

    /** Roundtrip: transform then inverse should recover original signal. */
    @Test
    public void testRoundTrip() {
        double[] a = SIGNAL.clone();
        double[] b = SIGNAL.clone();
        HaarWavelet w = new HaarWavelet();
        w.transform(a);
        w.inverse(a);
        assertArrayEquals(b, a, TOLERANCE);
    }

    /** Parseval's theorem: energy is preserved by an orthogonal transform. */
    @Test
    public void testEnergyPreservation() {
        double[] a = SIGNAL.clone();
        double energy = energy(a);
        new HaarWavelet().transform(a);
        assertEquals(energy, energy(a), 1E-7);
    }

    /** transform and inverse on minimal-length signal (n == 2). */
    @Test
    public void testMinimalLength() {
        double[] a = {1.0, -1.0};
        double[] b = a.clone();
        HaarWavelet w = new HaarWavelet();
        w.transform(a);
        w.inverse(a);
        assertArrayEquals(b, a, TOLERANCE);
    }

    /** Non-power-of-2 length must throw. */
    @Test
    public void testNonPowerOf2Throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new HaarWavelet().transform(new double[]{1, 2, 3}));
    }

    /** Known Haar forward step on [1,1,1,1]: all detail coeffs should be 0. */
    @Test
    public void testKnownHaarCoefficients() {
        // Constant signal: all detail wavelet coefficients must be 0
        double[] a = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
        new HaarWavelet().transform(a);
        // Only a[0] (global mean) should be non-zero
        for (int i = 1; i < a.length; i++) {
            assertEquals(0.0, a[i], TOLERANCE, "Detail coefficient at " + i + " should be 0 for constant signal");
        }
    }

    private static double energy(double[] a) {
        double sum = 0;
        for (double v : a) sum += v * v;
        return sum;
    }
}