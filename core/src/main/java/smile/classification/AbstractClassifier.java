/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.classification;

import smile.data.measure.NominalScale;
import smile.data.vector.BaseVector;
import smile.util.IntSet;

/**
 * Abstract base class of classifiers.
 *
 * @param <T> the type of input object
 *
 * @author Haifeng Li
 */
public abstract class AbstractClassifier<T> implements Classifier<T> {
    /**
     * The class labels.
     */
    protected final IntSet labels;

    /**
     * Constructor.
     * @param labels the class labels.
     */
    public AbstractClassifier(IntSet labels) {
        this.labels = labels;
    }

    /**
     * Constructor.
     * @param y the sample labels.
     */
    public AbstractClassifier(int[] y) {
        this.labels = ClassLabels.fit(y).labels;
    }

    /**
     * Constructor.
     * @param y the sample labels.
     */
    public AbstractClassifier(BaseVector y) {
        this.labels = ClassLabels.fit(y).labels;
    }

    @Override
    public int numClasses() {
        return labels.size();
    }

    @Override
    public int[] labels() {
        return labels.values;
    }

    @Override
    public NominalScale scale() {
        String[] values = new String[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            values[i] = String.valueOf(labels.valueOf(i));
        }
        return new NominalScale(values);
    }
}
