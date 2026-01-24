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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A facet is a trellis plot (or small multiple) of a series of similar
 * plots that displays different subsets of the same data, facilitating
 * comparison across subsets.
 * <p>
 * The facet channels (facet, row, and column) are encoding channels that
 * serves as macros for a facet specification. Vega-Lite automatically
 * translates this shortcut to use the facet operator.
 *
 * @author Haifeng Li
 */
public class Facet extends VegaLite implements ViewLayoutComposition {
    /** Definition for how to facet the data. */
    final ObjectNode facet = spec.putObject("facet");

    /**
     * Constructor.
     * @param view the view spec.
     */
    public Facet(VegaLite view) {
        spec.set("spec", view.spec);
    }

    /**
     * Sets the number of columns to include in the view composition layout.
     * @param columns The number of columns to include in the view composition layout.
     * @return this object.
     */
    public Facet columns(int columns) {
        spec.put("columns", columns);
        return this;
    }

    /**
     * Returns the field definition for faceting the plot by one field.
     * @param field A string defining the name of the field from which to pull
     *             a data value. Dots (.) and brackets ([ and ]) can be used to
     *             access nested objects (e.g., "field": "foo.bar" and
     *             "field": "foo['bar']"). If field names contain dots or
     *             brackets but are not nested, you can use \\ to escape dots
     *             and brackets (e.g., "a\\.b" and "a\\[0\\]").
     * @return the facet field object.
     */
    public FacetField facet(String field) {
        facet.put("field", field);
        return new FacetField(facet);
    }

    /**
     * Returns the field definition for the horizontal facet of trellis plots.
     * @param field A string defining the name of the field from which to pull
     *             a data value. Dots (.) and brackets ([ and ]) can be used to
     *             access nested objects (e.g., "field": "foo.bar" and
     *             "field": "foo['bar']"). If field names contain dots or
     *             brackets but are not nested, you can use \\ to escape dots
     *             and brackets (e.g., "a\\.b" and "a\\[0\\]").
     * @return the facet field object.
     */
    public FacetField row(String field) {
        ObjectNode node = facet.putObject("row");
        node.put("field", field);
        return new FacetField(node);
    }

    /**
     * Returns the field definition for the vertical facet of trellis plots.
     * @param field A string defining the name of the field from which to pull
     *             a data value. Dots (.) and brackets ([ and ]) can be used to
     *             access nested objects (e.g., "field": "foo.bar" and
     *             "field": "foo['bar']"). If field names contain dots or
     *             brackets but are not nested, you can use \\ to escape dots
     *             and brackets (e.g., "a\\.b" and "a\\[0\\]").
     * @return the facet field object.
     */
    public FacetField column(String field) {
        ObjectNode node = facet.putObject("column");
        node.put("field", field);
        return new FacetField(node);
    }

    @Override
    public Facet usermeta(JsonNode metadata) {
        super.usermeta(metadata);
        return this;
    }

    @Override
    public Facet usermeta(Object metadata) {
        super.usermeta(metadata);
        return this;
    }

    @Override
    public Facet background(String color) {
        super.background(color);
        return this;
    }

    @Override
    public Facet padding(int size) {
        super.padding(size);
        return this;
    }

    @Override
    public Facet padding(int left, int top, int right, int bottom) {
        super.padding(left, top, right, bottom);
        return this;
    }

    @Override
    public Facet autosize() {
        super.autosize();
        return this;
    }

    @Override
    public Facet autosize(String type, boolean resize, String contains) {
        super.autosize(type, resize, contains);
        return this;
    }

    @Override
    public Facet name(String name) {
        super.name(name);
        return this;
    }

    @Override
    public Facet description(String description) {
        super.description(description);
        return this;
    }

    @Override
    public Facet title(String title) {
        super.title(title);
        return this;
    }

    @Override
    public Facet resolveScale(String channel, String resolution) {
        ViewLayoutComposition.super.resolveScale(channel, resolution);
        return this;
    }

    @Override
    public Facet resolveAxis(String channel, String resolution) {
        ViewLayoutComposition.super.resolveAxis(channel, resolution);
        return this;
    }

    @Override
    public Facet resolveLegend(String channel, String resolution) {
        ViewLayoutComposition.super.resolveLegend(channel, resolution);
        return this;
    }

    @Override
    public Facet align(String strategy) {
        ViewLayoutComposition.super.align(strategy);
        return this;
    }

    @Override
    public Facet align(String row, String column) {
        ViewLayoutComposition.super.align(row, column);
        return this;
    }

    @Override
    public Facet bounds(String bounds) {
        ViewLayoutComposition.super.bounds(bounds);
        return this;
    }

    @Override
    public Facet center(boolean flag) {
        ViewLayoutComposition.super.center(flag);
        return this;
    }

    @Override
    public Facet center(int row, int column) {
        ViewLayoutComposition.super.center(row, column);
        return this;
    }

    @Override
    public Facet spacing(int size) {
        ViewLayoutComposition.super.spacing(size);
        return this;
    }

    @Override
    public Facet spacing(int row, int column) {
        ViewLayoutComposition.super.spacing(row, column);
        return this;
    }
}
