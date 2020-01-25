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

import java.awt.Color;

/**
 * This is specialized line for axis lines. Coordinates used here are are
 * proportional to the base coordinates.
 * @author Haifeng Li
 */
class BaseLine extends Line {

    /**
     * Constructor.
     * @param start the start point of the line.
     * @param end   the end point of the line.
     */
    public BaseLine(double[] start, double[] end) {
        super(start, end);
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());
        g.drawLineBaseRatio(points);
        g.setColor(c);
    }
}
