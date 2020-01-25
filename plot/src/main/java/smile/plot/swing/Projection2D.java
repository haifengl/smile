/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.plot.swing;

/**
 * Project 2D logical coordinates to Java2D coordinates.
 *
 * @author Haifeng Li
 */
class Projection2D extends Projection {

    /**
     * Constructor.
     */
    public Projection2D(PlotCanvas canvas) {
        super(canvas);
    }

    @Override
    double[] baseCoordsScreenProjectionRatio(double[] xy) {
        double[] ratio = new double[2];
        ratio[0] = (xy[0] - canvas.base.lowerBound[0]) / (canvas.base.upperBound[0] - canvas.base.lowerBound[0]);
        ratio[1] = (xy[1] - canvas.base.lowerBound[1]) / (canvas.base.upperBound[1] - canvas.base.lowerBound[1]);
        return ratio;
    }

    /**
     * Project the screen coordinate back to the logical coordinates.
     * @param x the x of Java2D coordinate in the canvas.
     * @param y the y of Java2D coordinate in the canvas
     */
    public double[] inverseProjection(int x, int y) {
        double[] sc = new double[2];

        double ratio = (canvas.base.upperBound[0] - canvas.base.lowerBound[0]) / (canvas.getWidth() * (1 - 2 * canvas.margin));
        sc[0] = canvas.base.lowerBound[0] + ratio * (x - canvas.getWidth() * canvas.margin);

        ratio = (canvas.base.upperBound[1] - canvas.base.lowerBound[1]) / (canvas.getHeight() * (1 - 2 * canvas.margin));
        sc[1] = canvas.base.lowerBound[1] + ratio * (canvas.getHeight() * (1 - canvas.margin) - y);

        return sc;
    }
}
