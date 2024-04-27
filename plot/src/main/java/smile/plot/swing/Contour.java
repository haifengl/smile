/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.plot.swing;

import java.util.ArrayList;
import java.util.List;
import smile.math.MathEx;

/**
 * A contour plot is a graphical technique for representing a 3-dimensional
 * surface by plotting constant z slices, called contours, on a 2-dimensional
 * format. That is, given a value for z, lines are drawn for connecting the
 * (x, y) coordinates where that z value occurs. The contour plot is an
 * alternative to a 3-D surface plot.
 * 
 * @author Haifeng Li
 */
public class Contour extends Plot {
    /**
     * The x coordinate of surface.
     */
    private double[] x;
    /**
     * The y coordinate of surface.
     */
    private double[] y;
    /**
     * The two-dimensional surface.
     */
    private double[][] z;
    /**
     * Minimum of data matrix.
     */
    private double zMin;
    /**
     * Maximum of data matrix.
     */
    private double zMax;
    /**
     * Plot contours in logarithmic scale.
     */
    private boolean logScale = false;
    /**
     * The number of contours.
     */
    private int numLevels = 10;
    /**
     * The contour level values.
     */
    private double[] levels;
    /**
     * The set of contours.
     */
    private List<Isoline> contours;
    /**
     * If show axis ticks.
     */
    private boolean isTickVisible;
    /**
     * Show the level.
     */
    private boolean isLevelVisible = true;

    /**
     * Constructor.
     * @param z the data matrix to create contour plot.
     * @param numLevels the number of contour levels.
     * @param logScale true to interpolate contour levels in logarithmic scale.
     */
    public Contour(double[][] z, int numLevels, boolean logScale) {
        this.z = z;
        this.numLevels = numLevels;
        this.logScale = logScale;
        init();
    }

    /**
     * Constructor.
     * @param z the data matrix to create contour plot.
     * @param levels the level values of contours.
     */
    public Contour(double[][] z, double[] levels) {
        this.z = z;
        this.levels = levels;
        isLevelVisible = false;
        init();
    }

    /**
     * Constructor.
     * @param x the x coordinates of the data grid of z. Must be in ascending order.
     * @param y the y coordinates of the data grid of z. Must be in ascending order.
     * @param z the data matrix to create contour plot.
     * @param numLevels the number of contour levels.
     * @param logScale true to interpolate contour levels in logarithmical scale.
     */
    public Contour(double[] x, double[] y, double[][] z, int numLevels, boolean logScale) {
        if (x.length != z[0].length) {
            throw new IllegalArgumentException("x.length != z[0].length");
        }

        if (y.length != z.length) {
            throw new IllegalArgumentException("y.length != z.length");
        }

        this.x = x;
        this.y = y;
        this.z = z;
        this.numLevels = numLevels;
        this.logScale = logScale;
        init();
    }

    /**
     * Constructor.
     * @param x the x coordinates of the data grid of z. Must be in ascending order.
     * @param y the y coordinates of the data grid of z. Must be in ascending order.
     * @param z the data matrix to create contour plot.
     * @param levels the level values of contours. Must be strictly increasing and finite.
     */
    public Contour(double[] x, double[] y, double[][] z, double[] levels) {
        if (x.length != z[0].length) {
            throw new IllegalArgumentException("x.length != z[0].length");
        }

        if (y.length != z.length) {
            throw new IllegalArgumentException("y.length != z.length");
        }

        this.x = x;
        this.y = y;
        this.z = z;
        this.levels = levels;
        isLevelVisible = false;
        init();
    }

    /**
     * A line segment in contour line.
     */
    static class Segment {

        double x0;
        double y0;
        double x1;
        double y1;
        Segment next;

        Segment(double x0, double y0, double x1, double y1, Segment next) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.next = next;
        }

