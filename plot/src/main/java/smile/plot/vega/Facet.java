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
public class Facet extends VegaLite {
}
