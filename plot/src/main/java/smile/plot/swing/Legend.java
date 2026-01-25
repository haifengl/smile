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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

import java.awt.Color;

/**
 * Legend is a single line text which coordinates are in
 * proportional to the base coordinates.
 *
 * @author Haifeng Li
 */
public class Legend {
    /**
     * The text of label.
     */
    final String text;
    /**
     * The coordinates of label.
     */
    final Color color;

    /**
     * Constructor.
     * @param text the legend label.
     * @param color the color of legend.
     */
    public Legend(String text, Color color) {
        this.text = text;
        this.color = color;
    }
}
