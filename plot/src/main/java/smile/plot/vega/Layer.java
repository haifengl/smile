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
 * To superimpose one chart on top of another.
 *
 * @author Haifeng Li
 */
public class Layer extends ViewComposition {
    /**
     * Constructor.
     *
     * Note: Specifications inside layer cannot use row and column
     * channels as layering facet specifications is not allowed.
     * Instead, use the facet operator and place a layer inside a facet.
     *
     * @param views Layer or single view specifications to be superimposed.
     */
    public Layer(View... views) {
        ArrayNode layer = mapper.createArrayNode();
        for (VegaLite view : views) {
            layer.add(view.spec);
        }
        spec.set("layer", layer);
    }

    /**
     * Sets the width of a plot with a continuous x-field,
     * or the fixed width of a plot a discrete x-field or no x-field.
     */
    public Layer width(int width) {
        spec.put("width", width);
        return this;
    }

    /**
     * Sets the height of a plot with a continuous y-field,
     * or the fixed height of a plot a discrete y-field or no y-field.
     */
    public Layer height(int height) {
        spec.put("height", height);
        return this;
    }

    /**
     * To enable responsive sizing on width.
     * @param width it should be set to "container".
     */
    public Layer width(String width) {
        assert width == "container" : "Invalid width: " + width;
        spec.put("width", "container");
        return this;
    }

    /**
     * To enable responsive sizing on height.
     * @param height it should be set to "container".
     */
    public Layer height(String height) {
        assert height == "container" : "Invalid height: " + height;
        spec.put("height", "container");
        return this;
    }

    /**
     * For a discrete x-field, sets the width per discrete step.
     */
    public Layer widthStep(int step) {
        ObjectNode width = spec.putObject("width");
        width.put("step", step);
        return this;
    }

    /**
     * For a discrete y-field, sets the height per discrete step..
     */
    public Layer heightStep(int step) {
        ObjectNode height = spec.putObject("height");
        height.put("step", step);
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
    public Layer encodingValue(String channel, int value) {
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
    public Layer encodingValue(String channel, double value) {
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
    public Layer encodingValue(String channel, String value) {
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
    public Layer encodingDatum(String channel, int datum) {
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
    public Layer encodingDatum(String channel, double datum) {
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
    public Layer encodingDatum(String channel, String datum) {
        ObjectNode encoding = encoding();
        ObjectNode node = encoding.putObject(channel);
        node.put("datum", datum);
        return this;
    }
}
