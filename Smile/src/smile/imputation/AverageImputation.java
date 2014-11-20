/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

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
