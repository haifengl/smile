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

package smile.feature;

import smile.base.cart.CART;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;

/**
 * SHAP of ensemble tree methods. TreeSHAP is a fast and exact method to
 * estimate SHAP values for tree models and ensembles of trees, under
 * several different possible assumptions about feature dependence.
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

        double[] phi = null;
        for (CART tree : forest) {
            double[] phii = tree.shap(xt);

            if (phi == null) {
              phi = phii;
            } else {
                for (int i = 0; i < phi.length; i++) {
                    phi[i] += phii[i];
                }
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
