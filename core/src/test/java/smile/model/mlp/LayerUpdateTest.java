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
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct tests for {@link Layer#update(int, double, double, double, double, double)}.
 */
public class LayerUpdateTest {
    private static final double FLOAT_TOLERANCE = 1E-6;

    @Test
    public void testGivenMultiNeuronGradientsWhenUpdatingWithoutMomentumThenAverageDecayAndResetApply() {
        // Given
        try (HiddenLayer layer = new HiddenLayer(2, 2, 0.0, ActivationFunction.linear())) {
            layer.weight().set(0, 0, 1.0);
            layer.weight().set(0, 1, 2.0);
            layer.weight().set(1, 0, 3.0);
            layer.weight().set(1, 1, 4.0);
            layer.bias().set(0, 0.5);
            layer.bias().set(1, -0.5);

            layer.weightGradient.get().set(0, 0, 2.0);
            layer.weightGradient.get().set(0, 1, -4.0);
            layer.weightGradient.get().set(1, 0, 6.0);
            layer.weightGradient.get().set(1, 1, -8.0);
            layer.biasGradient.get().set(0, 1.0);
            layer.biasGradient.get().set(1, -2.0);

            // When
            layer.update(2, 0.2, 0.0, 0.95, 0.0, 1E-7);

            // Then
            assertEquals(1.14, layer.weight().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(1.52, layer.weight().get(0, 1), FLOAT_TOLERANCE);
            assertEquals(3.42, layer.weight().get(1, 0), FLOAT_TOLERANCE);
            assertEquals(3.04, layer.weight().get(1, 1), FLOAT_TOLERANCE);
            assertEquals(0.6, layer.bias().get(0), FLOAT_TOLERANCE);
            assertEquals(-0.7, layer.bias().get(1), FLOAT_TOLERANCE);
            assertEquals(0.0, layer.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(0.0, layer.weightGradient.get().get(1, 1), FLOAT_TOLERANCE);
            assertEquals(0.0, layer.biasGradient.get().get(0), FLOAT_TOLERANCE);
            assertEquals(0.0, layer.biasGradient.get().get(1), FLOAT_TOLERANCE);
        }
    }

    @Test
    public void testGivenMomentumAcrossMultipleUpdatesWhenUpdatingLayerThenVelocityAccumulatesPerEntry() {
        // Given
        try (HiddenLayer layer = new HiddenLayer(2, 2, 0.0, ActivationFunction.linear())) {
            layer.weight().fill(0.0);
            layer.bias().fill(0.0);

            layer.weightGradient.get().set(0, 0, 1.0);
            layer.weightGradient.get().set(0, 1, 2.0);
            layer.weightGradient.get().set(1, 0, 3.0);
            layer.weightGradient.get().set(1, 1, 4.0);
            layer.biasGradient.get().set(0, 0.5);
            layer.biasGradient.get().set(1, -1.0);
            layer.update(1, 0.1, 0.5, 1.0, 0.0, 1E-7);

            layer.weightGradient.get().set(0, 0, -2.0);
            layer.weightGradient.get().set(0, 1, 1.0);
            layer.weightGradient.get().set(1, 0, 0.5);
            layer.weightGradient.get().set(1, 1, -1.0);
            layer.biasGradient.get().set(0, -0.5);
            layer.biasGradient.get().set(1, 0.25);

            // When
            layer.update(1, 0.1, 0.5, 1.0, 0.0, 1E-7);

            // Then
            assertEquals(-0.05, layer.weight().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(0.4, layer.weight().get(0, 1), FLOAT_TOLERANCE);
            assertEquals(0.5, layer.weight().get(1, 0), FLOAT_TOLERANCE);
            assertEquals(0.5, layer.weight().get(1, 1), FLOAT_TOLERANCE);
            assertEquals(0.025, layer.bias().get(0), FLOAT_TOLERANCE);
            assertEquals(-0.125, layer.bias().get(1), FLOAT_TOLERANCE);

            assertEquals(-0.15, layer.weightUpdate.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(0.2, layer.weightUpdate.get().get(0, 1), FLOAT_TOLERANCE);
            assertEquals(0.2, layer.weightUpdate.get().get(1, 0), FLOAT_TOLERANCE);
            assertEquals(0.1, layer.weightUpdate.get().get(1, 1), FLOAT_TOLERANCE);
            assertEquals(-0.025, layer.biasUpdate.get().get(0), FLOAT_TOLERANCE);
            assertEquals(-0.025, layer.biasUpdate.get().get(1), FLOAT_TOLERANCE);
        }
    }

    @Test
    public void testGivenMultiNeuronRmsPropWhenUpdatingThenNormalizationIsPerEntryAndMomentsAreStored() {
        // Given
        try (HiddenLayer layer = new HiddenLayer(2, 2, 0.0, ActivationFunction.linear())) {
            layer.weight().fill(0.0);
            layer.bias().fill(0.0);

            layer.weightGradient.get().set(0, 0, 2.0);
            layer.weightGradient.get().set(0, 1, 4.0);
            layer.weightGradient.get().set(1, 0, 6.0);
            layer.weightGradient.get().set(1, 1, 8.0);
            layer.biasGradient.get().set(0, 1.0);
            layer.biasGradient.get().set(1, 2.0);

            // When
            layer.update(2, 0.1, 0.0, 1.0, 0.5, 1E-7);

            // Then
            assertEquals(0.1414213, layer.weight().get(0, 0), 1E-5);
            assertEquals(0.1414213, layer.weight().get(0, 1), 1E-5);
            assertEquals(0.1414213, layer.weight().get(1, 0), 1E-5);
            assertEquals(0.1414213, layer.weight().get(1, 1), 1E-5);
            assertEquals(0.1414213, layer.bias().get(0), 1E-5);
            assertEquals(0.1414213, layer.bias().get(1), 1E-5);

            assertEquals(0.5, layer.weightGradientMoment2.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(2.0, layer.weightGradientMoment2.get().get(0, 1), FLOAT_TOLERANCE);
            assertEquals(4.5, layer.weightGradientMoment2.get().get(1, 0), FLOAT_TOLERANCE);
            assertEquals(8.0, layer.weightGradientMoment2.get().get(1, 1), FLOAT_TOLERANCE);
            assertEquals(0.125, layer.biasGradientMoment2.get().get(0), FLOAT_TOLERANCE);
            assertEquals(0.5, layer.biasGradientMoment2.get().get(1), FLOAT_TOLERANCE);
            assertEquals(0.0, layer.weightGradient.get().get(0, 0), FLOAT_TOLERANCE);
            assertEquals(0.0, layer.biasGradient.get().get(1), FLOAT_TOLERANCE);
        }
    }
}


