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
 * Impute missing values with the average of other attributes in the instance.
 * Assume the attributes of the dataset are of same kind, e.g. microarray gene
 * expression data, the missing values can be estimated as the average of
 * non-missing attributes in the same instance. Note that this is not the
 * average of same attribute across different instances.
 * 
 * @author Haifeng Li
 */
public class AverageImputation implements MissingValueImputation {
    /**
     * Constructor.
     */
    public AverageImputation() {

    }

    @Override
    public void impute(double[][] data) throws MissingValueImputationException {
        for (int i = 0; i < data.length; i++) {
            int n = 0;
            double sum = 0.0;

            for (double x : data[i]) {
                if (!Double.isNaN(x)) {
                    n++;
                    sum += x;
                }
            }

            if (n == 0) {
                throw new MissingValueImputationException("The whole row " + i + " is missing");
            }

            if (n < data[i].length) {
                double avg = sum / n;
                for (int j = 0; j < data[i].length; j++) {
                    if (Double.isNaN(data[i][j])) {
                        data[i][j] = avg;
                    }
                }
            }
        }
    }
}
