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
public class Facet extends ViewLayoutComposition {
    /** Definition for how to facet the data. */
    final ObjectNode facet = spec.putObject("facet");

    /**
     * Constructor.
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
}
