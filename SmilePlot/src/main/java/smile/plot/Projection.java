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
    protected PlotCanvas canvas;
    /**
     * The base coordinates on Java2D screen.
     */
    private int[][] baseScreenCoords;

    /**
     * Constructor.
     */
    public Projection(PlotCanvas canvas) {
        this.canvas = canvas;
        initBaseCoordsProjection();
    }

    /**
     * Reset the base coordinates on Java2D screen.
     */
    public void reset() {
        initBaseCoordsProjection();
    }

    /**
     * Initialize base coordinates on Java2D screen.
     */
    private void initBaseCoordsProjection() {
        baseScreenCoords = new int[canvas.base.baseCoords.length][2];
        for (int i = 0; i < canvas.base.dimension + 1; i++) {
            double[] ratio = baseCoordsScreenProjectionRatio(canvas.base.baseCoords[i]);
            baseScreenCoords[i][0] = (int) (canvas.getWidth() * (canvas.margin + (1 - 2 * canvas.margin) * ratio[0]));
            baseScreenCoords[i][1] = (int) (canvas.getHeight() - canvas.getHeight() * (canvas.margin + (1 - 2 * canvas.margin) * ratio[1]));
        }
    }

    /**
     * Project logical coordinates to Java2D coordinates.
     */
    public int[] screenProjection(double... coord) {
        double[] sc = new double[2];
        sc[0] = baseScreenCoords[0][0];
        sc[1] = baseScreenCoords[0][1];

        for (int i = 0; i < canvas.base.dimension; i++) {
            sc[0] += ((coord[i] - canvas.base.baseCoords[0][i]) / (canvas.base.baseCoords[i + 1][i] - canvas.base.baseCoords[0][i])) * (baseScreenCoords[i + 1][0] - baseScreenCoords[0][0]);
            sc[1] += ((coord[i] - canvas.base.baseCoords[0][i]) / (canvas.base.baseCoords[i + 1][i] - canvas.base.baseCoords[0][i])) * (baseScreenCoords[i + 1][1] - baseScreenCoords[0][1]);
        }

        return new int[]{(int) sc[0], (int) sc[1]};
    }

    /**
     * Project logical coordinates in base ratio to Java2D coordinates.
     */
    public int[] screenProjectionBaseRatio(double... coord) {
        double[] sc = new double[2];
        sc[0] = baseScreenCoords[0][0];
        sc[1] = baseScreenCoords[0][1];

        for (int i = 0; i < canvas.base.dimension; i++) {
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
