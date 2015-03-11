/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.classification;

import smile.data.Attribute;

/**
 * Abstract classifier trainer.
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public abstract class ClassifierTrainer <T> {
    /**
     * The feature attributes. This is optional since most classifiers can only
     * work on real-valued attributes.
     */
    Attribute[] attributes;
    
    /**
     * Constructor.
     */
    public ClassifierTrainer() {
        
    }
    
    /**
     * Constructor.
     * @param attributes the attributes of independent variable.
     */
    public ClassifierTrainer(Attribute[] attributes) {
        this.attributes = attributes;
    }
    
    /**
     * Sets feature attributes. This is optional since most classifiers can only
     * work on real-valued attributes.
     * 
     * @param attributes the feature attributes.
     */
    public void setAttributes(Attribute[] attributes) {
        this.attributes = attributes;
    }
    
    /**
     * Learns a classifier with given training data.
     * 
     * @param x the training instances.
     * @param y the training labels.
     * @return a trained classifier.
     */
    public abstract Classifier<T> train(T[] x, int[] y);
}
