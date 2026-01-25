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
 * Abstract object that knows how to use a renderer to paint onto the canvas.
 *
 * @author Haifeng Li
 */
public abstract class Shape {

    /**
     * The color of shape. By default, it is black.
     */
    final Color color;

    /**
     * Constructor.
     */
    public Shape() {
        this(Color.BLACK);
    }

    /**
     * Constructor.
     * @param color the color of shape.
     */
    public Shape(Color color) {
        this.color = color;
    }

    /**
     * Draws the shape.
     * @param g the renderer.
     */
    public abstract void paint(Renderer g);
}
