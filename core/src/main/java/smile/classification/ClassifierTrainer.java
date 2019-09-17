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
 * Abstract classifier trainer.
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public abstract class ClassifierTrainer <T> {

    /**
     * Constructor.
     */
    public ClassifierTrainer() {
        
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
