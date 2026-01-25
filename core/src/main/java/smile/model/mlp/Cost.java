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
package smile.model.mlp;

/**
 * Neural network cost function.
 *
 * @author Haifeng Li
 */
public enum Cost {
    /**
     * Mean squares error cost.
     */
    MEAN_SQUARED_ERROR,

    /**
     * Negative likelihood (or log-likelihood) cost.
     * It is equivalently described as the cross-entropy in classification.
     */
    LIKELIHOOD
}