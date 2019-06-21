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

package smile.imputation;

/**
 * Interface to impute missing values in the dataset.
 *
 * @author Haifeng
 */
public interface MissingValueImputation {
    /**
     * Impute missing values in the dataset.
     * @param data a data set with missing values (represented as Double.NaN).
     * On output, missing values are filled with estimated values.
     */
    public void impute(double[][] data) throws MissingValueImputationException;
}
