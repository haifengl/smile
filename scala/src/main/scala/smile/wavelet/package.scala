/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile

/** A wavelet is a wave-like oscillation with an amplitude that starts out at
  * zero, increases, and then decreases back to zero. Like the fast Fourier
  * transform (FFT), the discrete wavelet transform (DWT) is a fast, linear
  * operation that operates on a data vector whose length is an integer power
  * of 2, transforming it into a numerically different vector of the same length.
  * The wavelet transform is invertible and in fact orthogonal. Both FFT and DWT
  * can be viewed as a rotation in function space.
  *
  * @author Haifeng Li
  */
package object wavelet {
  /** Returns the wavelet filter. The filter name is derived from one of four classes of wavelet transform filters:
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
  def wavelet(filter: String): Wavelet = {
    filter match {
      case "haar" => new HaarWavelet
      case "d4"   => new D4Wavelet
      case "bl14" => new BestLocalizedWavelet(14)
      case "bl18" => new BestLocalizedWavelet(18)
      case "bl20" => new BestLocalizedWavelet(20)
      case "c6"   => new CoifletWavelet(6)
      case "c12"  => new CoifletWavelet(12)
      case "c18"  => new CoifletWavelet(18)
      case "c24"  => new CoifletWavelet(24)
      case "c30"  => new CoifletWavelet(30)
      case "d6"   => new DaubechiesWavelet(6)
      case "d8"   => new DaubechiesWavelet(8)
      case "d10"  => new DaubechiesWavelet(10)
      case "d12"  => new DaubechiesWavelet(12)
      case "d14"  => new DaubechiesWavelet(14)
      case "d16"  => new DaubechiesWavelet(16)
      case "d18"  => new DaubechiesWavelet(18)
      case "d20"  => new DaubechiesWavelet(20)
      case "la8"  => new SymletWavelet(8)
      case "la10" => new SymletWavelet(10)
      case "la12" => new SymletWavelet(12)
      case "la14" => new SymletWavelet(14)
      case "la16" => new SymletWavelet(16)
      case "la18" => new SymletWavelet(18)
      case "la20" => new SymletWavelet(20)
    }
  }

  /** Discrete wavelet transform.
    * @param t the time series array. The size should be a power of 2. For time
    *          series of size no power of 2, 0 padding can be applied.
    * @param filter wavelet filter.
    */
  def dwt(t: Array[Double], filter: String): Unit = {
    wavelet(filter).transform(t)
  }

  /** Inverse discrete wavelet transform.
    * @param wt the wavelet coefficients. The size should be a power of 2. For time
    *          series of size no power of 2, 0 padding can be applied.
    * @param filter wavelet filter.
    */
  def idwt(wt: Array[Double], filter: String): Unit = {
    wavelet(filter).inverse(wt)
  }

  /** The wavelet shrinkage is a signal denoising technique based on the idea of
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
  def wsdenoise(t: Array[Double], filter: String, soft: Boolean = false): Unit = {
    WaveletShrinkage.denoise(t, wavelet(filter), soft)
  }
}