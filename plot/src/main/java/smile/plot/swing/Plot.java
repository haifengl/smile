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
import java.util.Optional;
import javax.swing.JComponent;

/**
 * The abstract base class of plots.
 *
 * @author Haifeng Li
 */
public abstract class Plot extends Shape {
    /** Constructor. */
    public Plot() {
        this(Color.BLACK);
    }

    /** Constructor. */
    public Plot(Color color) {
        super(color);
    }

    /** Returns the lower bound of data. */
    public abstract double[] getLowerBound();
    /** Returns the upper bound of data. */
    public abstract double[] getUpperBound();

    /** Returns a canvas of the plot. */
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound());
        canvas.add(this);
        return canvas;
    }

    /**
     * Returns the optional name of shape, which will be used to
     * draw a legend outside the box.
     */
    public Optional<Legend[]> legends() {
        return Optional.empty();
    }

    /**
     * Returns a optional tool tip for the object at given coordinates.
     * @param coord the logical coordinates of current mouse position.
     * @return a string if an object with label close to the given coordinates.
     */
    public Optional<String> tooltip(double[] coord) {
        return Optional.empty();
    }
    
    /**
     * Returns an optional list of components in tool bar to control the plot.
     * @return an optional list of toolbar components.
     */
    public Optional<JComponent[]> toolbar() {
        return Optional.empty();
    }
}
