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

/**
 * Projection provides methods to map logical coordinates to Java2D coordinates.
 * Both 2D and 3D logical coordinates are supported.
 *
 * @author Haifeng Li
 */
abstract class Projection {
    /**
     * The canvas associated with this projection. The base object of canvas
     * provides logical coordinate space and the Java2D coordinate space of
     * canvas is the projection target.
     */
    protected final Canvas canvas;
    /**
     * The base coordinates on Java2D screen.
     */
    private int[][] baseScreenCoords;
    /**
     * The width of canvas in Java2D coordinate space.
     */
    protected int width = 1;
    /**
     * The height of canvas in Java2D coordinate space.
     */
    protected int height = 1;

    /**
     * Constructor.
     */
    public Projection(Canvas canvas) {
        this.canvas = canvas;
        init();
    }

    /**
     * Reset the base coordinates on Java2D screen.
     */
    public void reset() {
        init();
    }

    /**
     * Sets the size of physical plot area.
     */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        reset();
    }

    /**
     * Initialize base coordinates on Java2D screen.
     */
    private void init() {
        Base base = canvas.base;
        double margin = canvas.margin;

        baseScreenCoords = new int[base.baseCoords.length][2];
        for (int i = 0; i < base.dimension + 1; i++) {
            double[] ratio = baseCoordsScreenProjectionRatio(base.baseCoords[i]);
            baseScreenCoords[i][0] = (int) (width * (margin + (1 - 2 * margin) * ratio[0]));
            baseScreenCoords[i][1] = (int) (height - height * (margin + (1 - 2 * margin) * ratio[1]));
        }
    }

    /**
     * Project logical coordinates to Java2D coordinates.
     */
    public int[] screenProjection(double... coord) {
        Base base = canvas.base;

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
     */
    public int[] screenProjectionBaseRatio(double... coord) {
        Base base = canvas.base;

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
