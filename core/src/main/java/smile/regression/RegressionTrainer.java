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
