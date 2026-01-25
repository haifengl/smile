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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/**
 * Concatenating views.
 *
 * @author Haifeng Li
 */
public class Concat extends VegaLite implements ViewLayoutComposition {
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

    /**
     * Constructor for horizontal concatenation or vertical concatenation.
     * @param layout "hconcat" or "vconcat".
     * @param views multiple views.
     */
    Concat(String layout, VegaLite... views) {
        ArrayNode node = spec.putArray(layout);
        for (VegaLite view : views) {
            node.add(view.spec);
        }
    }

    /**
     * Returns a horizontal concatenation of views.
     * @param views multiple views.
     * @return a horizontal concatenation of views.
     */
    public static Concat horizontal(VegaLite... views) {
        return new Concat("hconcat", views);
    }

    /**
     * Returns a vertical concatenation of views.
     * @param views multiple views.
     * @return a vertical concatenation of views.
     */
    public static Concat vertical(VegaLite... views) {
        return new Concat("vconcat", views);
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
        ViewLayoutComposition.super.resolveScale(channel, resolution);
        return this;
    }

    @Override
    public Concat resolveAxis(String channel, String resolution) {
        ViewLayoutComposition.super.resolveAxis(channel, resolution);
        return this;
    }

    @Override
    public Concat resolveLegend(String channel, String resolution) {
        ViewLayoutComposition.super.resolveLegend(channel, resolution);
        return this;
    }

    @Override
    public Concat align(String strategy) {
        ViewLayoutComposition.super.align(strategy);
        return this;
    }

    @Override
    public Concat align(String row, String column) {
        ViewLayoutComposition.super.align(row, column);
        return this;
    }

    @Override
    public Concat bounds(String bounds) {
        ViewLayoutComposition.super.bounds(bounds);
        return this;
    }

    @Override
    public Concat center(boolean flag) {
        ViewLayoutComposition.super.center(flag);
        return this;
    }

    @Override
    public Concat center(int row, int column) {
        ViewLayoutComposition.super.center(row, column);
        return this;
    }

    @Override
    public Concat spacing(int size) {
        ViewLayoutComposition.super.spacing(size);
        return this;
    }

    @Override
    public Concat spacing(int row, int column) {
        ViewLayoutComposition.super.spacing(row, column);
        return this;
    }
}
