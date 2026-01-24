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
 * The geographic projection, which will be applied to shape path for
 * "geoshape" marks and to latitude and "longitude" channels for other
 * marks.
 * <p>
 * A cartographic projection maps longitude and latitude pairs to x, y
 * coordinates. As with Vega, one can use projections in Vega-Lite to
 * lay out both geographic points (such as locations on a map) represented
 * by longitude and latitude coordinates, or to project geographic regions
 * (such as countries and states) represented using the GeoJSON format.
 * Projections are specified at the unit specification level, alongside
 * encoding. Geographic coordinate data can then be mapped to longitude
 * and latitude channels (and longitude2 and latitude2 for ranged marks).
 *
 * @author Haifeng Li
 */
public class Projection {
    /** VegaLite's Projection object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Projection(ObjectNode spec) {
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
     * Sets the projection's center, a two-element array of longitude and latitude in degrees.
     * @param longitude longitude in degrees.
     * @param latitude latitude in degrees.
     * @return this object.
     */
    public Projection center(double longitude, double latitude) {
        ArrayNode node = spec.putArray("center");
        node.add(longitude);
        node.add(latitude);
        return this;
    }

    /**
     * Sets the projection's clipping circle radius to the specified angle
     * in degrees.
     * @param angle The clip angle in degrees.
     * @return this object.
     */
    public Projection clipAngle(double angle) {
        spec.put("clipAngle", angle);
        return this;
    }

    /**
     * Sets the projection's viewport clip extent to the specified bounds
     * in pixels. The extent bounds are specified as [x0, y0] and [x1, y1].
     * @param x0 the left of the viewport.
     * @param y0 the top of the viewport.
     * @param x1 the right of the viewport.
     * @param y1 the bottom of the viewport.
     * @return this object.
     */
    public Projection clipExtent(double x0, double y0, double x1, double y1) {
        ArrayNode extend = spec.putArray("clipExtent");
        extend.addArray().add(x0).add(y0);
        extend.addArray().add(x1).add(y1);
        return this;
    }

    /**
     * For conic projections, sets the two standard parallels that define the map layout.
     * @param parallels two standard parallels.
     * @return this object.
     * @see <a href="https://en.wikipedia.org/wiki/Map_projection#Conic">Conic</a>
     */
    public Projection parallels(double... parallels) {
        ArrayNode node = spec.putArray("parallels");
        for (double parallel : parallels) {
            node.add(parallel);
        }
        return this;
    }

    /**
     * Sets the default radius (in pixels) to use when drawing GeoJSON Point
     * and MultiPoint geometries.
     * @param radius the fill color.
     * @return this object.
     */
    public Projection pointRadius(double radius) {
        spec.put("pointRadius", radius);
        return this;
    }

    /**
     * Sets the threshold for the projection's adaptive resampling to the
     * specified value in pixels. This value corresponds to the Douglas–Peucker
     * distance.
     * @param threshold the threshold.
     * @return this object.
     */
    public Projection precision(double threshold) {
        spec.put("precision", threshold);
        return this;
    }

    /**
     * Sets the projection's three-axis rotation to the specified angles
     * by specifying the rotation angles in degrees about each spherical axis.
     * @param lambda correspond to yaw.
     * @param phi correspond to pitch.
     * @param gamma correspond to roll.
     * @return this object.
     */
    public Projection rotate(double lambda, double phi, double gamma) {
        ArrayNode node = spec.putArray("rotate");
        node.add(lambda);
        node.add(phi);
        node.add(gamma);
        return this;
    }

    /**
     * Sets the projection's scale (zoom) factor, overriding automatic fitting.
     * The default scale is projection-specific. The scale factor corresponds
     * linearly to the distance between projected points; however, scale
     * factor values are not equivalent across projections.
     * @param scale the scale factor.
     * @return this object.
     */
    public Projection scale(double scale) {
        spec.put("scale", scale);
        return this;
    }

    /**
     * Sets the projection's translation offset.
     * @param tx the offset in x-axis.
     * @param ty the offset in y-axis.
     * @return this object.
     */
    public Projection translate(double tx, double ty) {
        ArrayNode node = spec.putArray("translate");
        node.add(tx);
        node.add(ty);
        return this;
    }
}
