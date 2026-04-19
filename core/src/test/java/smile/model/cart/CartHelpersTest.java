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
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted helper tests for CART utilities.
 */
public class CartHelpersTest {
    @Test
    public void testGivenLossStringRoundTripWhenParsingThenQuantileAndHuberAreSupported() {
        // Given
        Loss quantile = Loss.quantile(0.3);
        Loss huber = Loss.huber(0.7);

        // When
        Loss restoredQuantile = Loss.valueOf(quantile.toString());
        Loss restoredHuber = Loss.valueOf(huber.toString());

        // Then
        assertEquals(quantile.toString(), restoredQuantile.toString());
        assertEquals(huber.toString(), restoredHuber.toString());
    }

    @Test
    public void testGivenInvalidLossSpecificationsWhenParsingThenThrowsMeaningfulException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> Loss.quantile(0.0));
        assertThrows(IllegalArgumentException.class, () -> Loss.huber(1.0));
        assertThrows(IllegalArgumentException.class, () -> Loss.valueOf("Unsupported"));
    }

    @Test
    public void testGivenRegressionAndLogisticLossesWhenComputingThenValuesAreDeterministic() {
        // Given
        Loss leastSquares = Loss.ls();
        Loss logistic = Loss.logistic(new int[] {0, 1});

        // When
        double intercept = leastSquares.intercept(new double[] {1.0, 2.0, 5.0});
        double output = leastSquares.output(new int[] {0, 1, 2}, new int[] {1, 1, 1});
        double logisticIntercept = logistic.intercept(new double[0]);
        double[] logisticResponse = logistic.response();
        double logisticOutput = logistic.output(new int[] {0, 1}, new int[] {1, 1});

        // Then
        assertEquals(8.0 / 3.0, intercept, 1E-12);
        assertEquals(0.0, output, 1E-12);
        assertEquals(0.0, logisticIntercept, 1E-12);
        assertArrayEquals(new double[] {-1.0, 1.0}, logisticResponse, 1E-12);
        assertEquals(0.0, logisticOutput, 1E-12);
    }

    @Test
    public void testGivenMixedSchemaWhenOrderingSamplesThenOnlyNumericColumnsAreSorted() {
        // Given
        DataFrame data = new DataFrame(
                new DoubleVector("x", new double[] {3.0, 1.0, 2.0}),
                new IntVector(new StructField("cat", DataTypes.IntType, new NominalScale("red", "blue")), new int[] {0, 1, 0})
        );

        // When
        int[][] order = CART.order(data);

        // Then
        assertArrayEquals(new int[] {1, 2, 0}, order[0]);
        assertNull(order[1]);
    }

    @Test
    public void testGivenNominalAndOrdinalNodesWhenBranchingThenPredictionsAndFormattingAreStable() {
        // Given
        DecisionNode left = new DecisionNode(new int[] {2, 0});
        DecisionNode right = new DecisionNode(new int[] {0, 2});
        StructType nominalSchema = new StructType(new StructField("color", DataTypes.IntType, new NominalScale("red", "blue", "green")));
        Tuple blue = Tuple.of(nominalSchema, new int[] {1});
        Tuple red = Tuple.of(nominalSchema, new int[] {0});
        NominalNode nominal = new NominalNode(0, 1, 0.4, 0.2, left, right);

        StructType numericSchema = new StructType(new StructField("x", DataTypes.DoubleType));
        Tuple small = Tuple.of(numericSchema, new double[] {1.5});
        Tuple large = Tuple.of(numericSchema, new double[] {3.0});
        OrdinalNode ordinal = new OrdinalNode(0, 2.0, 0.3, 0.1, left, right);

        // When / Then
        assertTrue(nominal.branch(blue));
        assertFalse(nominal.branch(red));
        assertSame(left, nominal.predict(blue));
        assertSame(right, nominal.predict(red));
        assertEquals("color=blue", nominal.toString(nominalSchema, true));
        assertEquals("color=red,green", nominal.toString(nominalSchema, false));

        assertTrue(ordinal.branch(small));
        assertFalse(ordinal.branch(large));
        assertSame(left, ordinal.predict(small));
        assertSame(right, ordinal.predict(large));
        assertTrue(ordinal.toString(numericSchema, true).startsWith("x<="));
        assertTrue(ordinal.toString(numericSchema, false).startsWith("x>"));

        InternalNode replacedNominal = nominal.replace(right, left);
        InternalNode replacedOrdinal = ordinal.replace(right, left);
        assertSame(right, replacedNominal.trueChild());
        assertSame(right, replacedOrdinal.trueChild());
    }

    @Test
    public void testGivenSplitDescriptorsWhenCreatingNodesThenPredicateAndMetadataArePreserved() {
        // Given
        DecisionNode leaf = new DecisionNode(new int[] {3, 1});
        DecisionNode trueChild = new DecisionNode(new int[] {2, 0});
        DecisionNode falseChild = new DecisionNode(new int[] {1, 1});
        NominalSplit nominalSplit = new NominalSplit(leaf, 1, 2, 0.7, 0, 4, 2, 2, i -> i % 2 == 0);
        OrdinalSplit ordinalSplit = new OrdinalSplit(leaf, 0, 1.5, 0.6, 0, 4, 3, 1, i -> i < 3);

        // When
        NominalNode nominalNode = nominalSplit.toNode(trueChild, falseChild);
        OrdinalNode ordinalNode = ordinalSplit.toNode(trueChild, falseChild);

        // Then
        assertTrue(nominalSplit.predicate().test(2));
        assertFalse(nominalSplit.predicate().test(1));
        assertTrue(ordinalSplit.predicate().test(2));
        assertFalse(ordinalSplit.predicate().test(3));
        assertEquals(0.7, nominalNode.score(), 1E-12);
        assertEquals(leaf.deviance(), nominalNode.deviance(), 1E-12);
        assertEquals(0.6, ordinalNode.score(), 1E-12);
        assertEquals(leaf.deviance(), ordinalNode.deviance(), 1E-12);
    }
}

