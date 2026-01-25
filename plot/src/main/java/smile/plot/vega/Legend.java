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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.vega;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Similar to axes, legends visualize scales. However, whereas axes aid
 * interpretation of scales with positional ranges, legends aid
 * interpretation of scales with ranges such as colors, shapes and sizes.
 * <p>
 * By default, Vega-Lite automatically creates legends with default properties
 * for color, opacity, size, and shape channels when they encode data fields.
 * User can set the legend property of a mark property channel's field
 * definition to an object to customize legend properties or set legend to
 * null to remove the legend.
 * <p>
 * Besides legend property of a field definition, the configuration object
 * (config) also provides legend config for setting default legend properties
 * for all legends.
 *
 * @author Haifeng Li
 */
public class Legend {
    /** VegaLite's Legend object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Legend(ObjectNode spec) {
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
    public Legend title(String title) {
        spec.put("title", title);
        return this;
    }

    /**
     * Sets if ARIA attributes should be included (SVG output only).
     * @param flag A flag indicating if ARIA attributes should be
     *            included (SVG output only). If false, the "aria-hidden"
     *            attribute will be set on the output SVG group, removing
     *            the legend from the ARIA accessibility tree.
     * @return this object.
     */
    public Legend aria(boolean flag) {
        spec.put("aria", flag);
        return this;
    }

    /**
     * Sets the corner radius for the full legend.
     * @param radius the corner radius for the full legend.
     * @return this object.
     */
    public Legend cornerRadius(double radius) {
        spec.put("cornerRadius", radius);
        return this;
    }

    /**
     * Sets the text description of this legend for ARIA accessibility (SVG
     * output only). If the aria property is true, for SVG output the
     * "aria-label" attribute will be set to this description. If the
     * description is unspecified it will be automatically generated.
     * @param description the text description of this legend for ARIA
     *                   accessibility.
     * @return this object.
     */
    public Legend description(String description) {
        spec.put("description", description);
        return this;
    }

    /**
     * Sets the direction of the legend, one of "vertical" or "horizontal".
     * @param direction "vertical" or "horizontal".
     * @return this object.
     */
    public Legend direction(String direction) {
        spec.put("direction", direction);
        return this;
    }

    /**
     * Sets the background fill color for the full legend.
     * @param color the background fill color.
     * @return this object.
     */
    public Legend fillColor(String color) {
        spec.put("fillColor", color);
        return this;
    }

    /**
     * Sets the custom x-position for legend with orient "none".
     * @param x the custom x-position for legend with orient "none".
     * @return this object.
     */
    public Legend x(double x) {
        spec.put("legendX", x);
        return this;
    }

    /**
     * Sets the custom y-position for legend with orient "none".
     * @param y the custom y-position for legend with orient "none".
     * @return this object.
     */
    public Legend y(double y) {
        spec.put("legendY", y);
        return this;
    }

    /**
     * Sets the offset, in pixels, by which to displace the legend from the edge
     * of the enclosing group or data rectangle.
     * @param offset the offset in pixels.
     * @return this object.
     */
    public Legend offset(double offset) {
        spec.put("offset", offset);
        return this;
    }

    /**
     * Sets the orientation of the legend.
     * @param orient "top", "bottom", "left" or "right".
     * @return this object.
     */
    public Legend orient(String orient) {
        spec.put("orient", orient);
        return this;
    }

    /**
     * Sets the padding between the border and content of the legend group.
     * @param padding the padding between the border and content of the legend group.
     * @return this object.
     */
    public Legend padding(double padding) {
        spec.put("padding", padding);
        return this;
    }

    /**
     * Sets the border stroke color for the full legend.
     * @param color the border stroke color.
     * @return this object.
     */
    public Legend strokeColor(String color) {
        spec.put("strokeColor", color);
        return this;
    }

    /**
     * Sets the type of the legend. Use "symbol" to create a discrete
     * legend and "gradient" for a continuous color gradient.
     * @param type "symbol" or "gradient".
     * @return this object.
     */
    public Legend type(String type) {
        spec.put("type", type);
        return this;
    }

