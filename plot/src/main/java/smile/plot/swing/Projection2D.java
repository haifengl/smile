/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
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
    public Projection2D(Figure figure) {
        super(figure);
    }

    @Override
    double[] baseCoordsScreenProjectionRatio(double[] xy) {
        Base base = figure.base;
        double[] ratio = new double[2];
        ratio[0] = (xy[0] - base.lowerBound[0]) / (base.upperBound[0] - base.lowerBound[0]);
        ratio[1] = (xy[1] - base.lowerBound[1]) / (base.upperBound[1] - base.lowerBound[1]);
        return ratio;
    }

    /**
     * Project the screen coordinate back to the logical coordinates.
     * @param x the x of Java2D coordinate in the canvas.
     * @param y the y of Java2D coordinate in the canvas
     */
    public double[] inverseProjection(int x, int y) {
        Base base = figure.base;
        double margin = figure.margin;
        double[] sc = new double[2];

        double ratio = (base.upperBound[0] - base.lowerBound[0]) / (width * (1 - 2 * margin));
        sc[0] = base.lowerBound[0] + ratio * (x - width * margin);

        ratio = (base.upperBound[1] - base.lowerBound[1]) / (height * (1 - 2 * margin));
        sc[1] = base.lowerBound[1] + ratio * (height * (1 - margin) - y);

        return sc;
    }
}
