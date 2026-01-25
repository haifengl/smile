/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import smile.data.vector.ValueVector;
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
    protected final IntSet classes;

    /**
     * Constructor.
     * @param classes the class labels.
     */
    public AbstractClassifier(IntSet classes) {
        this.classes = classes;
    }

    /**
     * Constructor.
     * @param y the sample labels.
     */
    public AbstractClassifier(int[] y) {
        this.classes = ClassLabels.fit(y).classes;
    }

    /**
     * Constructor.
     * @param y the sample labels.
     */
    public AbstractClassifier(ValueVector y) {
        this.classes = ClassLabels.fit(y).classes;
    }

    @Override
    public int numClasses() {
        return classes.size();
    }

    @Override
    public int[] classes() {
        return classes.values;
    }
}
