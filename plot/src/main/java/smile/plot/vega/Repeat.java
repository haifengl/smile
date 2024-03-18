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
 * Repeat a View. It provides a shortcut that creates a view for each
 * entry in an array of fields. This operator generates multiple plots
 * like facet. However, unlike facet it allows full replication of a
 * data set in each view.
 *
 * @author Haifeng Li
 */
public class Repeat extends ViewLayoutComposition {
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

    /**
     * Sets the default spacing in pixels between composed sub-views.
     * @param spacing The default spacing in pixels between composed sub-views.
     * @return this object.
     */
    public Repeat spacing(int spacing) {
        spec.put("spacing", spacing);
        return this;
    }
}
