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
 * Axes provide axis lines, ticks, and labels to convey how a positional range
 * represents a data range. Simply put, axes visualize scales.
 * <p>
 * By default, Vega-Lite automatically creates axes with default properties
 * for x and y channels when they encode data fields. User can set the axis
 * property of x- or y-field definition to an object to customize axis
 * properties or set axis to null to remove the axis.
 * <p>
 * Besides axis property of a field definition, the configuration object
 * (config) also provides axis config for setting default axis properties
 * for all axes.
 *
 * @author Haifeng Li
 */
public class Axis {
    /** VegaLite's Axis object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Axis(ObjectNode spec) {
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
     * Sets a descriptive title.
     * @param title a descriptive title.
     * @return this object.
     */
    public Axis title(String title) {
        spec.put("title", title);
        return this;
    }

    /**
     * Sets if ARIA attributes should be included (SVG output only).
     * @param flag A flag indicating if ARIA attributes should be
     *            included (SVG output only). If false, the "aria-hidden"
     *            attribute will be set on the output SVG group, removing
     *            the axis from the ARIA accessibility tree.
     * @return this object.
     */
    public Axis aria(boolean flag) {
        spec.put("aria", flag);
        return this;
    }

    /**
     * For band scales, sets the interpolation fraction where axis ticks
     * should be positioned.
     * @param position An interpolation fraction indicating where, for band
     *                scales, axis ticks should be positioned. A value of 0
     *                places ticks at the left edge of their bands. A value
     *                of 0.5 places ticks in the middle of their bands.
     * @return this object.
     */
    public Axis bandPosition(double position) {
        spec.put("bandPosition", position);
        return this;
    }

    /**
     * Sets the text description of this axis for ARIA accessibility (SVG
     * output only). If the aria property is true, for SVG output the
     * "aria-label" attribute will be set to this description. If the
     * description is unspecified it will be automatically generated.
     * @param description the text description of this axis for ARIA
     *                   accessibility.
     * @return this object.
     */
    public Axis description(String description) {
        spec.put("description", description);
        return this;
    }

    /**
     * Sets the maximum extent in pixels that axis ticks and labels should use.
     * This determines a maximum offset value for axis titles.
     * @param extent the maximum extent in pixels that axis ticks and labels should use.
     * @return this object.
     */
    public Axis maxExtent(int extent) {
        spec.put("maxExtent", extent);
        return this;
    }

    /**
     * Sets the minimum extent in pixels that axis ticks and labels should use.
     * This determines a minimum offset value for axis titles.
     * @param extent the minimum extent in pixels that axis ticks and labels should use.
     * @return this object.
     */
    public Axis minExtent(int extent) {
        spec.put("minExtent", extent);
        return this;
    }

    /**
     * Sets the orientation of the axis.
     * @param orient "top", "bottom", "left" or "right".
     * @return this object.
     */
    public Axis orient(String orient) {
        spec.put("orient", orient);
        return this;
    }

    /**
     * Sets the offset, in pixels, by which to displace the axis from the edge
     * of the enclosing group or data rectangle.
     * @param offset the offset in pixels.
     * @return this object.
     */
    public Axis offset(double offset) {
        spec.put("offset", offset);
        return this;
    }

    /**
     * Sets the anchor position of the axis in pixels.
     * @param position the anchor position of the axis in pixels.
     * @return this object.
     */
    public Axis position(double position) {
        spec.put("position", position);
        return this;
    }

    /**
     * Sets the coordinate space translation offset for axis layout.
     * By default, axes are translated by a 0.5 pixel offset for both
     * the x and y coordinates in order to align stroked lines with the
     * pixel grid. However, for vector graphics output these pixel-specific
     * adjustments may be undesirable, in which case translate can be changed
     * (for example, to zero).
     * @param translate the coordinate space translation offset for axis layout.
     * @return this object.
     */
    public Axis translate(double translate) {
        spec.put("translate", translate);
        return this;
    }

    /**
     * Sets a non-negative integer indicating the z-index of the axis.
     * If zindex is 0, axes should be drawn behind all chart elements.
     * To put them in front, set zindex to 1 or more.
     * @param zindex a non-negative integer indicating the z-index of the axis.
     * @return this object.
     */
    public Axis zindex(int zindex) {
        spec.put("zindex", zindex);
        return this;
    }

