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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }
}

