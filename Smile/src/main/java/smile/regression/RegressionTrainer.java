/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.regression;

import smile.data.Attribute;

/**
 * Abstract regression model trainer.
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public abstract class RegressionTrainer <T> {
    /**
     * The feature attributes. This is optional since most classifiers can only
     * work on real-valued attributes.
     */
    Attribute[] attributes;
    
    /**
     * Constructor.
     */
    public RegressionTrainer() {
        
    }
    
    /**
     * Constructor.
     * @param attributes the attributes of independent variable.
     */
    public RegressionTrainer(Attribute[] attributes) {
        this.attributes = attributes;
    }
    
    /**
     * Sets feature attributes. This is optional since most regression models
     * can only work on real-valued attributes.
     * 
     * @param attributes the feature attributes.
     */
    public void setAttributes(Attribute[] attributes) {
        this.attributes = attributes;
    }
    
    /**
     * Learns a regression model with given training data.
     * 
     * @param x the training instances.
     * @param y the training response values.
     * @return a trained regression model.
     */
    public abstract Regression<T> train(T[] x, double[] y);
}
