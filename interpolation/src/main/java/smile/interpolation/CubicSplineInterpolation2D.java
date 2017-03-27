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

package smile.interpolation;

/**
 * Cubic spline interpolation in a two-dimensional regular grid.
 * It is similar to one-dimensional splines as it guarantees the
 * continuity of the first and second function
 * derivatives. Note that bicubic spline guarantees continuity of only gradient and
 * cross-derivative.
 *
 * @author Haifeng Li
 */
public class CubicSplineInterpolation2D implements Interpolation2D {
    private int m;
    private double[] x1;
    private double[] yv;
    private CubicSplineInterpolation1D[] srp;

    /**
     * Constructor.
     */
    public CubicSplineInterpolation2D(double[] x1, double[] x2, double[][] y) {
        m = x1.length;

        this.x1 = x1;
        yv = new double[m];

        srp = new CubicSplineInterpolation1D[m];
        for (int i = 0; i < m; i++) {
            srp[i] = new CubicSplineInterpolation1D(x2, y[i]);
        }
    }

    @Override
    public double interpolate(double x1p, double x2p) {
        for (int i = 0; i < m; i++)
            yv[i] = srp[i].interpolate(x2p);

        CubicSplineInterpolation1D scol = new CubicSplineInterpolation1D(x1, yv);

        return scol.interpolate(x1p);
    }
}
