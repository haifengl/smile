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
import smile.math.Math;
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
     * Constructor.
     * @param z the z-axis values of surface. The x-axis and y-axis location of
     * surface will be set to 0.5, 1.5, 2.5, ...
     */
    public Surface(double[][] z) {
        super(Color.GRAY);
        init(z);
    }
    
    /**
     * Initialization.
     */
    private void init(double[][] z) {
        max = Math.max(z);
        min = Math.min(z);
        if (palette != null) {
            width = (max - min) / palette.length;
        }

        int m = z.length;
        int n = z[0].length;
        triangles = new int[2 * m * n][6];
        az = new double[2 * m * n];
        order = new int[az.length];

        zc = new double[m][n];
        data = new double[m][n][3];
        for (int i = 0, k = 0; i < m; i++) {
            for (int j = 0; j < n; j++, k += 2) {
                data[i][j][0] = i + 0.5;
                data[i][j][1] = j + 0.5;
                data[i][j][2] = z[i][j];

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
    }

    /**
     * Constructor.
     * @param z the z-axis values of surface. The x-axis and y-axis location of
     * surface will be set to 0.5, 1.5, 2.5, ...
     * @param palette the color palette.
     */
    public Surface(double[][] z, Color[] palette) {
        this.palette = palette;
        init(z);
    }
    
    /**
     * Constructor for regular mesh grid.
     * @param x the x-axis values of surface.
     * @param y the y-axis values of surface.
     * @param z the z-axis values of surface.
     */
    public Surface(double[] x, double[] y, double[][] z) {
        super(Color.GRAY);
        init(x, y, z);
    }
    
    /**
     * Initialization.
     */
    private void init(double[] x, double[] y, double[][] z) {
        max = Math.max(z);
        min = Math.min(z);
        if (palette != null) {
            width = (max - min) / palette.length;
        }

        int m = z.length;
        int n = z[0].length;
        triangles = new int[2 * m * n][6];
        az = new double[2 * m * n];
        order = new int[az.length];

        zc = new double[m][n];
        data = new double[m][n][3];
        for (int i = 0, k = 0; i < m; i++) {
            for (int j = 0; j < n; j++, k += 2) {
                data[i][j][0] = x[i];
                data[i][j][1] = y[j];
                data[i][j][2] = z[i][j];

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
    }

    /**
     * Constructor for regular mesh grid.
     * @param x the x-axis values of surface.
     * @param y the y-axis values of surface.
     * @param z the z-axis values of surface.
     * @param palette the color palette.
     */
    public Surface(double[] x, double[] y, double[][] z, Color[] palette) {
        this.palette = palette;
        init(x, y, z);
    }

    /**
     * Constructor for irregular mesh grid.
     * @param data an m x n x 3 array which are coordinates of m x n surface.
     */
    public Surface(double[][][] data) {
        super(Color.GRAY);
        init(data);
    }
    
    /**
     * Initialization.
     */
    private void init(double[][][] data) {
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

    /**
     * Constructor for irregular mesh grid.
     * @param data an m x n x 3 array which are coordinates of m x n surface.
     */
    public Surface(double[][][] data, Color[] palette) {
        this.palette = palette;
        init(data);
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

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
        
        g.setColor(c);
    }

    /**
     * Create a plot canvas with the 3D surface plot of given data.
     * @param z the z-axis values of surface. The x-axis and y-axis location of
     * surface will be set to 0.5, 1.5, 2.5, ...
     */
    public static PlotCanvas plot(double[][] z) {
        double[] lowerBound = {0, 0, Math.min(z)};
        double[] upperBound = {z.length, z[0].length, Math.max(z)};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Surface surface = new Surface(z);
        canvas.add(surface);

        return canvas;
    }

    /**
     * Create a plot canvas with the 3D surface plot of given data.
     * @param z the z-axis values of surface. The x-axis and y-axis location of
     * surface will be set to 0.5, 1.5, 2.5, ...
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][] z, Color[] palette) {
        double[] lowerBound = {0, 0, Math.min(z)};
        double[] upperBound = {z.length, z[0].length, Math.max(z)};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Surface surface = new Surface(z, palette);
        canvas.add(surface);

        return canvas;
    }

    /**
     * Create a plot canvas with the 3D surface plot of given data.
     * @param x the x-axis values of surface.
     * @param y the y-axis values of surface.
     * @param z the z-axis values of surface.
     */
    public static PlotCanvas plot(double[] x, double[] y, double[][] z) {
        double[] lowerBound = {Math.min(x), Math.min(y), Math.min(z)};
        double[] upperBound = {Math.max(x), Math.max(y), Math.max(z)};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Surface surface = new Surface(x, y, z);
        canvas.add(surface);

        return canvas;
    }

    /**
     * Create a plot canvas with the 3D surface plot of given data.
     * @param x the x-axis values of surface.
     * @param y the y-axis values of surface.
     * @param z the z-axis values of surface.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[] x, double[] y, double[][] z, Color[] palette) {
        double[] lowerBound = {Math.min(x), Math.min(y), Math.min(z)};
        double[] upperBound = {Math.max(x), Math.max(y), Math.max(z)};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Surface surface = new Surface(x, y, z, palette);
        canvas.add(surface);

        return canvas;
    }

    /**
     * Create a plot canvas with the 3D surface plot of given data.
     * @param data an m x n x 3 array which are coordinates of m x n surface.
     */
    public static PlotCanvas plot(double[][][] data) {
        double[] lowerBound = {data[0][0][0], data[0][0][1], data[0][0][2]};
        double[] upperBound = {data[0][0][0], data[0][0][1], data[0][0][2]};
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j][0] < lowerBound[0]) {
                    lowerBound[0] = data[i][j][0];
                }
                if (data[i][j][0] > upperBound[0]) {
                    upperBound[0] = data[i][j][0];
                }
                if (data[i][j][1] < lowerBound[1]) {
                    lowerBound[1] = data[i][j][1];
                }
                if (data[i][j][1] > upperBound[1]) {
                    upperBound[1] = data[i][j][1];
                }
                if (data[i][j][2] < lowerBound[2]) {
                    lowerBound[2] = data[i][j][2];
                }
                if (data[i][j][2] > upperBound[2]) {
                    upperBound[2] = data[i][j][2];
                }
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Surface surface = new Surface(data);
        canvas.add(surface);

        return canvas;
    }

    /**
     * Create a plot canvas with the 3D surface plot of given data.
     * @param data an m x n x 3 array which are coordinates of m x n surface.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][][] data, Color[] palette) {
        double[] lowerBound = {data[0][0][0], data[0][0][1], data[0][0][2]};
        double[] upperBound = {data[0][0][0], data[0][0][1], data[0][0][2]};
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j][0] < lowerBound[0]) {
                    lowerBound[0] = data[i][j][0];
                }
                if (data[i][j][0] > upperBound[0]) {
                    upperBound[0] = data[i][j][0];
                }
                if (data[i][j][1] < lowerBound[1]) {
                    lowerBound[1] = data[i][j][1];
                }
                if (data[i][j][1] > upperBound[1]) {
                    upperBound[1] = data[i][j][1];
                }
                if (data[i][j][2] < lowerBound[2]) {
                    lowerBound[2] = data[i][j][2];
                }
                if (data[i][j][2] > upperBound[2]) {
                    upperBound[2] = data[i][j][2];
                }
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Surface surface = new Surface(data, palette);
        canvas.add(surface);

        return canvas;
    }
}