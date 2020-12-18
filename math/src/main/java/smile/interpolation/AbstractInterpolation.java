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

package smile.interpolation;

/**
 * Abstract base class of one-dimensional interpolation methods.
 * 
 * @author Haifeng Li
 */
public abstract class AbstractInterpolation implements Interpolation {

    /**
     * The factor min(1, pow(n, 0.25)) to check if
     * consecutive calls seem correlated.
     */
    private final int dj;
    /**
     * The previous search location.
     */
    private int jsav;
    /**
     * The indicator if consecutive calls seem correlated.
     * This variable is used by <code>interpolate</code> to
     * decide if to use <code>locate</code> or <code>hunt</code>
     * on the next call, which is invisible to the user.
     */
    private boolean cor;
    /**
     * The number of control points.
     */
    int n;
    /**
     * The tabulated control points.
     */
    double[] xx;
    /**
     * The function values at control points.
     */
    double[] yy;

    /**
     * Constructor. Setup for interpolation on a table of x and y.
     * The value in x must be monotonic, either increasing or decreasing.
     *
     * @param x the tabulated points.
     * @param y the function values at <code>x</code>.
     */
    public AbstractInterpolation(double[] x, double[] y) {
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
     *
     * @param x a real number.
     * @return the index {@code j} of x in the tabulated points.
     */
    protected int search(double x) {
        return cor ? hunt(x) : locate(x);
    }

    /**
     * Given a value x, return a value j such that x is (insofar as possible)
     * centered in the subrange xx[j..j+m-1], where xx is the stored data. The
     * returned value is not less than 0, nor greater than n-1, where n is the
     * length of xx. This method employs the bisection algorithm.
     *
     * @param x a real number.
     * @return the index {@code j} of x in the tabulated points.
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
     * Searches with correlated values. If two calls that
     * are close, instead of a full bisection, it anticipates
     * that the next call will also be.
     * <p>
     * Given a value x, return a value j such that x is (insofar as possible)
     * centered in the subrange xx[j..j+m-1], where xx is the stored data. The
     * returned value is not less than 0, nor greater than n-1, where n is the
     * length of xx.
     *
     * @param x a real number.
     * @return the index {@code j} of x in the tabulated points.
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
     *
     * @param jlo the value jlo is such that x is (insofar as possible)
     *        centered in the subrange xx[j..j+m-1], where xx is the stored data.
     * @param x interpolate at this value
     * @return the raw interpolated value.
     */
    public abstract double rawinterp(int jlo, double x);
}
