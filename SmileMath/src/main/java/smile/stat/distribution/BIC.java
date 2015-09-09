/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.stat.distribution;

/**
 * Bayesian information criterion (BIC) or Schwarz Criterion is a criterion for
 * model selection among a class of parametric models with different numbers
 * of parameters. Choosing a model to optimize BIC is a form of regularization.
 * <p>
 * When estimating model parameters using maximum likelihood estimation, it
 * is possible to increase the likelihood by adding additional parameters,
 * which may result in over-fitting. The BIC resolves this problem by
 * introducing a penalty term for the number of parameters in the model.
 * BIC is very closely related to the Akaike information criterion (AIC).
 * However, its penalty for additional parameters is stronger than that of AIC.
 * <p>
 * The formula for the BIC is BIC = L - 0.5 * v * log n where L is the
 * log-likelihood of estimated model, v is the number of free parameters
 * to be estimated in the model, and n is the number of samples.
 * <p>
 * Given any two estimated models, the model with the larger value of BIC is
 * the one to be preferred.
 * 
 * @author Haifeng Li
 */
public class BIC {

    /**
     * Returns the BIC score of an estimated model.
     * @param L the log-likelihood of estimated model.
     * @param v the number of free parameters to be estimated in the model.
     * @param n the number of samples.
     * @return BIC score.
     */
    public static double bic(double L, int v, int n) {
        return L - 0.5 * v * Math.log(n);
    }
}
