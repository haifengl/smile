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
 * Mark definition object. Marks are the basic visual building block of
 * a visualization. They provide basic shapes whose properties (such as
 * position, size, and color) can be used to visually encode data, either
 * from a data field, or a constant value.
 *
 * @author Haifeng Li
 */
public class Mark {
    /** VegaLite's Mark definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Mark(ObjectNode spec) {
        this.spec = spec;
    }

    /**
     * Sets the aria.
     * @param aria A boolean flag indicating if ARIA attributes should be
     *            included (SVG output only). If false, the "aria-hidden"
     *            attribute will be set on the output SVG element, removing
     *            the mark item from the ARIA accessibility tree.
     * @return this object.
     */
    public Mark aria(boolean aria) {
        spec.put("aria", aria);
        return this;
    }

    /**
     * Sets the description.
     * @param description A text description of the mark item for ARIA accessibility
     *             (SVG output only). If specified, this property determines
     *             the “aria-label” attribute.
     * @return this object.
     */
    public Mark description(String description) {
        spec.put("description", description);
        return this;
    }

    /**
     * Sets the style. Note: Any specified style will augment the default style.
     * @param style A string or array of strings indicating the name of custom
     *             styles to apply to the mark. A style is a named collection
     *             of mark property defaults defined within the style
     *             configuration. If style is an array, later styles will
     *             override earlier styles. Any mark properties explicitly
     *             defined within the encoding will override a style default.
     *             The default value is the mark's name. For example, a bar
     *             mark will have style "bar" by default.
     * @return this object.
     */
    public Mark style(String... style) {
        if (style.length == 0) {
            spec.put("style", style[0]);
        } else {
            ArrayNode node = spec.putArray("style");
            for (String s : style) {
                node.add(s);
            }
        }
        return this;
    }

    /**
     * Sets the tooltip text string to show upon mouse hover or which fields
     * should the tooltip be derived from.
     * @param tooltip "encoding", "data", or tooltip text. If "encoding",
     *               then all fields from encoding will be used. If "data",
     *               then all fields that appear in the highlighted data
     *               point will be used.
     * @return this object.
     */
    public Mark tooltip(String tooltip) {
        if (tooltip.equals("encoding") || tooltip.equals("data")) {
            ObjectNode node = spec.putObject("tooltip");
            node.put("content", tooltip);
        } else {
            spec.put("tooltip", tooltip);
        }
        return this;
    }

    /**
     * Turns on/off the tooltip.
     * @param flag If true, then all fields from encoding will be used.
     *             If false, then no tooltip will be used.
     * @return this object.
     */
    public Mark tooltip(boolean flag) {
        spec.put("tooltip", flag);
        return this;
    }

    /**
     * Sets whether a mark be clipped to the enclosing group's width and height.
     * @param flag whether a mark be clipped to the enclosing group's width and height.
     * @return this object.
     */
    public Mark clip(boolean flag) {
        spec.put("clip", flag);
        return this;
    }

    /**
     * Sets how Vega-Lite should handle marks for invalid values (null and NaN).
     * @param invalid If set to "filter", all data items with null values will
     *               be skipped (for line, trail, and area marks) or filtered
     *               (for other marks). If null, all data items are included.
     *               In this case, invalid values will be interpreted as zeroes.
     * @return this object.
     */
    public Mark invalid(String invalid) {
        spec.put("invalid", invalid);
        return this;
    }

    /**
     * For line and trail marks, sets this order property false to make the lines
     * use the original order in the data sources.
     * @param flag if false, use the original order in the data sources.
     * @return this object.
     */
    public Mark order(boolean flag) {
        spec.put("order", flag);
        return this;
    }
}
