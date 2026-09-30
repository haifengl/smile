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
package smile

import java.util.Properties
import scala.jdk.CollectionConverters.*
import smile.hpo.{BayesianOptimization, Hyperparameters}
import smile.math.kernel.{MaternKernel, MercerKernel}
import smile.util.time

/** Hyperparameter optimization.
  *
  * @author Haifeng Li
  */
package object hpo {

  /** Creates a new hyperparameter search space.
    *
    * @return the hyperparameter search space builder.
    */
  def hyperparameters: Hyperparameters = new Hyperparameters()

  /** Executes Bayesian hyperparameter optimization.
    *
    * @param hp the hyperparameter configuration space.
    * @param maxTrials the total number of evaluations to perform.
    * @param initialTrials the number of initial random trials (-1 for heuristic default).
    * @param maximize true to maximize the objective, false to minimize.
    * @param acquisition the acquisition function strategy.
    * @param exploration exploration parameter (&xi; for EI/PI, &kappa; for UCB).
    * @param kernel the Mercer covariance kernel for GP surrogate modeling.
    * @param objective the objective function evaluating a configuration.
    * @return the optimization result containing best parameters, value, and trial history.
    */
  def bayes(
    hp: Hyperparameters,
    maxTrials: Int = 50,
    initialTrials: Int = -1,
    maximize: Boolean = true,
    acquisition: BayesianOptimization.Acquisition = BayesianOptimization.Acquisition.EXPECTED_IMPROVEMENT,
    exploration: Double = 0.01,
    kernel: MercerKernel[Array[Double]] = new MaternKernel(1.0, 2.5)
  )(objective: Properties => Double): BayesianOptimization.Result = time("Bayesian Optimization") {
    val options = new BayesianOptimization.Options(maxTrials, initialTrials, maximize, acquisition, exploration, kernel)
    hp.bayes(objective(_), options)
  }

  /** Implicit operations on Hyperparameters. */
  implicit class HyperparametersOps(val hp: Hyperparameters) extends AnyVal {
    /** Returns all grid configurations as a Scala sequence. */
    def gridSeq: Seq[Properties] = hp.grid().iterator().asScala.toSeq

    /** Returns n randomly sampled configurations as a Scala sequence. */
    def randomSeq(n: Int): Seq[Properties] = hp.random(n).iterator().asScala.toSeq
  }
}
