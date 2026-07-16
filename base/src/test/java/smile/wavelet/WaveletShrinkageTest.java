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
 * Tests for WaveletShrinkage.
 *
 * @author Haifeng Li
 */
public class WaveletShrinkageTest {

    /**
     * Builds a clean smooth signal: a low-frequency sine wave (length 256).
     */
    private static double[] cleanSignal() {
        int n = 256;
        double[] t = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = Math.sin(2 * Math.PI * i / 64.0);  // 4 full cycles
        }
        return t;
    }

    /**
     * Returns a noisy version of the clean signal.
     * Noise is constructed as a sum of many high-frequency sinusoids at
     * incommensurable frequencies with small amplitudes, approximating
     * broadband (white) noise — the case the universal threshold is designed for.
     * The noise amplitude is set so that it clearly degrades the MSE, but
     * hard-thresholding can reliably recover most of the clean signal.
     */
    private static double[] noisySignal() {
        double[] t = cleanSignal();
        int n = t.length;
        // Sum 60 high-frequency sinusoids at prime-number-derived frequencies
        // with amplitude 0.4/sqrt(60) each => total RMS ≈ 0.4, SNR ≈ 0dB.
        int[] primes = {
            31, 37, 41, 43, 47, 53, 59, 61, 67, 71,
            73, 79, 83, 89, 97,101,103,107,109,113,
           127,131,137,139,149,151,157,163,167,173,
           179,181,191,193,197,199,211,223,227,229,
           233,239,241,251,257,263,269,271,277,281,
           283,293,307,311,313,317,331,337,347,349
        };
        double amp = 0.4 / Math.sqrt(primes.length);
        for (int i = 0; i < n; i++) {
            double noise = 0;
            for (int p : primes) {
                noise += Math.sin(2 * Math.PI * i * p / n + p * 0.1);
            }
            t[i] += amp * noise;
        }
        return t;
    }

    /**
     * Hard-thresholding denoising with HaarWavelet: the result should be
     * closer to the clean signal than the noisy input (MSE improvement).
     */
    @Test
    public void testHardThresholdingReducesNoise() {
        double[] clean  = cleanSignal();
        double[] noisy  = noisySignal();

        double noisyMse = mse(clean, noisy);

        double[] denoised = noisy.clone();
        WaveletShrinkage.denoise(denoised, new HaarWavelet(), false);

        double denoisedMse = mse(clean, denoised);
        assertTrue(denoisedMse < noisyMse,
                "Hard-thresholding should reduce MSE: before=" + noisyMse + " after=" + denoisedMse);
    }

    /**
     * Soft-thresholding denoising with HaarWavelet: same MSE improvement check.
     */
    @Test
    public void testSoftThresholdingReducesNoise() {
        double[] clean  = cleanSignal();
        double[] noisy  = noisySignal();

        double noisyMse = mse(clean, noisy);

        double[] denoised = noisy.clone();
        WaveletShrinkage.denoise(denoised, new HaarWavelet(), true);

        double denoisedMse = mse(clean, denoised);
        assertTrue(denoisedMse < noisyMse,
                "Soft-thresholding should reduce MSE: before=" + noisyMse + " after=" + denoisedMse);
    }

    /**
     * Denoising a pure constant signal should return a signal very close
     * to the original (no noise means threshold does not distort the result
     * significantly).
     */
    @Test
    public void testDenoiseConstantSignal() {
        double[] a = new double[32];
        double val = 3.14;
        java.util.Arrays.fill(a, val);

        WaveletShrinkage.denoise(a, new HaarWavelet(), false);

        // Constant signal has zero detail coefficients so MAD=0 => lambda=0,
        // meaning nothing is thresholded and the signal is perfectly recovered.
        for (double v : a) {
            assertEquals(val, v, 1E-7, "Constant signal should survive denoising unchanged");
        }
    }

    /**
     * Default (hard) overload and explicit hard overload should produce identical results.
     */
    @Test
    public void testDefaultIsHard() {
        double[] a1 = noisySignal();
        double[] a2 = a1.clone();
        WaveletShrinkage.denoise(a1, new HaarWavelet());
        WaveletShrinkage.denoise(a2, new HaarWavelet(), false);
        assertArrayEquals(a2, a1, 1E-12, "Default denoise should be identical to hard-thresholding");
    }

    /**
     * denoise() with all supported wavelet families should not throw.
     */
    @Test
    public void testMultipleWavelets() {
        Wavelet[] wavelets = {
                new HaarWavelet(),
                new D4Wavelet(),
                new DaubechiesWavelet(6),
                new DaubechiesWavelet(8),
                new CoifletWavelet(6),
                new SymletWavelet(8),
                new BestLocalizedWavelet(14)
        };
        for (Wavelet w : wavelets) {
            double[] a = noisySignal();
            assertDoesNotThrow(() -> WaveletShrinkage.denoise(a, w),
                    "denoise() should not throw for " + w.getClass().getSimpleName());
        }
    }

    /**
     * denoise2D (hard) on a constant 2-D matrix: should be preserved exactly.
     */
    @Test
    public void testDenoise2DConstant() {
        int n = 32;
        double val = 2.0;
        double[][] matrix = new double[n][n];
        for (double[] row : matrix) java.util.Arrays.fill(row, val);

        WaveletShrinkage.denoise2D(matrix, new HaarWavelet(), false);

        for (double[] row : matrix) {
            for (double v : row) {
                assertEquals(val, v, 1E-7, "2-D constant signal should survive denoising unchanged");
            }
        }
    }

    /**
     * denoise2D default overload should equal explicit hard overload.
     */
    @Test
    public void testDenoise2DDefaultIsHard() {
        int n = 32;
        double[][] m1 = new double[n][n];
        double[][] m2 = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = Math.sin(i * 0.3) * Math.cos(j * 0.4);
                m1[i][j] = v;
                m2[i][j] = v;
            }
        }
        WaveletShrinkage.denoise2D(m1, new HaarWavelet());
        WaveletShrinkage.denoise2D(m2, new HaarWavelet(), false);
        for (int i = 0; i < n; i++) {
            assertArrayEquals(m2[i], m1[i], 1E-12,
                    "denoise2D default should equal explicit hard-thresholding at row " + i);
        }
    }

    private static double mse(double[] expected, double[] actual) {
        double sum = 0;
        for (int i = 0; i < expected.length; i++) {
            double diff = expected[i] - actual[i];
            sum += diff * diff;
        }
        return sum / expected.length;
    }
}

