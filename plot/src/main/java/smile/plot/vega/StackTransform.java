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
package smile.plot.vega;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The stack transform.
 *
 * @author Haifeng Li
 */
public class StackTransform {
    /** VegaLite's Stack definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    StackTransform(ObjectNode spec) {
        this.spec = spec;
    }

    @Override
    public String toString() {
        return spec.toString();
    }

    /**
     * Returns the specification in pretty print.
     * @return the specification in pretty print.
     */
    public String toPrettyString() {
        return spec.toPrettyString();
    }

    /**
     * Sets the mode for stacking marks. The "zero" offset will stack starting
     * at 0. The "center" offset will center the stacks. The "normalize" offset
     * will compute percentage values for each stack point, with output values
     * in the range [0,1].
     *
     * @param mode "zero", "center", or "normalize".
     * @return this object.
     */
    public StackTransform offset(String mode) {
        spec.put("offset", mode);
        return this;
    }

    /**
     * Sets the fields for sorting data objects within a window. If two data
     * objects are considered equal by the comparator, they are considered
     * "peer" values of equal rank. If sort is not specified, the order is
     * undefined: data objects are processed in the order they are observed
     * and none are considered peers (the ignorePeers parameter is ignored
     * and treated as if set to true).
     *
     * @param fields the partitioning fields.
     * @return this object.
     */
    public StackTransform sort(String... fields) {
        ArrayNode node = spec.putArray("sort");
        for (var field : fields) {
            node.addObject().put("field", field);
        }
        return this;
    }

    /**
     * Sets the fields for sorting data objects within a window. If two data
     * objects are considered equal by the comparator, they are considered
     * "peer" values of equal rank. If sort is not specified, the order is
     * undefined: data objects are processed in the order they are observed
     * and none are considered peers (the ignorePeers parameter is ignored
     * and treated as if set to true).
     *
     * @param fields the partitioning fields.
     * @return this object.
     */
    public StackTransform sort(SortField... fields) {
        ArrayNode node = spec.putArray("sort");
        for (var field : fields) {
            node.addObject()
                    .put("field", field.field())
                    .put("order", field.order());
        }
        return this;
    }
}
