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
package smile.association

import java.util.function.Supplier
import java.util.stream.Stream
import org.specs2.mutable.*

/**
  *
  * @author Haifeng Li
  */
class AssociationSpec extends Specification {

  val itemsets = Array(
    Array(1, 3),
    Array(2),
    Array(4),
    Array(2, 3, 4),
    Array(2, 3),
    Array(2, 3),
    Array(1, 2, 3, 4),
    Array(1, 3),
    Array(1, 2, 3),
    Array(1, 2, 3)
  )

  object streamSupplier extends Supplier[Stream[Array[Int]]] {
    def get: Stream[Array[Int]] = Stream.of(itemsets *)
  }

  "association rule mining" should {
    "FP-Growth" in {
      val tree = fptree(3, streamSupplier)
      val results = fpgrowth(tree).toList
      results.forEach { itemSet =>
        println(itemSet)
      }

      8 === results.size

      3 === results.get(0).support
      1 === results.get(0).items.length
      4 === results.get(0).items()(0)

      5 === results.get(1).support
      1 === results.get(1).items.length
      1 === results.get(1).items()(0)

      6 === results.get(6).support
      2 === results.get(6).items.length
      3 === results.get(6).items()(0)
      2 === results.get(6).items()(1)

      8 === results.get(7).support
      1 === results.get(7).items.length
      3 === results.get(7).items()(0)
    }

    "ARM" in {
      val tree = fptree(3, streamSupplier)
      val rules = arm(0.5, tree).toList
      rules.forEach { rule =>
        println(rule)
      }

      9 === rules.size

      rules.get(0).support() must beCloseTo(0.6, 1E-2)
      rules.get(0).confidence() must beCloseTo(0.75, 1E-2)
      1 === rules.get(0).antecedent().length
      3 === rules.get(0).antecedent()(0)
      1 === rules.get(0).consequent().length
      2 === rules.get(0).consequent()(0)

      rules.get(4).support() must beCloseTo(0.3, 1E-2)
      rules.get(4).confidence() must beCloseTo(0.6, 1E-2)
      1 === rules.get(4).antecedent().length
      1 === rules.get(4).antecedent()(0)
      1 === rules.get(4).consequent().length
      2 === rules.get(4).consequent()(0)

      rules.get(8).support() must beCloseTo(0.3, 1E-2)
      rules.get(8).confidence() must beCloseTo(0.6, 1E-2)
      1 === rules.get(8).antecedent().length
      1 === rules.get(8).antecedent()(0)
      2 === rules.get(8).consequent().length
      3 === rules.get(8).consequent()(0)
      2 === rules.get(8).consequent()(1)
    }
  }
}
