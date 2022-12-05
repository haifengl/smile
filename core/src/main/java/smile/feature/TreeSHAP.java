/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.feature;

import smile.base.cart.CART;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;

/**
 * SHAP of ensemble tree methods. TreeSHAP is a fast and exact method to
 * estimate SHAP values for tree models and ensembles of trees, under
 * several possible assumptions about feature dependence.
 * <p>
 * SHAP (SHapley Additive exPlanations) is a game theoretic approach to
 * explain the output of any machine learning model. It connects optimal
 * credit allocation with local explanations using the classic Shapley
 * values from game theory.
 * <p>
 * SHAP leverages local methods designed to explain a prediction
 * <code>f(x)</code> based on a single input <code>x</code>.
 * The local methods are defined as any interpretable approximation
 * of the original model. In particular, SHAP employs additive feature
 * attribution methods.
 * <p>
 * SHAP values attribute to each feature the change in the expected
 * model prediction when conditioning on that feature. They explain
 * how to get from the base value <code>E[f(z)]</code> that would be
 * predicted if we did not know any features to the current output
 * <code>f(x)</code>.
 * <p>
 * In game theory, the Shapley value is the average expected marginal
 * contribution of one player after all possible combinations have
 * been considered.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Lundberg, Scott M., and Su-In Lee. A unified approach to interpreting model predictions. NIPS, 2017.</li>
 * <li>Lundberg, Scott M., Gabriel G. Erion, and Su-In Lee. Consistent individualized feature attribution for tree ensembles.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public interface TreeSHAP {

    /**
     * Returns the decision trees.
     * @return the decision trees.
     */
    CART[] trees();

    /**
     * Returns the formula associated with the model.
     * @return the model formula.
     */
    Formula formula();

    /**
     * Returns the SHAP values. For regression, the length of SHAP values
     * is same as the number of features. For classification, SHAP values
     * are of <code>p x k</code>, where <code>p</code> is the number of
     * features and <code>k</code> is the classes. The first k elements are
     * the SHAP values of first feature over k classes, respectively. The
     * rest features follow accordingly.
     *
     * @param x an instance.
     * @return the SHAP values.
     */
    default double[] shap(Tuple x) {
        CART[] forest = trees();
        Tuple xt = formula().x(x);

        double[] phi = forest[0].shap(xt);
        for (int k = 1; k < forest.length; k++) {
            double[] phii = forest[k].shap(xt);
            for (int i = 0; i < phi.length; i++) {
                phi[i] += phii[i];
            }
        }

        for (int i = 0; i < phi.length; i++) {
            phi[i] /= forest.length;
        }

        return phi;
    }

    /**
     * Returns the average of absolute SHAP values over a data frame.
     * @param data the data.
     * @return the average of absolute SHAP values.
     */
    default double[] shap(DataFrame data) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula().bind(data.schema());
        return smile.math.MathEx.colMeans(
                data.stream().parallel().map(x -> {
                    double[] values = shap(x);
                    for (int i = 0; i < values.length; i++)
                        values[i] = Math.abs(values[i]);
                    return values;
                }).toArray(double[][]::new)
        );
    }
}
