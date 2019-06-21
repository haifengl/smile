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

package smile.wavelet;

/**
 * The simplest and most localized wavelet, Daubechies wavelet of 4 coefficients.
 * Note that this class uses the different centering method from the one used in
 * the Daubechies class.
 *
 * @author Haifeng Li
 */
public class D4Wavelet extends Wavelet {

    /**
     * Wavelet coefficients.
     */
    private static final double C0 =  0.4829629131445341;
    private static final double C1 =  0.8365163037378079;
    private static final double C2 =  0.2241438680420134;
    private static final double C3 = -0.1294095225512604;

    /**
     * Workspace.
     */
    private double[] workspace = new double[1024];

    /**
     * Constructor.
     */
    public D4Wavelet() {
        super(new double[]{0.4829629131445341, 0.8365163037378079, 0.2241438680420134, -0.1294095225512604});
    }

    @Override
    void forward(double[] a, int n) {
        if (n < 4) {
            return;
        }

        if (n > workspace.length) {
            workspace = new double[n];
        }

        int i, j;
        int nh = n >> 1;
        int n3 = n - 3;
        
        for (i = 0, j = 0; j < n3; j+=2, i++) {
            workspace[i] = C0 * a[j] + C1 * a[j + 1] + C2 * a[j + 2] + C3 * a[j + 3];
            workspace[i + nh] = C3 * a[j] - C2 * a[j + 1] + C1 * a[j + 2] - C0 * a[j + 3];
        }

        workspace[i] = C0 * a[n - 2] + C1 * a[n - 1] + C2 * a[0] + C3 * a[1];
        workspace[i + nh] = C3 * a[n - 2] - C2 * a[n - 1] + C1 * a[0] - C0 * a[1];

        System.arraycopy(workspace, 0, a, 0, n);
    }

    @Override
    void backward(double[] a, int n) {
        if (n < 4) {
            return;
        }

        if (n > workspace.length) {
            workspace = new double[n];
        }

        int nh = n >> 1;
        int nh1 = nh - 1;

        workspace[0] = C2 * a[nh - 1] + C1 * a[n - 1] + C0 * a[0] + C3 * a[nh];
        workspace[1] = C3 * a[nh - 1] - C0 * a[n - 1] + C1 * a[0] - C2 * a[nh];
        for (int i = 0, j = 2; i < nh1; i++) {
            workspace[j++] = C2 * a[i] + C1 * a[i + nh] + C0 * a[i + 1] + C3 * a[i + nh + 1];
            workspace[j++] = C3 * a[i] - C0 * a[i + nh] + C1 * a[i + 1] - C2 * a[i + nh + 1];
        }

        System.arraycopy(workspace, 0, a, 0, n);
    }
}

