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
 * all view layout composition (facet, concat, and repeat) can have the
 * following layout properties: align, bounds, center, spacing.
 *
 * @author Haifeng Li
 */
public class ViewLayoutComposition extends ViewComposition {
    public ViewLayoutComposition(String layout, VegaLite... views) {
        ArrayNode array = mapper.createArrayNode();
        for (VegaLite view : views) {
            array.add(view.spec);
        }
        spec.set(layout, array);
    }

    public ViewLayoutComposition(VegaLite view) {
        spec.set("spec", view.spec);
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param fields The fields that should be used for each entry.
     */
    public ViewLayoutComposition repeat(String... fields) {
        ArrayNode layer = mapper.createArrayNode();
        for (String field : fields) {
            layer.add(field);
        }
        ObjectNode repeat = mapper.createObjectNode();
        repeat.set("layer", layer);
        spec.set("repeat", repeat);
        return this;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param row    An array of fields to be repeated vertically.
     * @param column An array of fields to be repeated horizontally.
     */
    public ViewLayoutComposition repeat(String[] row, String[] column) {
        ObjectNode repeat = mapper.createObjectNode();
        ArrayNode rows = mapper.createArrayNode();
        ArrayNode columns = mapper.createArrayNode();
        for (String field : row) {
            rows.add(field);
        }
        for (String field : column) {
            columns.add(field);
        }
        repeat.set("row", rows);
        repeat.set("column", columns);
        spec.set("repeat", repeat);
        return this;
    }
}
