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
import smile.tensor.Vector;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for activation and output functions in {@code smile.model.mlp}.
 */
public class ActivationAndOutputFunctionTest {
    @Test
    public void testGivenActivationFunctionsWhenApplyingForwardAndBackwardThenOutputsAreDeterministic() {
        // Given
        Vector rectifierInput = Vector.column(new double[] {-2.0, 0.0, 3.0});
        Vector rectifierGradient = Vector.column(new double[] {1.0, 1.0, 1.0});
        ActivationFunction rectifier = ActivationFunction.rectifier();

        Vector tanhOutput = Vector.column(new double[] {0.5, -0.5});
        Vector tanhGradient = Vector.column(new double[] {2.0, 2.0});
        ActivationFunction tanh = ActivationFunction.tanh();

        // When
        rectifier.f(rectifierInput);
        rectifier.g(rectifierGradient, rectifierInput);
        tanh.g(tanhGradient, tanhOutput);

        // Then
        assertArrayEquals(new double[] {0.0, 0.0, 3.0}, rectifierInput.toArray(new double[3]), 1E-12);
        assertArrayEquals(new double[] {0.0, 0.0, 1.0}, rectifierGradient.toArray(new double[3]), 1E-12);
        assertArrayEquals(new double[] {1.5, 1.5}, tanhGradient.toArray(new double[2]), 1E-12);
    }

    @Test
    public void testGivenInvalidLeakyParameterWhenCreatingActivationThenThrowsMeaningfulException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> ActivationFunction.leaky(-0.1));
        assertThrows(IllegalArgumentException.class, () -> ActivationFunction.leaky(1.0));
    }

    @Test
    public void testGivenOutputFunctionsWhenApplyingGradientsThenGuardrailsHold() {
        // Given
        Vector linearGradient = Vector.column(new double[] {1.0});
        Vector linearOutput = Vector.column(new double[] {0.2});
        Vector sigmoidGradient = Vector.column(new double[] {1.0, -1.0});
        Vector sigmoidOutput = Vector.column(new double[] {0.25, 0.75});
        Vector softmaxInput = Vector.column(new double[] {0.0, Math.log(2.0)});

        // When
        OutputFunction.SIGMOID.g(Cost.MEAN_SQUARED_ERROR, sigmoidGradient, sigmoidOutput);
        OutputFunction.SOFTMAX.f(softmaxInput);

        // Then
        assertArrayEquals(new double[] {0.1875, -0.1875}, sigmoidGradient.toArray(new double[2]), 1E-12);
        assertArrayEquals(new double[] {1.0 / 3.0, 2.0 / 3.0}, softmaxInput.toArray(new double[2]), 1E-12);
        assertThrows(IllegalStateException.class, () -> OutputFunction.LINEAR.g(Cost.LIKELIHOOD, linearGradient, linearOutput));
        assertThrows(IllegalStateException.class, () -> OutputFunction.SOFTMAX.g(Cost.MEAN_SQUARED_ERROR, linearGradient, linearOutput));
    }

    @Test
    public void givenLinearActivation_whenApplyingForwardAndBackward_thenInputIsUnchanged() {
        // Given: linear is the identity for both forward and backward
        ActivationFunction linear = ActivationFunction.linear();
        Vector x = Vector.column(new double[] {-3.0, 0.0, 5.0});
        Vector g = Vector.column(new double[] {1.0, -2.0, 3.0});
        Vector y = Vector.column(new double[] {-3.0, 0.0, 5.0});

        // When
        linear.f(x);
        linear.g(g, y);

        // Then
        assertArrayEquals(new double[] {-3.0, 0.0, 5.0}, x.toArray(new double[3]), 1E-12);
        assertArrayEquals(new double[] {1.0, -2.0, 3.0}, g.toArray(new double[3]), 1E-12);
        assertEquals("LINEAR", linear.name());
    }

    @Test
    public void givenSigmoidActivation_whenApplyingForward_thenOutputIsInUnitInterval() {
        // Given: σ(0)=0.5, σ(large)≈1, σ(-large)≈0
        ActivationFunction sigmoid = ActivationFunction.sigmoid();
        Vector x = Vector.column(new double[] {0.0, 100.0, -100.0});

        // When
        sigmoid.f(x);

        // Then
        assertEquals(0.5, x.get(0), 1E-10);
        assertEquals(1.0, x.get(1), 1E-10);
        assertEquals(0.0, x.get(2), 1E-10);
    }

    @Test
    public void givenSigmoidActivation_whenApplyingBackward_thenGradientScaledByDerivative() {
        // Given: g_i *= y_i*(1-y_i), with y_i = sigmoid outputs
        // For y = 0.5: derivative = 0.5 * 0.5 = 0.25; for y = 0.8: 0.8 * 0.2 = 0.16
        ActivationFunction sigmoid = ActivationFunction.sigmoid();
        Vector g = Vector.column(new double[] {1.0, 1.0});
        Vector y = Vector.column(new double[] {0.5, 0.8});

        // When
        sigmoid.g(g, y);

        // Then
        assertArrayEquals(new double[] {0.25, 0.16}, g.toArray(new double[2]), 1E-12);
    }

    @Test
    public void givenLeakyReLUActivation_whenApplyingForward_thenNegativeInputsLeakedByFactor() {
        // Given: max(a*x, x); a=0.1 → negative inputs scaled by 0.1
        ActivationFunction leaky = ActivationFunction.leaky(0.1);
        Vector x = Vector.column(new double[] {-4.0, 0.0, 3.0});

        // When
        leaky.f(x);

        // Then: -4*0.1=-0.4, 0 stays 0, 3 unchanged
        assertArrayEquals(new double[] {-0.4, 0.0, 3.0}, x.toArray(new double[3]), 1E-12);
    }

    @Test
    public void givenLeakyReLUActivation_whenApplyingBackward_thenNegativeRegionUsesAlpha() {
        // Given: g_i *= (y_i > 0 ? 1 : a); a=0.1
        ActivationFunction leaky = ActivationFunction.leaky(0.1);
        Vector g = Vector.column(new double[] {2.0, -3.0, 4.0});
        Vector y = Vector.column(new double[] {-0.4, 0.0, 3.0}); // post-activation values

        // When
        leaky.g(g, y);

        // Then: negative region → *0.1; non-positive threshold → *0.1; positive → *1
        assertArrayEquals(new double[] {0.2, -0.3, 4.0}, g.toArray(new double[3]), 1E-12);
    }

    @Test
    public void givenOutputLayerWithUnitWeight_whenComputingGradient_thenNoScalingApplied() {
        // weight == 1.0: gradient must equal target - output, multiplied by sigmoid derivative
        try (OutputLayer layer = new OutputLayer(2, 2, OutputFunction.SIGMOID, Cost.MEAN_SQUARED_ERROR)) {
            layer.output().set(0, 0.25);
            layer.output().set(1, 0.75);
            Vector target = Vector.column(new double[] {1.0, 0.0});

            // When: weight = 1.0 (neutral)
            layer.computeOutputGradient(target, 1.0);

            // Then: same as weight=2.0 result halved
            // δ = [0.75, -0.75], then sigmoid: [0.75*0.25*0.75, -0.75*0.75*0.25] = [0.140625, -0.140625]
            assertArrayEquals(new double[] {0.140625, -0.140625},
                    layer.gradient().toArray(new double[2]), 1E-12);
        }
    }
}

