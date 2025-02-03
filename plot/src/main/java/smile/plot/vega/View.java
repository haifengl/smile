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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Single view specification, which describes a view that uses a single
 * mark type to visualize the data.
 *
 * @author Haifeng Li
 */
public class View extends VegaLite {
    /**
     * Constructor.
     */
    public View() {
    }

    /**
     * Constructor.
     * @param title a descriptive title.
     */
    public View(String title) {
        title(title);
    }

    /**
     * Sets the width of a plot with a continuous x-field,
     * or the fixed width of a plot a discrete x-field or no x-field.
     */
    public View width(int width) {
        spec.put("width", width);
        return this;
    }

    /**
     * Sets the height of a plot with a continuous y-field,
     * or the fixed height of a plot a discrete y-field or no y-field.
     */
    public View height(int height) {
        spec.put("height", height);
        return this;
    }

    /**
     * To enable responsive sizing on width.
     * @param width it should be set to "container".
     */
    public View width(String width) {
        assert "container".equals(width) : "Invalid width: " + width;
        spec.put("width", width);
        return this;
    }

    /**
     * To enable responsive sizing on height.
     * @param height it should be set to "container".
     */
    public View height(String height) {
        assert "container".equals(height) : "Invalid height: " + height;
        spec.put("height", height);
        return this;
    }

    /**
     * For a discrete x-field, sets the width per discrete step.
     */
    public View widthStep(int step) {
        ObjectNode width = spec.putObject("width");
        width.put("step", step);
        return this;
    }

    /**
     * For a discrete y-field, sets the height per discrete step.
     */
    public View heightStep(int step) {
        ObjectNode height = spec.putObject("height");
        height.put("step", step);
        return this;
    }

    /**
     * Returns the mark definition object.
     * @param type The mark type. This could a primitive mark type (one of
     *            "bar", "circle", "square", "tick", "line", "area", "point",
     *            "geoshape", "rule", and "text") or a composite mark type
     *            ("boxplot", "errorband", "errorbar").
     * @return the mark definition object.
     */
    public Mark mark(String type) {
        ObjectNode node = spec.putObject("mark");
        node.put("type", type);
        return new Mark(node);
    }

    /**
     * Returns the view background's fill and stroke object.
     */
    public Background background() {
        return new Background(spec.has("view") ? (ObjectNode) spec.get("view") : spec.putObject("view"));
    }

    /**
     * Returns the defining properties of geographic projection, which will be
     * applied to shape path for "geoshape" marks and to latitude and
     * "longitude" channels for other marks.
     * @param type The cartographic projection to use.
     * @see <a href="https://vega.github.io/vega-lite/docs/projection.html#projection-types">projection types</a>
     */
    public Projection projection(String type) {
        ObjectNode node = spec.putObject("projection");
        node.put("type", type);
        return new Projection(node);
    }

    /**
     * Returns the field object for encoding a channel.
     * @param channel Vega-Lite supports the following groups of encoding channels:
     *   - Position Channels: x, y, x2, y2, xError, yError, xError2, yError2
     *   - Position Offset Channels: xOffset, yOffset
     *   - Polar Position Channels: theta, theta2, radius, radius2
     *   - Geographic Position Channels: longitude, latitude, longitude2, latitude2
     *   - Mark Property Channels: angle, color (and fill / stroke), opacity, fillOpacity, strokeOpacity, shape, size, strokeDash, strokeWidth
     *   - Text and Tooltip Channels: text, tooltip
     *   - Hyperlink Channel: href
     *   - Description Channel: description
     *   - Level of Detail Channel: detail
     *   - Key Channel: key
     *   - Order Channel: order
     *   - Facet Channels: facet, row, column
     * @param field A string defining the name of the field from which to pull
     *             a data value. Dots (.) and brackets ([ and ]) can be used to
     *             access nested objects (e.g., "field": "foo.bar" and
     *             "field": "foo['bar']"). If field names contain dots or
     *             brackets but are not nested, you can use \\ to escape dots
     *             and brackets (e.g., "a\\.b" and "a\\[0\\]").
     * @return the field object for encoding the channel.
     */
    public Field encode(String channel, String field) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        if (field == null) {
            node.putNull("field");
        } else if (field.startsWith("repeat:")) {
            node.putObject("field").put("repeat", field.substring(7));
        } else {
            node.put("field", field);
        }
        return new Field(node);
    }

    /**
     * Sets an encoded constant visual value.
     * @param channel the encoding channel.
     * @param value the constant visual value.
     * @return this object.
     */
    public View encodeValue(String channel, int value) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("value", value);
        return this;
    }

    /**
     * Sets an encoded constant visual value.
     * @param channel the encoding channel.
     * @param value the constant visual value.
     * @return this object.
     */
    public View encodeValue(String channel, double value) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("value", value);
        return this;
    }

    /**
     * Sets an encoded constant visual value.
     * @param channel the encoding channel.
     * @param value the constant visual value.
     * @return this object.
     */
    public View encodeValue(String channel, String value) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("value", value);
        return this;
    }

    /**
     * Sets a constant data value encoded via a scale.
     * @param channel the encoding channel.
     * @param datum the constant data value.
     * @return this object.
     */
    public View encodeDatum(String channel, int datum) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("datum", datum);
        return this;
    }

    /**
     * Sets a constant data value encoded via a scale.
     * @param channel the encoding channel.
     * @param datum the constant data value.
     * @return this object.
     */
    public View encodeDatum(String channel, double datum) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("datum", datum);
        return this;
    }

    /**
     * Sets a constant data value encoded via a scale.
     * @param channel the encoding channel.
     * @param datum the constant data value.
     * @return this object.
     */
    public View encodeDatum(String channel, String datum) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("datum", datum);
        return this;
    }

    @Override
    public View usermeta(JsonNode metadata) {
        super.usermeta(metadata);
        return this;
    }

    @Override
    public View usermeta(Object metadata) {
        super.usermeta(metadata);
        return this;
    }

    @Override
    public View background(String color) {
        super.background(color);
        return this;
    }

    @Override
    public View padding(int size) {
        super.padding(size);
        return this;
    }

    @Override
    public View padding(int left, int top, int right, int bottom) {
        super.padding(left, top, right, bottom);
        return this;
    }

    @Override
    public View autosize() {
        super.autosize();
        return this;
    }

    @Override
    public View autosize(String type, boolean resize, String contains) {
        super.autosize(type, resize, contains);
        return this;
    }

    @Override
    public View name(String name) {
        super.name(name);
        return this;
    }

    @Override
    public View description(String description) {
        super.description(description);
        return this;
    }

    @Override
    public View title(String title) {
        super.title(title);
        return this;
    }
}
