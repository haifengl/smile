/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.wavelet;

import smile.math.Math;

/**
 * The wavelet shrinkage is a signal denoising technique based on the idea of
 * thresholding the wavelet coefficients. Wavelet coefficients having small
 * absolute value are considered to encode mostly noise and very fine details
 * of the signal. In contrast, the important information is encoded by the
 * coefficients having large absolute value. Removing the small absolute value
 * coefficients and then reconstructing the signal should produce signal with
 * lesser amount of noise. The wavelet shrinkage approach can be summarized as
 * follows:
 * <ol>
 * <li> Apply the wavelet transform to the signal.
 * <li> Estimate a threshold value.
 * <li> The so-called hard thresholding method zeros the coefficients that are
 * smaller than the threshold and leaves the other ones unchanged. In contrast,
 * the soft thresholding scales the remaining coefficients in order to form a
 * continuous distribution of the coefficients centered on zero.
 * <li> Reconstruct the signal (apply the inverse wavelet transform).
 * </ol>
 * The biggest challenge in the wavelet shrinkage approach is finding an
 * appropriate threshold value. In this class, we use the universal threshold
 * T = &sigma; sqrt(2*log(N)), where N is the length of time series
 * and &sigma; is the estimate of standard deviation of the noise by the
 * so-called scaled median absolute deviation (MAD) computed from the high-pass
 * wavelet coefficients of the first level of the transform. 
 *
 * @author Haifeng Li
 */
public class WaveletShrinkage {
    /**
     * Adaptive hard-thresholding denoising a time series with given wavelet.
     *
     * @param t the time series array. The size should be a power of 2. For time
     * series of size no power of 2, 0 padding can be applied.
     * @param wavelet the wavelet to transform the time series.
     */
    public static void denoise(double[] t, Wavelet wavelet) {
        denoise(t, wavelet, false);
    }

    /**
     * Adaptive denoising a time series with given wavelet.
     *
     * @param t the time series array. The size should be a power of 2. For time
     * series of size no power of 2, 0 padding can be applied.
     * @param wavelet the wavelet to transform the time series.
     * @param soft true if apply soft thresholding.
     */
    public static void denoise(double[] t, Wavelet wavelet, boolean soft) {
        wavelet.transform(t);

        int n = t.length;
        int nh = t.length >> 1;

        double[] wc = new double[nh];
        System.arraycopy(t, nh, wc, 0, nh);
        double error = Math.mad(wc) / 0.6745;

        double lambda = error * Math.sqrt(2 * Math.log(n));

        if (soft) {
            for (int i = 2; i < n; i++) {
                t[i] = Math.signum(t[i]) * Math.max(Math.abs(t[i]) - lambda, 0.0);
            }
        } else {
            for (int i = 2; i < n; i++) {
                if (Math.abs(t[i]) < lambda) {
                    t[i] = 0.0;
                }
            }
        }
        
        wavelet.inverse(t);
    }
}
