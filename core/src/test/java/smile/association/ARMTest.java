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
package smile.association;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ARM (Association Rule Mining) and related classes.
 *
 * @author Haifeng Li
 */
public class ARMTest {

    // 10 transactions, items 1-4
    int[][] itemsets = {
        {1, 3},
        {2},
        {4},
        {2, 3, 4},
        {2, 3},
        {2, 3},
        {1, 2, 3, 4},
        {1, 3},
        {1, 2, 3},
        {1, 2, 3}
    };

    @Test
    public void givenMinSupport3AndConfidence0_5_whenMiningRules_thenExpectedCountAndValues() {
        // Given
        FPTree tree = FPTree.of(3, itemsets);

        // When
        List<AssociationRule> rules = ARM.apply(0.5, tree).toList();
        for (var rule : rules) {
            System.out.println(rule);
        }

        // Then
        assertEquals(9, rules.size());

        // first rule: {3} => {2}
        assertEquals(0.6,  rules.get(0).support(),    1E-2);
        assertEquals(0.75, rules.get(0).confidence(), 1E-2);
        assertEquals(1,    rules.get(0).antecedent().length);
        assertEquals(3,    rules.get(0).antecedent()[0]);
        assertEquals(1,    rules.get(0).consequent().length);
        assertEquals(2,    rules.get(0).consequent()[0]);

        // rule 5: {1} => {2}
        assertEquals(0.3, rules.get(4).support(),    1E-2);
        assertEquals(0.6, rules.get(4).confidence(), 1E-2);

        // rule 9: {1} => {3,2}  (multi-item consequent)
        assertEquals(0.3, rules.get(8).support(),    1E-2);
        assertEquals(0.6, rules.get(8).confidence(), 1E-2);
        assertEquals(1,   rules.get(8).antecedent().length);
        assertEquals(2,   rules.get(8).consequent().length);
    }

    @Test
    public void givenConfidence1_0_whenMiningRules_thenOnlyPerfectRulesReturned() {
        // Given
        FPTree tree = FPTree.of(3, itemsets);

        // When
        List<AssociationRule> rules = ARM.apply(1.0, tree).toList();

        // Then — every returned rule must have confidence == 1.0
        assertFalse(rules.isEmpty());
        for (AssociationRule r : rules) {
            assertEquals(1.0, r.confidence(), 1E-9,
                    "Expected confidence 1.0 but got " + r.confidence() + " for " + r);
        }
    }

    @Test
    public void givenConfidence0_0_whenMiningRules_thenAtLeastAsManyRulesAsHigherThreshold() {
        // Given
        FPTree tree = FPTree.of(3, itemsets);

        // When
        long count0   = ARM.apply(0.0, tree).count();
        long count0_5 = ARM.apply(0.5, tree).count();

        // Then
        assertTrue(count0 >= count0_5,
                "count(conf=0) should be >= count(conf=0.5): " + count0 + " vs " + count0_5);
    }

    @Test
    public void givenRule_whenCheckingLiftAndLeverage_thenMetricsAreCorrect() {
        // Given — rule {3} => {2}
        // antecedentSupport = 8/10 = 0.8
        // consequentSupport = 7/10 = 0.7
        // support(3 union 2) = 6/10 = 0.6
        // confidence         = 6/8  = 0.75
        // lift               = 0.6 / (0.8 * 0.7) = 1.071...
        // leverage           = 0.6 - 0.8 * 0.7   = 0.04
        FPTree tree = FPTree.of(3, itemsets);

        // When
        AssociationRule rule = ARM.apply(0.5, tree).toList().getFirst(); // {3} => {2}

        // Then
        assertEquals(0.6,   rule.support(),    1E-2);
        assertEquals(0.75,  rule.confidence(), 1E-2);
        assertEquals(1.071, rule.lift(),       1E-2);
        assertEquals(0.04,  rule.leverage(),   1E-2);
        assertTrue(rule.lift() > 1.0, "positive correlation expected");
    }

