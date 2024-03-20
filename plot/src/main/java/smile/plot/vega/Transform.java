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

import com.fasterxml.jackson.databind.ObjectMapper;
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
    /** JSON object mapping. */
    final ObjectMapper mapper;
    /** VegaLite's Transform definition object. */
    final ArrayNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Transform(ObjectMapper mapper, ArrayNode spec) {
        this.mapper = mapper;
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
     * Aggregate summarizes a table as one record for each group.
     * To preserve the original table structure and instead add
     * a new column with the aggregate values, use the join aggregate
     * transform.
     *
     * @param op The aggregation operation to apply to the fields
     *          (e.g., "sum", "average", or "count").
     * @param field The data field for which to compute aggregate function.
     *             This is required for all aggregation operations except "count".
     * @param as The output field names to use for each aggregated field.
     * @param groupby The data fields to group by. If not specified, a single
     *               group containing all data objects will be used.
     * @return this object.
     */
    public Transform aggregate(String op, String field, String as, String... groupby) {
        ObjectNode node = spec.addObject();
        ArrayNode a = node.putArray("aggregate");
        a.addObject()
                .put("op", op)
                .put("field", field)
                .put("as", as);
        ArrayNode g = node.putArray("groupby");
        for (String f : groupby) {
            g.add(f);
        }
        return this;
    }

    /**
     * The join-aggregate transform extends the input data objects with
     * aggregate values in a new field. Aggregation is performed and the
     * results are then joined with the input data. This transform can be
     * helpful for creating derived values that combine both raw data and
     * aggregate calculations, such as percentages of group totals. This
     * transform is a special case of the window transform where the frame
     * is always [null, null]. Compared with the regular aggregate transform,
     * join-aggregate preserves the original table structure and augments
     * records with aggregate values rather than summarizing the data in
     * one record for each group.
     *
     * @param op The aggregation operation to apply to the fields
     *          (e.g., "sum", "average", or "count").
     * @param field The data field for which to compute aggregate function.
     *             This is required for all aggregation operations except "count".
     * @param as The output field names to use for each aggregated field.
     * @param groupby The data fields to group by. If not specified, a single
     *               group containing all data objects will be used.
     * @return this object.
     */
    public Transform joinAggregate(String op, String field, String as, String... groupby) {
        ObjectNode node = spec.addObject();
        ArrayNode a = node.putArray("joinaggregate");
        a.addObject()
                .put("op", op)
                .put("field", field)
                .put("as", as);
        ArrayNode g = node.putArray("groupby");
        for (String f : groupby) {
            g.add(f);
        }
        return this;
    }

    /**
     * Adds a bin transformation.
     *
     * @param field The data field to bin.
     * @param as The output fields at which to write the start and end bin values.
     * @return this object.
     */
    public Transform bin(String field, String as) {
        ObjectNode node = spec.addObject();
        node.put("bin", true)
                .put("field", field)
                .put("as", as);
        return this;
    }

    /**
     * Adds a formula transform extends data objects with new fields
     * (columns) according to an expression.
     * @param expr an expression string. Use the variable datum to refer
     *            to the current data object.
     * @param field the field for storing the computed formula value.
     * @return this object.
     */
    public Transform calculate(String expr, String field) {
        ObjectNode node = spec.addObject();
        node.put("calculate", expr);
        node.put("as", field);
        return this;
    }

    /**
     * Adds a density transformation.
     *
     * @param field The data field for which to perform density estimation.
     * @param groupby The data fields to group by. If not specified, a single
     *               group containing all data objects will be used.
     * @return this object.
     */
    public Density density(String field, String... groupby) {
        ObjectNode node = spec.addObject().put("density", field);
        if (groupby.length > 0) {
            ArrayNode array = node.putArray("groupby");
            for (String f : groupby) {
                array.add(f);
            }
        }
        return new Density(node);
    }

    /**
     * Adds a filter transform.
     * @param predicate an expression string, where datum can be used to refer
     *                 to the current data object. For example, "datum.b2 > 60"
     *                  would make the output data includes only items that have
     *                  values in the field b2 over 60.
     * @return this object.
     */
    public Transform filter(String predicate) {
        ObjectNode node = spec.addObject();
        node.put("filter", predicate);
        return this;
    }

    /**
     * Adds a lookup transformation.
     * @param key the key in primary data source.
     * @param from the data source or selection for secondary data reference.
     * @return this object.
     */
    public Transform lookup(String key, ObjectNode from) {
        ObjectNode node = spec.addObject();
        node.put("lookup", key)
                .set("from", from);
        return this;
    }

    /**
     * Adds a lookup transformation.
     * @param key the key in primary data source.
     * @param from the data source or selection for secondary data reference.
     * @param as the output fields on which to store the looked up data values.
     * @return this object.
     */
    public Transform lookup(String key, ObjectNode from, String as) {
        ObjectNode node = spec.addObject();
        node.put("lookup", key)
                .put("as", as)
                .set("from", from);
        return this;
    }

    /**
     * Creates a data specification object.
     * @return a data specification object.
     */
    public Data data() {
        return new Data(mapper);
    }

    /**
     * Returns a Lookup Data object.
     *
     * @param key Key in data to lookup.
     * @param data Secondary data source to lookup in.
     * @param fields Fields in foreign data or selection to lookup.
     *              If not specified, the entire object is queried.
     * @return a Lookup Data object.
     */
    public ObjectNode lookupData(String key, Data data, String... fields) {
        ObjectNode node = mapper.createObjectNode()
                .put("key", key)
                .set("data", data.spec);

        if (fields.length > 0) {
            ArrayNode array = node.putArray("fields");
            for (String field : fields) {
                array.add(field);
            }
        }

        return node;
    }

    /**
     * Returns a Lookup Selection object.
     * @param param Selection parameter name to look up.
     * @return a Lookup Selection object.
     */
    public ObjectNode lookupSelection(String param) {
        return mapper.createObjectNode().put("param", param);
    }
}
