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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Independent Component Analysis (ICA). ICA is a computational method
 * for separating a multivariate signal into additive components. This
 * is done by assuming that at most one subcomponent is a non-Gaussian
 * signals and that the subcomponents are statistically independent of
 * each other. ICA is a special case of blind source separation. A common
 * example application is the "cocktail party problem" of listening in
 * on one person's speech in a noisy room.
 * <p>
 * FastICA is an efficient algorithm for ICA invented by Aapo Hyvärinen.
 * Using maximum entropy approximations of differential entropy, FastICA
 * introduce a family of new contrast (objective) functions for ICA.
 * These contrast functions enable both the estimation of the whole
 * decomposition by minimizing mutual information, and estimation of
 * individual independent components as projection pursuit directions.
 * <p>
 * The contrast functions must be a non-quadratic non-linear function
 * that has second-order derivative.
 *
 * @author Haifeng Li
 */
package smile.ica;