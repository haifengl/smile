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

/**
 * Classifier with online learning capability. Online learning is a model of
 * induction that learns one instance at a time. More formally, an online
 * algorithm proceeds in a sequence of trials.
 * 
 * @param <T> the type of input object
 * 
 * @author Haifeng Li
 */
public interface OnlineClassifier <T> extends Classifier <T> {
    /**
     * Updates the model with a (micro-)batch of new samples.
     * @param x the training instances.
     * @param y the target values.
     */
    default void update(T[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Input vector x of size %d not equal to length %d of y", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++){
            update(x[i], y[i]);
        }
    }

    /**
     * Online update the classifier with a new training instance.
     * In general, this method may be NOT multi-thread safe.
     * 
     * @param x training instance.
     * @param y training label.
     */
    void update(T x, int y);
}
