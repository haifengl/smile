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

    /**
     * Sets the default spacing in pixels between composed sub-views.
     * @param spacing The default spacing in pixels between composed sub-views.
     * @return this object.
     */
    public Concat spacing(int spacing) {
        spec.put("spacing", spacing);
        return this;
    }
}
