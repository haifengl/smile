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

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The style of a single view visualization.
 *
 * @author Haifeng Li
 */
public class ViewConfig {
    /** VegaLite's Config's View object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     * @param spec the specification object.
     */
    ViewConfig(ObjectNode spec) {
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
     * Sets the default width when the plot has a continuous field
     * for x or longitude, or has arc marks.
     * @param width the width.
     * @return this object.
     */
    public ViewConfig continuousWidth(int width) {
        spec.put("continuousWidth", width);
        return this;
    }

    /**
     * Sets the default height when the plot has a continuous field
     * for y or latitude, or has arc marks.
     * @param height the width.
     * @return this object.
     */
    public ViewConfig continuousHeight(int height) {
        spec.put("continuousHeight", height);
        return this;
    }

    /**
     * Sets the default width when the plot has non-arc marks
     * and either a discrete x-field or no x-field.
     * @param width the width.
     * @return this object.
     */
    public ViewConfig discreteWidth(int width) {
        spec.put("discreteWidth", width);
        return this;
    }

    /**
     * Sets the default height when the plot has non arc marks
     * and either a discrete y-field or no y-field.
     * @param height the width.
     * @return this object.
     */
    public ViewConfig discreteHeight(int height) {
        spec.put("discreteHeight", height);
        return this;
    }

    /**
     * Sets the default step size for x-/y- discrete fields.
     * @param step the step size.
     * @return this object.
     */
    public ViewConfig step(int step) {
        spec.put("step", step);
        return this;
    }

    /**
     * Sets the radius of corners.
     * @param cornerRadius The radius in pixels of rounded rectangles or arcs' corners.
     * @return this object.
     */
    public ViewConfig cornerRadius(int cornerRadius) {
        spec.put("cornerRadius", cornerRadius);
        return this;
    }

    /**
     * Sets the mouse cursor used over the view.
     * @param cursor Any valid CSS cursor type can be used.
     * @return this object.
     */
    public ViewConfig cursor(String cursor) {
        spec.put("cursor", cursor);
        return this;
    }

    /**
     * Sets the fill color.
     * @param color the fill color.
     * @return this object.
     */
    public ViewConfig fill(String color) {
        spec.put("fill", color);
        return this;
    }

    /**
     * Sets the fill opacity.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public ViewConfig fillOpacity(double opacity) {
        spec.put("fillOpacity", opacity);
        return this;
    }

    /**
     * Sets the overall opacity.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public ViewConfig opacity(double opacity) {
        spec.put("opacity", opacity);
        return this;
    }

    /**
     * Sets the stroke color.
     * @param color the stroke color.
     * @return this object.
     */
    public ViewConfig stroke(String color) {
        spec.put("stroke", color);
        return this;
    }

    /**
     * Sets the stroke cap for line ending style. One of "butt", "round", or "square".
     * @param cap "butt", "round", or "square".
     * @return this object.
     */
    public ViewConfig strokeCap(String cap) {
        spec.put("strokeCap", cap);
        return this;
    }

    /**
     * Sets the alternating [stroke, space] lengths for stroke dash.
     * @param stroke the stroke length.
     * @param space the space length.
     * @return this object.
     */
    public ViewConfig strokeDash(double stroke, double space) {
        spec.putArray("strokeDash").add(stroke).add(space);
        return this;
    }

    /**
     * Sets the offset (in pixels) into which to begin drawing with the stroke dash array.
     * @param offset the stroke offset.
     * @return this object.
     */
    public ViewConfig strokeDashOffset(int offset) {
        spec.put("strokeDashOffset", offset);
        return this;
    }

    /**
     * Sets the stroke line join method.
     * @param join "miter", "round" or "bevel".
     * @return this object.
     */
    public ViewConfig strokeJoin(String join) {
        spec.put("strokeJoin", join);
        return this;
    }

    /**
     * Sets the miter limit at which to bevel a line join.
     * @param limit the miter limit.
     * @return this object.
     */
    public ViewConfig strokeMiterLimit(int limit) {
        spec.put("strokeMiterLimit", limit);
        return this;
    }

    /**
     * Sets the stroke opacity
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public ViewConfig strokeOpacity(double opacity) {
        spec.put("strokeOpacity", opacity);
        return this;
    }

    /**
     * Sets the stroke width.
     * @param width the width in pixels.
     * @return this object.
     */
    public ViewConfig strokeWidth(int width) {
        spec.put("strokeWidth", width);
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
}
