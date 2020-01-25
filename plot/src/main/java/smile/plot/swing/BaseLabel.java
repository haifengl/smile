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
import java.awt.Font;

/**
 * This is specialized label for axis labels. Coordinates used here are are
 * proportional to the base coordinates.
 *
 * @author Haifeng Li
 */
class BaseLabel extends Label {

    /**
     * Constructor.
     */
    public BaseLabel(String text, double horizontalReference, double verticalReference, double... coord) {
        super(text, horizontalReference, verticalReference, 0.0, coord);
    }

    /**
     * Constructor.
     */
    public BaseLabel(String text, double horizontalReference, double verticalReference, double rotation, double... coord) {
        super(text, horizontalReference, verticalReference, rotation, coord);
    }

    @Override
    public void paint(Graphics g) {
        Font f = g.getFont();
        if (font != null) {
            g.setFont(font);
        }

        Color c = g.getColor();
        g.setColor(getColor());

        g.drawTextBaseRatio(text, horizontalReference, verticalReference, rotation, coord);

        g.setColor(c);
        if (font != null) {
            g.setFont(f);
        }
    }
}
