/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
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
    /** Plot name. */
    final String name;

    /** Constructor. */
    public Plot() {
        this(null, Color.BLACK);
    }

    /** Constructor. */
    public Plot(Color color) {
        this(null, color);
    }

    /** Constructor. */
    public Plot(String name, Color color) {
        super(color);
        this.name = name;
    }

    /** Returns the lower bound of data. */
    public abstract double[] getLowerBound();
    /** Returns the upper bound of data. */
    public abstract double[] getUpperBound();

    /** Returns a canvas of the plot. */
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound());
        canvas.add(this);
        if (name != null) {
            canvas.setTitle(name);
        }
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
     * Returns an optional tooltip for the object at given coordinates.
     * @param coord the logical coordinates of current mouse position.
     * @return a string if an object with label close to the given coordinates.
     */
    public Optional<String> tooltip(double[] coord) {
        return Optional.empty();
    }
    
    /**
     * Returns an optional list of components in toolbar to control the plot.
     * @return an optional list of toolbar components.
     */
    public Optional<JComponent[]> toolbar() {
        return Optional.empty();
    }
}
