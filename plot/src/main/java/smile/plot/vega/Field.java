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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Encoding field definition object. An integral part of the data visualization
 * process is encoding data with visual properties of graphical marks.
 * The encoding property of a single view specification represents the
 * mapping between encoding channels (such as x, y, or color) and data
 * fields, constant visual values, or constant data values (datum).
 *
 * @author Haifeng Li
 */
public class Field {
    /** VegaLite's channel field definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Field(ObjectNode spec) {
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
     * Sets the field's type of measurement.
     * @param type The encoded field's type of measurement ("quantitative",
     *             "temporal", "ordinal", or "nominal"). It can also be a
     *             "geojson" type for encoding ‘geoshape'.
     * <p>
     *             Data type describes the semantics of the data rather than
     *             the primitive data types (number, string, etc.). The same
     *             primitive data type can have different types of
     *             measurement. For example, numeric data can represent
     *             quantitative, ordinal, or nominal data.
     * <p>
     *             Data values for a temporal field can be either a
     *             date-time string (e.g., "2015-03-07 12:32:17",
     *             "17:01", "2015-03-16", "2015") or a timestamp
     *             number (e.g., 1552199579097).
     * @return this object.
     */
    public Field type(String type) {
        spec.put("type", type);
        return this;
    }

    /**
     * Turns on/off binning a quantitative field.
     * @param flag If true, default binning parameters will be applied.
     * @return this object.
     */
    public Field bin(boolean flag) {
        spec.put("bin", flag);
        return this;
    }

    /**
     * Indicates that the data for x or y channel are binned
     * before they are imported into Vega-Lite.
     * @param bin it should be set to "binned".
     * @return this object.
     */
    public Field bin(String bin) {
        assert "binned".equals(bin) : "Invalid bin: " + bin;
        spec.put("bin", bin);
        return this;
    }

    /**
     * Sets custom binning parameters.
     * @param params the binning parameters.
     * @return this object.
     */
    public Field bin(BinParams params) {
        spec.set("bin", params.spec);
        return this;
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
    public Field timeUnit(String timeUnit) {
        spec.put("timeUnit", timeUnit);
        return this;
    }

    /**
     * Sets the aggregation function for the field
     * (e.g., "mean", "sum", "median", "min", "max", "count").
     * @param aggregate Aggregation function for the field.
     * @return this object.
     */
    public Field aggregate(String aggregate) {
        spec.put("aggregate", aggregate);
        return this;
    }

    /**
     * Sets the title for the field. If null, the title will be removed.
     * @param title the title text.
     * @return this object.
     */
    public Field title(String title) {
        spec.put("title", title);
        return this;
    }

    /**
     * Sets the function that transforms values in the data domain (numbers,
     * dates, strings, etc.) to visual values (pixels, colors, sizes) for
     * position and mark property channels.
     * <p>
     * Vega-Lite supports the following categories of scale types:
     * <p>
     * 1) Continuous Scales - mapping continuous domains to continuous output
     * ranges ("linear", "pow", "sqrt", "symlog", "log", "time", "utc".
     * <p>
     * 2) Discrete Scales - mapping discrete domains to discrete ("ordinal")
     * or continuous ("band" and "point") output ranges.
     * <p>
     * 3) Discretizing Scales - mapping continuous domains to discrete output
     * ranges "bin-ordinal", "quantile", "quantize" and "threshold".
     *
     * @param type the function name. If null, the scale will be disabled
     *             and the data value will be directly encoded.
     * @return this object.
     */
    public Field scale(String type) {
        if (type == null) {
            spec.putNull("scale");
        } else {
            ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
            node.put("type", type);
        }
        return this;
    }

    /**
     * If true, ensures that a zero baseline value is included in the scale domain.
     * @param flag If true, ensures that a zero baseline value is included in the scale domain.
     * @return this object.
     */
    public Field zero(boolean flag) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("zero", flag);
        return this;
    }

