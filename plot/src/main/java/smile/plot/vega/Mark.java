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
     * Returns the underlying Mark definition object.
     * @return the underlying Mark definition object.
     */
    public ObjectNode spec() {
        return spec;
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

    /**
     * Sets the X coordinates of the marks.
     * @param value the X coordinates.
     * @return this object.
     */
    public Mark x(double value) {
        spec.put("x", value);
        return this;
    }

    /**
     * Sets the width of horizontal "bar" and "area" without specified x2 or width.
     * @param width "width" for the width of the plot.
     * @return this object.
     */
    public Mark x(String width) {
        assert "width".equals(width)  : "Invalid width: " + width;
        spec.put("x", width);
        return this;
    }

    /**
     * Sets the X2 coordinates for ranged "area", "bar", "rect", and "rule".
     * @param value the X2 coordinates.
     * @return this object.
     */
    public Mark x2(double value) {
        spec.put("x2", value);
        return this;
    }

    /**
     * Sets the width.
     * @param width "width" for the width of the plot.
     * @return this object.
     */
    public Mark x2(String width) {
        assert "width".equals(width) : "Invalid width: " + width;
        spec.put("x2", width);
        return this;
    }

    /**
     * Sets the Y coordinates of the marks.
     * @param value the Y coordinates.
     * @return this object.
     */
    public Mark y(double value) {
        spec.put("y", value);
        return this;
    }

    /**
     * Sets the height of horizontal "bar" and "area" without specified x2 or width.
     * @param height "height" for the height of the plot.
     * @return this object.
     */
    public Mark y(String height) {
        assert "height".equals(height) : "Invalid height: " + height;
        spec.put("y", height);
        return this;
    }

    /**
     * Sets the Y2 coordinates for ranged "area", "bar", "rect", and "rule".
     * @param value the Y2 coordinates.
     * @return this object.
     */
    public Mark y2(double value) {
        spec.put("y2", value);
        return this;
    }

    /**
     * Sets the width.
     * @param height "height" for the height of the plot.
     * @return this object.
     */
    public Mark y2(String height) {
        assert "height".equals(height) : "Invalid height: " + height;
        spec.put("y2", height);
        return this;
    }

    /**
     * Sets the offset for x-position.
     * @param offset the offset for x-position.
     * @return this object.
     */
    public Mark xOffset(double offset) {
        spec.put("xOffset", offset);
        return this;
    }

    /**
     * Sets the offset for x2-position.
     * @param offset the offset for x2-position.
     * @return this object.
     */
    public Mark x2Offset(double offset) {
        spec.put("x2Offset", offset);
        return this;
    }

    /**
     * Sets the offset for y-position.
     * @param offset the offset for y-position.
     * @return this object.
     */
    public Mark yOffset(double offset) {
        spec.put("yOffset", offset);
        return this;
    }

    /**
     * Sets the offset for y2-position.
     * @param offset the offset for y2-position.
     * @return this object.
     */
    public Mark y2Offset(double offset) {
        spec.put("y2Offset", offset);
        return this;
    }

    /**
     * Sets the width of the marks.
     * @param width the width of the marks.
     * @return this object.
     */
    public Mark width(double width) {
        spec.put("width", width);
        return this;
    }

    /**
     * Sets the height of the marks.
     * @param height the height of the marks.
     * @return this object.
     */
    public Mark height(double height) {
        spec.put("height", height);
        return this;
    }

    /**
     * Sets whether the mark's color should be used as fill color instead of stroke color.
     * @param flag A flag indicating whether the mark's color should be used as fill color instead of stroke color.
     * @return this object.
     */
    public Mark filled(boolean flag) {
        spec.put("filled", flag);
        return this;
    }

    /**
     * Sets the default color. The fill and stroke properties have higher
     * precedence than color and will override color.
     * @param color the default color.
     * @return this object.
     */
    public Mark color(String color) {
        spec.put("color", color);
        return this;
    }

    /**
     * Sets the default fill color. This property has higher precedence than config.color.
     *
     * @param color the default fill color.
     * @return this object.
     */
    public Mark fill(String color) {
        spec.put("fill", color);
        return this;
    }

    /**
     * Sets the default stroke color. This property has higher precedence than config.color.
     *
     * @param color the default stroke color.
     * @return this object.
     */
    public Mark stroke(String color) {
        spec.put("stroke", color);
        return this;
    }

    /**
     * Sets the color blend mode for drawing an item on its current background.
     *
     * @param mode Any valid CSS mix-blend-mode value can be used.
     * @return this object.
     */
    public Mark blend(String mode) {
        spec.put("blend", mode);
        return this;
    }

    /**
     * Sets the overall opacity.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Mark opacity(double opacity) {
        spec.put("opacity", opacity);
        return this;
    }

    /**
     * Sets the fill opacity.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Mark fillOpacity(double opacity) {
        spec.put("fillOpacity", opacity);
        return this;
    }

    /**
     * Sets the stroke opacity.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Mark strokeOpacity(double opacity) {
        spec.put("strokeOpacity", opacity);
        return this;
    }

    /**
     * Sets the stroke cap for line ending style.
     * @param cap "butt", "round" or "square".
     * @return this object.
     */
    public Mark strokeCap(String cap) {
        spec.put("strokeCap", cap);
        return this;
    }

    /**
     * Sets the alternating [stroke, space] lengths for dashed lines.
     * @param stroke the stroke length.
     * @param space the space length.
     * @return this object.
     */
    public Mark strokeDash(double stroke, double space) {
        spec.putArray("strokeDash").add(stroke).add(space);
        return this;
    }

    /**
     * Sets the pixel offset at which to start drawing with the dash array.
     * @param offset the pixel offset at which to start drawing with the dash array.
     * @return this object.
     */
    public Mark strokeDashOffset(double offset) {
        spec.put("strokeDashOffset", offset);
        return this;
    }

    /**
     * Sets the stroke line join method.
     * @param join "miter", "round" or "bevel".
     * @return this object.
     */
    public Mark strokeJoin(String join) {
        spec.put("strokeJoin", join);
        return this;
    }

    /**
     * Sets the miter limit at which to bevel a line join.
     * @param limit the miter limit at which to bevel a line join.
     * @return this object.
     */
    public Mark strokeMiterLimit(double limit) {
        spec.put("strokeMiterLimit", limit);
        return this;
    }

    /**
     * Sets the stroke width of axis domain line.
     * @param width the stroke width of axis domain line.
     * @return this object.
     */
    public Mark strokeWidth(double width) {
        spec.put("strokeWidth", width);
        return this;
    }

    /**
     * Sets whether overlaying points on top of line or area marks.
     * @param flag A flag indicating whether overlaying points on top of line or area marks.
     * @return this object.
     */
    public Mark point(boolean flag) {
        spec.put("point", flag);
        return this;
    }

    /**
     * Sets the shape of the point marks.
     * @param shape "circle", "square", "cross", "diamond", "triangle-up",
     *             "triangle-down", "triangle-right", or "triangle-left".
     * @return this object.
     */
    public Mark shape(String shape) {
        spec.put("shape", shape);
        return this;
    }

    /**
     * Sets the size of the point marks.
     * @param size the pixel area of the marks.
     * @return this object.
     */
    public Mark size(int size) {
        spec.put("size", size);
        return this;
    }

    /**
     * Sets the extent of the band. Available options include:
     * <p>
     * "ci" - Extend the band to the confidence interval of the mean.
     * "stderr" - The size of band are set to the value of standard error, extending from the mean.
     * "stdev" - The size of band are set to the value of standard deviation, extending from the mean.
     * "iqr" - Extend the band to the q1 and q3.
     * @param extent "ci", "stderr", "stdev", or "iqr".
     * @return this object.
     */
    public Mark extent(String extent) {
        spec.put("extent", extent);
        return this;
    }

    /**
     * Sets whether the line mark is shown.
     * @param flag A flag indicating whether the line mark is shown.
     * @return this object.
     */
    public Mark line(boolean flag) {
        spec.put("line", flag);
        return this;
    }

    /**
     * Sets the orientation of a non-stacked bar, tick, area, and line charts.
     *
     * @param orient "horizontal" or "vertical".
     * @return this object.
     */
    public Mark orient(String orient) {
        spec.put("orient", orient);
        return this;
    }

    /**
     * Sets the line interpolation method to use for line and area marks.
     * Available options include:
     * <p>
     * "linear" - piecewise linear segments, as in a polyline.
     * "linear-closed" - close the linear segments to form a polygon.
     * "step" - alternate between horizontal and vertical segments, as in a step function.
     * "step-before" - alternate between vertical and horizontal segments, as in a step function.
     * "step-after" - alternate between horizontal and vertical segments, as in a step function.
     * "basis" - a B-spline, with control point duplication on the ends.
     * "basis-open" - an open B-spline; may not intersect the start or end.
     * "basis-closed" - a closed B-spline, as in a loop.
     * "cardinal" - a Cardinal spline, with control point duplication on the ends.
     * "cardinal-open" - an open Cardinal spline; may not intersect the start or end, but will intersect other control points.
     * "cardinal-closed" - a closed Cardinal spline, as in a loop.
     * "bundle" - equivalent to basis, except the tension parameter is used to straighten the spline.
     * "monotone" - cubic interpolation that preserves monotonicity in y.
     * @param method the line interpolation method.
     * @return this object.
     */
    public Mark interpolate(String method) {
        spec.put("interpolate", method);
        return this;
    }

    /**
     * Depending on the interpolation type, sets the tension parameter (for line and area marks).
     * @param value the tension value.
     * @return this object.
     */
    public Mark tension(double value) {
        spec.put("tension", value);
        return this;
    }

    /**
     * Sets the primary (outer) radius in pixels for arc mark,
     * or polar coordinate radial offset of the text from the
     * origin determined by the x and y properties for text marks.
     * @param radius the radius in pixels.
     * @return this object.
     */
    public Mark radius(double radius) {
        spec.put("radius", radius);
        return this;
    }

    /**
     * Sets the secondary (inner) radius in pixels for arc mark.
     * @param radius the radius in pixels.
     * @return this object.
     */
    public Mark radius2(double radius) {
        spec.put("radius2", radius);
        return this;
    }

    /**
     * Sets the primary (inner) radius in pixels for arc mark.
     * outerRadius is an alias for radius.
     * @param radius the radius in pixels.
     * @return this object.
     */
    public Mark outerRadius(double radius) {
        spec.put("outerRadius", radius);
        return this;
    }

    /**
     * Sets the secondary (inner) radius in pixels for arc mark.
     * innerRadius is an alias for radius2.
     * @param radius the radius in pixels.
     * @return this object.
     */
    public Mark innerRadius(double radius) {
        spec.put("innerRadius", radius);
        return this;
    }

    /**
     * Sets the offset for radius.
     * @param offset the offset for radius.
     * @return this object.
     */
    public Mark radiusOffset(double offset) {
        spec.put("radiusOffset", offset);
        return this;
    }

    /**
     * Sets the offset for radius2.
     * @param offset the offset for radius2.
     * @return this object.
     */
    public Mark radius2Offset(double offset) {
        spec.put("radius2Offset", offset);
        return this;
    }

    /**
     * For arc marks, sets the arc length in radians if theta2 is not specified,
     * otherwise the start arc angle. (A value of 0 indicates up or "north",
     * increasing values proceed clockwise.)
     * @param angle the arc length in radians.
     * @return this object.
     */
    public Mark theta(double angle) {
        spec.put("theta", angle);
        return this;
    }

    /**
     * Sets the end angle of arc marks in radians. A value of 0 indicates
     * up or "north", increasing values proceed clockwise.
     * @param angle the end angle in radians.
     * @return this object.
     */
    public Mark theta2(double angle) {
        spec.put("theta2", angle);
        return this;
    }

    /**
     * Sets the offset for theta.
     * @param offset the offset for theta.
     * @return this object.
     */
    public Mark thetaOffset(double offset) {
        spec.put("thetaOffset", offset);
        return this;
    }

    /**
     * Sets the offset for theta2.
     * @param offset the offset for theta2.
     * @return this object.
     */
    public Mark theta2Offset(double offset) {
        spec.put("theta2Offset", offset);
        return this;
    }

    /**
     * Setsthe angular padding applied to sides of the arc in radians.
     * @param angle the angular padding applied to sides of the arc in radians.
     * @return this object.
     */
    public Mark padAngle(double angle) {
        spec.put("padAngle", angle);
        return this;
    }

    /**
     * Sets the radius in pixels of rounded rectangles or arcs' corners.
     * @param radius the corner radius in pixels.
     * @return this object.
     */
    public Mark cornerRadius(double radius) {
        spec.put("cornerRadius", radius);
        return this;
    }

    /**
     * For vertical bars, sets the top-left and top-right corner radius.
     * For horizontal bars, sets the top-right and bottom-right corner radius.
     * @param radius the corner radius in pixels.
     * @return this object.
     */
    public Mark cornerRadiusEnd(double radius) {
        spec.put("cornerRadiusEnd", radius);
        return this;
    }

    /**
     * Sets the radius in pixels of rounded rectangles' top left corner.
     * @param radius the corner radius in pixels.
     * @return this object.
     */
    public Mark cornerRadiusTopLeft(double radius) {
        spec.put("cornerRadiusTopLeft", radius);
        return this;
    }

    /**
     * Sets the radius in pixels of rounded rectangles' top right corner.
     * @param radius the corner radius in pixels.
     * @return this object.
     */
    public Mark cornerRadiusTopRight(double radius) {
        spec.put("cornerRadiusTopRight", radius);
        return this;
    }

    /**
     * Sets the radius in pixels of rounded rectangles' bottom left corner.
     * @param radius the corner radius in pixels.
     * @return this object.
     */
    public Mark cornerRadiusBottomLeft(double radius) {
        spec.put("cornerRadiusBottomLeft", radius);
        return this;
    }

    /**
     * Sets the radius in pixels of rounded rectangles' bottom right corner.
     * @param radius the corner radius in pixels.
     * @return this object.
     */
    public Mark cornerRadiusBottomRight(double radius) {
        spec.put("cornerRadiusBottomRight", radius);
        return this;
    }
}
