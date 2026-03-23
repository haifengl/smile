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
 * Hyperparameter optimization. Hyperparameter optimization or tuning is
 * the problem of choosing a set of optimal hyperparameters for a learning
 * algorithm. A hyperparameter is a parameter whose value is used to control
 * the learning process. Hyperparameter optimization finds a tuple of
 * hyperparameters that yields an optimal model which minimizes a predefined
 * loss function on given independent data. The objective function takes
 * a tuple of hyperparameters and returns the associated loss. Cross
 * validation is often used to estimate this generalization performance.
 *
 * @author Haifeng Li
 */
package smile.hpo;
