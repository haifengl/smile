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
 * An interface to calculate node output. Note that samples[i] is the
 * number of sampling of dataset[i]. 0 means that the datum is not
 * included and values of greater than 1 are possible because of
 * sampling with replacement.
 */
public interface RegressionNodeOutput {
    /**
     * Calculate the node output.
     *
     * @param lo the inclusive lower bound of the data partition in the reordered sample index array.
     * @param hi the exclusive upper bound of the data partition in the reordered sample index array.
     * @param nodeSamples the index of node samples to their original locations in training dataset.
     * @param sampleCount samples[i] is the number of sampling of dataset[i]. 0 means that the
     *               datum is not included and values of greater than 1 are
     *               possible because of sampling with replacement.
     * @return the node output
     */
    double calculate(int[] nodeSamples, int[] sampleCount);
}