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

package smile.regression;

/**
 * Regression model with online learning capability. Online learning is a
 * model of induction that learns one instance at a time. More formally,
 * an online algorithm proceeds in a sequence of trials.
 * 
 * @param <T> the type of input object
 * 
 * @author Haifeng Li
 */
public interface OnlineRegression <T> extends Regression <T> {
    /**
     * Updates the model with a (micro-)batch of new samples.
     * @param x the training instances.
     * @param y the target values.
     */
    default void learn(T[] x, double y[]) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Input vector x of size %d not equal to length %d of y", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++){
            update(x[i], y[i]);
        }
    }

    /**
     * Online update the regression model with a new training instance.
     * In general, this method may be NOT multi-thread safe.
     * 
     * @param x training instance.
     * @param y response variable.
     */
    void update(T x, double y);
}
