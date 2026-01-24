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
 * The view background of a single-view or layer specification.
 *
 * @author Haifeng Li
 */
public class Background {
    /** VegaLite's ViewBackground object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Background(ObjectNode spec) {
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
     * Sets the custom styles.
     * @param style A string or array of strings indicating the name of custom
     *             styles to apply to the view background. A style is a named
     *             collection of mark property defaults defined within the style
     *             configuration. If style is an array, later styles will
     *             override earlier styles.
     * @return this object.
     */
    public Background style(String... style) {
        if (style.length == 1) {
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
     * Sets the radius of corners.
     * @param cornerRadius The radius in pixels of rounded rectangles or arcs' corners.
     * @return this object.
     */
    public Background cornerRadius(int cornerRadius) {
        spec.put("cornerRadius", cornerRadius);
        return this;
    }

    /**
     * Sets the mouse cursor used over the view.
     * @param cursor Any valid CSS cursor type can be used.
     * @return this object.
     */
    public Background cursor(String cursor) {
        spec.put("cursor", cursor);
        return this;
    }

    /**
     * Sets the fill color.
     * @param color the fill color.
     * @return this object.
     */
    public Background fill(String color) {
        spec.put("fill", color);
        return this;
    }

    /**
     * Sets the fill opacity.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Background fillOpacity(double opacity) {
        spec.put("fillOpacity", opacity);
        return this;
    }

    /**
     * Sets the overall opacity.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Background opacity(double opacity) {
        spec.put("opacity", opacity);
        return this;
    }

    /**
     * Sets the stroke color.
     * @param color the stroke color.
     * @return this object.
     */
    public Background stroke(String color) {
        spec.put("stroke", color);
        return this;
    }

    /**
     * Sets the stroke cap for line ending style.
     * @param cap "butt", "round", or "square".
     * @return this object.
     */
    public Background strokeCap(String cap) {
        spec.put("strokeCap", cap);
        return this;
    }

    /**
     * Sets the alternating [stroke, space] lengths for stroke dash.
     * @param stroke the stroke length.
     * @param space the space length.
     * @return this object.
     */
    public Background strokeDash(double stroke, double space) {
        spec.putArray("strokeDash").add(stroke).add(space);
        return this;
    }

    /**
     * Sets the offset (in pixels) into which to begin drawing with the stroke dash array.
     * @param offset the stroke offset.
     * @return this object.
     */
    public Background strokeDashOffset(int offset) {
        spec.put("strokeDashOffset", offset);
        return this;
    }

    /**
     * Sets the stroke line join method.
     * @param join "miter", "round" or "bevel".
     * @return this object.
     */
    public Background strokeJoin(String join) {
        spec.put("strokeJoin", join);
        return this;
    }

    /**
     * Sets the miter limit at which to bevel a line join.
     * @param limit the miter limit.
     * @return this object.
     */
    public Background strokeMiterLimit(int limit) {
        spec.put("strokeMiterLimit", limit);
        return this;
    }

    /**
     * Sets the stroke opacity
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Background strokeOpacity(double opacity) {
        spec.put("strokeOpacity", opacity);
        return this;
    }

    /**
     * Sets the stroke width.
     * @param width the width in pixels.
     * @return this object.
     */
    public Background strokeWidth(int width) {
        spec.put("strokeWidth", width);
        return this;
    }
}
