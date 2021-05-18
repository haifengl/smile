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

package smile.plot.swing;

import java.awt.Color;
import smile.math.MathEx;
import smile.sort.QuickSort;

/**
 * A surface object gives 3D information e.g. a contour plots.
 * 
 * @author Haifeng Li
 */
public class Surface extends Plot {

    /**
     * The data-axis locations of surface.
     */
    private double[][][] data;
    /**
     * Vertex Z-axis value in camera coordinate.
     */
    private double[][] zc;
    /**
     * Average z-axis value of triangles to fill for painter's algorithm.
     */
    private double[] az;
    /**
     * The indices of triangles in descending order of average z-axis values.
     */
    private int[] order;
    /**
     * Triangles. Each row is the index of triangle vertices in data.
     */
    private int[][] triangles;
    /**
     * The minimum of the data.
     */
    private double min;
    /**
     * The maximum of the data.
     */
    private double max;
    /**
     * The window width of values for each color.
     */
    private double width = 1.0;
    /**
     * The color palette to represent values.
     */
    private Color[] palette;

    /**
     * Constructor for irregular mesh grid.
     * @param data an m x n x 3 array which are coordinates of m x n surface.
     */
    public Surface(double[][][] data) {
        this(data, null);
    }

    /**
     * Constructor for irregular mesh surface.
     * @param data an m x n x 3 array which are coordinates of m x n surface.
     */
    public Surface(double[][][] data, Color[] palette) {
        super(Color.GRAY);

        this.palette = palette;
        this.data = data;

        int m = data.length;
        int n = data[0].length;
        zc = new double[m][n];

        triangles = new int[2 * m * n][6];
        az = new double[2 * m * n];
        order = new int[az.length];

        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
        for (int i = 0, k = 0; i < m; i++) {
            for (int j = 0; j < n; j++, k += 2) {
                double z = data[i][j][2];
                if (z < min) {
                    min = z;
                }
                if (z > max) {
                    max = z;
                }

                if (i < m - 1 && j < n - 1) {
                    triangles[k][0] = i;
                    triangles[k][1] = j;
                    triangles[k][2] = i + 1;
                    triangles[k][3] = j;
                    triangles[k][4] = i;
                    triangles[k][5] = j + 1;

                    triangles[k + 1][0] = i + 1;
                    triangles[k + 1][1] = j + 1;
                    triangles[k + 1][2] = i + 1;
                    triangles[k + 1][3] = j;
                    triangles[k + 1][4] = i;
                    triangles[k + 1][5] = j + 1;
                }
            }
        }
        
        if (palette != null) {
            width = (max - min) / palette.length;
        }

    }

    @Override
    public double[] getLowerBound() {
        double[] bound = {data[0][0][0], data[0][0][1], data[0][0][2]};
        for (double[][] row : data) {
            for (double[] x : row) {
                if (x[0] < bound[0]) {
                    bound[0] = x[0];
                }
                if (x[1] < bound[1]) {
                    bound[1] = x[1];
                }
                if (x[2] < bound[2]) {
                    bound[2] = x[2];
                }
            }
        }

        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = {data[0][0][0], data[0][0][1], data[0][0][2]};
        for (double[][] row : data) {
            for (double[] x : row) {
                if (x[0] > bound[0]) {
                    bound[0] = x[0];
                }
                if (x[1] > bound[1]) {
                    bound[1] = x[1];
                }
                if (x[2] > bound[2]) {
                    bound[2] = x[2];
                }
            }
        }

        return bound;
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(color);

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length - 1; j++) {
                g.drawLine(data[i][j], data[i][j + 1]);
            }
        }

        for (int i = 0; i < data.length - 1; i++) {
            for (int j = 0; j < data[i].length; j++) {
                g.drawLine(data[i][j], data[i + 1][j]);
            }
        }

        if (palette != null) {
            int m = data.length;
            int n = data[0].length;
            Projection3D p3d = (Projection3D) g.projection;

            /**
             * Calculates z-axis values in camera coordinates.
             */
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    zc[i][j] = p3d.z(data[i][j]);
                }
            }

            /**
             * Calculate (average) z-value for each triangle.
             * Note that this is actually just sum, which is sufficient
             * for us to sort them.
             */
            for (int i = 0; i < triangles.length; i++) {
                az[i] = (zc[triangles[i][0]][triangles[i][1]] + zc[triangles[i][2]][triangles[i][3]] + zc[triangles[i][4]][triangles[i][5]]);
            }

            /**
             * Sorts triangles by z-values and paint them from furthest to
             * nearest, i.e. painter's algorithm. Although the painter's
             * algorithm is computationally and conceptually much easier than
             * most alternatives, it does suffer from several flaws. The most
             * obvious example of where the painter's algorithm falls short
             * is with intersecting surfaces.
             */
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            QuickSort.sort(az, order);

            for (int i : order) {
                double avg = (data[triangles[i][0]][triangles[i][1]][2] + data[triangles[i][2]][triangles[i][3]][2] + data[triangles[i][4]][triangles[i][5]][2]) / 3.0;
                int k = (int) ((avg - min) / width);
                if (k == palette.length) {
                    k = palette.length - 1;
                }

                g.setColor(palette[k]);
                g.fillPolygon(data[triangles[i][0]][triangles[i][1]], data[triangles[i][2]][triangles[i][3]], data[triangles[i][4]][triangles[i][5]]);
            }
        }
    }

    /**
     * Creates a regular mesh surface with the jet color palette.
     * @param z the z-axis values of surface. The x-axis and y-axis location of
     * surface will be set to 0.5, 1.5, 2.5, ...
     * @param k the number of colors in the palette.
     */
    public static Surface of(double[][] z, int k) {
        return of(z, Palette.jet(k, 1.0f));
    }

    /**
     * Creates a regular mesh surface.
     * @param z the z-axis values of surface. The x-axis and y-axis location of
     * surface will be set to 0.5, 1.5, 2.5, ...
     * @param palette the color palette.
     */
    public static Surface of(double[][] z, Color[] palette) {
        int m = z.length;
        int n = z[0].length;
        double[] x = new double[m];
        double[] y = new double[n];
        for (int i = 0; i < m; i++) {
            x[i] = i + 0.5;
        }
        for (int j = 0; j < n; j++) {
            y[j] = j + 0.5;
        }

        return of(x, y, z, palette);
    }

    /**
     * Creates an irregular mesh grid.
     * @param x the x-axis values of surface.
     * @param y the y-axis values of surface.
     * @param z the z-axis values of surface.
     */
    public static Surface of(double[] x, double[] y, double[][] z) {
        return of(x, y, z, null);
    }

    /**
     * Creates an irregular mesh surface with the jet color palette.
     * @param x the x-axis values of surface.
     * @param y the y-axis values of surface.
     * @param z the z-axis values of surface.
     */
    public static Surface of(double[] x, double[] y, double[][] z, int k) {
        return of(x, y, z, Palette.jet(k, 1.0f));
    }

    /**
     * Creates an irregular mesh surface.
     * @param x the x-axis values of surface.
     * @param y the y-axis values of surface.
     * @param z the z-axis values of surface.
     * @param palette the color palette.
     */
    public static Surface of(double[] x, double[] y, double[][] z, Color[] palette) {
        int m = z.length;
        int n = z[0].length;
        double[][][] data = new double[m][n][3];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                data[i][j][0] = x[i];
                data[i][j][1] = y[j];
                data[i][j][2] = z[i][j];
            }
        }

        return new Surface(data, palette);
    }
}
