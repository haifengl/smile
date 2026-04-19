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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.formula.Formula;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Formatting tests for {@link CART#toString()} and {@link CART#dot()}.
 */
public class CartFormattingTest {
    private static final class TestCart extends CART {
        TestCart(StructType schema, StructField response, Node root) {
            super((Formula) null, schema, response, root, new double[schema.length()]);
        }

        @Override
        protected double impurity(LeafNode node) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected LeafNode newNode(int[] nodeSamples) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Optional<Split> findBestSplit(LeafNode node, int column, double impurity, int lo, int hi) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void testGivenClassificationTreeWhenFormattingThenToStringAndDotAreStable() {
        // Given
        StructType schema = new StructType(new StructField("color", DataTypes.IntType, new NominalScale("red", "blue", "green")));
        StructField response = new StructField("class", DataTypes.IntType, new NominalScale("no", "yes"));
        DecisionNode trueChild = new DecisionNode(new int[] {2, 0});
        DecisionNode falseChild = new DecisionNode(new int[] {0, 2});
        TestCart cart = new TestCart(schema, response, new NominalNode(0, 1, 0.4, 0.2, trueChild, falseChild));

        // When
        String tree = cart.toString();
        String dot = cart.dot();

        // Then
        String expectedTree = String.join("\n",
                "n=4",
                "node), split, n, loss, yval, (yprob)",
                "* denotes terminal node",
                "1) root 4 0.20000 no (0.50000 0.50000)",
                " 2) color=blue 2 1.1507 no (0.75000 0.25000) *",
                " 3) color=red,green 2 1.1507 yes (0.25000 0.75000) *");
        assertEquals(expectedTree, tree);

        String expectedDot = "digraph CART {\n"
                + " node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n"
                + " edge [fontname=helvetica];\n"
                + " 1 [label=<color = blue<br/>size = 4<br/>impurity reduction = 0.4000>, fillcolor=\"#00000000\"];\n"
                + " 1 -> 2 [labeldistance=2.5, labelangle=45, headlabel=\"True\"];\n"
                + " 1 -> 3 [labeldistance=2.5, labelangle=-45, headlabel=\"False\"];\n"
                + " 2 [label=<class = no<br/>size = 2<br/>deviance = 1.1507>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + " 3 [label=<class = yes<br/>size = 2<br/>deviance = 1.1507>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + "}";
        assertEquals(expectedDot, dot);
    }

    @Test
    public void testGivenRegressionTreeWhenFormattingThenToStringAndDotAreStable() {
        // Given
        StructType schema = new StructType(new StructField("x", DataTypes.DoubleType));
        StructField response = new StructField("y", DataTypes.DoubleType);
        RegressionNode trueChild = new RegressionNode(2, 1.5, 1.0, 0.2);
        RegressionNode falseChild = new RegressionNode(3, 2.5, 3.0, 0.6);
        TestCart cart = new TestCart(schema, response, new OrdinalNode(0, 1.5, 0.3, 0.4, trueChild, falseChild));

        // When
        String tree = cart.toString();
        String dot = cart.dot();

        // Then
        String expectedTree = String.join("\n",
                "n=5",
                "node), split, n, loss, yval, (yprob)",
                "* denotes terminal node",
                "1) root 5 0.40000 2.10000 ",
                " 2) x<=1.50000 2 0.20000 1.50000 *",
                " 3) x>1.50000 3 0.60000 2.50000 *");
        assertEquals(expectedTree, tree);

        String expectedDot = "digraph CART {\n"
                + " node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n"
                + " edge [fontname=helvetica];\n"
                + " 1 [label=<x &le; 1.5<br/>size = 5<br/>impurity reduction = 0.3000>, fillcolor=\"#00000000\"];\n"
                + " 1 -> 2 [labeldistance=2.5, labelangle=45, headlabel=\"True\"];\n"
                + " 1 -> 3 [labeldistance=2.5, labelangle=-45, headlabel=\"False\"];\n"
                + " 2 [label=<y = 1.5000<br/>size = 2<br/>deviance = 0.2000>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + " 3 [label=<y = 2.5000<br/>size = 3<br/>deviance = 0.6000>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + "}";
        assertEquals(expectedDot, dot);
    }

    @Test
    public void testGivenDeeperClassificationTreeWhenFormattingThenNestedNumberingAndBranchesAreStable() {
        // Given
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("color", DataTypes.IntType, new NominalScale("red", "blue", "green"))
        );
        StructField response = new StructField("class", DataTypes.IntType, new NominalScale("no", "yes"));
        DecisionNode leaf3 = new DecisionNode(new int[] {0, 4});
        DecisionNode leaf4 = new DecisionNode(new int[] {3, 0});
        DecisionNode leaf5 = new DecisionNode(new int[] {1, 2});
        InternalNode node2 = new NominalNode(1, 1, 0.2, 0.3, leaf4, leaf5);
        TestCart cart = new TestCart(schema, response, new OrdinalNode(0, 1.5, 0.4, 0.9, node2, leaf3));

        // When
        String tree = cart.toString();
        String dot = cart.dot();

        // Then
        String expectedTree = String.join("\n",
                "n=10",
                "node), split, n, loss, yval, (yprob)",
                "* denotes terminal node",
                "1) root 10 0.90000 yes (0.41667 0.58333)",
                " 2) x<=1.50000 6 0.30000 no (0.62500 0.37500)",
                "  4) color=blue 3 1.3389 no (0.80000 0.20000) *",
                "  5) color=red,green 3 3.8759 yes (0.40000 0.60000) *",
                " 3) x>1.50000 4 1.4586 yes (0.16667 0.83333) *");
        assertEquals(expectedTree, tree);

        String expectedDot = "digraph CART {\n"
                + " node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n"
                + " edge [fontname=helvetica];\n"
                + " 1 [label=<x &le; 1.5<br/>size = 10<br/>impurity reduction = 0.4000>, fillcolor=\"#00000000\"];\n"
                + " 1 -> 2 [labeldistance=2.5, labelangle=45, headlabel=\"True\"];\n"
                + " 1 -> 3 [labeldistance=2.5, labelangle=-45, headlabel=\"False\"];\n"
                + " 2 [label=<color = blue<br/>size = 6<br/>impurity reduction = 0.2000>, fillcolor=\"#00000000\"];\n"
                + " 2 -> 4\n"
                + " 2 -> 5\n"
                + " 3 [label=<class = yes<br/>size = 4<br/>deviance = 1.4586>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + " 4 [label=<class = no<br/>size = 3<br/>deviance = 1.3389>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + " 5 [label=<class = yes<br/>size = 3<br/>deviance = 3.8759>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + "}";
        assertEquals(expectedDot, dot);
    }

    @Test
    public void testGivenDeeperRegressionTreeWhenFormattingThenNestedNumberingAndAggregatesAreStable() {
        // Given
        StructType schema = new StructType(new StructField("x", DataTypes.DoubleType));
        StructField response = new StructField("y", DataTypes.DoubleType);
        RegressionNode leaf3 = new RegressionNode(3, 5.0, 5.0, 0.3);
        RegressionNode leaf4 = new RegressionNode(1, 1.0, 1.0, 0.1);
        RegressionNode leaf5 = new RegressionNode(2, 3.0, 3.0, 0.2);
        InternalNode node2 = new OrdinalNode(0, 0.5, 0.2, 0.4, leaf4, leaf5);
        TestCart cart = new TestCart(schema, response, new OrdinalNode(0, 1.5, 0.4, 0.9, node2, leaf3));

        // When
        String tree = cart.toString();
        String dot = cart.dot();

        // Then
        String expectedTree = String.join("\n",
                "n=6",
                "node), split, n, loss, yval, (yprob)",
                "* denotes terminal node",
                "1) root 6 0.90000 3.66667 ",
                " 2) x<=1.50000 3 0.40000 2.33333 ",
                "  4) x<=0.500000 1 0.10000 1.00000 *",
                "  5) x>0.500000 2 0.20000 3.00000 *",
                " 3) x>1.50000 3 0.30000 5.00000 *");
        assertEquals(expectedTree, tree);

        String expectedDot = "digraph CART {\n"
                + " node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n"
                + " edge [fontname=helvetica];\n"
                + " 1 [label=<x &le; 1.5<br/>size = 6<br/>impurity reduction = 0.4000>, fillcolor=\"#00000000\"];\n"
                + " 1 -> 2 [labeldistance=2.5, labelangle=45, headlabel=\"True\"];\n"
                + " 1 -> 3 [labeldistance=2.5, labelangle=-45, headlabel=\"False\"];\n"
                + " 2 [label=<x &le; 0.5<br/>size = 3<br/>impurity reduction = 0.2000>, fillcolor=\"#00000000\"];\n"
                + " 2 -> 4\n"
                + " 2 -> 5\n"
                + " 3 [label=<y = 5.0000<br/>size = 3<br/>deviance = 0.3000>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + " 4 [label=<y = 1.0000<br/>size = 1<br/>deviance = 0.1000>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + " 5 [label=<y = 3.0000<br/>size = 2<br/>deviance = 0.2000>, fillcolor=\"#00000000\", shape=ellipse];\n"
                + "}";
        assertEquals(expectedDot, dot);
    }
}

