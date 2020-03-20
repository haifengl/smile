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
import java.util.Optional;

/**
 * A shape with optional name, which may be used to
 * draw a legend outside the box.
 *
 * @author Haifeng Li
 */
public abstract class NamedShape extends Shape {

    /**
     * The optional name of shape.
     */
    final Optional<String> name;

    /**
     * Constructor.
     */
    public NamedShape(String name, Color color) {
        super(color);
        this.name = Optional.ofNullable(name);
    }

    /**
     * Returns the optional name of shape, which will be used to
     * draw a legend outside the box.
     */
    public Optional<String> name() {
        return name;
    }
}
