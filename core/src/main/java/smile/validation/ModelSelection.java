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

package smile.validation;

/**
 * Model selection criteria. Model selection is the task of selecting
 * a statistical model from a set of candidate models, given data.
 * In the simplest cases, a pre-existing set of data is considered.
 * Given candidate models of similar predictive or explanatory power,
 * the simplest model is most likely to be the best choice (Occam's razor).
 * <p>
 * A good model selection technique will balance goodness of fit with
 * simplicity. More complex models will be better able to adapt their
 * shape to fit the data, but the additional parameters may not represent
 * anything useful. Goodness of fit is generally determined using
 * a likelihood ratio approach, or an approximation of this, leading
 * to a chi-squared test. The complexity is generally measured by
 * counting the number of parameters in the model.
 * <p>
 * The most commonly used criteria are the Akaike information criterion
 * and the Bayesian information criterion. The formula for BIC is similar
 * to the formula for AIC, but with a different penalty for the number of
 * parameters. With AIC the penalty is <code>2k</code>, whereas with BIC
 * the penalty is <code>log(n) * k</code>.
 * <p>
 * AIC and BIC are both approximately correct according to a different goal
 * and a different set of asymptotic assumptions. Both sets of assumptions
 * have been criticized as unrealistic.
 * <p>
 * AIC is better in situations when a false negative finding would be
 * considered more misleading than a false positive, and BIC is better
 * in situations where a false positive is as misleading as, or more
 * misleading than, a false negative.
 *
 * @author Haifeng Li
 */
public interface ModelSelection {
    /**
     * Akaike information criterion.
     *
     * AIC = 2 * k - 2 * log(L), where L is the likelihood of estimated model
     * and n is the number of samples.
     *
     * @param logL the log-likelihood of estimated model.
     * @param k the number of free parameters to be estimated in the model.
     * @return the AIC score.
     */
    static double AIC(double logL, int k) {
        return 2 * (k - logL);
    }

    /**
     * Bayesian information criterion.
     *
     * BIC = k * log(n) - 2 * log(L), where L is the likelihood of estimated model,
     * k is the number of free parameters to be estimated in the model,
     * and n is the number of samples.
     *
     * @param logL the log-likelihood of estimated model.
     * @param k the number of free parameters to be estimated in the model.
     * @param n the number of samples.
     * @return the BIC score.
     */
    static double BIC(double logL, int k, int n) {
        return k * Math.log(n) - 2 * logL;
    }
}
