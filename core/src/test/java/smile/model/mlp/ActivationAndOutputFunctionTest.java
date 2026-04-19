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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}

