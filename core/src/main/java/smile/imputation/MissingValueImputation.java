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
 * Interface to impute missing values in the data.
 *
 * @author Haifeng
 */
public interface MissingValueImputation {
    /**
     * Impute missing values in the data.
     * @param data a data set with missing values (represented as Double.NaN).
     * On output, missing values are filled with estimated values.
     */
    void impute(double[][] data) throws MissingValueImputationException;

    /**
     * Impute the missing values with column averages.
     * @param data data with missing values.
     */
    static void imputeWithColumnAverage(double[][] data) {
        for (int j = 0; j < data[0].length; j++) {
            int n = 0;
            double sum = 0.0;

            for (int i = 0; i < data.length; i++) {
                if (!Double.isNaN(data[i][j])) {
                    n++;
                    sum += data[i][j];
                }
            }

            if (n == 0) {
                continue;
            }

            if (n < data.length) {
                double avg = sum / n;
                for (int i = 0; i < data.length; i++) {
                    if (Double.isNaN(data[i][j])) {
                        data[i][j] = avg;
                    }
                }
            }
        }
    }
}
