/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.vega;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The impute transform groups data and determines missing values of the key
 * field within each group. For each missing value in each group, the impute
 * transform will produce a new tuple with the imputed field generated based
 * on a specified imputation method (by using a constant value or by
 * calculating statistics such as mean within each group).
 *
 * @author Haifeng Li
 */
public class ImputeTransform {
    /** VegaLite's Impute definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    ImputeTransform(ObjectNode spec) {
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
     * Sets the key values that should be considered for imputation.
     *
     * @param values the key values that should be considered
     *              for imputation.
     * @return this object.
     */
    public ImputeTransform keyvals(double[] values) {
        ArrayNode node = spec.putArray("keyvals");
        for (var value : values) {
            node.add(value);
        }
        return this;
    }

    /**
     * Sets the sequence of key values that should be considered for imputation.
     *
     * @param start The starting value of the sequence.
     * @param stop The ending value(exclusive) of the sequence.
     * @param step The step value between sequence entries.
     * @return this object.
     */
    public ImputeTransform keyvals(double start, double stop, double step) {
        ObjectNode node = spec.putObject("keyvals");
        node.put("start", start)
            .put("stop", stop)
            .put("step", step);
        return this;
    }

    /**
     * Sets the data fields by which to group the values.
     * Imputation will then be performed on a per-group basis.
     *
     * @param fields the data fields by which to group the values. Imputation
     *              will then be performed on a per-group basis.
     * @return this object.
     */
    public ImputeTransform groupby(String... fields) {
        ArrayNode node = spec.putArray("groupby");
        for (var field : fields) {
            node.add(field);
        }
        return this;
    }

    /**
     * Sets the frame to control the window over which the specified method
     * is applied. The array entries should either be a number indicating
     * the offset from the current data object, or null to indicate unbounded
     * rows preceding or following the current data object. For example,
     * the value [-5, 5] indicates that the window should include five objects
     * preceding and five objects following the current object.
     * <p>
     * Default value [null, null] indicating that the window includes all objects.
     *
     * @param first the offset of the first object in the sliding window.
     * @param last the offset of the first object in the sliding window.
     * @return this object.
     */
    public ImputeTransform frame(Integer first, Integer last) {
        spec.putArray("frame").add(first).add(last);
        return this;
    }

    /**
     * Sets the imputation method to use for the field value of imputed data objects.
     * @param method "value", "mean", "median", "max" or "min".
     * @return this object.
     */
    public ImputeTransform method(String method) {
        spec.put("method", method);
        return this;
    }

    /**
     * Sets the field value to use when the imputation method is "value".
     * @param value the field value to use when the imputation method is "value".
     * @return this object.
     */
    public ImputeTransform value(JsonNode value) {
        spec.set("value", value);
        return this;
    }
}
