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
     * Sets the width of the data rectangle (plotting) dimensions.
     */
    public View width(int width) {
        spec.put("width", width);
        return this;
    }

    /**
     * Sets the height of the data rectangle (plotting) dimensions.
     */
    public View height(int height) {
        spec.put("height", height);
        return this;
    }

    /**
     * Sets the top-level width properties to "container" to indicate
     * that the width of the plot should be the same as its surrounding
     * container. The width and height can be set independently,
     * for example, you can have a responsive width and a fixed height
     * by setting width to "container" and height to a number.
     * <p>
     * After setting width or height to "container", you need to ensure
     * that the container's width or height is determined outside the plot.
     * For example, the container can be a `<div>` element that has style
     * width: 100%; height: 300px. When the container is not available
     * or its size is not defined (e.g., in server-side rendering),
     * the default width and height are config.view.continuousWidth
     * and config.view.continuousHeight, respectively.
     */
    public View width(String width) {
        assert width == "container" : "Invalid width: " + width;
        spec.put("width", "container");
        return this;
    }

    /**
     * Sets the top-level height properties to "container" to indicate
     * that the height of the plot should be the same as its surrounding
     * container. The width and height can be set independently,
     * for example, you can have a responsive width and a fixed height
     * by setting width to "container" and height to a number.
     * <p>
     * After setting width or height to "container", you need to ensure
     * that the container's width or height is determined outside the plot.
     * For example, the container can be a `<div>` element that has style
     * width: 100%; height: 300px. When the container is not available
     * or its size is not defined (e.g., in server-side rendering),
     * the default width and height are config.view.continuousWidth
     * and config.view.continuousHeight, respectively.
     */
    public View height(String height) {
        assert height == "container" : "Invalid height: " + height;
        spec.put("height", "container");
        return this;
    }

    /**
     * For a discrete x-field, sets the width per discrete step.
     */
    public View widthStep(int step) {
        ObjectNode width = mapper.createObjectNode();
        width.put("step", step);
        spec.set("width", width);
        return this;
    }

    /**
     * For a discrete y-field, sets the height per discrete step..
     */
    public View heightStep(int step) {
        ObjectNode height = mapper.createObjectNode();
        height.put("step", step);
        spec.set("height", height);
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

    /** Returns the encoding object. */
    private ObjectNode encoding() {
        return spec.has("encoding") ? (ObjectNode) spec.get("encoding") : spec.putObject("encoding");
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
     *             a data value or an object defining iterated values from the
     *             repeat operator.
     * @return the field object for encoding the channel.
     */
    public Field encoding(String channel, String field) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("field", field);
        return new Field(node);
    }

    /**
     * Sets an encoded constant visual value.
     * @param channel the encoding channel.
     * @param value the constant visual value.
     * @return this object.
     */
    public View encodingValue(String channel, int value) {
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
    public View encodingValue(String channel, double value) {
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
    public View encodingValue(String channel, String value) {
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
    public View encodingDatum(String channel, int datum) {
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
    public View encodingDatum(String channel, double datum) {
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
    public View encodingDatum(String channel, String datum) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("datum", datum);
        return this;
    }

    /** Sets a mark property by value. */
    public View setPropertyValue(String prop, JsonNode value) {
        ObjectNode data = mapper.createObjectNode();
        data.set("value", value);
        encoding().set(prop, data);
        return this;
    }

    /** Sets a mark property by datum. */
    public View setPropertyDatum(String prop, JsonNode datum) {
        ObjectNode data = mapper.createObjectNode();
        data.set("datum", datum);
        encoding().set(prop, data);
        return this;
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
     * @link https://vega.github.io/vega-lite/docs/projection.html#projection-types
     */
    public Projection projection(String type) {
        ObjectNode node = spec.putObject("projection");
        node.put("type", type);
        return new Projection(node);
    }
}