    /**
     * Sets the customize domain values.
     * @param values the domain values.
     * @return this object.
     */
    public Field domain(double... values) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        ArrayNode domain = node.putArray("domain");
        for (double value : values) {
            domain.add(value);
        }
        return this;
    }

    /**
     * Sets the customize domain values.
     * @param values the domain values.
     * @return this object.
     */
    public Field domain(String... values) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        ArrayNode domain = node.putArray("domain");
        for (String value : values) {
            domain.add(value);
        }
        return this;
    }

    /**
     * Sets the minimum value in the scale domain, overriding the domain property
     * or the default domain. This property is only intended for use with scales
     * having continuous domains.
     * @param min the minimum value in the scale domain.
     * @return this object.
     */
    public Field domainMin(double min) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("domainMin", min);
        return this;
    }

    /**
     * Sets the minimum value in the scale domain, overriding the domain property
     * or the default domain. This property is only intended for use with scales
     * having continuous domains.
     * @param min the minimum value in the scale domain.
     * @return this object.
     */
    public Field domainMin(String min) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("domainMin", min);
        return this;
    }

    /**
     * Sets the maximum value in the scale domain, overriding the domain property
     * or the default domain. This property is only intended for use with scales
     * having continuous domains.
     * @param max the maximum value in the scale domain.
     * @return this object.
     */
    public Field domainMax(double max) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("domainMax", max);
        return this;
    }

    /**
     * Sets the maximum value in the scale domain, overriding the domain property
     * or the default domain. This property is only intended for use with scales
     * having continuous domains.
     * @param max the maximum value in the scale domain.
     * @return this object.
     */
    public Field domainMax(String max) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("domainMax", max);
        return this;
    }

    /**
     * Sets the customize range values.
     * @param values the range values.
     * @return this object.
     */
    public Field range(double... values) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        ArrayNode range = node.putArray("range");
        for (double value : values) {
            range.add(value);
        }
        return this;
    }

    /**
     * Sets the customize range values.
     * @param values the range values.
     * @return this object.
     */
    public Field range(String... values) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        ArrayNode range = node.putArray("range");
        for (String value : values) {
            range.add(value);
        }
        return this;
    }

    /**
     * Sets the minimum value in the scale range, overriding the range property
     * or the default range. This property is only intended for use with scales
     * having continuous ranges.
     * @param min the minimum value in the scale range.
     * @return this object.
     */
    public Field rangeMin(double min) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("rangeMin", min);
        return this;
    }

    /**
     * Sets the minimum value in the scale range, overriding the range property
     * or the default range. This property is only intended for use with scales
     * having continuous ranges.
     * @param min the minimum value in the scale range.
     * @return this object.
     */
    public Field rangeMin(String min) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("rangeMin", min);
        return this;
    }

    /**
     * Sets the maximum value in the scale range, overriding the range property
     * or the default range. This property is only intended for use with scales
     * having continuous ranges.
     * @param max the maximum value in the scale range.
     * @return this object.
     */
    public Field rangeMax(double max) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("rangeMax", max);
        return this;
    }

    /**
     * Sets the maximum value in the scale range, overriding the range property
     * or the default range. This property is only intended for use with scales
     * having continuous ranges.
     * @param max the maximum value in the scale range.
     * @return this object.
     */
    public Field rangeMax(String max) {
        ObjectNode node = spec.has("scale") ? (ObjectNode) spec.get("scale") : spec.putObject("scale");
        node.put("rangeMax", max);
        return this;
    }

    /**
     * Sets the sorting property.
     *
     * @param value "ascending", "descending", or an encoding channel name to
     *             sort by (e.g., "-x" to sort by x-field, descending).
     * @return this object.
     */
    public Field sort(String value) {
        spec.put("sort", value);
        return this;
    }

    /**
     * Sets the type of stacking offset if the field should be stacked.
     * stack is only applicable for x, y, theta, and radius channels with
     * continuous domains. For example, stack of y can be used to customize
     * stacking for a vertical bar chart.
     * <p>
     * Stack can be one of the following values:
     * <p>
     * "zero" - stacking with baseline offset at zero value of the scale
     * (for creating typical stacked bar and area chart).
     * <p>
     * "normalize" - stacking with normalized domain (for creating normalized
     * stacked bar and area charts and pie charts with percentage tooltip).
     * <p>
     * "center" - stacking with center baseline (for streamgraph).
     * <p>
     * null - No-stacking. This will produce layered bar and area chart.
     *
     * @param type "zero", "normalize", "center", or null.
     * @return this object.
     */
    public Field stack(String type) {
        spec.put("stack", type);
        return this;
    }

    /**
     * For facet, row and column channels, sets the spacing in pixels between
     * facet's sub-views.
     * @param spacing the spacing in pixels between facet's sub-views.
     * @return this object.
     */
    public Field spacing(double spacing) {
        spec.put("spacing", spacing);
        return this;
    }

    /**
     * Returns the axis definition object.
     * @return the axis definition object.
     */
    public Axis axis() {
        ObjectNode node = spec.has("axis") ? (ObjectNode) spec.get("axis") : spec.putObject("axis");
        return new Axis(node);
    }

    /**
     * Returns the legend definition object.
     * @return the legend definition object.
     */
    public Legend legend() {
        ObjectNode node = spec.has("legend") ? (ObjectNode) spec.get("legend") : spec.putObject("legend");
        return new Legend(node);
    }

    /**
     * Removes the legend for the encoding channel will be removed.
     * @return this object.
     */
    public Field removeLegend() {
        spec.putNull("legend");
        return this;
    }
}
