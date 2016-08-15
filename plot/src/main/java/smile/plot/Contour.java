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
package smile.plot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import smile.math.Math;

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

    private static final String DIMENSIONS_XZ_DONT_MATCH = "The dimensions of x and z don't match.";
    private static final String DIMENSIONS_YZ_DONT_MATCH = "The dimensions of y and z don't match.";
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
     * The contour color.
     */
    private Color[] colors;
    /**
     * True if show the level values.
     */
    private boolean showLevelValue = true;
    /**
     * The set of contours.
     */
    private List<Isoline> contours;

    /**
     * Contour contains a list of segments.
     */
    class Isoline {

        /**
         * The coordinates of points along the contour line.
         */
        List<double[]> points = new ArrayList<>();
        /**
         * The level value of contour line.
         */
        double level;
        /**
         * The color of contour line.
         */
        Color color;
        /**
         * The label of contour line.
         */
        Label label;

        /**
         * Constructor.
         */
        Isoline(double level) {
            this.level = level;
        }

        /**
         * Constructor.
         */
        Isoline(double level, Color color) {
            this.level = level;
            this.color = color;
        }

        /**
         * Add a point to the contour line.
         */
        void add(double[] point) {
            points.add(point);
        }

        /**
         * Add a point to the contour line.
         */
        void add(double x, double y) {
            double[] point = {x, y};
            points.add(point);
        }

        /**
         * Paint the contour line. If the color attribute is null, the level
         * value of contour line will be shown along the line.
         */
        void paint(Graphics g) {
            Color c = g.getColor();
            if (color != null) {
                g.setColor(color);
            }

            double angle = 0.0;
            double horizontalReference = 0.0;
            double verticalReference = 0.0;
            double[] coord = null;

            if (points.size() > 1) {
                double[] x1 = points.get(0);
                for (int i = 1; i < points.size(); i++) {
                    double[] x2 = points.get(i);
                    g.drawLine(x1, x2);
                    x1 = x2;

                    if (label == null) {
                        if (i == points.size() / 2) {
                            coord = x1;

                            int k = i + 1;
                            if (k >= points.size()) {
                                k = i;
                            }

                            // compute label angle
                            angle = Math.PI / 2;

                            double dxx = points.get(k)[0] - points.get(i)[0];
                            double dyy = points.get(k)[1] - points.get(i)[1];
                            if (dyy < 0.0) {
                                angle = -Math.PI / 2;
                            }

                            if (dxx != 0.0) {
                                angle = Math.atan(dyy / dxx) + Math.PI / 2;
                            }
                        }
                    }
                }
            } else if (points.size() == 1) {
                double[] x1 = points.get(0);
                g.drawPoint('@', x1);
                coord = x1;
                horizontalReference = 0.0;
            }

            if (label == null) {
                double[] lb = g.getLowerBound();
                double[] ub = g.getUpperBound();
                double xrange = ub[0] - lb[0];
                double yrange = ub[1] - lb[1];

                if (ub[0] - coord[0] < xrange / 10) {
                    horizontalReference = 1.0;
                }

                if (ub[1] - coord[1] < yrange / 10) {
                    horizontalReference = 1.0;
                }

                if (coord[1] - lb[1] < yrange / 10) {
                    horizontalReference = 0.0;
                }

                label = new Label(String.format("%.2G", level), horizontalReference, verticalReference, angle, coord);
            }

            if (showLevelValue && label != null) {
                label.paint(g);
            }

            if (color != null) {
                g.setColor(c);
            }
        }
    }

    /**
     * Constructor.
     * @param z the data matrix to create contour plot.
     */
    public Contour(double[][] z) {
        this.z = z;
        init();
    }

    /**
     * Constructor.
     * @param z the data matrix to create contour plot.
     * @param numLevels the number of contour levels.
     */
    public Contour(double[][] z, int numLevels) {
        this.z = z;
        this.numLevels = numLevels;
        init();
    }

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
        init();
    }

    /**
     * Constructor.
     * @param z the data matrix to create contour plot.
     * @param levels the level values of contours.
     * @param colors the color for each contour level.
     */
    public Contour(double[][] z, double[] levels, Color[] colors) {
        this.z = z;
        this.levels = levels;
        this.colors = colors;
        showLevelValue = false;
        init();
    }

    /**
     * Constructor.
     * @param x the x coordinates of the data grid of z. Must be in ascending order.
     * @param y the y coordinates of the data grid of z. Must be in ascending order.
     * @param z the data matrix to create contour plot.
     */
    public Contour(double[] x, double[] y, double[][] z) {
        if (x.length != z[0].length) {
            throw new IllegalArgumentException(DIMENSIONS_XZ_DONT_MATCH);
        }

        if (y.length != z.length) {
            throw new IllegalArgumentException(DIMENSIONS_YZ_DONT_MATCH);
        }

        this.x = x;
        this.y = y;
        this.z = z;
        init();
    }

    /**
     * Constructor.
     * @param x the x coordinates of the data grid of z. Must be in ascending order.
     * @param y the y coordinates of the data grid of z. Must be in ascending order.
     * @param z the data matrix to create contour plot.
     * @param numLevels the number of contour levels.
     */
    public Contour(double[] x, double[] y, double[][] z, int numLevels) {
        if (x.length != z[0].length) {
            throw new IllegalArgumentException(DIMENSIONS_XZ_DONT_MATCH);
        }

        if (y.length != z.length) {
            throw new IllegalArgumentException(DIMENSIONS_YZ_DONT_MATCH);
        }

        this.x = x;
        this.y = y;
        this.z = z;
        this.numLevels = numLevels;
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
            throw new IllegalArgumentException(DIMENSIONS_XZ_DONT_MATCH);
        }

        if (y.length != z.length) {
            throw new IllegalArgumentException(DIMENSIONS_YZ_DONT_MATCH);
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
            throw new IllegalArgumentException(DIMENSIONS_XZ_DONT_MATCH);
        }

        if (y.length != z.length) {
            throw new IllegalArgumentException(DIMENSIONS_YZ_DONT_MATCH);
        }

        this.x = x;
        this.y = y;
        this.z = z;
        this.levels = levels;
        init();
    }

    /**
     * Constructor.
     * @param x the x coordinates of the data grid of z. Must be in ascending order.
     * @param y the y coordinates of the data grid of z. Must be in ascending order.
     * @param z the data matrix to create contour plot.
     * @param levels the level values of contours. Must be strictly increasing and finite.
     * @param colors the color for each contour level.
     */
    public Contour(double[] x, double[] y, double[][] z, double[] levels, Color[] colors) {
        if (x.length != z[0].length) {
            throw new IllegalArgumentException(DIMENSIONS_XZ_DONT_MATCH);
        }

        if (y.length != z.length) {
            throw new IllegalArgumentException(DIMENSIONS_YZ_DONT_MATCH);
        }

        if (levels.length != colors.length) {
            throw new IllegalArgumentException("The number of levels and colors don't match.");
        }

        this.x = x;
        this.y = y;
        this.z = z;
        this.levels = levels;
        this.colors = colors;
        showLevelValue = false;
        init();
    }

    /**
     * A line segment in contour line.
     */
    class Segment {

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

        zMin = Math.min(z);
        zMax = Math.max(z);

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
                    precisionDigits -= 1;
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

        for (int c = 0; c < levels.length; c++) {
            double zc = levels[c];
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
                            seglist = new Segment(xx[0], yy[0], xx[1], yy[1], seglist);
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

                            seglist = new Segment(xx[0], yy[0], xx[1], yy[1], seglist);
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
                    Segment seglist = null;
                    while ((seglist = segments[j][i]) != null) {
                        ij[0] = i;
                        ij[1] = j;

                        Segment start = seglist;
                        Segment end = seglist;
                        segments[j][i] = seglist.next;

                        double xend = seglist.x1;
                        double yend = seglist.y1;

                        int dir = 0;
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
                        dir = 0;
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
                        Isoline contour = new Isoline(zc);
                        if (colors != null) {
                            contour.color = colors[c];
                        }

                        Segment s = start;
                        contour.add(s.x0, s.y0);
                        while (s.next != null) {
                            s = s.next;
                            contour.add(s.x0, s.y0);
                        }
                        contour.add(s.x1, s.y1);
                        
                        if (!contour.points.isEmpty()) {
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
        if ((z0 - zc) * (z1 - zc) < 0.0) {
            return true;
        }

        return false;
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

    /**
     * Set if show the level value with each contour line.
     */
    public void showLevelValue(boolean b) {
        showLevelValue = b;
    }

    @Override
    public void paint(Graphics g) {
        for (Isoline contour : contours) {
            contour.paint(g);
        }

        if (colors != null) {
            g.clearClip();
            Color c = g.getColor();

            double height = 0.7 / colors.length;
            double[] start = {1.1, 0.15};
            double[] end = {1.13, start[1] - height};

            for (int i = 0; i < colors.length; i++) {
                g.setColor(colors[i]);
                g.fillRectBaseRatio(start, end);
                start[1] += height;
                end[1] += height;
            }

            g.setColor(Color.BLACK);
            start[1] -= height;
            end[1] = 0.15 - height;
            g.drawRectBaseRatio(start, end);
            start[0] = 1.14;
            double log = Math.log10(Math.abs(levels[levels.length-1]));
            int decimal = 1;
            if (log < 0) {
                decimal = (int) -log + 1;
            }
            g.drawTextBaseRatio(String.valueOf(Math.round(levels[levels.length-1], decimal)), 0.0, 1.0, start);

            start[1] = 0.15 - height;
            log = Math.log10(Math.abs(levels[0]));
            decimal = 1;
            if (log < 0) {
                decimal = (int) -log + 1;
            }
            g.drawTextBaseRatio(String.valueOf(Math.round(levels[0], decimal)), 0.0, 0.0, start);

            g.setColor(c);
        }
    }

    /**
     * Create a plot canvas with the contour plot of given data.
     * @param z a matrix.
     */
    public static PlotCanvas plot(double[][] z) {
        double[] lowerBound = {0, 0};
        double[] upperBound = {z[0].length, z.length};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Contour(z));

        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(1).setLabelVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the contour plot of given data.
     * @param z a matrix.
     */
    public static PlotCanvas plot(double[][] z, double[] levels, Color[] palette) {
        double[] lowerBound = {0, 0};
        double[] upperBound = {z[0].length, z.length};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Contour(z, levels, palette));

        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(1).setLabelVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the contour plot of given data.
     * @param z a matrix.
     */
    public static PlotCanvas plot(double[] x, double[] y, double[][] z) {
        double[] lowerBound = {Math.min(x), Math.min(y)};
        double[] upperBound = {Math.max(x), Math.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Contour(x, y, z));

        return canvas;
    }

    /**
     * Create a plot canvas with the contour plot of given data.
     * @param z a matrix.
     */
    public static PlotCanvas plot(double[] x, double[] y, double[][] z, double[] levels, Color[] palette) {
        double[] lowerBound = {Math.min(x), Math.min(y)};
        double[] upperBound = {Math.max(x), Math.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Contour(x, y, z, levels, palette));

        return canvas;
    }
}
