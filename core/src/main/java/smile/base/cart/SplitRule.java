/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.base.cart;

/**
 * The criterion to choose variable to split instances.
 */
public enum SplitRule {
    /**
     * Used by the CART algorithm, Gini impurity is a measure of how often
     * a randomly chosen element from the set would be incorrectly labeled
     * if it were randomly labeled according to the distribution of labels
     * in the subset. Gini impurity can be computed by summing the
     * probability of each item being chosen times the probability
     * of a mistake in categorizing that item. It reaches its minimum
     * (zero) when all cases in the node fall into a single target category.
     */
    GINI,

    /**
     * Used by the ID3, C4.5 and C5.0 tree generation algorithms.
     */
    ENTROPY,

    /**
     * Classification error.
     */
    CLASSIFICATION_ERROR
}
