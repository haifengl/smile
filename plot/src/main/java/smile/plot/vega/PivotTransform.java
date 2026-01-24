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

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The pivot transform maps unique values from a field to new aggregated
 * fields (columns) in the output stream. The transform requires both a
 * field to pivot on (providing new field names) and a field of values
 * to aggregate to populate the new cells. In addition, any number of
 * groupby fields can be provided to further subdivide the data into
 * output data objects (rows).
 * <p>
 * Pivot transforms are useful for creating matrix or cross-tabulation
 * data, acting as an inverse to the fold transform.
 *
 * @author Haifeng Li
 */
public class PivotTransform {
    /** VegaLite's Pivot definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    PivotTransform(ObjectNode spec) {
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
     * Sets the maximum number of pivoted fields to generate. The default (0)
     * applies no limit. The pivoted pivot names are sorted in ascending
     * order prior to enforcing the limit.
     *
     * @param limit the maximum number of pivoted fields to generate.
     *              0 applies no limit.
     * @return this object.
     */
    public PivotTransform limit(double limit) {
        spec.put("limit", limit);
        return this;
    }

    /**
     * Sets the data fields to group by. If not specified, a single group
     * containing all data objects will be used.
     *
     * @param fields The data fields to group by. If not specified,
     *              a single group containing all data objects will be used.
     * @return this object.
     */
    public PivotTransform groupby(String... fields) {
        ArrayNode node = spec.putArray("groupby");
        for (var field : fields) {
            node.add(field);
        }
        return this;
    }

    /**
     * Sets the aggregation operation to apply to grouped value field values.
     * Default value: sum
     *
     * @param op The aggregation operation to apply to grouped value field values.
     * @return this object.
     */
    public PivotTransform op(String op) {
        spec.put("op", op);
        return this;
    }
}
