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

package smile.classification;

import java.io.Serializable;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

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
public interface Classifier<T> extends ToIntFunction<T>, ToDoubleFunction<T>, Serializable {
    /**
     * Predicts the class label of an instance.
     *
     * @param x the instance to be classified.
     * @return the predicted class label.
     */
    int predict(T x);

    /**
     * Returns the real-valued decision function value.
     *
     * @param x the instance to be classified.
     * @return the prediction score.
     */
    default double f(T x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Predicts the class labels of an array of instances.
     *
     * @param x the instances to be classified.
     * @return the predicted class labels.
     */
    default int[] predict(T[] x) {
        int n = x.length;
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            y[i] = predict(x[i]);
        }
        return y;
    }

    @Override
    default int applyAsInt(T x) {
        return predict(x);
    }

    @Override
    default double applyAsDouble(T x) {
        return f(x);
    }
}
