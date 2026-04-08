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
 * Tests for DaubechiesWavelet.
 *
 * @author Haifeng Li
 */
public class DaubechiesWaveletTest {

    static final double[] SIGNAL32 = {
            .2, -.4, -.6, -.5, -.8, -.4, -.9, 0, -.2, .1, -.1, .1, .7, .9, 0, .3,
            .2, -.4, -.6, -.5, -.8, -.4, -.9, 0, -.2, .1, -.1, .1, .7, .9, 0, .3
    };

    /** Roundtrip for all supported orders. */
    @Test
    public void testRoundTrip() {
        for (int p = 4; p <= 20; p += 2) {
            double[] a = SIGNAL32.clone();
            double[] b = SIGNAL32.clone();
            new DaubechiesWavelet(p).transform(a);
            new DaubechiesWavelet(p).inverse(a);
            assertArrayEquals(b, a, 1E-7, "DaubechiesWavelet(" + p + ") roundtrip failed");
        }
    }

    /** Energy preservation (Parseval) for all supported orders. */
    @Test
    public void testEnergyPreservation() {
        for (int p = 4; p <= 20; p += 2) {
            double[] a = SIGNAL32.clone();
            double energy = energy(a);
            new DaubechiesWavelet(p).transform(a);
            assertEquals(energy, energy(a), 1E-7, "DaubechiesWavelet(" + p + ") energy not preserved");
        }
    }

    /** Invalid order must throw IllegalArgumentException before any transform. */
    @Test
    public void testInvalidOrderThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DaubechiesWavelet(3));
        assertThrows(IllegalArgumentException.class, () -> new DaubechiesWavelet(22));
        assertThrows(IllegalArgumentException.class, () -> new DaubechiesWavelet(0));
        assertThrows(IllegalArgumentException.class, () -> new DaubechiesWavelet(5));
    }

    /** Non-power-of-2 signal length must throw. */
    @Test
    public void testNonPowerOf2Throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new DaubechiesWavelet(4).transform(new double[10]));
    }

    private static double energy(double[] a) {
        double sum = 0;
        for (double v : a) sum += v * v;
        return sum;
    }
}