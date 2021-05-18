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
import java.awt.Font;

/**
 * This is specialized label for axis grid labels.
 *
 * @author Haifeng Li
 */
class GridLabel extends Label {
    /**
     * The font for axis label.
     */
    private static final Font BitStreamVeraSans = new Font("BitStream Vera Sans", Font.PLAIN, 12);

    /**
     * Constructor.
     */
    public GridLabel(String text, double[] coordinates, double horizontalReference, double verticalReference, double rotation) {
        super(text, coordinates, horizontalReference, verticalReference, rotation, BitStreamVeraSans, Color.BLACK);
    }
}
