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
     */
    public Legend(String text, Color color) {
        this.text = text;
        this.color = color;
    }
}
