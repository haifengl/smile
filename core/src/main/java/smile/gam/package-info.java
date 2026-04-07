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
 * Generalized Additive Models (GAMs). GAMs are flexible statistical models
 * that extend Generalized Linear Models (GLMs) by allowing the linear
 * predictor to depend on smooth, non-linear functions of predictors
 * rather than just linear coefficients. They balance flexibility with
 * interpretability, using smooth functions (e.g., splines) to capture
 * complex, nonlinear relationships while maintaining an additive structure.
 *
 * @author Haifeng Li
 */
package smile.gam;