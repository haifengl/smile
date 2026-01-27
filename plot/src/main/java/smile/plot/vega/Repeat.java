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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.vega;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Repeat a View. It provides a shortcut that creates a view for each
 * entry in an array of fields. This operator generates multiple plots
 * like facet. However, unlike facet it allows full replication of a
 * data set in each view.
 *
 * @author Haifeng Li
 */
public class Repeat extends VegaLite implements ViewLayoutComposition {
    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param view the view specification.
     * @param fields The fields that should be used for each entry.
     */
    public Repeat(VegaLite view, String... fields) {
        spec.set("spec", view.spec);
        ObjectNode repeat = spec.putObject("repeat");
        ArrayNode layer = repeat.putArray("layer");
        for (String field : fields) {
            layer.add(field);
        }
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param view the view specification.
     * @param row    An array of fields to be repeated vertically.
     * @param column An array of fields to be repeated horizontally.
     */
    public Repeat(VegaLite view, String[] row, String[] column) {
        spec.set("spec", view.spec);
        ObjectNode repeat = spec.putObject("repeat");

        ArrayNode rows = repeat.putArray("row");
        for (String field : row) {
            rows.add(field);
        }

        ArrayNode columns = repeat.putArray("column");
        for (String field : column) {
            columns.add(field);
        }
    }

    /**
     * Sets the number of columns to include in the view composition layout.
     * @param columns The number of columns to include in the view composition layout.
     * @return this object.
     */
    public Repeat columns(int columns) {
        spec.put("columns", columns);
        return this;
    }

    @Override
    public Repeat usermeta(JsonNode metadata) {
        super.usermeta(metadata);
        return this;
    }

    @Override
    public Repeat usermeta(Object metadata) {
        super.usermeta(metadata);
        return this;
    }

    @Override
    public Repeat background(String color) {
        super.background(color);
        return this;
    }

    @Override
    public Repeat padding(int size) {
        super.padding(size);
        return this;
    }

    @Override
    public Repeat padding(int left, int top, int right, int bottom) {
        super.padding(left, top, right, bottom);
        return this;
    }

    @Override
    public Repeat autosize() {
        super.autosize();
        return this;
    }

    @Override
    public Repeat autosize(String type, boolean resize, String contains) {
        super.autosize(type, resize, contains);
        return this;
    }

    @Override
    public Repeat name(String name) {
        super.name(name);
        return this;
    }

    @Override
    public Repeat description(String description) {
        super.description(description);
        return this;
    }

    @Override
    public Repeat title(String title) {
        super.title(title);
        return this;
    }

    @Override
    public Repeat resolveScale(String channel, String resolution) {
        ViewLayoutComposition.super.resolveScale(channel, resolution);
        return this;
    }

    @Override
    public Repeat resolveAxis(String channel, String resolution) {
        ViewLayoutComposition.super.resolveAxis(channel, resolution);
        return this;
    }

    @Override
    public Repeat resolveLegend(String channel, String resolution) {
        ViewLayoutComposition.super.resolveLegend(channel, resolution);
        return this;
    }

    @Override
    public Repeat align(String strategy) {
        ViewLayoutComposition.super.align(strategy);
        return this;
    }

    @Override
    public Repeat align(String row, String column) {
        ViewLayoutComposition.super.align(row, column);
        return this;
    }

    @Override
    public Repeat bounds(String bounds) {
        ViewLayoutComposition.super.bounds(bounds);
        return this;
    }

    @Override
    public Repeat center(boolean flag) {
        ViewLayoutComposition.super.center(flag);
        return this;
    }

    @Override
    public Repeat center(int row, int column) {
        ViewLayoutComposition.super.center(row, column);
        return this;
    }

    @Override
    public Repeat spacing(int size) {
        ViewLayoutComposition.super.spacing(size);
        return this;
    }

    @Override
    public Repeat spacing(int row, int column) {
        ViewLayoutComposition.super.spacing(row, column);
        return this;
    }
}
