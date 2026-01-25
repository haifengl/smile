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

/**
 * A sort field definition for sorting data objects within a window.
 *
 * @param op The window or aggregation operation to apply within a window
 *          (e.g., "rank", "lead", "sum", "average" or "count").
 * @param as The output name for the window operation.
 * @param field The data field for which to compute the aggregate or window
 *             function. This can be omitted for window functions that do not
 *             operate over a field such as "count", "rank", "dense_rank".
 * @param param Parameter values for the window functions. Parameter values
 *             can be omitted for operations that do not accept a parameter.
 * @author Haifeng Li
 */
public record WindowTransformField(String op, String field, double param, String as) {

}
