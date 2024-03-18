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
package smile.plot.vega;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * View-level data transformations such as filter and new field calculation.
 * When both view-level transforms and field transforms inside encoding are
 * specified, the view-level transforms are executed first based on the order
 * in the array. Then the inline transforms are executed in this order: bin,
 * timeUnit, aggregate, sort, and stack.
 *
 * @author Haifeng Li
 */
public class Transform {
    /** VegaLite's Transform definition object. */
    final ArrayNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Transform(ArrayNode spec) {
        this.spec = spec;
    }

    /**
     * Adds a filter transform.
     * @param predicate an expression string, where datum can be used to refer
     *                 to the current data object. For example, "datum.b2 > 60"
     *                  would make the output data includes only items that have
     *                  values in the field b2 over 60.
     * @return this object.
     */
    Transform filter(String predicate) {
        ObjectNode node = spec.addObject();
        node.put("filter", predicate);
        spec.add(node);
        return this;
    }
}
