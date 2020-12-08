/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.wavelet

/**
 * Crerates a wavelet filter. The filter name is derived from one of four classes of wavelet transform filters:
 * Daubechies, Least Asymetric, Best Localized and Coiflet. The prefixes for filters of these classes are
 * d, la, bl and c, respectively. Following the prefix, the filter name consists of an integer indicating length.
 * Supported lengths are as follows:
 *
 * '''Daubechies''' 4,6,8,10,12,14,16,18,20.
 *
 * '''Least Asymetric''' 8,10,12,14,16,18,20.
 *
 * '''Best Localized''' 14,18,20.
 *
 * '''Coiflet''' 6,12,18,24,30.
 *
 * Additionally "haar" is supported for Haar wavelet.
 *
 * Besides, "d4", the simplest and most localized wavelet, uses a different centering method
 * from other Daubechies wavelet.
 *
 * @param filter filter name
 */
fun wavelet(filter: String): Wavelet {
    return when (filter) {
        "haar" -> HaarWavelet()
        "d4" -> D4Wavelet()
        "bl14" -> BestLocalizedWavelet(14)
        "bl18" -> BestLocalizedWavelet(18)
        "bl20" -> BestLocalizedWavelet(20)
        "c6" -> CoifletWavelet(6)
        "c12" -> CoifletWavelet(12)
        "c18" -> CoifletWavelet(18)
        "c24" -> CoifletWavelet(24)
        "c30" -> CoifletWavelet(30)
        "d6" -> DaubechiesWavelet(6)
        "d8" -> DaubechiesWavelet(8)
        "d10" -> DaubechiesWavelet(10)
        "d12" -> DaubechiesWavelet(12)
        "d14" -> DaubechiesWavelet(14)
        "d16" -> DaubechiesWavelet(16)
        "d18" -> DaubechiesWavelet(18)
        "d20" -> DaubechiesWavelet(20)
        "la8" -> SymletWavelet(8)
        "la10" -> SymletWavelet(10)
        "la12" -> SymletWavelet(12)
        "la14" -> SymletWavelet(14)
        "la16" -> SymletWavelet(16)
        "la18" -> SymletWavelet(18)
        "la20" -> SymletWavelet(20)
        else -> throw java.lang.IllegalArgumentException("Unsupported wavelet: " + filter)
    }
}

/**
 * Discrete wavelet transform.
 * @param t the time series array. The size should be a power of 2. For time
 *          series of size no power of 2, 0 padding can be applied.
 * @param filter wavelet filter.
 */
fun dwt(t: DoubleArray, filter: String): Unit {
    wavelet(filter).transform(t)
}

/**
 * Inverse discrete wavelet transform.
 * @param wt the wavelet coefficients. The size should be a power of 2. For time
 *          series of size no power of 2, 0 padding can be applied.
 * @param filter wavelet filter.
 */
fun idwt(wt: DoubleArray, filter: String): Unit {
    wavelet(filter).inverse(wt)
}

/**
 * The wavelet shrinkage is a signal denoising technique based on the idea of
 * thresholding the wavelet coefficients. Wavelet coefficients having small
 * absolute value are considered to encode mostly noise and very fine details
 * of the signal. In contrast, the important information is encoded by the
 * coefficients having large absolute value. Removing the small absolute value
 * coefficients and then reconstructing the signal should produce signal with
 * lesser amount of noise. The wavelet shrinkage approach can be summarized as
 * follows:
 *
 *  - Apply the wavelet transform to the signal.
 *  - Estimate a threshold value.
 *  - The so-called hard thresholding method zeros the coefficients that are
 *    smaller than the threshold and leaves the other ones unchanged. In contrast,
 *    the soft thresholding scales the remaining coefficients in order to form a
 *    continuous distribution of the coefficients centered on zero.
 *  - Reconstruct the signal (apply the inverse wavelet transform).
 *
 * The biggest challenge in the wavelet shrinkage approach is finding an
 * appropriate threshold value. In this method, we use the universal threshold
 * T = &sigma; sqrt(2*log(N)), where N is the length of time series
 * and &sigma; is the estimate of standard deviation of the noise by the
 * so-called scaled median absolute deviation (MAD) computed from the high-pass
 * wavelet coefficients of the first level of the transform.
 *
 * @param t the time series array. The size should be a power of 2. For time
 *          series of size no power of 2, 0 padding can be applied.
 * @param filter the wavelet filter to transform the time series.
 * @param soft true if apply soft thresholding.
 */
fun wsdenoise(t: DoubleArray, filter: String, soft: Boolean = false): Unit {
    WaveletShrinkage.denoise(t, wavelet(filter), soft)
}
