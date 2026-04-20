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
package smile.model.cart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests for {@link DecisionNode}.
 */
public class DecisionNodeTest {
    @Test
    public void testGivenDecisionNodeWhenInspectingImpurityPosterioriAndDevianceThenValuesAreDeterministic() {
        // Given
        int[] count = {2, 1, 0};
        DecisionNode node = new DecisionNode(count);

        // When
        double[] posteriori = node.posteriori(new double[count.length]);

        // Then
        assertEquals(0, node.output());
        assertArrayEquals(count, node.count());
        assertEquals(4.0 / 9.0, node.impurity(SplitRule.GINI), 1E-12);
        assertEquals(-(2.0 / 3.0) * log2(2.0 / 3.0) - (1.0 / 3.0) * log2(1.0 / 3.0),
                node.impurity(SplitRule.ENTROPY), 1E-12);
        assertEquals(1.0 / 3.0, node.impurity(SplitRule.CLASSIFICATION_ERROR), 1E-12);
        assertArrayEquals(new double[] {0.5, 1.0 / 3.0, 1.0 / 6.0}, posteriori, 1E-12);
        assertEquals(4.969813299576001, node.deviance(), 1E-12);
    }

    @Test
    public void givenPureNode_whenImpurity_thenZeroForAllRules() {
        // Given: all samples in class 0
        int[] count = {5, 0, 0};
        DecisionNode node = new DecisionNode(count);

        // Then: pure node should have 0 impurity
        assertEquals(0.0, node.impurity(SplitRule.GINI), 1E-12);
        assertEquals(0.0, node.impurity(SplitRule.ENTROPY), 1E-12);
        assertEquals(0.0, node.impurity(SplitRule.CLASSIFICATION_ERROR), 1E-12);
    }

    @Test
    public void givenBinaryEqualSplit_whenGini_thenReturnsHalf() {
        // Given: 50/50 binary split is max impurity = 0.5 for Gini
        int[] count = {3, 3};
        DecisionNode node = new DecisionNode(count);

        // Gini = 1 - (3/6)^2 - (3/6)^2 = 1 - 0.25 - 0.25 = 0.5
        assertEquals(0.5, node.impurity(SplitRule.GINI), 1E-12);
        // Entropy of 50/50 = 1.0 bit
        assertEquals(1.0, node.impurity(SplitRule.ENTROPY), 1E-12);
        // Error = 1 - max(0.5,0.5) = 0.5
        assertEquals(0.5, node.impurity(SplitRule.CLASSIFICATION_ERROR), 1E-12);
    }

    @Test
    public void givenSameOutput_whenEquals_thenNodesAreEqual() {
        // Same majority class  → equal regardless of count values
        DecisionNode a = new DecisionNode(new int[]{4, 1});
        DecisionNode b = new DecisionNode(new int[]{3, 2});

        assertEquals(a, b);
    }

    @Test
    public void givenDifferentOutput_whenEquals_thenNodesAreNotEqual() {
        DecisionNode a = new DecisionNode(new int[]{4, 1});  // output = 0
        DecisionNode b = new DecisionNode(new int[]{1, 4});  // output = 1

        assertNotEquals(a, b);
    }

    @Test
    public void givenDecisionNodeAndRegressionNode_whenEquals_thenNotEqual() {
        DecisionNode d = new DecisionNode(new int[]{2, 1});
        RegressionNode r = new RegressionNode(3, 0.0, 0.0, 0.0);

        assertNotEquals(d, r);
    }

    @Test
    public void givenPerfectNode_whenDeviance_thenIsFiniteAndNonNegative() {
        DecisionNode pure = new DecisionNode(new int[]{10, 0});
        assertTrue(pure.deviance() >= 0.0);
        assertTrue(Double.isFinite(pure.deviance()));
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }
}

