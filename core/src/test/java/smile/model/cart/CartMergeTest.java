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
 * Tests for {@link InternalNode#merge()} behavior.
 */
public class CartMergeTest {
    @Test
    public void testGivenClassificationChildrenWithSamePredictionWhenMergingThenCountsAreCombined() {
        // Given
        DecisionNode left = new DecisionNode(new int[] {3, 1});
        DecisionNode right = new DecisionNode(new int[] {2, 0});
        InternalNode node = new OrdinalNode(0, 1.5, 0.4, 0.2, left, right);

        // When
        Node merged = node.merge();

        // Then
        assertInstanceOf(DecisionNode.class, merged);
        DecisionNode leaf = (DecisionNode) merged;
        assertEquals(0, leaf.output());
        assertArrayEquals(new int[] {5, 1}, leaf.count());
        assertEquals(6, leaf.size());
    }

    @Test
    public void testGivenClassificationChildrenWithDifferentPredictionWhenMergingThenNodeIsKept() {
        // Given
        DecisionNode left = new DecisionNode(new int[] {3, 0});
        DecisionNode right = new DecisionNode(new int[] {0, 2});
        InternalNode node = new NominalNode(0, 1, 0.5, 0.3, left, right);

        // When
        Node merged = node.merge();

        // Then
        assertSame(node, merged);
    }

    @Test
    public void testGivenRegressionChildrenWithSamePredictionWhenMergingThenStatisticsAreCombined() {
        // Given
        RegressionNode left = new RegressionNode(2, 2.0, 1.0, 0.5);
        RegressionNode right = new RegressionNode(4, 2.0, 3.0, 1.5);
        InternalNode node = new OrdinalNode(0, 0.0, 0.7, 0.4, left, right);

        // When
        Node merged = node.merge();

        // Then
        assertInstanceOf(RegressionNode.class, merged);
        RegressionNode leaf = (RegressionNode) merged;
        assertEquals(6, leaf.size());
        assertEquals(2.0, leaf.output(), 1E-12);
        assertEquals((2.0 * 1.0 + 4.0 * 3.0) / 6.0, leaf.mean(), 1E-12);
        assertEquals(2.0, leaf.impurity(), 1E-12);
    }

    @Test
    public void testGivenRegressionChildrenWithDifferentPredictionWhenMergingThenNodeIsKept() {
        // Given
        RegressionNode left = new RegressionNode(2, 1.0, 1.0, 0.5);
        RegressionNode right = new RegressionNode(4, 2.0, 2.5, 1.5);
        InternalNode node = new OrdinalNode(0, 0.0, 0.7, 0.4, left, right);

        // When
        Node merged = node.merge();

        // Then
        assertSame(node, merged);
    }
}

