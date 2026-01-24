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
        for (var f : groupby) {
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
        for (var f : groupby) {
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
    public DensityTransform density(String field, String... groupby) {
        ObjectNode node = spec.addObject().put("density", field);
        if (groupby.length > 0) {
            ArrayNode array = node.putArray("groupby");
            for (var f : groupby) {
                array.add(f);
            }
        }
        return new DensityTransform(node);
    }

    /**
     * Adds an extent transform. The extent transform finds the extent
     * of a field and stores the result in a parameter.
     *
     * @param field The field of which to get the extent.
     * @param param The output parameter produced by the extent transform.
     * @return this object.
     */
    public Transform extent(String field, String param) {
        ObjectNode node = spec.addObject();
        node.put("extent", field).put("param", param);
        return this;
    }

    /**
     * Adds a flatten transform. The flatten transform maps array-valued fields
     * to a set of individual data objects, one per array entry. This transform
     * generates a new data stream in which each data object consists of an
     * extracted array value as well as all the original fields of the
     * corresponding input data object.
     *
     * @param fields An array of one or more data fields containing arrays to
     *              flatten. If multiple fields are specified, their array
     *              values should have a parallel structure, ideally with the
     *              same length. If the lengths of parallel arrays do not
     *              match, the longest array will be used with null values
     *              added for missing entries.
     * @param output The output parameter produced by the extent transform.
     * @return this object.
     */
    public Transform flatten(String[] fields, String[] output) {
        ObjectNode node = spec.addObject();
        ArrayNode array = node.putArray("flatten");
        for (var field : fields) {
            array.add(field);
        }

        array = node.putArray("as");
        for (var field : output) {
            array.add(field);
        }
        return this;
    }

    /**
     * Adds a fold transform. The fold transform collapses (or "folds") one or
     * more data fields into two properties: a key property (containing the
     * original data field name) and a value property (containing the data value).
     * <p>
     * The fold transform is useful for mapping matrix or cross-tabulation data
     * into a standardized format.
     * <p>
     * This transform generates a new data stream in which each data object
     * consists of the key and value properties as well as all the original
     * fields of the corresponding input data object.
     * <p>
     * Note: The fold transform only applies to a list of known fields (set
     * using the fields parameter). If your data objects instead contain
     * array-typed fields, you may wish to use the flatten transform instead.
     *
     * @param fields An array of data fields indicating the properties to fold.
     * @param output The output field names for the key and value properties
     *              produced by the fold transform.
     * @return this object.
     */
    public Transform fold(String[] fields, String[] output) {
        ObjectNode node = spec.addObject();
        ArrayNode array = node.putArray("fold");
        for (var field : fields) {
            array.add(field);
        }

        array = node.putArray("as");
        for (var field : output) {
            array.add(field);
        }
        return this;
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
     * Adds a filter transform.
     * @param predicate a predicate object.
     * @return this object.
     */
    public Transform filter(Predicate predicate) {
        ObjectNode node = spec.addObject();
        node.set("filter", predicate.spec);
        return this;
    }

    /**
     * Adds an impute transform.
     * @param field The data field for which the missing values should be imputed.
     * @param key A key field that uniquely identifies data objects within a group.
     *           Missing key values (those occurring in the data but not in the
     *           current group) will be imputed.
     * @return an impute transform object.
     */
    public ImputeTransform impute(String field, String key) {
        ObjectNode node = spec.addObject().put("impute", field);
        node.put("key", key);
        return new ImputeTransform(node);
    }

    /**
     * Adds a loess transform.
     * @param field The data field of the dependent variable to smooth.
     * @param on The data field of the independent variable to use a predictor.
     * @return a loess transform object.
     */
    public LoessTransform loess(String field, String on) {
        ObjectNode node = spec.addObject().put("loess", field);
        node.put("on", on);
        return new LoessTransform(node);
    }

    /**
     * Adds a lookup transformation.
     * @param key the key in primary data source.
     * @param param Selection parameter name to look up.
     * @return this object.
     */
    public Transform lookup(String key, String param) {
        ObjectNode node = spec.addObject();
        node.put("lookup", key);
        node.putObject("from").put("param", param);
        return this;
    }

    /**
     * Adds a lookup transformation.
     * @param key the key in primary data source.
     * @param from the data source or selection for secondary data reference.
     * @return this object.
     */
    public Transform lookup(String key, LookupData from) {
        ObjectNode node = spec.addObject();
        node.put("lookup", key).set("from", from.spec);
        return this;
    }

    /**
     * Creates a lookup data.
     *
     * @param key the key in data to lookup.
     * @return a lookup data.
     */
    public LookupData lookupData(String key) {
        ObjectNode node = VegaLite.mapper.createObjectNode().put("key", key);
        Data data = new Data();
        node.set("data", data.spec);
        return new LookupData(node, data);
    }

    /**
     * Adds a pivot transform.
     * @param field The data field to pivot on. The unique values of this
     *             field become new field names in the output stream.
     * @param value The data field to populate pivoted fields. The aggregate
     *             values of this field become the values of the new pivoted
     *             fields.
     * @return a pivot transform object.
     */
    public PivotTransform pivot(String field, String value) {
        ObjectNode node = spec.addObject().put("pivot", field);
        node.put("value", value);
        return new PivotTransform(node);
    }
    /**
     * Adds a quantile transform.
     * @param field The data field for which to perform quantile estimation.
     * @return a quantile transform object.
     */
    public QuantileTransform quantile(String field) {
        ObjectNode node = spec.addObject().put("quantile", field);
        return new QuantileTransform(node);
    }

    /**
     * Adds a regression transform.
     * @param field The data field of the dependent variable to predict.
     * @param on The data field of the independent variable to use a predictor.
     * @return a regression transform object.
     */
    public RegressionTransform regression(String field, String on) {
        ObjectNode node = spec.addObject().put("regression", field);
        node.put("on", on);
        return new RegressionTransform(node);
    }

    /**
     * Adds a sample transform. The sample transform filters random rows from
     * the data source to reduce its size. As input data objects are added and
     * removed, the sampled values may change in first-in, first-out manner.
     * This transform uses reservoir sampling to maintain a representative
     * sample of the stream.
     *
     * @param size The maximum number of data objects to include in the sample.
     * @return this object.
     */
    public Transform sample(int size) {
        spec.addObject().put("sample", size);
        return this;
    }

    /**
     * Adds a stack transform.
     *
     * @param field The field which is stacked.
     * @param groupby The data fields to group by.
     * @param as the output start field name. The end field will be "$as_end".
     * @return a stack transform object.
     */
    public StackTransform stack(String field, String as, String... groupby) {
        ObjectNode node = spec.addObject().put("stack", field);
        ArrayNode g = node.putArray("groupby");
        for (var f : groupby) {
            g.add(f);
        }
        node.put("as", as);
        return new StackTransform(node);
    }

    /**
     * Adds a time unit transform.
     *
     * @param timeUnit The timeUnit.
     * @param field The data field to apply time unit.
     * @param as The output field to write the timeUnit value.
     * @return this object.
     */
    public Transform timeUnit(String timeUnit, String field, String as) {
        spec.addObject().put("timeUnit", timeUnit).put("field", field).put("as", as);
        return this;
    }

    /**
     * Creates a data specification object.
     * @param fields the transform fields.
     * @return a data specification object.
     */
    public WindowTransform window(WindowTransformField... fields) {
        ObjectNode node = spec.addObject();
        ArrayNode array = node.putArray("window");
        for (var field : fields) {
            array.addObject()
                 .put("op", field.op())
                 .put("field", field.field())
                 .put("param", field.param())
                 .put("as", field.as());
        }
        return new WindowTransform(node);
    }
}