    /**
     * Sets the desired number of tick values for quantitative legends.
     * @param count the desired number of tick values.
     * @return this object.
     */
    public Legend tickCount(int count) {
        spec.put("tickCount", count);
        return this;
    }

    /**
     * Sets the desired number of tick values for quantitative legends.
     * @param expr the expression of desired number of tick values.
     * @return this object.
     */
    public Legend tickCount(String expr) {
        spec.put("tickCount", expr);
        return this;
    }

    /**
     * Sets the explicitly set the visible legend values.
     * @param values the visible legend values.
     * @return this object.
     */
    public Legend values(String... values) {
        ArrayNode node = spec.putArray("values");
        for (String value : values) {
            node.add(value);
        }
        return this;
    }

    /**
     * Sets a non-negative integer indicating the z-index of the legend.
     * If zindex is 0, axes should be drawn behind all chart elements.
     * To put them in front, set zindex to 1 or more.
     * @param zindex a non-negative integer indicating the z-index of the legend.
     * @return this object.
     */
    public Legend zindex(int zindex) {
        spec.put("zindex", zindex);
        return this;
    }

    /**
     * Sets the length in pixels of the primary axis of a color gradient.
     * This value corresponds to the height of a vertical gradient or the
     * width of a horizontal gradient.
     * @param length the length in pixels.
     * @return this object.
     */
    public Legend gradientLength(double length) {
        spec.put("gradientLength", length);
        return this;
    }

    /**
     * Sets the opacity of the color gradient.
     * @param opacity a value between [0, 1].
     * @return this object.
     */
    public Legend gradientOpacity(double opacity) {
        spec.put("gradientOpacity", opacity);
        return this;
    }

    /**
     * Sets the color of the gradient stroke.
     * @param color the color of the gradient stroke.
     * @return this object.
     */
    public Legend gradientStrokeColor(String color) {
        spec.put("gradientStrokeColor", color);
        return this;
    }

    /**
     * Sets the width of the gradient stroke.
     * @param width the width in pixels.
     * @return this object.
     */
    public Legend gradientStrokeWidth(double width) {
        spec.put("gradientStrokeWidth", width);
        return this;
    }

