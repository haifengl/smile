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

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

class HPOSpec extends AnyWordSpec with Matchers {

  "hpo shims" should {
    "support hyperparameters builder and sequences" in {
      val hp = hyperparameters
        .add("p1", Array(1, 2, 3))
        .add("p2", Array("a", "b"))

      val grid = hp.gridSeq
      grid.size must be (6)

      val random = hp.randomSeq(4)
      random.size must be (4)
    }

    "execute bayes optimization with defaults" in {
      val hp = hyperparameters
        .add("x1", -5.0, 5.0)
        .add("x2", -5.0, 5.0)

      val result = bayes(hp, maxTrials = 10, maximize = false) { props =>
        val x1 = props.getProperty("x1").toDouble
        val x2 = props.getProperty("x2").toDouble
        x1 * x1 + x2 * x2
      }

      result.trials.size must be (10)
      result.bestValue must be >= 0.0
      result.bestParameters.getProperty("x1") must not be null
    }
  }
}
