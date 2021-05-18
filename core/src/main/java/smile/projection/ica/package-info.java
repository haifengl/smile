/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * The contrast functions in FastICA. Using maximum entropy approximations of
 * differential entropy, FastICA introduce a family of new contrast (objective)
 * functions for ICA. These contrast functions enable both the estimation of
 * the whole decomposition by minimizing mutual information, and estimation
 * of individual independent components as projection pursuit directions.
 * <p>
 * The contrast functions must be a non-quadratic non-linear function
 * that has second-order derivative.
 *
 * @author Haifeng Li
 */
package smile.projection.ica;