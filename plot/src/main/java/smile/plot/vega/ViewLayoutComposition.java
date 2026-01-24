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

import tools.jackson.databind.node.ObjectNode;

/**
 * All view layout composition (facet, concat, and repeat) can have the
 * following layout properties: align, bounds, center, spacing.
 *
 * @author Haifeng Li
 */
public interface ViewLayoutComposition extends ViewComposition {
    /**
     * Sets the alignment to apply to grid rows and columns. The supported string
     * values are "all" (the default), "each", and "none".
     * <p>
     * For "none", a flow layout will be used, in which adjacent subviews
     * are simply placed one after the other.
     * <p>
     * For "each", subviews will be aligned into a clean grid structure,
     * but each row or column may be of variable size.
     * <p>
     * For "all", subviews will be aligned and each row or column will be
     * sized identically based on the maximum observed size. String values
     * for this property will be applied to both grid rows and columns.
     * @param alignment "all", "each", or "none".
     * @return this object.
     */
    default ViewLayoutComposition align(String alignment) {
        spec().put("align", alignment);
        return this;
    }

    /**
     * Sets different alignments for rows and columns.
     * @param row alignment for rows.
     * @param column alignment for columns.
     * @return this object.
     */
    default ViewLayoutComposition align(String row, String column) {
        ObjectNode node = spec().putObject("align");
        node.put("row", row);
        node.put("column", column);
        return this;
    }

    /**
     * Sets the bounds calculation method to use for determining the extent
     * of a sub-plot. One of "full" (the default) or "flush".
     * <p>
     * If set to "full", the entire calculated bounds (including axes,
     * title, and legend) will be used.
     * <p>
     * If set to "flush", only the specified width and height values
     * for the sub-view will be used. The flush setting can be useful
     * when attempting to place sub-plots without axes or legends into
     * a uniform grid structure.
     * @param method "full" or "flush".
     * @return this object.
     */
    default ViewLayoutComposition bounds(String method) {
        spec().put("bounds", method);
        return this;
    }

    /**
     * Sets if subviews should be centered relative to their respective
     * rows or columns.
     * @param flag a flag indicating if subviews should be centered relative
     *            to their respective rows or columns.
     * @return this object.
     */
    default ViewLayoutComposition center(boolean flag) {
        spec().put("center", flag);
        return this;
    }

    /**
     * Sets if subviews should be centered relative to their respective
     * rows or columns.
     * @param row a flag indicating if subviews should be centered relative
     *           to their respective rows.
     * @param column a flag indicating if subviews should be centered relative
     *              to their respective columns.
     * @return this object.
     */
    default ViewLayoutComposition center(int row, int column) {
        ObjectNode node = spec().putObject("center");
        node.put("row", row);
        node.put("column", column);
        return this;
    }

    /**
     * Sets the spacing in pixels between sub-views of the composition operator.
     * @param size the spacing between sub-views.
     * @return this object.
     */
    default ViewLayoutComposition spacing(int size) {
        spec().put("spacing", size);
        return this;
    }

    /**
     * Sets different spacing values for rows and columns.
     * @param row the spacing between sub-views.
     * @param column the spacing between sub-views.
     * @return this object.
     */
    default ViewLayoutComposition spacing(int row, int column) {
        ObjectNode node = spec().putObject("spacing");
        node.put("row", row);
        node.put("column", column);
        return this;
    }
}
