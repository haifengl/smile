/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

/**
 * An abstract interface to measure the classification performance.
 *
 * @author Haifeng Li
 */
public interface ClassificationMeasure {

    /**
     * Returns an index to measure the quality of classification.
     * @param truth the true class labels.
     * @param prediction the predicted class labels.
     */
    public double measure(int[] truth, int[] prediction);
}
