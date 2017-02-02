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

/**
 * Haar wavelet. The Haar wavelet is a certain sequence of rescaled
 * "square-shaped" functions which together form a wavelet family or basis.
 * As a special case of the Daubechies wavelet, it is also known as D2.
 * The Haar wavelet is also the simplest possible wavelet. The technical
 * disadvantage of the Haar wavelet is that it is not continuous, and
 * therefore not differentiable. This property can, however, be an advantage
 * for the analysis of signals with sudden transitions, such as monitoring
 * of tool failure in machines.
 *
 * @author Haifeng Li
 */
public class HaarWavelet extends Wavelet {

    /**
     * Wavelet coefficients.
     */
    private static final double C =  0.7071067811865475;

    /**
     * Workspace.
     */
    private double[] workspace = new double[1024];

    /**
     * Constructor.
     */
    public HaarWavelet() {
        super(new double[]{C, C});
    }

    @Override
    void forward(double[] a, int n) {
        if (n < 2) {
            return;
        }

        if (n > workspace.length) {
            workspace = new double[n];
        }

        int n1 = n - 1;
        int nh = n >> 1;
        
        for (int i = 0, j = 0; j < n1; j+=2, i++) {
            workspace[i]      = C * (a[j] + a[j + 1]);
            workspace[i + nh] = C * (a[j] - a[j + 1]);
        }

        System.arraycopy(workspace, 0, a, 0, n);
    }

    @Override
    void backward(double[] a, int n) {
        if (n < 2) {
            return;
        }

        if (n > workspace.length) {
            workspace = new double[n];
        }

        int n1 = n - 1;
        int nh = n >> 1;

        for (int i = 0, j = 0; j < n1; j+=2, i++) {
            workspace[j]     = C * (a[i] + a[i + nh]);
            workspace[j + 1] = C * (a[i] - a[i + nh]);
        }

        System.arraycopy(workspace, 0, a, 0, n);
    }
}
