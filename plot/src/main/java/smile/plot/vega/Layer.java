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

/**
 * To superimpose one chart on top of another.
 *
 * @author Haifeng Li
 */
public class Layer extends View implements ViewComposition {
    /**
     * Constructor.
     * <p>
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

    @Override
    public Layer width(int width) {
        super.width(width);
        return this;
    }

    @Override
    public Layer height(int height) {
        super.height(height);
        return this;
    }

    @Override
    public Layer width(String width) {
        super.width(width);
        return this;
    }

    @Override
    public Layer height(String height) {
        super.height(height);
        return this;
    }

    @Override
    public Layer widthStep(int step) {
        super.widthStep(step);
        return this;
    }

    @Override
    public Layer heightStep(int step) {
        super.heightStep(step);
        return this;
    }

    @Override
    public Layer encodeValue(String channel, int value) {
        super.encodeValue(channel, value);
        return this;
    }

    @Override
    public Layer encodeValue(String channel, double value) {
        super.encodeValue(channel, value);
        return this;
    }

    @Override
    public Layer encodeValue(String channel, String value) {
        super.encodeValue(channel, value);
        return this;
    }

    @Override
    public Layer encodeDatum(String channel, int datum) {
        super.encodeDatum(channel, datum);
        return this;
    }

    @Override
    public Layer encodeDatum(String channel, double datum) {
        super.encodeDatum(channel, datum);
        return this;
    }

    @Override
    public Layer encodeDatum(String channel, String datum) {
        super.encodeDatum(channel, datum);
        return this;
    }

    @Override
    public Layer resolveScale(String channel, String resolution) {
        ViewComposition.super.resolveScale(channel, resolution);
        return this;
    }

    @Override
    public Layer resolveAxis(String channel, String resolution) {
        ViewComposition.super.resolveAxis(channel, resolution);
        return this;
    }

    @Override
    public Layer resolveLegend(String channel, String resolution) {
        ViewComposition.super.resolveLegend(channel, resolution);
        return this;
    }
}
