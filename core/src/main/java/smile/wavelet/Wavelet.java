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

import java.util.Arrays;
import smile.math.Math;

/**
 * A wavelet is a wave-like oscillation with an amplitude that starts out at
 * zero, increases, and then decreases back to zero. Like the fast Fourier
 * transform (FFT), the discrete wavelet transform (DWT) is a fast, linear
 * operation that operates on a data vector whose length is an integer power
 * of 2, transforming it into a numerically different vector of the same length.
 * The wavelet transform is invertible and in fact orthogonal. Both FFT and DWT
 * can be viewed as a rotation in function space.
 * 
 * @author Haifeng Li
 */
public class Wavelet {

    /**
     * The number of coefficients.
     */
    private int ncof;
    /**
     * Centering.
     */
    private int ioff, joff;
    /**
     * Wavelet coefficients.
     */
    private double[] cc;
    private double[] cr;

    /**
     * Workspace.
     */
    private double[] workspace = new double[1024];


    /**
     * Constructor. Create a wavelet with given coefficients.
     */
    public Wavelet(double[] coefficients) {
        ncof = coefficients.length;

        ioff = joff = -(ncof >> 1);

        cc = coefficients;

        double sig = -1.0;
        cr = new double[ncof];
        for (int i = 0; i < ncof; i++) {
            cr[ncof - 1 - i] = sig * cc[i];
            sig = -sig;
        }
    }

    /**
     * Applies the wavelet filter to a data vector a[0, n-1].
     */
    void forward(double[] a, int n) {
        if (n < ncof) {
            return;
        }

        if (n > workspace.length) {
            workspace = new double[n];
        } else {
            Arrays.fill(workspace, 0, n, 0.0);
        }

        int nmod = ncof * n;
        int n1 = n - 1;
        int nh = n >> 1;

        for (int ii = 0, i = 0; i < n; i += 2, ii++) {
            int ni = i + 1 + nmod + ioff;
            int nj = i + 1 + nmod + joff;
            for (int k = 0; k < ncof; k++) {
                int jf = n1 & (ni + k + 1);
                int jr = n1 & (nj + k + 1);
                workspace[ii] += cc[k] * a[jf];
                workspace[ii + nh] += cr[k] * a[jr];
            }
        }

        System.arraycopy(workspace, 0, a, 0, n);
    }

    /**
     * Applies the inverse wavelet filter to a data vector a[0, n-1].
     */
    void backward(double[] a, int n) {
        if (n < ncof) {
            return;
        }

        if (n > workspace.length) {
            workspace = new double[n];
        } else {
            Arrays.fill(workspace, 0, n, 0.0);
        }

        int nmod = ncof * n;
        int n1 = n - 1;
        int nh = n >> 1;

        for (int ii = 0, i = 0; i < n; i += 2, ii++) {
            double ai = a[ii];
            double ai1 = a[ii + nh];
            int ni = i + 1 + nmod + ioff;
            int nj = i + 1 + nmod + joff;
            for (int k = 0; k < ncof; k++) {
                int jf = n1 & (ni + k + 1);
                int jr = n1 & (nj + k + 1);
                workspace[jf] += cc[k] * ai;
                workspace[jr] += cr[k] * ai1;
            }
        }

        System.arraycopy(workspace, 0, a, 0, n);
    }

    /**
     * Discrete wavelet transform.
     */
    public void transform(double[] a) {
        int n = a.length;

        if (!Math.isPower2(n)) {
            throw new IllegalArgumentException("The data vector size is not a power of 2.");
        }

        if (n < ncof) {
            throw new IllegalArgumentException("The data vector size is less than wavelet coefficient size.");
        }

        for (int nn = n; nn >= ncof; nn >>= 1) {
            forward(a, nn);
        }
    }

    /**
     * Inverse discrete wavelet transform.
     */
    public void inverse(double[] a) {
        int n = a.length;

        if (!Math.isPower2(n)) {
            throw new IllegalArgumentException("The data vector size is not a power of 2.");
        }

        if (n < ncof) {
            throw new IllegalArgumentException("The data vector size is less than wavelet coefficient size.");
        }

        int start = n >> (int) Math.floor(Math.log2(n/(ncof-1)));
        for (int nn = start; nn <= n; nn <<= 1) {
            backward(a, nn);
        }
    }
}
