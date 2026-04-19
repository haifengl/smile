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
package smile.feature.importance;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.model.cart.CART;
import smile.model.cart.LeafNode;
import smile.model.cart.RegressionNode;
import smile.model.cart.Split;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Synthetic coverage for SHAP aggregation helpers.
 */
public class ShapAggregationTest {
    private static final double TOLERANCE = 1E-12;

    @Test
    public void testGivenInstanceStreamWhenAggregatingShapValuesThenAbsoluteColumnMeansAreReturned() {
        // Given
        SHAP<double[]> shap = x -> new double[] {x[0], -2.0 * x[1]};

        // When
        double[] importance = shap.shap(Stream.of(
                new double[] {1.0, 2.0},
                new double[] {-3.0, 4.0}
        ));

        // Then
        assertArrayEquals(new double[] {2.0, 6.0}, importance, TOLERANCE);
    }

    @Test
    public void testGivenForestAndFormulaWhenComputingTreeShapThenPerTreeContributionsAreAveragedOverProjectedPredictors() {
        // Given
        StructType inputSchema = new StructType(
                new StructField("x2", DataTypes.DoubleType),
                new StructField("y", DataTypes.DoubleType),
                new StructField("x1", DataTypes.DoubleType)
        );
        DataFrame data = DataFrame.of(inputSchema, List.of(
                Tuple.of(inputSchema, new Object[] {-4.0, 10.0, 2.0}),
                Tuple.of(inputSchema, new Object[] {3.0, 20.0, -1.0})
        ));
        Formula formula = Formula.of("y", "x1", "x2");
        TreeSHAP shap = new StubTreeShap(
                formula,
                new CART[] {
                        new StubCart(1.0),
                        new StubCart(3.0)
                }
        );

        // When
        double[] tupleShap = shap.shap(data.get(0));
        double[] dataShap = shap.shap(data);

        // Then
        assertArrayEquals(new double[] {4.0, 8.0}, tupleShap, TOLERANCE);
        assertArrayEquals(new double[] {3.0, 7.0}, dataShap, TOLERANCE);
    }

    private record StubTreeShap(Formula formula, CART[] trees) implements TreeSHAP {
    }

    private static final class StubCart extends CART {
        private final double scale;

        StubCart(double scale) {
            super(
                    null,
                    new StructType(
                            new StructField("x1", DataTypes.DoubleType),
                            new StructField("x2", DataTypes.DoubleType)
                    ),
                    new StructField("y", DataTypes.DoubleType),
                    new RegressionNode(1, 0.0, 0.0, 0.0),
                    new double[2]
            );
            this.scale = scale;
        }

        @Override
        public double[] shap(Tuple x) {
            return new double[] {
                    scale * x.getDouble(0),
                    -scale * x.getDouble(1)
            };
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
}

