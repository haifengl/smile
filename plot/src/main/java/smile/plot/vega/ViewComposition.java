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

/**
 * All view composition specifications (layer, facet, concat, and repeat)
 * can have the resolve property for scale, axes, and legend resolution.
 * <p>
 * Vega-Lite determines whether scale domains should be unioned.
 * If the scale domain is unioned, axes and legends can be merged.
 * Otherwise, they have to be independent.
 * <p>
 * There are two options to resolve a scale, axis, or legend: "shared"
 * and "independent". Independent scales imply independent axes and legends.
 *
 * @author Haifeng Li
 */
public class ViewComposition extends VegaLite {
}
