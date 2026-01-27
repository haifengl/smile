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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.importance;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.model.cart.CART;

/**
 * SHAP of ensemble tree methods. TreeSHAP is a fast and exact method to
 * estimate SHAP values for tree models and ensembles of trees, under
 * several possible assumptions about feature dependence.
 *
 * @author Haifeng Li
 */
public interface TreeSHAP extends SHAP<Tuple> {

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

    @Override
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
        return shap(data.stream().parallel());
    }
}
