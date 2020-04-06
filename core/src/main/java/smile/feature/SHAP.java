/*******************************************************************************
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
 ******************************************************************************/

package smile.feature;

import java.util.stream.Stream;

/**
 * SHAP (SHapley Additive exPlanations) is a game theoretic approach to
 * explain the output of any machine learning model. It connects optimal
 * credit allocation with local explanations using the classic Shapley
 * values from game theory.
 *
 * SHAP leverages local methods designed to explain a prediction f(x)
 * based on a single input x. The local methods are defined as any
 * interpretable approximation of the original model. In particular,
 * SHAP employs additive feature attribution methods.
 *
 * SHAP values attribute to each feature the change in the expected
 * model prediction when conditioning on that feature. They explain
 * how to get from the base value <code>E[f(z)]</code> that would be
 * predicted if we did not know any features to the current output
 * <code>f(x)</code>.
 *
 * In game theory, the Shapley value is the average expected marginal
 * contribution of one player after all possible combinations have
 * been considered.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Lundberg, Scott M., and Su-In Lee. A unified approach to interpreting model predictions. NIPS, 2017.</li>
 * <li>Lundberg, Scott M., Gabriel G. Erion, and Su-In Lee. Consistent individualized feature attribution for tree ensembles.</li>
 * </ol>
 * <pre>
 *
 * @author Haifeng Li
 */
public interface SHAP<T> {
    /**
     * Returns the SHAP values.
     *
     * @param x an instance.
     * @return the SHAP values.
     */
    double[] shap(T x);

    /**
     * Returns the average of absolute SHAP values over a data set.
     */
    default double[] shap(Stream<T> data) {
        return smile.math.MathEx.colMeans(
                data.map(x -> {
                    double[] shap = shap(x);
                    for (int i = 0; i < shap.length; i++)
                        shap[i] = Math.abs(shap[i]);
                    return shap;
                }).
                toArray(double[][]::new));
    }
}