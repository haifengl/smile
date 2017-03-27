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
 * Bicubic interpolation in a two-dimensional regular grid. Bicubic
 * spline interpolation guarantees the continuity of the first derivatives,
 * as well as the continuity of a cross-derivative.
 * <p>
 * Note that CubicSplineInterpolation2D guarantees the continuity of the
 * first and second function derivatives but bicubic spline guarantees
 * continuity of only gradient and cross-derivative. Second derivatives
 * could be discontinuous.
 * <p>
 * In image processing, bicubic interpolation is often chosen over bilinear
 * interpolation or nearest neighbor in image resampling, when speed is not
 * an issue. Images resampled with bicubic interpolation are smoother and
 * have fewer interpolation artifacts.
 * 
 * @author Haifeng Li
 */
public class BicubicInterpolation implements Interpolation2D {

    private static final int[][] wt = {
        { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        { 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0},
        {-3, 0, 0, 3, 0, 0, 0, 0,-2, 0, 0,-1, 0, 0, 0, 0},
        { 2, 0, 0,-2, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0},
        { 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
        { 0, 0, 0, 0,-3, 0, 0, 3, 0, 0, 0, 0,-2, 0, 0,-1},
        { 0, 0, 0, 0, 2, 0, 0,-2, 0, 0, 0, 0, 1, 0, 0, 1},
        {-3, 3, 0, 0,-2,-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        { 0, 0, 0, 0, 0, 0, 0, 0,-3, 3, 0, 0,-2,-1, 0, 0},
        { 9,-9, 9,-9, 6, 3,-3,-6, 6,-6,-3, 3, 4, 2, 1, 2},
        {-6, 6,-6, 6,-4,-2, 2, 4,-3, 3, 3,-3,-2,-1,-1,-2},
        { 2,-2, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        { 0, 0, 0, 0, 0, 0, 0, 0, 2,-2, 0, 0, 1, 1, 0, 0},
        {-6, 6,-6, 6,-3,-3, 3, 3,-4, 4, 2,-2,-2,-2,-1,-1},
        { 4,-4, 4,-4, 2, 2,-2,-2, 2,-2,-2, 2, 1, 1, 1, 1}
    };

    private int m, n;
    private double[][] yv;
    private double[] x1;
    private double[] x2;
    private LinearInterpolation x1terp, x2terp;

    private double[] y;
    private double[] y1;
    private double[] y2;
    private double[] y12;


    /**
     * Constructor. The value in x1 and x2 must be monotonically increasing.
     */
    public BicubicInterpolation(double[] x1, double[] x2, double[][] y) {
        m = x1.length;
        n = x2.length;
        x1terp = new LinearInterpolation(x1, x1);
        x2terp = new LinearInterpolation(x2, x2);

        this.x1 = x1;
        this.x2 = x2;
        this.yv = y;

        this.y = new double[4];
        y1 = new double[4];
        y2 = new double[4];
        y12 = new double[4];
    }

    /**
     * Given arrays y[0..3], y1[0..3], y2[0..3], and y12[0..3], containing
     * the function, gradients, and cross-derivative at the four grid points
     * of a rectangular grid cell (numbered counterclockwise from the lower
     * left), and given d1 and d2, the length of the grid cell in the 1 and 2
     * directions, returns the table c[0..3][0..3] that is used by bcuint
     * for bicubic interpolation.
     */
    private static double[][] bcucof(double[] y, double[] y1, double[] y2, double[] y12, double d1, double d2) {

        double d1d2 = d1 * d2;
        double[] cl = new double[16];
        double[] x = new double[16];
        double[][] c = new double[4][4];

        for (int i = 0; i < 4; i++) {
            x[i] = y[i];
            x[i + 4] = y1[i] * d1;
            x[i + 8] = y2[i] * d2;
            x[i + 12] = y12[i] * d1d2;
        }

        double xx;
        for (int i = 0; i < 16; i++) {
            xx = 0.0;
            for (int k = 0; k < 16; k++) {
                xx += wt[i][k] * x[k];
            }
            cl[i] = xx;
        }

        int l = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                c[i][j] = cl[l++];
            }
        }

        return c;
    }

    /**
     * Bicubic interpolation within a grid square.
     */
    private static double bcuint(double[] y, double[] y1, double[] y2, double[] y12,
            double x1l, double x1u, double x2l, double x2u, double x1p, double x2p) {
        
        if (x1u == x1l) {
            throw new IllegalArgumentException("Nearby control points take same value: " + x1u);
        }

        if (x2u == x2l) {
            throw new IllegalArgumentException("Nearby control points take same value: " + x2u);
        }

        double t, u, d1 = x1u - x1l, d2 = x2u - x2l;
        double[][] c = bcucof(y, y1, y2, y12, d1, d2);

        t = (x1p - x1l) / d1;
        u = (x2p - x2l) / d2;

        double ansy = 0.0;

        for (int i = 3; i >= 0; i--) {
            ansy = t * ansy + ((c[i][3] * u + c[i][2]) * u + c[i][1]) * u + c[i][0];
        }

        return ansy;
    }

    @Override
    public double interpolate(double x1p, double x2p) {
        int j = x1terp.search(x1p);
        int k = x2terp.search(x2p);

        double x1l = x1[j];
        double x1u = x1[j + 1];
        double x2l = x2[k];
        double x2u = x2[k + 1];

        y[0] = yv[j][k];
        y[1] = yv[j+1][k];
        y[2] = yv[j+1][k+1];
        y[3] = yv[j][k+1];

        y1[0] = (j - 1 < 0) ? (yv[j+1][k] - yv[j][k]) / (x1[j+1] - x1[j]) : (yv[j+1][k] - yv[j-1][k]) / (x1[j+1] - x1[j-1]);
        y1[1] = (j + 2 < m) ? (yv[j+2][k]   - yv[j][k])   / (x1[j+2] - x1[j]) : (yv[j+1][k]   - yv[j][k]) / (x1[j+1] - x1[j]);
        y1[2] = (j + 2 < m) ? (yv[j+2][k+1] - yv[j][k+1]) / (x1[j+2] - x1[j]) : (yv[j+1][k+1] - yv[j][k+1]) / (x1[j+1] - x1[j]);
        y1[3] = (j - 1 < 0) ? (yv[j+1][k+1] - yv[j][k+1]) / (x1[j+1] - x1[j]) : (yv[j+1][k+1] - yv[j-1][k+1]) / (x1[j+1] - x1[j-1]);

        y2[0] = (k - 1 < 0) ? (yv[j][k+1] - yv[j][k]) / (x2[k+1] - x2[k]) : (yv[j][k+1] - yv[j][k-1]) / (x2[k+1] - x2[k-1]);
        y2[1] = (k - 1 < 0) ? (yv[j+1][k+1] - yv[j+1][k]) / (x2[k+1] - x2[k]) : (yv[j+1][k+1] - yv[j+1][k-1]) / (x2[k+1] - x2[k-1]);
        y2[2] = (k + 2 < n) ? (yv[j+1][k+2] - yv[j+1][k]) / (x2[k+2] - x2[k]) : (yv[j+1][k+1] - yv[j+1][k]) / (x2[k+1] - x2[k]);
        y2[3] = (k + 2 < n) ? (yv[j][k+2]   - yv[j][k])   / (x2[k+2] - x2[k]) : (yv[j][k+1]   - yv[j][k])   / (x2[k+1] - x2[k]);

        if (k - 1 < 0 && j - 1 < 0)
            y12[0] = (yv[j+1][k+1] - yv[j+1][k] - yv[j][k+1] + yv[j][k]) / ((x1[j+1] - x1[j]) * (x2[k+1] - x2[k]));
        else if (k - 1 < 0)
            y12[0] = (yv[j+1][k+1] - yv[j+1][k] - yv[j-1][k+1] + yv[j-1][k]) / ((x1[j+1] - x1[j-1]) * (x2[k+1] - x2[k]));
        else if (j - 1 < 0)
            y12[0] = (yv[j+1][k+1] - yv[j+1][k-1] - yv[j][k+1] + yv[j][k-1]) / ((x1[j+1] - x1[j]) * (x2[k+1] - x2[k-1]));
        else
            y12[0] = (yv[j+1][k+1] - yv[j+1][k-1] - yv[j-1][k+1] + yv[j-1][k-1]) / ((x1[j+1] - x1[j-1]) * (x2[k+1] - x2[k-1]));

        if (j + 2 < m) {
            if (k - 1 < 0) {
                y12[1] = (yv[j + 2][k + 1] - yv[j + 2][k] - yv[j][k + 1] + yv[j][k]) / ((x1[j + 2] - x1[j]) * (x2[k + 1] - x2[k]));
            } else {
                y12[1] = (yv[j + 2][k + 1] - yv[j + 2][k - 1] - yv[j][k + 1] + yv[j][k - 1]) / ((x1[j + 2] - x1[j]) * (x2[k + 1] - x2[k - 1]));
            }
        } else {
            if (k - 1 < 0) {
                y12[1] = (yv[j + 1][k + 1] - yv[j + 1][k] - yv[j][k + 1] + yv[j][k]) / ((x1[j + 1] - x1[j]) * (x2[k + 1] - x2[k]));
            } else {
                y12[1] = (yv[j + 1][k + 1] - yv[j + 1][k - 1] - yv[j][k + 1] + yv[j][k - 1]) / ((x1[j + 1] - x1[j]) * (x2[k + 1] - x2[k - 1]));
            }
        }
    
        if (j + 2 < m && k + 2 < n) {
            y12[2] = (yv[j + 2][k + 2] - yv[j + 2][k] - yv[j][k + 2] + yv[j][k]) / ((x1[j + 2] - x1[j]) * (x2[k + 2] - x2[k]));
        } else if (j + 2 < m) {
            y12[2] = (yv[j + 2][k + 1] - yv[j + 2][k] - yv[j][k + 1] + yv[j][k]) / ((x1[j + 2] - x1[j]) * (x2[k + 1] - x2[k]));
        } else if (k + 2 < n) {
            y12[2] = (yv[j + 1][k + 2] - yv[j + 1][k] - yv[j][k + 2] + yv[j][k]) / ((x1[j + 1] - x1[j]) * (x2[k + 2] - x2[k]));
        } else {
            y12[2] = (yv[j + 1][k + 1] - yv[j + 1][k] - yv[j][k + 1] + yv[j][k]) / ((x1[j + 1] - x1[j]) * (x2[k + 1] - x2[k]));
        }

        if (k + 2 < n) {
            if (j - 1 < 0) {
                y12[3] = (yv[j + 1][k + 2] - yv[j + 1][k] - yv[j][k + 2] + yv[j][k]) / ((x1[j + 1] - x1[j]) * (x2[k + 2] - x2[k]));
            } else {
                y12[3] = (yv[j + 1][k + 2] - yv[j + 1][k] - yv[j - 1][k + 2] + yv[j - 1][k]) / ((x1[j + 1] - x1[j - 1]) * (x2[k + 2] - x2[k]));
            }
        } else {
            if (j - 1 < 0) {
                y12[3] = (yv[j + 1][k + 1] - yv[j + 1][k] - yv[j][k + 1] + yv[j][k]) / ((x1[j + 1] - x1[j]) * (x2[k + 1] - x2[k]));
            } else {
                y12[3] = (yv[j + 1][k + 1] - yv[j + 1][k] - yv[j - 1][k + 1] + yv[j - 1][k]) / ((x1[j + 1] - x1[j - 1]) * (x2[k + 1] - x2[k]));
            }
        }

        return bcuint(y, y1, y2, y12, x1l, x1u, x2l, x2u, x1p, x2p);
    }
}
