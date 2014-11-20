/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

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