    /**
     * Sets the thickness in pixels of the color gradient. This value
     * corresponds to the width of a vertical gradient or the height
     * of a horizontal gradient.
     * @param thickness the thickness in pixels.
     * @return this object.
     */
    public Legend gradientThickness(double thickness) {
        spec.put("gradientThickness", thickness);
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
    public Legend format(String format) {
        spec.put("format", format);
        return this;
    }

    /**
     * Sets the format type for labels.
     * @param formatType "number", "time", or a registered custom format type.
     * @return this object.
     */
    public Legend formatType(String formatType) {
        spec.put("formatType", formatType);
        return this;
    }

    /**
     * Sets the alignment of the legend label.
     * @param alignment "left", "center", or "right".
     * @return this object.
     */
    public Legend labelAlign(String alignment) {
        spec.put("labelAlign", alignment);
        return this;
    }

    /**
     * Sets the position of the baseline of legend label.
     * @param baseline "top", "middle", "bottom", or "alphabetic".
     * @return this object.
     */
    public Legend labelBaseline(String baseline) {
        spec.put("labelBaseline", baseline);
        return this;
    }

    /**
     * Sets the color of the legend label.
     * @param color the color of the legend label.
     * @return this object.
     */
    public Legend labelColor(String color) {
        spec.put("labelColor", color);
        return this;
    }

    /**
     * Sets the Vega expression for customizing labels.
     * The label text and value can be assessed via the label and value
     * properties of the legend's backing datum object.
     * @param expr the Vega expression.
     * @return this object.
     */
    public Legend labelExpr(String expr) {
        spec.put("labelExpr", expr);
        return this;
    }

    /**
     * Sets the font of the legend label.
     * @param font the font of the legend label.
     * @return this object.
     */
    public Legend labelFont(String font) {
        spec.put("labelFont", font);
        return this;
    }

    /**
     * Sets the font size of the label in pixels.
     * @param size the font size in pixels.
     * @return this object.
     */
    public Legend labelFontSize(double size) {
        spec.put("labelFontSize", size);
        return this;
    }

    /**
     * Sets the font style of the title.
     * @param style the font style of the title.
     * @return this object.
     */
    public Legend labelFontStyle(String style) {
        spec.put("labelFontStyle", style);
        return this;
    }

    /**
     * Sets the font weight of legend labels.
     * @param weight the font weight of legend labels.
     * @return this object.
     */
    public Legend labelFontWeight(String weight) {
        spec.put("labelFontWeight", weight);
        return this;
    }

    /**
     * Sets the font weight of legend labels.
     * @param weight the font weight of legend labels.
     * @return this object.
     */
    public Legend labelFontWeight(int weight) {
        spec.put("labelFontWeight", weight);
        return this;
    }

    /**
     * Sets the maximum allowed pixel width of legend labels.
     * @param limit the maximum allowed pixel width of legend labels.
     * @return this object.
     */
    public Legend labelLimit(int limit) {
        spec.put("labelLimit", limit);
        return this;
    }

    /**
     * Sets the position offset in pixels to apply to labels.
     * @param offset the position offset in pixels to apply to labels.
     * @return this object.
     */
    public Legend labelOffset(int offset) {
        spec.put("labelOffset", offset);
        return this;
    }

    /**
     * Sets the strategy to use for resolving overlap of legend labels.
     * If false (the default), no overlap reduction is attempted.
     * If set to true, a strategy of removing every other label is
     * used (this works well for standard linear axes).
     *
     * @param flag a value between [0, 1].
     * @return this object.
     */
    public Legend labelOverlap(boolean flag) {
        spec.put("labelOverlap", flag);
        return this;
    }

    /**
     * Sets the strategy to use for resolving overlap of legend labels.
     * If set to "parity", a strategy of removing every other label is
     * used (this works well for standard linear axes). If set to
     * "greedy", a linear scan of the labels is performed, removing
     * any labels that overlaps with the last visible label (this often
     * works better for log-scaled axes).
     *
     * @param strategy "parity" or "greedy".
     * @return this object.
     */
    public Legend labelOverlap(String strategy) {
        spec.put("labelOverlap", strategy);
        return this;
    }

    /**
     * Sets the height in pixels to clip symbol legend entries and limit their size.
     * @param height the height in pixels.
     * @return this object.
     */
    public Legend clipHeight(double height) {
        spec.put("clipHeight", height);
        return this;
    }

    /**
     * Sets the horizontal padding in pixels between symbol legend entries.
     * @param padding the horizontal padding in pixels.
     * @return this object.
     */
    public Legend columnPadding(double padding) {
        spec.put("columnPadding", padding);
        return this;
    }

    /**
     * Sets the vertical padding in pixels between symbol legend entries.
     * @param padding the vertical padding in pixels.
     * @return this object.
     */
    public Legend rowPadding(double padding) {
        spec.put("rowPadding", padding);
        return this;
    }

    /**
     * Sets the number of columns in which to arrange symbol legend entries.
     * A value of 0 or lower indicates a single row with one column per entry.
     * @param columns the number of columns.
     * @return this object.
     */
    public Legend columns(int columns) {
        spec.put("columns", columns);
        return this;
    }

    /**
     * Sets the alignment to apply to symbol legends rows and columns.
     *
     * @param alignment "all", "each", or "none".
     * @return this object.
     */
    public Legend gridAlign(String alignment) {
        spec.put("gridAlign", alignment);
        return this;
    }

    /**
     * Sets the maximum number of allowed entries for a symbol legend.
     * Additional entries will be dropped.
     * @param limit the maximum number of allowed entries.
     * @return this object.
     */
    public Legend symbolLimit(int limit) {
        spec.put("symbolLimit", limit);
        return this;
    }
}
