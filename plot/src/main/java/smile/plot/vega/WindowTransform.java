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
package smile.plot.vega;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The window transform performs calculations over sorted groups of data
 * objects. These calculations including ranking, lead/lag analysis, and
 * aggregates such as running sums and averages. Calculated values are
 * written back to the input data stream. If you only want to set the same
 * aggregated value in a new field, you can use the simpler join aggregate
 * transform.
 *
 * @author Haifeng Li
 */
public class WindowTransform {
    /** VegaLite's Window definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    WindowTransform(ObjectNode spec) {
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
     * Sets the frame specification indicating how the sliding window should
     * proceed. It is either a number indicating the offset from the current
     * data object, or null to indicate unbounded rows preceding or following
     * the current data object. The default value is [null, 0], indicating that
     * the sliding window includes the current object and all preceding objects.
     * The value [-5, 5] indicates that the window should include five objects
     * preceding and five objects following the current object. Finally,
     * [null, null] indicates that the window frame should always include all
     * data objects. If you this frame and want to assign the same value to add
     * objects, you can use the simpler join aggregate transform. The only
     * operators affected are the aggregation operations and the first_value,
     * last_value, and nth_value window operations. The other window operations
     * are not affected by this.
     *
     * @param first the offset of the first object in the sliding window.
     * @param last the offset of the first object in the sliding window.
     * @return this object.
     */
    public WindowTransform frame(Integer first, Integer last) {
        spec.putArray("frame").add(first).add(last);
        return this;
    }

    /**
     * Sets if the sliding window frame should ignore peer values (data that
     * are considered identical by the sort criteria). The default is false,
     * causing the window frame to expand to include all peer values. If set
     * to true, the window frame will be defined by offset values only. This
     * setting only affects those operations that depend on the window frame,
     * namely aggregation operations and the first_value, last_value, and
     * nth_value window operations.
     *
     * @param flag If true, ignore peer values.
     * @return this object.
     */
    public WindowTransform ignorePeers(boolean flag) {
        spec.put("ignorePeers", flag);
        return this;
    }

    /**
     * Sets the data fields for partitioning the data objects into separate
     * windows. If unspecified, all data points will be in a single window.
     *
     * @param fields the partitioning fields.
     * @return this object.
     */
    public WindowTransform groupby(String... fields) {
        ArrayNode node = spec.putArray("groupby");
        for (var field : fields) {
            node.add(field);
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
    public WindowTransform sort(String... fields) {
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
    public WindowTransform sort(SortField... fields) {
        ArrayNode node = spec.putArray("sort");
        for (var field : fields) {
            node.addObject()
                .put("field", field.field())
                .put("order", field.order());
        }
        return this;
    }
}
