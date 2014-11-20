/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.feature;

import smile.data.Attribute;

/**
 * Sequence feature generator.
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public interface SequenceFeature <T> {
    /**
     * Returns the variable attributes of generated features.
     * @return the variable attributes of generated features
     */
    public Attribute[] attributes();
    
    /**
     * Generates the feature set of sequence at given index.
     * @param sequence a sequence of interest.
     * @param index the index of feature to be generated.
     * @return a feature value.
     */
    public double[] f(T[] sequence, int index);    
}