    @Test
    public void givenRule_whenCallingToString_thenContainsKeyMetrics() {
        // Given
        AssociationRule rule = new AssociationRule(
                new int[]{1, 2}, new int[]{3}, 0.4, 0.8, 1.5, 0.05);

        // When
        String s = rule.toString();

        // Then
        assertTrue(s.contains("=>"),         "toString should contain '=>'");
        assertTrue(s.contains("support"),    "toString should contain 'support'");
        assertTrue(s.contains("confidence"), "toString should contain 'confidence'");
        assertTrue(s.contains("lift"),       "toString should contain 'lift'");
        assertTrue(s.contains("leverage"),   "toString should contain 'leverage'");
    }

    @Test
    public void givenEqualRules_whenComparing_thenEqualsAndHashCodeMatchButDerivedMetricsIgnored() {
        // Given — lift and leverage differ; equals/hashCode intentionally ignores them
        AssociationRule r1 = new AssociationRule(new int[]{1}, new int[]{2}, 0.5, 0.8, 1.2, 0.1);
        AssociationRule r2 = new AssociationRule(new int[]{1}, new int[]{2}, 0.5, 0.8, 2.0, 0.2);
        AssociationRule r3 = new AssociationRule(new int[]{1}, new int[]{2}, 0.5, 0.7, 1.2, 0.1);

        // When / Then
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1, r3);
        assertNotEquals(r1.hashCode(), r3.hashCode());
    }

    @Test
    public void givenPercentageMinSupport_whenBuildingFPTree_thenSameResultAsAbsoluteSupport() {
        // Given — 10 transactions, 30% => effective minSupport = 3
        FPTree treeAbs = FPTree.of(3,   itemsets);
        FPTree treePct = FPTree.of(0.3, itemsets);

        // When
        long countAbs = ARM.apply(0.5, treeAbs).count();
        long countPct = ARM.apply(0.5, treePct).count();

        // Then
        assertEquals(countAbs, countPct);
    }

    @Test
    public void givenInvalidConfidence_whenApply_thenThrowsIllegalArgumentException() {
        // Given
        FPTree tree = FPTree.of(3, itemsets);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> ARM.apply(-0.1, tree));
        assertThrows(IllegalArgumentException.class, () -> ARM.apply(1.1,  tree));
    }

    @Test
    public void givenInvalidMinSupport_whenBuildingFPTree_thenThrowsIllegalArgumentException() {
        // When / Then — integer variants
        assertThrows(IllegalArgumentException.class, () -> FPTree.of(0,  itemsets));
        assertThrows(IllegalArgumentException.class, () -> FPTree.of(-1, itemsets));
        // percentage variants
        assertThrows(IllegalArgumentException.class, () -> FPTree.of(0.0, itemsets));
        assertThrows(IllegalArgumentException.class, () -> FPTree.of(1.1, itemsets));
    }

    @Test
    public void givenVeryHighMinSupport_whenMining_thenNoFrequentItemSets() {
        // Given — minSupport > total transactions => nothing is frequent
        FPTree tree = FPTree.of(11, itemsets);  // only 10 transactions

        // When
        long fpCount  = FPGrowth.apply(tree).count();
        long armCount = ARM.apply(0.5, tree).count();

        // Then
        assertEquals(0, fpCount);
        assertEquals(0, armCount);
    }

    @Test
    public void givenArmIterator_whenExhausted_thenNextThrowsNoSuchElementException() {
        // Given
        FPTree tree = FPTree.of(3, itemsets);
        ARM arm = new ARM(0.5, new TotalSupportTree(tree));
        var iterator = arm.iterator();
        while (iterator.hasNext()) {
            iterator.next();
        }

        // When / Then
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    @Tag("integration")
    public void testPima() {
        System.out.println("pima");
        FPTree tree = FPTree.of(20, () -> ItemSetTestData.read("transaction/pima.D38.N768.C2"));
        Stream<AssociationRule> rules = ARM.apply(0.9, tree);
        assertEquals(6803, rules.count());
    }

    @Test
    @Tag("integration")
    public void testKosarak() {
        System.out.println("kosarak");
        FPTree tree = FPTree.of(0.003, () -> ItemSetTestData.read("transaction/kosarak.dat"));
        Stream<AssociationRule> rules = ARM.apply(0.5, tree);
        assertEquals(17954, rules.count());
    }
}
