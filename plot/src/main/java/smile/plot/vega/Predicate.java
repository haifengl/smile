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
 * To test a data point in a filter transform or a test property in conditional
 * encoding, a predicate definition of the following forms must be specified:
 * <p>
 * - a Vega expression string, where datum can be used to refer to the current
 *   data object. For example, datum.b2 > 60 would test if the value in the
 *   field b2 for each data point is over 60.
 * <p>
 * - one of the field predicates: equal, lt, lte, gt, gte, range, oneOf, or valid.
 * <p>
 * - a parameter predicate, which defines the names of a selection that the data
 *   point should belong to (or a logical composition of selections).
 * <p>
 * - a logical composition of (1), (2), or (3).
 *
 * @author Haifeng Li
 */
public class Predicate {
    /** The predicate specification object. */
    final ObjectNode spec;
    /** The Vega expression string. */
    final String expr;

    /**
     * Constructor.
     * @param expr a Vega expression string.
     */
    public Predicate(String expr) {
        this.spec = null;
        this.expr = expr;
    }

    /**
     * Constructor of parameter predicate.
     * For example, with {"param": "brush"}, only data values that fall within
     * the selection named brush will remain in the dataset. Notice, by default,
     * empty selections are considered to contain all data values. We can toggle
     * this behavior by setting the optional empty property true on the predicate.
     *
     * @param param Filter using a parameter name.
     * @param empty For selection parameters, the predicate of empty selections
     *             returns true by default. Override this behavior, by setting
     *             this property false.
     */
    public Predicate(String param, boolean empty) {
        this.expr = null;
        this.spec = VegaLite.mapper.createObjectNode();
        spec.put("param", param).put("empty", empty);
    }

    /**
     * Constructor.
     * @param spec the predicate specification object.
     */
    Predicate(ObjectNode spec) {
        this.spec = spec;
        this.expr = null;
    }

    /**
     * Sets the time unit for a temporal field. Vega-Lite supports the following time units:
     * <p>
     * "year" - Gregorian calendar years.
     * "quarter" - Three-month intervals, starting in one of January, April, July, and October.
     * "month" - Calendar months (January, February, etc.).
     * "date" - Calendar day of the month (January 1, January 2, etc.).
     * "week" - Sunday-based weeks. Days before the first Sunday of the year are considered to be
     *          in week 0, the first Sunday of the year is the start of week 1, the second Sunday
     *          week 2, etc.
     * "day" - Day of the week (Sunday, Monday, etc.).
     * "dayofyear" - Day of the year (1, 2, …, 365, etc.).
     * "hours" - Hours of the day (12:00am, 1:00am, etc.).
     * "minutes" - Minutes in an hour (12:00, 12:01, etc.).
     * "seconds" - Seconds in a minute (12:00:00, 12:00:01, etc.).
     * "milliseconds" - Milliseconds in a second.
     *
     * @param timeUnit  Time unit.
     * @return this object.
     */
    public Predicate timeUnit(String timeUnit) {
        assert spec != null && spec.has("field") : "Not a Field Predicate";
        spec.put("timeUnit", timeUnit);
        return this;
    }

    /**
     * Test if a field in the data point satisfies certain conditions.
     *
     * @param field the field to be tested.
     * @param op equal, lt (less than), lte (less than or equal),
     *          gt (greater than), or gte(greater than or equal).
     * @param value the value to compare.
     * @return a field predicate.
     */
    public static Predicate of(String field, String op, boolean value) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        node.put("field", field).put(op, value);
        return new Predicate(node);
    }

    /**
     * Test if a field in the data point satisfies certain conditions.
     *
     * @param field the field to be tested.
     * @param op equal, lt (less than), lte (less than or equal),
     *          gt (greater than), or gte(greater than or equal).
     * @param value the value to compare.
     * @return a field predicate.
     */
    public static Predicate of(String field, String op, double value) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        node.put("field", field).put(op, value);
        return new Predicate(node);
    }

    /**
     * Test if a field in the data point satisfies certain conditions.
     *
     * @param field the field to be tested.
     * @param op equal, lt (less than), lte (less than or equal),
     *          gt (greater than), or gte(greater than or equal).
     * @param value the value to compare.
     * @return a field predicate.
     */
    public static Predicate of(String field, String op, String value) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        node.put("field", field).put(op, value);
        return new Predicate(node);
    }

    /**
     * Test if a field in the data point satisfies certain conditions.
     *
     * @param field the field to be tested.
     * @param min inclusive minimum values for a field value of a data
     *           item to be included in the filtered data.
     * @param max inclusive maximum values for a field value of a data
     *           item to be included in the filtered data.
     * @return a field predicate.
     */
    public static Predicate range(String field, double min, double max) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        node.put("field", field);
        node.putArray("range").add(min).add(max);
        return new Predicate(node);
    }

    /**
     * Test if a field in the data point satisfies certain conditions.
     *
     * @param field the field to be tested.
     * @param values a set of values that the field's value should be a member
     *              of, for a data item included in the filtered data.
     * @return a field predicate.
     */
    public static Predicate oneOf(String field, double... values) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        node.put("field", field);
        ArrayNode array = node.putArray("oneOf");
        for (var value : values) {
            array.add(value);
        }
        return new Predicate(node);
    }

    /**
     * Test if a field in the data point satisfies certain conditions.
     *
     * @param field the field to be tested.
     * @param values a set of values that the field's value should be a member
     *              of, for a data item included in the filtered data.
     * @return a field predicate.
     */
    public static Predicate oneOf(String field, String... values) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        node.put("field", field);
        ArrayNode array = node.putArray("oneOf");
        for (var value : values) {
            array.add(value);
        }
        return new Predicate(node);
    }

    /**
     * Test if a field is valid, meaning it is neither null nor NaN.
     *
     * @param field the field to be tested.
     * @return a field predicate.
     */
    public static Predicate valid(String field) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        node.put("field", field).put("valid", true);
        return new Predicate(node);
    }

    /**
     * Logical AND composition to combine predicates.
     * @param predicates the predicates.
     * @return AND predicate.
     */
    public static Predicate and(Predicate... predicates) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        ArrayNode array = node.putArray("and");
        for (var predicate : predicates) {
            if (predicate.expr != null) {
                array.add(predicate.expr);
            } else {
                array.add(predicate.spec);
            }
        }
        return new Predicate(node);
    }

    /**
     * Logical OR composition to combine predicates.
     * @param predicates the predicates.
     * @return OR predicate.
     */
    public static Predicate or(Predicate... predicates) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        ArrayNode array = node.putArray("or");
        for (var predicate : predicates) {
            if (predicate.expr != null) {
                array.add(predicate.expr);
            } else {
                array.add(predicate.spec);
            }
        }
        return new Predicate(node);
    }

    /**
     * Logical NOT operation.
     * @param predicate the predicate.
     * @return NOT predicate.
     */
    public static Predicate not(Predicate predicate) {
        ObjectNode node = VegaLite.mapper.createObjectNode();
        if (predicate.expr != null) {
            node.put("not", predicate.expr);
        } else {
            node.set("not", predicate.spec);
        }
        return new Predicate(node);
    }
}
