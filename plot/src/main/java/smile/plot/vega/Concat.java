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
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Concatenating views.
 *
 * @author Haifeng Li
 */
public class Concat extends ViewLayoutComposition {

    /**
     * Constructor for horizontal concatenation or vertical concatenation.
     * @param layout "hconcat" or "vconcat".
     * @param views multiple views.
     */
    public Concat(String layout, VegaLite... views) {
        ArrayNode node = spec.putArray(layout);
        for (VegaLite view : views) {
            node.add(view.spec);
        }
    }

    /**
     * Constructor to put multiple views into a flexible flow layout.
     * @param columns the number of columns.
     * @param views multiple views.
     */
    public Concat(int columns, VegaLite... views) {
        spec.put("columns", columns);
        ArrayNode node = spec.putArray("concat");
        for (VegaLite view : views) {
            node.add(view.spec);
        }
    }

    @Override
    public Concat usermeta(JsonNode metadata) {
        super.usermeta(metadata);
        return this;
    }

    @Override
    public Concat usermeta(Object metadata) {
        super.usermeta(metadata);
        return this;
    }

    @Override
    public Concat background(String color) {
        super.background(color);
        return this;
    }

    @Override
    public Concat padding(int size) {
        super.padding(size);
        return this;
    }

    @Override
    public Concat padding(int left, int top, int right, int bottom) {
        super.padding(left, top, right, bottom);
        return this;
    }

    @Override
    public Concat autosize() {
        super.autosize();
        return this;
    }

    @Override
    public Concat autosize(String type, boolean resize, String contains) {
        super.autosize(type, resize, contains);
        return this;
    }

    @Override
    public Concat name(String name) {
        super.name(name);
        return this;
    }

    @Override
    public Concat description(String description) {
        super.description(description);
        return this;
    }

    @Override
    public Concat title(String title) {
        super.title(title);
        return this;
    }

    @Override
    public Concat resolveScale(String channel, String resolution) {
        super.resolveScale(channel, resolution);
        return this;
    }

    @Override
    public Concat resolveAxis(String channel, String resolution) {
        super.resolveAxis(channel, resolution);
        return this;
    }

    @Override
    public Concat resolveLegend(String channel, String resolution) {
        super.resolveLegend(channel, resolution);
        return this;
    }

    @Override
    public Concat align(String strategy) {
        super.align(strategy);
        return this;
    }

    @Override
    public Concat align(String row, String column) {
        super.align(row, column);
        return this;
    }

    @Override
    public Concat bounds(String bounds) {
        super.bounds(bounds);
        return this;
    }

    @Override
    public Concat center(boolean flag) {
        super.center(flag);
        return this;
    }

    @Override
    public Concat center(int row, int column) {
        super.center(row, column);
        return this;
    }

    @Override
    public Concat spacing(int size) {
        super.spacing(size);
        return this;
    }

    @Override
    public Concat spacing(int row, int column) {
        super.spacing(row, column);
        return this;
    }
}
