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
 * Tests for {@link RegressionNode}.
 */
public class RegressionNodeTest {

    @Test
    public void givenTypicalNode_whenReadingGetters_thenReturnConstructorValues() {
        // Given
        RegressionNode node = new RegressionNode(10, 3.5, 4.0, 8.0);

        // Then
        assertEquals(10, node.size());
        assertEquals(3.5, node.output(), 1E-12);
        assertEquals(4.0, node.mean(), 1E-12);
        assertEquals(8.0, node.impurity(), 1E-12);
        assertEquals(8.0, node.deviance(), 1E-12);  // deviance == rss
    }

    @Test
    public void givenRegressionNode_whenCheckingLeafProperties_thenReturnExpectedValues() {
        // Given
        RegressionNode node = new RegressionNode(5, 2.0, 2.0, 1.0);

        // Then
        assertEquals(1, node.leaves());
        assertEquals(1, node.depth());
        assertSame(node, node.merge());
        assertSame(node, node.predict(null));  // LeafNode.predict returns itself
    }

    @Test
    public void givenTwoNodesWithSameOutput_whenEquality_thenAreEqual() {
        // Given
        RegressionNode a = new RegressionNode(10, 2.5, 2.5, 4.0);
        RegressionNode b = new RegressionNode(20, 2.5, 3.0, 9.0);  // different size/mean/rss

        // Then: equals only compares output
        assertEquals(a, b);
    }

    @Test
    public void givenTwoNodesWithDifferentOutput_whenEquality_thenAreNotEqual() {
        // Given
        RegressionNode a = new RegressionNode(10, 2.5, 2.5, 4.0);
        RegressionNode b = new RegressionNode(10, 2.6, 2.5, 4.0);

        // Then
        assertNotEquals(a, b);
    }

    @Test
    public void givenRegressionNodeAndDecisionNode_whenEquality_thenAreNotEqual() {
        // Given
        RegressionNode reg = new RegressionNode(5, 1.0, 1.0, 0.0);
        DecisionNode dec = new DecisionNode(new int[]{3, 2});

        // Then: cross-type equality is always false
        assertNotEquals(reg, dec);
    }

    @Test
    public void givenZeroRssNode_whenDeviance_thenReturnsZero() {
        // Given: perfect node (all samples have same value)
        RegressionNode node = new RegressionNode(7, 5.0, 5.0, 0.0);

        // Then
        assertEquals(0.0, node.deviance(), 1E-12);
        assertEquals(0.0, node.impurity(), 1E-12);
    }

    @Test
    public void givenNode_whenDotString_thenContainsKeyInfo() {
        // Given
        var schema = new smile.data.type.StructType(
                new smile.data.type.StructField("y", smile.data.type.DataTypes.DoubleType));
        var response = schema.field(0);
        RegressionNode node = new RegressionNode(10, 3.14, 3.14, 2.5);

        // When
        String dot = node.dot(schema, response, 7);

        // Then: must contain the node id, field name, output, size and deviance
        assertTrue(dot.contains("7"));
        assertTrue(dot.contains("y"));
        assertTrue(dot.contains("3.1400"));  // output formatted with %.4f
        assertTrue(dot.contains("10"));      // size
        assertTrue(dot.contains("2.5000"));  // deviance
    }
}
