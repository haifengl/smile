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
 * Vega-Lite's config object lists configuration properties of
 * a visualization for creating a consistent theme.
 *
 * @author Haifeng Li
 */
public class Config {
    /** VegaLite's Config object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Config(ObjectNode spec) {
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
     * Sets the overall size of the visualization. The total size of
     * a Vega-Lite visualization may be determined by multiple factors:
     * specified width, height, and padding values, as well as content
     * such as axes, legends, and titles.
     * @return this object.
     */
    public Config autosize() {
        return autosize("pad", false, "content");
    }

    /**
     * Sets the overall size of the visualization. The total size of
     * a Vega-Lite visualization may be determined by multiple factors:
     * specified width, height, and padding values, as well as content
     * such as axes, legends, and titles.
     *
     * @param type     The sizing format type. One of "pad", "fit", "fit-x",
     *                 "fit-y", or "none". See Vega-Lite documentation for
     *                 descriptions of each.
     * @param resize   A boolean flag indicating if autosize layout should
     *                 be re-calculated on every view update.
     * @param contains Determines how size calculation should be performed,
     *                 one of "content" or "padding". The default setting
     *                 ("content") interprets the width and height settings
     *                 as the data rectangle (plotting) dimensions, to which
     *                 padding is then added. In contrast, the "padding"
     *                 setting includes the padding within the view size
     *                 calculations, such that the width and height settings
     *                 indicate the total intended size of the view.
     * @see <a href="https://vega.github.io/vega-lite/docs/size.html#autosize">autosize</a>
     * @return this object.
     */
    public Config autosize(String type, boolean resize, String contains) {
        ObjectNode autosize = spec.putObject("autosize");
        autosize.put("type", type);
        autosize.put("resize", resize);
        autosize.put("contains", contains);
        return this;
    }

    /**
     * Sets the background with CSS color property.
     * @param color the background color.
     * @return this object.
     */
    public Config background(String color) {
        spec.put("background", color);
        return this;
    }

    /**
     * Specifies padding for all sides.
     * The visualization padding, in pixels, is from the edge of the
     * visualization canvas to the data rectangle.
     * @param size the padding size.
     * @return this object.
     */
    public Config padding(int size) {
        spec.put("padding", size);
        return this;
    }

    /**
     * Specifies padding for each side.
     * The visualization padding, in pixels, is from the edge of the
     * visualization canvas to the data rectangle.
     * @param left the left padding.
     * @param top the top padding.
     * @param right the right padding.
     * @param bottom the bottom padding.
     * @return this object.
     */
    public Config padding(int left, int top, int right, int bottom) {
        ObjectNode padding = spec.putObject("padding");
        padding.put("left", left);
        padding.put("top", top);
        padding.put("right", right);
        padding.put("bottom", bottom);
        return this;
    }

    /**
     * Sets the default axis and legend title for count fields.
     * @param title the count title.
     * @return this object.
     */
    public Config countTitle(String title) {
        spec.put("countTitle", title);
        return this;
    }

    /**
     * Defines how Vega-Lite generates title for fields. There are three possible styles:
     * <p>
     * "verbal" (Default) - displays function in a verbal style (e.g., “Sum of field”, “Year-month of date”, “field (binned)”).
     * <p>
     * "function" - displays function using parentheses and capitalized texts (e.g., “SUM(field)”, “YEARMONTH(date)”, “BIN(field)”).
     * <p>
     * "plain" - displays only the field name without functions (e.g., “field”, “date”, “field”).
     * @param title the field title.
     * @return this object.
     */
    public Config fieldTitle(String title) {
        spec.put("fieldTitle", title);
        return this;
    }

    /**
     * Sets the default font for all text marks, titles, and labels.
     * @param font the text font.
     * @return this object.
     */
    public Config font(String font) {
        spec.put("font", font);
        return this;
    }

    /**
     * Sets a delimiter, such as a newline character, upon which to break
     * text strings into multiple lines. This property provides a global
     * default for text marks, which is overridden by mark or style config
     * settings, and by the lineBreak mark encoding channel. If signal-valued,
     * either string or regular expression (regexp) values are valid.
     * @param lineBreak the line delimiter.
     * @return this object.
     */
    public Config lineBreak(String lineBreak) {
        spec.put("lineBreak", lineBreak);
        return this;
    }

    /**
     * Define custom format configuration for tooltips.
     * @return a FormatConfig object to set custom format configuration.
     */
    public FormatConfig tooltipFormat() {
        ObjectNode node = spec.putObject("tooltipFormat");
        return new FormatConfig(node);
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
}
