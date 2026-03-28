/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

/**
 * Projection provides methods to map logical coordinates to Java2D coordinates.
 * Both 2D and 3D logical coordinates are supported.
 *
 * @author Haifeng Li
 */
public abstract class Projection {
    /**
     * The canvas associated with this projection. The base object of canvas
     * provides logical coordinate space and the Java2D coordinate space of
     * canvas is the projection target.
     */
    final Figure figure;
    /**
     * The base coordinates on Java2D screen.
     */
    final int[][] baseScreenCoords;
    /**
     * The width of canvas in Java2D coordinate space.
     */
    int width = 800;
    /**
     * The height of canvas in Java2D coordinate space.
     */
    int height = 800;

    /**
     * Constructor.
     * @param figure the figure to project.
     */
    public Projection(Figure figure) {
        this.figure = figure;
        this.baseScreenCoords = new int[figure.base.baseCoords.length][2];
        init();
    }

    /**
     * Reset the base coordinates on Java2D screen.
     */
    public void reset() {
        init();
    }

    /**
     * Sets the canvas size.
     * @param width  the canvas width.
     * @param height the canvas height.
     */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        reset();
    }

    /**
     * Returns the width of canvas.
     * @return the width of canvas.
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height of canvas.
     * @return the height of canvas.
     */
    public int height() {
        return height;
    }

    /**
     * Initialize base coordinates on Java2D screen.
     */
    private void init() {
        Base base = figure.base;
        double margin = figure.margin;

        for (int i = 0; i < base.dimension + 1; i++) {
            double[] ratio = baseCoordsScreenProjectionRatio(base.baseCoords[i]);
            baseScreenCoords[i][0] = (int) (width * (margin + (1 - 2 * margin) * ratio[0]));
            baseScreenCoords[i][1] = (int) (height - height * (margin + (1 - 2 * margin) * ratio[1]));
        }
    }

    /**
     * Project logical coordinates to Java2D coordinates.
     * @param coord the logic coordinates.
     * @return Java2D coordinates.
     */
    public int[] screenProjection(double... coord) {
        Base base = figure.base;

        double[] sc = new double[2];
        sc[0] = baseScreenCoords[0][0];
        sc[1] = baseScreenCoords[0][1];

        for (int i = 0; i < base.dimension; i++) {
            sc[0] += ((coord[i] - base.baseCoords[0][i]) / (base.baseCoords[i + 1][i] - base.baseCoords[0][i])) * (baseScreenCoords[i + 1][0] - baseScreenCoords[0][0]);
            sc[1] += ((coord[i] - base.baseCoords[0][i]) / (base.baseCoords[i + 1][i] - base.baseCoords[0][i])) * (baseScreenCoords[i + 1][1] - baseScreenCoords[0][1]);
        }

        return new int[]{(int) sc[0], (int) sc[1]};
    }

    /**
     * Project logical coordinates in base ratio to Java2D coordinates.
     * @param coord the logic coordinates in base ratio.
     * @return Java2D coordinates.
     */
    public int[] screenProjectionBaseRatio(double... coord) {
        Base base = figure.base;

        double[] sc = new double[2];
        sc[0] = baseScreenCoords[0][0];
        sc[1] = baseScreenCoords[0][1];

        for (int i = 0; i < base.dimension; i++) {
            sc[0] += coord[i] * (baseScreenCoords[i + 1][0] - baseScreenCoords[0][0]);
            sc[1] += coord[i] * (baseScreenCoords[i + 1][1] - baseScreenCoords[0][1]);
        }

        return new int[]{(int) sc[0], (int) sc[1]};
    }

    /**
     * Returns the ratio of base coordinates to screen.
     */
    abstract double[] baseCoordsScreenProjectionRatio(double[] xyz);
}
