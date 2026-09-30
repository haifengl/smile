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
package smile.hpo

import java.util.Properties
import java.util.function.ToDoubleFunction
import smile.math.kernel.MaternKernel
import smile.math.kernel.MercerKernel

/**
 * Creates and configures a new hyperparameter search space using builder DSL.
 *
 * @param init initialization block.
 * @return configured Hyperparameters instance.
 */
fun hyperparameters(init: Hyperparameters.() -> Unit): Hyperparameters {
    val hp = Hyperparameters()
    hp.init()
    return hp
}

/**
 * Executes Bayesian hyperparameter optimization.
 *
 * @param maxTrials the total number of evaluations to perform.
 * @param initialTrials the number of initial random trials (-1 for heuristic default).
 * @param maximize true to maximize the objective, false to minimize.
 * @param acquisition the acquisition function strategy.
 * @param exploration exploration parameter (&xi; for EI/PI, &kappa; for UCB).
 * @param kernel the Mercer covariance kernel for GP surrogate modeling.
 * @param objective the objective function evaluating a configuration.
 * @return the optimization result.
 */
fun Hyperparameters.bayes(
    maxTrials: Int = 50,
    initialTrials: Int = -1,
    maximize: Boolean = true,
    acquisition: BayesianOptimization.Acquisition = BayesianOptimization.Acquisition.EXPECTED_IMPROVEMENT,
    exploration: Double = 0.01,
    kernel: MercerKernel<DoubleArray> = MaternKernel(1.0, 2.5),
    objective: (Properties) -> Double
): BayesianOptimization.Result {
    val options = BayesianOptimization.Options(maxTrials, initialTrials, maximize, acquisition, exploration, kernel)
    return bayes(ToDoubleFunction { objective(it) }, options)
}

/** Returns all grid search configurations as a Kotlin sequence. */
fun Hyperparameters.gridSequence(): Sequence<Properties> = grid().iterator().asSequence()

/** Returns n randomly sampled configurations as a Kotlin sequence. */
fun Hyperparameters.randomSequence(n: Int): Sequence<Properties> = random(n).iterator().asSequence()