    /**
     * Sets the custom styles to apply to the axis.
     * @param style A string or array of strings indicating the name of custom
     *             styles to apply to the axis. A style is a named
     *             collection of axis property defaults defined within the style
     *             configuration. If style is an array, later styles will
     *             override earlier styles.
     * @return this object.
     */
    public Axis style(String... style) {
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
     * Sets if the domain (the axis baseline) should be included as part of the axis.
     * @param flag A flag indicating if the domain (the axis baseline) should be included as part of the axis.
     * @return this object.
     */
    public Axis domain(boolean flag) {
        spec.put("domain", flag);
        return this;
    }

    /**
     * Sets the stroke cap for the domain line's ending style.
     * @param cap "butt", "round" or "square".
     * @return this object.
     */
    public Axis domainCap(String cap) {
        spec.put("domainCap", cap);
        return this;
    }

    /**
     * Sets the color of axis domain line.
     * @param color the color of axis domain line.
     * @return this object.
     */
    public Axis domainColor(String color) {
        spec.put("domainColor", color);
        return this;
    }

    /**
     * Sets the opacity of the axis domain line.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Axis domainOpacity(double opacity) {
        spec.put("domainOpacity", opacity);
        return this;
    }

    /**
     * Sets the stroke width of axis domain line.
     * @param width the stroke width of axis domain line.
     * @return this object.
     */
    public Axis domainWidth(double width) {
        spec.put("domainWidth", width);
        return this;
    }

    /**
     * Sets the alternating [stroke, space] lengths for dashed domain lines.
     * @param stroke the stroke length.
     * @param space the space length.
     * @return this object.
     */
    public Axis domainDash(double stroke, double space) {
        spec.putArray("domainDash").add(stroke).add(space);
        return this;
    }

    /**
     * Sets the pixel offset at which to start drawing with the domain dash array.
     * @param offset the pixel offset at which to start drawing with the domain dash array.
     * @return this object.
     */
    public Axis domainDashOffset(double offset) {
        spec.put("domainDashOffset", offset);
        return this;
    }

    /**
     * Sets the text format. When used with the default "number" and "time"
     * format type, the text formatting pattern for labels of guides (axes,
     * legends, headers) and text marks.
     * <p>
     * If the format type is "number" (e.g., for quantitative fields), this is
     * D3's number format pattern. If the format type is "time" (e.g., for
     * temporal fields), this is D3's time format pattern.
     * @param format the text formatting pattern.
     * @return this object.
     */
    public Axis format(String format) {
        spec.put("format", format);
        return this;
    }

    /**
     * Sets the format type for labels.
     * @param formatType "number", "time", or a registered custom format type.
     * @return this object.
     */
    public Axis formatType(String formatType) {
        spec.put("formatType", formatType);
        return this;
    }

    /**
     * Sets if labels should be included as part of the axis.
     * @param flag A flag indicating if labels should be included as part of the axis.
     * @return this object.
     */
    public Axis labels(boolean flag) {
        spec.put("labels", flag);
        return this;
    }

    /**
     * Sets the horizontal text alignment of axis tick labels.
     * @param strategy the horizontal text alignment of axis tick labels.
     * @return this object.
     */
    public Axis labelAlign(String strategy) {
        spec.put("labelAlign", strategy);
        return this;
    }

    /**
     * Sets the rotation angle of the axis labels.
     * @param angle the rotation angle of the axis labels.
     * @return this object.
     */
    public Axis labelAngle(double angle) {
        spec.put("labelAngle", angle);
        return this;
    }

    /**
     * Sets the vertical text baseline of axis tick labels.
     * @param baseline "alphabetic", "top", "middle", "bottom", "line-top", or "line-bottom".
     * @return this object.
     */
    public Axis labelBaseline(String baseline) {
        spec.put("labelBaseline", baseline);
        return this;
    }

    /**
     * Sets if labels should be hidden if they exceed the axis range.
     * @param flag If false (the default) no bounds overlap analysis is performed.
     *             If true, labels will be hidden if they exceed the axis range by
     *             more than 1 pixel.
     * @return this object.
     */
    public Axis labelBound(boolean flag) {
        spec.put("labelBound", flag);
        return this;
    }

    /**
     * Sets the pixel tolerance of label bounding box.
     * @param tolerance the pixel tolerance: the maximum amount by which a
     *                 label bounding box may exceed the axis range.
     * @return this object.
     */
    public Axis labelBound(double tolerance) {
        spec.put("labelBound", tolerance);
        return this;
    }

    /**
     * Sets the color of the tick label.
     * @param color the color of the tick label.
     * @return this object.
     */
    public Axis labelColor(String color) {
        spec.put("labelColor", color);
        return this;
    }

    /**
     * Sets the Vega expression for customizing labels.
     * The label text and value can be assessed via the label and value
     * properties of the axis's backing datum object.
     * @param expr the Vega expression.
     * @return this object.
     */
    public Axis labelExpr(String expr) {
        spec.put("labelExpr", expr);
        return this;
    }

    /**
     * Sets if the first and last axis labels should be aligned flush with
     * the scale range. Flush alignment for a horizontal axis will left-align
     * the first label and right-align the last label. For vertical axes,
     * bottom and top text baselines are applied instead.
     * @param flag A flag if the first and last axis labels should be aligned
     *            flush with the scale range.
     * @return this object.
     */
    public Axis labelFlush(boolean flag) {
        spec.put("labelFlush", flag);
        return this;
    }

    /**
     * Sets the number of pixels by which to offset the first and last labels.
     * The additional adjustment can sometimes help the labels better visually
     * group with corresponding axis ticks.
     * @param adjustment the number of pixels by which to offset the first and last labels.
     * @return this object.
     */
    public Axis labelFlush(double adjustment) {
        spec.put("labelFlush", adjustment);
        return this;
    }

    /**
     * Sets the number of pixels by which to offset flush-adjusted labels.
     * Offsets can help the labels better visually group with corresponding
     * axis ticks.
     * @param offset the number of pixels by which to offset flush-adjusted labels.
     * @return this object.
     */
    public Axis labelFlushOffset(double offset) {
        spec.put("labelFlushOffset", offset);
        return this;
    }

    /**
     * Sets the font of the tick label.
     * @param font the font of the tick label.
     * @return this object.
     */
    public Axis labelFont(String font) {
        spec.put("labelFont", font);
        return this;
    }

    /**
     * Sets the font size of the label in pixels.
     * @param size the font size in pixels.
     * @return this object.
     */
    public Axis labelFontSize(double size) {
        spec.put("labelFontSize", size);
        return this;
    }

    /**
     * Sets the font style of the title.
     * @param style the font style of the title.
     * @return this object.
     */
    public Axis labelFontStyle(String style) {
        spec.put("labelFontStyle", style);
        return this;
    }

    /**
     * Sets the font weight of axis tick labels.
     * @param weight the font weight of axis tick labels.
     * @return this object.
     */
    public Axis labelFontWeight(String weight) {
        spec.put("labelFontWeight", weight);
        return this;
    }

    /**
     * Sets the font weight of axis tick labels.
     * @param weight the font weight of axis tick labels.
     * @return this object.
     */
    public Axis labelFontWeight(int weight) {
        spec.put("labelFontWeight", weight);
        return this;
    }

    /**
     * Sets the maximum allowed pixel width of axis tick labels.
     * @param limit the maximum allowed pixel width of axis tick labels.
     * @return this object.
     */
    public Axis labelLimit(int limit) {
        spec.put("labelLimit", limit);
        return this;
    }

    /**
     * Sets the line height in pixels for multi-line label text.
     * @param height the line height in pixels for multi-line label text.
     * @return this object.
     */
    public Axis labelLineHeight(int height) {
        spec.put("labelLineHeight", height);
        return this;
    }

    /**
     * Sets the line height for multi-line label text.
     * @param height "line-top" or "line-bottom".
     * @return this object.
     */
    public Axis labelLineHeight(String height) {
        spec.put("labelLineHeight", height);
        return this;
    }

    /**
     * Sets the position offset in pixels to apply to labels, in addition to tickOffset.
     * @param offset the position offset in pixels to apply to labels.
     * @return this object.
     */
    public Axis labelOffset(int offset) {
        spec.put("labelOffset", offset);
        return this;
    }

    /**
     * Sets the opacity of the labels.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Axis labelOpacity(double opacity) {
        spec.put("labelOpacity", opacity);
        return this;
    }

    /**
     * Sets the strategy to use for resolving overlap of axis labels.
     * If false (the default), no overlap reduction is attempted.
     * If set to true, a strategy of removing every other label is
     * used (this works well for standard linear axes).
     *
     * @param flag a value between [0, 1].
     * @return this object.
     */
    public Axis labelOverlap(boolean flag) {
        spec.put("labelOverlap", flag);
        return this;
    }

    /**
     * Sets the strategy to use for resolving overlap of axis labels.
     * If set to "parity", a strategy of removing every other label is
     * used (this works well for standard linear axes). If set to
     * "greedy", a linear scan of the labels is performed, removing
     * any labels that overlaps with the last visible label (this often
     * works better for log-scaled axes).
     *
     * @param strategy "parity" or "greedy".
     * @return this object.
     */
    public Axis labelOverlap(String strategy) {
        spec.put("labelOverlap", strategy);
        return this;
    }

    /**
     * Sets the padding in pixels between labels and ticks.
     * @param padding the padding in pixels between labels and ticks.
     * @return this object.
     */
    public Axis labelPadding(double padding) {
        spec.put("labelPadding", padding);
        return this;
    }

    /**
     * Sets the minimum separation that must be between label bounding boxes
     * for them to be considered non-overlapping (default 0). This property
     * is ignored if labelOverlap resolution is not enabled.
     * @param separation the separation between label bounding boxes.
     * @return this object.
     */
    public Axis labelSeparation(double separation) {
        spec.put("labelSeparation", separation);
        return this;
    }

    /**
     * Sets if gridlines should be included as part of the axis.
     * @param flag A flag indicating if gridlines should be included as part of the axis.
     * @return this object.
     */
    public Axis grid(boolean flag) {
        spec.put("grid", flag);
        return this;
    }

    /**
     * Sets the stroke cap for gridlines' ending style.
     * @param cap "butt", "round" or "square".
     * @return this object.
     */
    public Axis gridCap(String cap) {
        spec.put("gridCap", cap);
        return this;
    }

    /**
     * Sets the color of gridlines.
     * @param color the color of gridlines.
     * @return this object.
     */
    public Axis gridColor(String color) {
        spec.put("gridColor", color);
        return this;
    }

    /**
     * Sets the stroke opacity of grid.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Axis gridOpacity(double opacity) {
        spec.put("gridOpacity", opacity);
        return this;
    }

    /**
     * Sets the grid width.
     * @param width the grid width in pixels.
     * @return this object.
     */
    public Axis gridWidth(double width) {
        spec.put("gridWidth", width);
        return this;
    }

    /**
     * Sets the alternating [stroke, space] lengths for dashed gridlines.
     * @param stroke the stroke length.
     * @param space the space length.
     * @return this object.
     */
    public Axis gridDash(double stroke, double space) {
        spec.putArray("gridDash").add(stroke).add(space);
        return this;
    }

    /**
     * Sets whether the axis should include ticks.
     * @param flag A flag indicating if the axis should include ticks.
     * @return this object.
     */
    public Axis ticks(boolean flag) {
        spec.put("ticks", flag);
        return this;
    }

    /**
     * For band scales, sets if ticks and grid lines should be placed at
     * the "center" of a band or at the band "extent"s to indicate intervals.
     * @param band "center" or "extent".
     * @return this object.
     */
    public Axis tickBand(String band) {
        spec.put("tickBand", band);
        return this;
    }

    /**
     * Sets the stroke cap for tick lines' ending style.
     * @param cap "butt", "round" or "square".
     * @return this object.
     */
    public Axis tickCap(String cap) {
        spec.put("tickCap", cap);
        return this;
    }

    /**
     * Sets the color of the axis's tick.
     * @param color the color of the axis's tick.
     * @return this object.
     */
    public Axis tickColor(String color) {
        spec.put("tickColor", color);
        return this;
    }

    /**
     * Sets a desired number of ticks, for axes visualizing quantitative scales.
     * @param count A desired number of ticks, for axes visualizing quantitative scales.
     * @return this object.
     */
    public Axis tickCount(int count) {
        spec.put("tickCount", count);
        return this;
    }
}
