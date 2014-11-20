/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.feature;

import smile.data.Attribute;

/**
 * Feature generator.
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public interface Feature <T> {
    /**
     * Returns the variable attributes of generated features.
     * Note that these are NOT the original variable attributes of objects.
     * @return the variable attributes of generated features
     */
    public Attribute[] attributes();
    
    /**
     * Generates a feature for given object.
     * @param object an object of interest.
     * @param id the index of feature to be generated.
     * @return a feature value.
     */
    public double f(T object, int id);
}
