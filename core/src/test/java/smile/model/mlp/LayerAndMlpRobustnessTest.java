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

import java.util.Properties;
import org.junit.jupiter.api.Test;
import smile.tensor.Vector;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted robustness tests for layer builders and MLP guardrails.
 */
public class LayerAndMlpRobustnessTest {
    private static final double FLOAT_TOLERANCE = 1E-6;

    private static final class TestMLP extends MultilayerPerceptron {
        TestMLP(Layer... net) {
            super(net);
        }

        double forward(double... x) {
            propagate(vector(x), false);
            return output.output().get(0);
        }

        void backward(double y) {
            target.get().set(0, y);
            backpropagate(false);
        }
    }

    @Test
    public void testGivenLayerFactoriesWhenBuildingThenConfigurationIsPreserved() {
        // Given
        LayerBuilder[] builders = Layer.of(3, 2, "ReLU(4)|Leaky(2, 0.1, 0.2)");

        // When / Then
        try (HiddenLayer leaky = Layer.leaky(3).build(2)) {
            assertEquals(4, builders.length);
            assertEquals(2, builders[0].neurons());
            assertEquals("RECTIFIER(4)", builders[1].toString());
            assertEquals("LEAKEY_RECTIFIER(0.200000)(2)", builders[2].toString());
            assertEquals("SOFTMAX(3) | LIKELIHOOD", builders[3].toString());
            assertTrue(leaky.toString().startsWith("LEAKEY_RECTIFIER"));
            assertThrows(IllegalArgumentException.class, () -> Layer.builder("unknown", 3, 0.0, Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> Layer.of(2, 2, "BrokenLayer"));
        }
    }

    @Test
    public void testGivenOutputLayerWhenComputingOutputGradientThenScalingAndSizeChecksApply() {
        // Given
        try (OutputLayer layer = new OutputLayer(2, 2, OutputFunction.SIGMOID, Cost.MEAN_SQUARED_ERROR)) {
            layer.output().set(0, 0.25);
            layer.output().set(1, 0.75);
            Vector target = Vector.column(new double[] {1.0, 0.0});

            // When
            layer.computeOutputGradient(target, 2.0);

            // Then
            assertArrayEquals(new double[] {0.28125, -0.28125}, layer.gradient().toArray(new double[2]), 1E-12);
            assertThrows(IllegalArgumentException.class,
                    () -> layer.computeOutputGradient(Vector.column(new double[] {1.0}), 1.0));
            assertThrows(IllegalArgumentException.class,
                    () -> new OutputLayer(2, 2, OutputFunction.SOFTMAX, Cost.MEAN_SQUARED_ERROR));
            assertThrows(IllegalArgumentException.class,
                    () -> new OutputLayer(1, 2, OutputFunction.LINEAR, Cost.LIKELIHOOD));
        }
    }

    @Test
    public void testGivenMlpGuardrailsWhenConstructingAndSettingParametersThenStateIsUpdated() {
        // Given
        Properties params = new Properties();
        params.setProperty("smile.mlp.learning_rate", "0.05");
        params.setProperty("smile.mlp.momentum", "0.25");
        params.setProperty("smile.mlp.weight_decay", "0.001");
        params.setProperty("smile.mlp.clip_value", "0.5");
        params.setProperty("smile.mlp.clip_norm", "1.5");
        params.setProperty("smile.mlp.RMSProp.rho", "0.9");

        try (TestMLP mlp = new TestMLP(
                new InputLayer(2),
                new HiddenLayer(3, 2, 0.0, ActivationFunction.rectifier()),
                new OutputLayer(1, 3, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR)
        )) {
            // When
            mlp.setParameters(params);

            // Then
            assertEquals(0.05, mlp.getLearningRate(), 1E-12);
            assertEquals(0.25, mlp.getMomentum(), 1E-12);
            assertEquals(0.001, mlp.getWeightDecay(), 1E-12);
            assertEquals(0.5, mlp.getClipValue(), 1E-12);
            assertEquals(1.5, mlp.getClipNorm(), 1E-12);
            assertTrue(mlp.toString().contains("learning rate = 0.050000"));
            assertTrue(mlp.toString().contains("momentum = 0.250000"));
            assertTrue(mlp.toString().contains("RMSProp = 0.900000"));

            assertThrows(IllegalArgumentException.class, () -> new TestMLP(new InputLayer(2), new OutputLayer(1, 2, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR)));
            assertThrows(IllegalArgumentException.class, () -> new TestMLP(new HiddenLayer(2, 2, 0.0, ActivationFunction.rectifier()), new HiddenLayer(2, 2, 0.0, ActivationFunction.rectifier()), new OutputLayer(1, 2, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR)));
            assertThrows(IllegalArgumentException.class, () -> new TestMLP(new InputLayer(2), new HiddenLayer(3, 4, 0.0, ActivationFunction.rectifier()), new OutputLayer(1, 3, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR)));
            assertThrows(IllegalArgumentException.class, () -> mlp.setRMSProp(1.0, 1E-7));
            assertThrows(IllegalArgumentException.class, () -> mlp.setRMSProp(0.9, 0.0));
            assertThrows(IllegalArgumentException.class, () -> mlp.setWeightDecay(-1.0));
            assertThrows(IllegalArgumentException.class, () -> mlp.setClipValue(-1.0));
            assertThrows(IllegalArgumentException.class, () -> mlp.setClipNorm(-1.0));
        }
    }

    @Test
    public void testGivenHandWiredNetworkWhenForwardAndBackpropagatingThenSignalsAndGradientsMatch() {
        // Given
        try (InputLayer input = new InputLayer(2);
             HiddenLayer hidden = new HiddenLayer(1, 2, 0.0, ActivationFunction.linear());
             OutputLayer output = new OutputLayer(1, 1, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR);
             TestMLP mlp = new TestMLP(input, hidden, output)) {
            hidden.weight().set(0, 0, 0.5);
            hidden.weight().set(0, 1, -1.0);
            hidden.bias().set(0, 0.1);
            output.weight().set(0, 0, 2.0);
            output.bias().set(0, -0.3);

            // When
            double prediction = mlp.forward(1.0, 2.0);
            mlp.backward(1.0);

            // Then
            assertEquals(-3.1, prediction, FLOAT_TOLERANCE);
            assertEquals(-1.4, hidden.output().get(0), FLOAT_TOLERANCE);
            assertEquals(4.1, output.gradient().get(0), FLOAT_TOLERANCE);
            assertEquals(8.2, hidden.gradient().get(0), FLOAT_TOLERANCE);
            assertEquals(8.2, hidden.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(16.4, hidden.weightGradient.get().get(0, 1), FLOAT_TOLERANCE);
            assertEquals(8.2, hidden.biasGradient.get().get(0), FLOAT_TOLERANCE);
            assertEquals(-5.74, output.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(4.1, output.biasGradient.get().get(0), FLOAT_TOLERANCE);
        }
    }
}


