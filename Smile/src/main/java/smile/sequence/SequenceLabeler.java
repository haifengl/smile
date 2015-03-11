/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.sequence;

/**
 * A sequence labeler assigns a class label to each position of the sequence.
 *
 * @author Haifeng Li
 */
public interface SequenceLabeler<T> {
    /**
     * Predicts the sequence labels.
     * @param x a sequence. At each position, it may be the original symbol or
     * a feature set about the symbol, its neighborhood, and/or other information.
     * @return the predicted sequence labels.
     */
    public int[] predict(T[] x);
}
