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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import smile.hpo.bayes
import smile.hpo.gridSequence
import smile.hpo.hyperparameters
import smile.hpo.randomSequence

class HPOTest {

    @Test
    fun testHyperparameters() {
        val hp = hyperparameters {
            add("p1", intArrayOf(1, 2, 3))
            add("p2", arrayOf("a", "b"))
        }

        val grid = hp.gridSequence().toList()
        assertEquals(6, grid.size)

        val random = hp.randomSequence(4).toList()
        assertEquals(4, random.size)
    }

    @Test
    fun testBayesianOptimization() {
        val hp = hyperparameters {
            add("x1", -5.0, 5.0)
            add("x2", -5.0, 5.0)
        }

        val result = hp.bayes(maxTrials = 10, maximize = false) { props ->
            val x1 = props.getProperty("x1").toDouble()
            val x2 = props.getProperty("x2").toDouble()
            x1 * x1 + x2 * x2
        }

        assertEquals(10, result.trials.size)
        assertTrue(result.bestValue >= 0.0)
        assertNotNull(result.bestParameters.getProperty("x1"))
    }
}
