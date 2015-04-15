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
 * Abstract base class of one-dimensional interpolation methods.
 * 
 * @author Haifeng Li
 */
public abstract class AbstractInterpolation implements Interpolation {

    private int jsav;
    private int dj;
    private boolean cor;
    /**
     * The number of control points.
     */
    int n;
    /**
     * Tabulated points
     */
    double[] xx;
    /**
     * Function values at xx.
     */
    double[] yy;

    /**
     * Constructor. Setup for interpolation on a table of x and y of length m.
     * The value in x must be monotonic, either increasing or decreasing.
     */
    AbstractInterpolation(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y have different length");
        }

        this.n = x.length;

        if (n < 2) {
            throw new IllegalArgumentException("locate size error");
        }

        this.xx = x;
        this.yy = y;

        jsav = 0;
        cor = false;
        dj = Math.min(1, (int) Math.pow(n, 0.25));
    }

    @Override
    public double interpolate(double x) {
        int jlo = search(x);
        return rawinterp(jlo, x);
    }

    /**
     * Given a value x, return a value j such that x is (insofar as possible)
     * centered in the subrange xx[j..j+m-1], where xx is the stored data. The
     * returned value is not less than 0, nor greater than n-1, where n is the
     * length of xx.
     */
    int search(double x) {
        return cor ? hunt(x) : locate(x);
    }

    /**
     * Given a value x, return a value j such that x is (insofar as possible)
     * centered in the subrange xx[j..j+m-1], where xx is the stored data. The
     * returned value is not less than 0, nor greater than n-1, where n is the
     * length of xx.
     */
    private int locate(double x) {
        int ju, jm, jl;

        boolean ascnd = (xx[n - 1] >= xx[0]);

        jl = 0;
        ju = n - 1;
        while (ju - jl > 1) {
            jm = (ju + jl) >> 1;
            if (x >= xx[jm] == ascnd) {
                jl = jm;
            } else {
                ju = jm;
            }
        }

        cor = Math.abs(jl - jsav) <= dj;
        jsav = jl;

        return Math.max(0, Math.min(n - 2, jl));
    }

    /**
     * Given a value x, return a value j such that x is (insofar as possible)
     * centered in the subrange xx[j..j+m-1], where xx is the stored data. The
     * returned value is not less than 0, nor greater than n-1, where n is the
     * length of xx.
     */
    private int hunt(double x) {
        int jl = jsav, jm, ju, inc = 1;

        boolean ascnd = (xx[n - 1] >= xx[0]);

        if (jl < 0 || jl > n - 1) {
            jl = 0;
            ju = n - 1;
        } else {
            if (x >= xx[jl] == ascnd) {
                for (;;) {
                    ju = jl + inc;
                    if (ju >= n - 1) {
                        ju = n - 1;
                        break;
                    } else if (x < xx[ju] == ascnd) {
                        break;
                    } else {
                        jl = ju;
                        inc += inc;
                    }
                }
            } else {
                ju = jl;
                for (;;) {
                    jl = jl - inc;
                    if (jl <= 0) {
                        jl = 0;
                        break;
                    } else if (x >= xx[jl] == ascnd) {
                        break;
                    } else {
                        ju = jl;
                        inc += inc;
                    }
                }
            }
        }

        while (ju - jl > 1) {
            jm = (ju + jl) >> 1;
            if (x >= xx[jm] == ascnd) {
                jl = jm;
            } else {
                ju = jm;
            }
        }

        cor = Math.abs(jl - jsav) <= dj;
        jsav = jl;
        return Math.max(0, Math.min(n - 2, jl));
    }

    /**
     * Subclasses provide this as the actual interpolation method.
     */
    abstract double rawinterp(int jlo, double x);
}
