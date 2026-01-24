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

import tools.jackson.databind.node.ObjectNode;

/**
 * Facet field definition object.
 *
 * @author Haifeng Li
 */
public class FacetField {
    /** VegaLite's facet field definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    FacetField(ObjectNode spec) {
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
    public FacetField type(String type) {
        spec.put("type", type);
        return this;
    }

    /**
     * Turns on/off binning a quantitative field.
     * @param flag If true, default binning parameters will be applied.
     * @return this object.
     */
    public FacetField bin(boolean flag) {
        spec.put("bin", flag);
        return this;
    }

    /**
     * Indicates that the data for x or y channel are binned
     * before they are imported into Vega-Lite.
     * @param bin it should be set to "binned".
     * @return this object.
     */
    public FacetField bin(String bin) {
        assert "binned".equals(bin)  : "Invalid bin: " + bin;
        spec.put("bin", bin);
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
    public FacetField timeUnit(String timeUnit) {
        spec.put("timeUnit", timeUnit);
        return this;
    }

    /**
     * Sets the header of facet.
     * @param title the header text.
     * @return this object.
     */
    public FacetField header(String title) {
        ObjectNode node = spec.putObject("header");
        node.put("title", title);
        return this;
    }

    /**
     * Sets the alignment to apply to row/column facet's subplot.
     * For "none", a flow layout will be used, in which adjacent subviews
     * are simply placed one after the other.
     * <p>
     * For "each", subviews will be aligned into a clean grid structure,
     * but each row or column may be of variable size.
     * <p>
     * For "all", subviews will be aligned and each row or column will be
     * sized identically based on the maximum observed size. String values
     * for this property will be applied to both grid rows and columns.
     *
     * @param alignment "all", "each", or "none".
     * @return this object.
     */
    public FacetField align(String alignment) {
        spec.put("align", alignment);
        return this;
    }

    /**
     * Sets if facet's subviews should be centered relative to their respective
     * rows or columns.
     * @param flag A flag indicating if facet's subviews should be centered
     *            relative to their respective rows or columns.
     * @return this object.
     */
    public FacetField center(boolean flag) {
        spec.put("center", flag);
        return this;
    }

    /**
     * Sets the spacing in pixels between facet's sub-views.
     * @param spacing the spacing in pixels between facet's sub-views.
     * @return this object.
     */
    public FacetField spacing(double spacing) {
        spec.put("spacing", spacing);
        return this;
    }

    /**
     * For the facet channel, sets the number of columns to include in the view composition layout.
     * @param columns the number of columns to include in the view composition layout.
     * @return this object.
     */
    public FacetField spacing(int columns) {
        spec.put("columns", columns);
        return this;
    }
}