        void swap() {
            double x, y;
            x = x0;
            y = y0;
            x0 = x1;
            y0 = y1;
            x1 = x;
            y1 = y;
        }
    }

    /**
     * Initialize the contour lines.
     */
    private void init() {
        isTickVisible = x != null || y != null;

        if (x == null) {
            x = new double[z[0].length];
            for (int i = 0; i < x.length; i++) {
                x[i] = i + 0.5;
            }

        }

        if (y == null) {
            y = new double[z.length];
            for (int i = 0; i < y.length; i++) {
                y[i] = i + 0.5;
            }

            double[][] zz = new double[z.length][];
            for (int i = 0; i < z.length; i++) {
                zz[i] = z[z.length - i - 1];
            }
            z = zz;
        }

        zMin = MathEx.min(z);
        zMax = MathEx.max(z);

        contours = new ArrayList<>(numLevels);

        if (logScale && zMin <= 0.0) {
            throw new IllegalArgumentException("Log scale is not support for non-positive data");
        }

        if (levels == null) {
            if (logScale) {
                double lowerBound = Math.ceil(Math.log10(zMin));
                double upperBound = Math.floor(Math.log10(zMax));

                int n = (int) Math.round(upperBound - lowerBound);
                if (n == 0) {
                    logScale = false;
                }

                numLevels = n + 1;
                levels = new double[numLevels];

                for (int i = 0; i < numLevels; i++) {
                    levels[i] = Math.pow(10, lowerBound + i);
                }
            }

            if (levels == null) {
                double digits = Math.log10(Math.abs(zMax - zMin));
                double residual = digits - Math.floor(digits);
                if (residual < 0.4) {
                    // If the range is less than 20 units, we reduce one level.
                    digits -= 1.0;
                }

                double precisionDigits = (int) Math.floor(digits);
                double precisionUnit = Math.pow(10, precisionDigits);

                if (residual >= 0.4 && residual <= 0.7) {
                    // In case of too few grids, we use a half of precision unit.
                    precisionUnit /= 2;
                }

                double lowerBound = precisionUnit * (Math.ceil(zMin / precisionUnit));
                double upperBound = precisionUnit * (Math.floor(zMax / precisionUnit));

                numLevels = (int) Math.round((upperBound - lowerBound) / precisionUnit) + 1;
                levels = new double[numLevels];

                for (int i = 0; i < numLevels; i++) {
                    levels[i] = lowerBound + i * precisionUnit;
                }
            }
        }

        double[] xx = new double[4];
        double[] yy = new double[4];
        int[] ij = new int[2];

        int nx = x.length;
        int ny = y.length;
        Segment[][] segments = new Segment[ny][nx];
        double atom = 1E-7 * (zMax - zMin);

        for (double zc : levels) {
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < ny; j++) {
                    segments[j][i] = null;
                }
            }

            for (int i = 0; i < nx - 1; i++) {
                double xl = x[i];
                double xh = x[i + 1];
                for (int j = 0; j < ny - 1; j++) {
                    double yl = y[j];
                    double yh = y[j + 1];

                    double zll = z[j][i];
                    double zhl = z[j][i + 1];
                    double zlh = z[j + 1][i];
                    double zhh = z[j + 1][i + 1];

                    // If the value at a corner is exactly equal to a contour level,
                    // change that value by a tiny amount
                    if (zll == zc) {
                        zll += atom;
                    }

                    if (zhl == zc) {
                        zhl += atom;
                    }

                    if (zlh == zc) {
                        zlh += atom;
                    }

                    if (zhh == zc) {
                        zhh += atom;
                    }

                    // Check for intersections with sides
                    int nacode = 0;
                    if (!Double.isInfinite(zll)) {
                        nacode += 1;
                    }
                    if (!Double.isInfinite(zhl)) {
                        nacode += 2;
                    }
                    if (!Double.isInfinite(zlh)) {
                        nacode += 4;
                    }
                    if (!Double.isInfinite(zhh)) {
                        nacode += 8;
                    }

                    int k = 0;
                    switch (nacode) {
                        case 15:
                            if (isIntersect(zll, zhl, zc)) {
                                double f = getIntersectRatio(zll, zhl, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yl;
                                k++;
                            }

                            if (isIntersect(zll, zlh, zc)) {
                                double f = getIntersectRatio(zll, zlh, zc);
                                yy[k] = yl + f * (yh - yl);
                                xx[k] = xl;
                                k++;
                            }

                            if (isIntersect(zhl, zhh, zc)) {
                                double f = getIntersectRatio(zhl, zhh, zc);
                                yy[k] = yl + f * (yh - yl);
                                xx[k] = xh;
                                k++;
                            }

                            if (isIntersect(zlh, zhh, zc)) {
                                double f = getIntersectRatio(zlh, zhh, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yh;
                                k++;
                            }
                            break;

                        case 14:
                            if (isIntersect(zhl, zhh, zc)) {
                                double f = getIntersectRatio(zhl, zhh, zc);
                                yy[k] = yl + f * (yh - yl);
                                xx[k] = xh;
                                k++;
                            }
                            if (isIntersect(zlh, zhh, zc)) {
                                double f = getIntersectRatio(zlh, zhh, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yh;
                                k++;
                            }
                            if (isIntersect(zlh, zhl, zc)) {
                                double f = getIntersectRatio(zlh, zhl, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yh + f * (yl - yh);
                                k++;
                            }
                            break;

                        case 13:
                            if (isIntersect(zll, zlh, zc)) {
                                double f = getIntersectRatio(zll, zlh, zc);
                                yy[k] = yl + f * (yh - yl);
                                xx[k] = xl;
                                k++;
                            }
                            if (isIntersect(zlh, zhh, zc)) {
                                double f = getIntersectRatio(zlh, zhh, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yh;
                                k++;
                            }
                            if (isIntersect(zll, zhh, zc)) {
                                double f = getIntersectRatio(zll, zhh, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yl + f * (yh - yl);
                                k++;
                            }
                            break;

                        case 11:
                            if (isIntersect(zhl, zhh, zc)) {
                                double f = getIntersectRatio(zhl, zhh, zc);
                                yy[k] = yl + f * (yh - yl);
                                xx[k] = xh;
                                k++;
                            }
                            if (isIntersect(zll, zhl, zc)) {
                                double f = getIntersectRatio(zll, zhl, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yl;
                                k++;
                            }
                            if (isIntersect(zll, zhh, zc)) {
                                double f = getIntersectRatio(zll, zhh, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yl + f * (yh - yl);
                                k++;
                            }
                            break;

                        case 7:
                            if (isIntersect(zll, zlh, zc)) {
                                double f = getIntersectRatio(zll, zlh, zc);
                                yy[k] = yl + f * (yh - yl);
                                xx[k] = xl;
                                k++;
                            }
                            if (isIntersect(zll, zhl, zc)) {
                                double f = getIntersectRatio(zll, zhl, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yl;
                                k++;
                            }
                            if (isIntersect(zlh, zhl, zc)) {
                                double f = getIntersectRatio(zlh, zhl, zc);
                                xx[k] = xl + f * (xh - xl);
                                yy[k] = yh + f * (yl - yh);
                                k++;
                            }
                            break;
                    }

                    // We now have k(=2,4) endpoints. Decide which to join.
                    Segment seglist = null;
                    if (k > 0) {
                        if (k == 2) {
                            seglist = new Segment(xx[0], yy[0], xx[1], yy[1], null);
                        } else if (k == 4) {
                            for (k = 3; k >= 1; k--) {
                                int m = k;
                                xl = xx[k];
                                for (int l = 0; l < k; l++) {
                                    if (xx[l] > xl) {
                                        xl = xx[l];
                                        m = l;
                                    }
                                }

                                if (m != k) {
                                    xl = xx[k];
                                    yl = yy[k];
                                    xx[k] = xx[m];
                                    yy[k] = yy[m];
                                    xx[m] = xl;
                                    yy[m] = yl;
                                }
                            }

                            seglist = new Segment(xx[0], yy[0], xx[1], yy[1], null);
                            seglist = new Segment(xx[2], yy[2], xx[3], yy[3], seglist);
                        } else {
                            throw new IllegalStateException("k != 2 or 4");
                        }
                    }

                    segments[j][i] = seglist;
                }
            }

            // Begin following contours.
            // 1. Grab a segment
            // 2. Follow its tail
            // 3. Follow its head
            // 4. Save the contour
            for (int i = 0; i < nx - 1; i++) {
                for (int j = 0; j < ny - 1; j++) {
                    Segment seglist;
                    while ((seglist = segments[j][i]) != null) {
                        ij[0] = i;
                        ij[1] = j;

                        Segment start = seglist;
                        Segment end = seglist;
                        segments[j][i] = seglist.next;

                        double xend = seglist.x1;
                        double yend = seglist.y1;

                        int dir;
                        while ((dir = segdir(xend, yend, ij)) != 0) {
                            // tail
                            int ii = ij[0];
                            int jj = ij[1];
                            Segment[] seg = {segments[jj][ii], null};
                            segments[jj][ii] = segupdate(xend, yend, dir, true, seg);
                            if (seg[1] == null) {
                                break;
                            }
                            end.next = seg[1];
                            end = seg[1];
                            xend = end.x1;
                            yend = end.y1;
                        }

                        end.next = null;
                        ij[0] = i;
                        ij[1] = j;
                        xend = seglist.x0;
                        yend = seglist.y0;

                        while ((dir = segdir(xend, yend, ij)) != 0) {
                            // head
                            int ii = ij[0];
                            int jj = ij[1];
                            Segment[] seg = {segments[jj][ii], null};
                            segments[jj][ii] = segupdate(xend, yend, dir, false, seg);
                            if (seg[1] == null) {
                                break;
                            }
                            seg[1].next = start;
                            start = seg[1];
                            xend = start.x0;
                            yend = start.y0;
                        }

                        // Save the contour locations into the list of contours
                        Isoline contour = new Isoline(zc, isLevelVisible);

                        Segment s = start;
                        contour.add(s.x0, s.y0);
                        while (s.next != null) {
                            s = s.next;
                            contour.add(s.x0, s.y0);
                        }
                        contour.add(s.x1, s.y1);

                        if (!contour.isEmpty()) {
                            contours.add(contour);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns true if zc is between z0 and z1.
     */
    private boolean isIntersect(double z0, double z1, double zc) {
        return (z0 - zc) * (z1 - zc) < 0.0;
    }

    /**
     * Returns the ratio of (zc - z0) / (z1 - z0)
     */
    private double getIntersectRatio(double z0, double z1, double zc) {
        return (zc - z0) / (z1 - z0);
    }

    private boolean XMATCH(double x0, double x1) {
        return Math.abs(x0 - x1) == 0;
    }

    private boolean YMATCH(double y0, double y1) {
        return Math.abs(y0 - y1) == 0;
    }

    /**
     * Determine the entry direction to the next cell  and update the cell
     * indices.
     */
    private int segdir(double xend, double yend, int[] ij) {
        if (YMATCH(yend, y[ij[1]])) {
            if (ij[1] == 0) {
                return 0;
            }
            ij[1] -= 1;
            return 3;
        }

        if (XMATCH(xend, x[ij[0]])) {
            if (ij[0] == 0) {
                return 0;
            }
            ij[0] -= 1;
            return 4;
        }

        if (YMATCH(yend, y[ij[1] + 1])) {
            if (ij[1] >= y.length - 1) {
                return 0;
            }
            ij[1] += 1;
            return 1;
        }

        if (XMATCH(xend, x[ij[0] + 1])) {
            if (ij[0] >= x.length - 1) {
                return 0;
            }
            ij[0] += 1;
            return 2;
        }

        return 0;
    }

    /**
     * Search seglist for a segment with endpoint (xend, yend).
     * The cell entry direction is dir, and if tail=1/0 we are
     * building the tail/head of a contour. The matching segment
     * is pointed to by seg and the updated segment list (with
     * the matched segment stripped) is returned by the function.
     */
    private Segment segupdate(double xend, double yend, int dir, boolean tail, Segment[] seg) {
        Segment seglist = seg[0];
        if (seglist == null) {
            seg[1] = null;
            return null;
        }

        switch (dir) {
            case 1:
            case 3:
                if (YMATCH(yend, seglist.y0)) {
                    if (!tail) {
                        seglist.swap();
                    }
                    seg[1] = seglist;
                    return seglist.next;
                }

                if (YMATCH(yend, seglist.y1)) {
                    if (tail) {
                        seglist.swap();
                    }
                    seg[1] = seglist;
                    return seglist.next;
                }
                break;

            case 2:
            case 4:
                if (XMATCH(xend, seglist.x0)) {
                    if (!tail) {
                        seglist.swap();
                    }
                    seg[1] = seglist;
                    return seglist.next;
                }

                if (XMATCH(xend, seglist.x1)) {
                    if (tail) {
                        seglist.swap();
                    }
                    seg[1] = seglist;
                    return seglist.next;
                }
                break;
        }

        Segment[] seg2 = {seglist.next, seg[1]};
        seglist.next = segupdate(xend, yend, dir, tail, seg2);
        seg[1] = seg2[1];

        return seglist;
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = {MathEx.min(x), MathEx.min(y)};
        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = {MathEx.max(x), MathEx.max(y)};
        return bound;
    }

    @Override
    public void paint(Graphics g) {
        for (Isoline contour : contours) {
            contour.paint(g);
        }
    }

    @Override
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound(), false);
        canvas.add(this);

        if (!isTickVisible) {
            canvas.getAxis(0).setTickVisible(false);
            canvas.getAxis(1).setTickVisible(false);
        }

        return canvas;
    }

    /**
     * Creates a contour plot with 10 isolines.
     * @param z the data matrix to create contour plot.
     */
    public static Contour of(double[][] z) {
        return of(z, 10);
    }

    /**
     * Creates a contour plot.
     * @param z the data matrix to create contour plot.
     * @param numLevels the number of contour levels.
     */
    public static Contour of(double[][] z, int numLevels) {
        return new Contour(z, numLevels, false);
    }

    /**
     * Creates a contour plot with 10 isolines.
     * @param x the x coordinates of the data grid of z. Must be in ascending order.
     * @param y the y coordinates of the data grid of z. Must be in ascending order.
     * @param z the data matrix to create contour plot.
     */
    public static Contour of(double[] x, double[] y, double[][] z) {
        return of(x, y, z, 10);
    }

    /**
     * Creates a contour plot.
     * @param x the x coordinates of the data grid of z. Must be in ascending order.
     * @param y the y coordinates of the data grid of z. Must be in ascending order.
     * @param z the data matrix to create contour plot.
     * @param numLevels the number of contour levels.
     */
    public static Contour of(double[] x, double[] y, double[][] z, int numLevels) {
        return new Contour(x, y, z, numLevels, false);
    }
}
