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

import smile.data.Tuple;
import smile.regression.RegressionTree;

/**
 * SHAP of ensemble tree methods.
 *
 * @author Haifeng Li
 */
public interface TreeSHAP extends SHAP<Tuple> {

    /**
     * Returns the regression trees.
     */
    RegressionTree[] trees();

    @Override
    default double[] shap(Tuple x) {
        RegressionTree[] forest = trees();

        double[] phi = null;
        for (RegressionTree tree : forest) {
            double[] phii = tree.shap(x);

            if (phi == null) phi = phii;
            else {
                for (int i = 0; i < phi.length; i++)
                    phi[i] += phii[i];
            }
        }

        for (int i = 0; i < phi.length; i++) {
            phi[i] /= forest.length;
        }

        return phi;
    }
}