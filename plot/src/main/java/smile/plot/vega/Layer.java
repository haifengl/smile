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
     * @param views the views to be superimposed.
     */
    public Layer(View... views) {
        ArrayNode layer = mapper.createArrayNode();
        for (VegaLite view : views) {
            layer.add(view.spec);
        }
        spec.set("layer", layer);
    }
}
