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

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * All view composition specifications (layer, facet, concat, and repeat)
 * can have the resolve property for scale, axes, and legend resolution.
 * <p>
 * Vega-Lite determines whether scale domains should be unioned.
 * If the scale domain is unioned, axes and legends can be merged.
 * Otherwise, they have to be independent.
 * <p>
 * There are two options to resolve a scale, axis, or legend: "shared"
 * and "independent". Independent scales imply independent axes and legends.
 *
 * @author Haifeng Li
 */
public class ViewComposition extends VegaLite {
    /** Resolve parent object. */
    ObjectNode resolve;
    /** Scale resolve specification object. */
    ObjectNode scale;
    /** Axis resolve specification object. */
    ObjectNode axis;
    /** Legend resolve specification object. */
    ObjectNode legend;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    ViewComposition() {
    }

    /**
     * Sets a scale resolution. For scales, resolution can be specified for every channel.
     * @param channel positional or non-positional channel.
     * @param resolution "shared" or "independent".
     */
    public ViewComposition resolveScale(String channel, String resolution) {
        if (resolve == null) {
            resolve = spec.putObject("resolve");
        }
        if (scale == null) {
            scale = resolve.putObject("scale");
        }
        scale.put(channel, resolution);
        return this;
    }

    /**
     * Sets an axis resolution.
     * @param channel positional channel: "x" or "y".
     * @param resolution "shared" or "independent".
     */
    public ViewComposition resolveAxis(String channel, String resolution) {
        if (resolve == null) {
            resolve = spec.putObject("resolve");
        }
        if (axis == null) {
            axis = resolve.putObject("axis");
        }
        axis.put(channel, resolution);
        return this;
    }

    /**
     * Sets a legend resolution.
     * @param channel non-positional channel: "color", "opacity", "shape", or "size".
     * @param resolution "shared" or "independent".
     */
    public ViewComposition resolveLegend(String channel, String resolution) {
        if (resolve == null) {
            resolve = spec.putObject("resolve");
        }
        if (legend == null) {
            legend = resolve.putObject("legend");
        }
        legend.put(channel, resolution);
        return this;
    }
}
