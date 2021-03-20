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

import java.util.Arrays;
import smile.math.MathEx;

/**
 * Ensemble methods use multiple learning algorithms to obtain better
 * predictive performance than could be obtained from any of the constituent
 * learning algorithms alone.
 *
 * @author Haifeng Li
 */
public interface Ensemble {
    /**
     * Returns an ensemble model.
     * @param models the base models.
     * @param <T> the type of input object
     * @return the ensemble model.
     */
    static <T> Classifier<T> of(Classifier<T>... models) {
        return new Classifier<T>() {
            @Override
            public int predict(T x) {
                int[] labels = new int[models.length];
                for (int i = 0; i < models.length; i++) {
                    labels[i] = models[i].predict(x);
                }
                return MathEx.mode(labels);
            }
        };
    }

    /**
     * Returns an ensemble model.
     * @param models the base models.
     * @param <T> the type of input object
     * @return the ensemble model.
     */
    static <T> SoftClassifier<T> of(SoftClassifier<T>... models) {
        return new SoftClassifier<T>() {
            @Override
            public int predict(T x) {
                int[] labels = new int[models.length];
                for (int i = 0; i < models.length; i++) {
                    labels[i] = models[i].predict(x);
                }
                return MathEx.mode(labels);
            }

            @Override
            public int predict(T x, double[] posteriori) {
                double[] p = new double[posteriori.length];
                Arrays.fill(posteriori, 0.0);
                for (SoftClassifier model : models) {
                    model.predict(x, p);
                    for (int i = 0; i < p.length; i++) {
                        posteriori[i] += p[i];
                    }
                }

                for (int i = 0; i < posteriori.length; i++) {
                    posteriori[i] /= models.length;
                }
                return MathEx.whichMax(posteriori);
            }
        };
    }
}
