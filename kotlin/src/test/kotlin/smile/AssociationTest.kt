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
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 *
 * @author Haifeng Li
 */
class AssociationTest {

    var itemsets = arrayOf<IntArray>(
        intArrayOf(1, 3),
        intArrayOf(2),
        intArrayOf(4),
        intArrayOf(2, 3, 4),
        intArrayOf(2, 3),
        intArrayOf(2, 3),
        intArrayOf(1, 2, 3, 4),
        intArrayOf(1, 3),
        intArrayOf(1, 2, 3),
        intArrayOf(1, 2, 3)
    )

    val streamSupplier: Supplier<Stream<IntArray>> = Supplier {
        Stream.of(*itemsets)
    }

    @Test
    fun testFPGrowth() {
        println("FP-Growth")
        val tree = fptree(3, streamSupplier)
        val results = fpgrowth(tree).toList()
        for (itemSet in results) {
            println(itemSet)
        }

        assertEquals(8, results.size)

        assertEquals(3, results.get(0).support)
        assertEquals(1, results.get(0).items.size)
        assertEquals(4, results.get(0).items[0])

        assertEquals(5, results.get(1).support)
        assertEquals(1, results.get(1).items.size)
        assertEquals(1, results.get(1).items[0])

        assertEquals(6, results.get(6).support)
        assertEquals(2, results.get(6).items.size)
        assertEquals(3, results.get(6).items[0])
        assertEquals(2, results.get(6).items[1])

        assertEquals(8, results.get(7).support)
        assertEquals(1, results.get(7).items.size)
        assertEquals(3, results.get(7).items[0])
    }

    @Test
    fun testARM() {
        println("ARM")
        val tree = fptree(3, streamSupplier)
        val rules = arm(0.5, tree).toList()
        for (rule in rules) {
            println(rule)
        }

        assertEquals(9, rules.size)

        assertEquals(0.6, rules.get(0).support(), 1E-2)
        assertEquals(0.75, rules.get(0).confidence(), 1E-2)
        assertEquals(1, rules.get(0).antecedent().size)
        assertEquals(3, rules.get(0).antecedent()[0])
        assertEquals(1, rules.get(0).consequent().size)
        assertEquals(2, rules.get(0).consequent()[0])

        assertEquals(0.3, rules.get(4).support(), 1E-2)
        assertEquals(0.6, rules.get(4).confidence(), 1E-2)
        assertEquals(1, rules.get(4).antecedent().size)
        assertEquals(1, rules.get(4).antecedent()[0])
        assertEquals(1, rules.get(4).consequent().size)
        assertEquals(2, rules.get(4).consequent()[0])

        assertEquals(0.3, rules.get(8).support(), 1E-2)
        assertEquals(0.6, rules.get(8).confidence(), 1E-2)
        assertEquals(1, rules.get(8).antecedent().size)
        assertEquals(1, rules.get(8).antecedent()[0])
        assertEquals(2, rules.get(8).consequent().size)
        assertEquals(3, rules.get(8).consequent()[0])
        assertEquals(2, rules.get(8).consequent()[1])
    }
}
