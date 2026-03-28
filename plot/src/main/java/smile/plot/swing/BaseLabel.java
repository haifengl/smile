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

import java.awt.Color;
import java.awt.Font;

/**
 * This is specialized label for axis labels. Coordinates used here are
 * proportional to the base coordinates.
 *
 * @author Haifeng Li
 */
class BaseLabel extends Label {
    /**
     * The font for axis label.
     */
    private static final Font Arial = new Font("Arial", Font.BOLD, 14);

    /**
     * Constructor.
     * @param text the label text.
     * @param coordinates the label location.
     * @param horizontalReference the horizontal reference position of coordinates respected to dimension of text.
     * @param verticalReference the vertical reference position of coordinates respected to dimension of text.
     * @param rotation the rotation angel of text.
     */
    public BaseLabel(String text, double[] coordinates, double horizontalReference, double verticalReference, double rotation) {
        super(text, coordinates, horizontalReference, verticalReference, rotation, Arial, Color.BLACK);
    }

    @Override
    public void paint(Renderer g) {
        Font f = g.getFont();
        g.setFont(font);

        Color c = g.getColor();
        g.setColor(color);

        g.drawTextBaseRatio(text, coordinates, horizontalReference, verticalReference, rotation);

        g.setColor(c);
        g.setFont(f);
    }
}
