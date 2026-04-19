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
package smile.model.mlp;

import org.junit.jupiter.api.Test;
import smile.util.function.TimeFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct tests for MLP optimizer paths.
 */
public class MultilayerPerceptronOptimizerTest {
    private static final double FLOAT_TOLERANCE = 1E-6;

    private static final class TestMLP extends MultilayerPerceptron {
        TestMLP(Layer... net) {
            super(net);
        }

        void directStep(double x, double y) {
            propagate(vector(new double[] {x}), false);
            target.get().set(0, y);
            backpropagate(true);
        }

        void accumulate(double x, double y) {
            propagate(vector(new double[] {x}), false);
            target.get().set(0, y);
            backpropagate(false);
        }

        void batchUpdate(int m) {
            update(m);
        }
    }

    @Test
    public void testGivenMomentumWhenRunningDirectUpdatesThenVelocityAccumulatesAcrossSteps() {
        // Given
        try (InputLayer input = new InputLayer(1);
             HiddenLayer hidden = new HiddenLayer(1, 1, 0.0, ActivationFunction.linear());
             OutputLayer output = new OutputLayer(1, 1, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR);
             TestMLP mlp = new TestMLP(input, hidden, output)) {
            hidden.weight().set(0, 0, 1.0);
            hidden.bias().set(0, 0.0);
            output.weight().set(0, 0, 1.0);
            output.bias().set(0, 0.0);
            mlp.setLearningRate(TimeFunction.constant(0.1));
            mlp.setMomentum(TimeFunction.constant(0.5));

            // When
            mlp.directStep(1.0, 0.0);
            mlp.directStep(1.0, 0.0);

            // Then
            assertEquals(0.7942, hidden.weight().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(-0.2058, hidden.bias().get(0), FLOAT_TOLERANCE);
            assertEquals(0.8004, output.weight().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(-0.2120, output.bias().get(0), FLOAT_TOLERANCE);
        }
    }

    @Test
    public void testGivenRmsPropWhenUpdatingMiniBatchThenGradientIsNormalizedBeforeStep() {
        // Given
        try (InputLayer input = new InputLayer(1);
             HiddenLayer hidden = new HiddenLayer(1, 1, 0.0, ActivationFunction.linear());
             OutputLayer output = new OutputLayer(1, 1, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR);
             TestMLP mlp = new TestMLP(input, hidden, output)) {
            hidden.weight().set(0, 0, 1.0);
            hidden.bias().set(0, 0.0);
            output.weight().set(0, 0, 1.0);
            output.bias().set(0, 0.0);
            mlp.setLearningRate(TimeFunction.constant(0.1));
            mlp.setRMSProp(0.9, 1E-7);

            // When
            mlp.accumulate(1.0, 0.0);
            mlp.batchUpdate(1);

            // Then
            assertEquals(0.6837724, hidden.weight().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(-0.3162276, hidden.bias().get(0), FLOAT_TOLERANCE);
            assertEquals(0.6837724, output.weight().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(-0.3162276, output.bias().get(0), FLOAT_TOLERANCE);
            assertEquals(0.0, hidden.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(0.0, output.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
        }
    }

    @Test
    public void testGivenClipValueWhenBackpropagatingThenActivationGradientsAreClippedBeforeWeightGradients() {
        // Given
        try (InputLayer input = new InputLayer(1);
             HiddenLayer hidden = new HiddenLayer(1, 1, 0.0, ActivationFunction.linear());
             OutputLayer output = new OutputLayer(1, 1, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR);
             TestMLP mlp = new TestMLP(input, hidden, output)) {
            hidden.weight().set(0, 0, 1.0);
            output.weight().set(0, 0, 1.0);
            mlp.setClipValue(0.25);

            // When
            mlp.accumulate(4.0, 0.0);

            // Then
            assertEquals(-0.25, output.gradient().get(0), FLOAT_TOLERANCE);
            assertEquals(-0.25, hidden.gradient().get(0), FLOAT_TOLERANCE);
            assertEquals(-1.0, hidden.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(-1.0, output.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
        }
    }

    @Test
    public void testGivenClipNormWhenBackpropagatingThenGradientNormIsBounded() {
        // Given
        try (InputLayer input = new InputLayer(1);
             HiddenLayer hidden = new HiddenLayer(1, 1, 0.0, ActivationFunction.linear());
             OutputLayer output = new OutputLayer(1, 1, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR);
             TestMLP mlp = new TestMLP(input, hidden, output)) {
            hidden.weight().set(0, 0, 1.0);
            output.weight().set(0, 0, 1.0);
            mlp.setClipNorm(0.5);

            // When
            mlp.accumulate(4.0, 0.0);

            // Then
            assertEquals(-0.5, output.gradient().get(0), FLOAT_TOLERANCE);
            assertEquals(-0.5, hidden.gradient().get(0), FLOAT_TOLERANCE);
            assertEquals(-2.0, hidden.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(-2.0, output.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
        }
    }
}

