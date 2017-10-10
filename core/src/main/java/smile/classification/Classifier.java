/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.classification;

/**
 * A classifier assigns an input object into one of a given number of categories.
 * The input object is formally termed an instance, and the categories are
 * termed classes. The instance is usually described by a vector of features,
 * which together constitute a description of all known characteristics of the
 * instance.
 * <p>
 * Classification normally refers to a supervised procedure, i.e. a procedure
 * that produces an inferred function to predict the output value of new
 * instances based on a training set of pairs consisting of an input object
 * and a desired output value. The inferred function is called a classifier
 * if the output is discrete or a regression function if the output is
 * continuous.
 * 
 * @param <T> the type of input object
 * 
 * @author Haifeng Li
 */
public interface Classifier<T> {
    /**
     * Predicts the class label of an instance.
     * 
     * @param x the instance to be classified.
     * @return the predicted class label.
     */
    public int predict(T x);

    /**
     * Predicts the class labels of an array of instances.
     *
     * @param x the instances to be classified.
     * @return the predicted class labels.
     */
    default public int[] predict(T[] x) {
        int[] y = new int[x.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = predict(x[i]);
        }
        return y;
    }
}
