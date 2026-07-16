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

import java.io.*;
import org.junit.jupiter.api.Test;
import smile.tensor.Vector;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InputLayer}.
 */
public class InputLayerTest {

    @Test
    public void givenInputLayer_whenPropagating_thenOutputCopiesInput() {
        // Given
        try (InputLayer layer = new InputLayer(3)) {
            Vector x = Vector.column(new double[]{1.0, -2.0, 3.5});

            // When
            layer.propagate(x);

            // Then: the output matches the input
            assertEquals(1.0,  layer.output().get(0), 1E-10);
            assertEquals(-2.0, layer.output().get(1), 1E-10);
            assertEquals(3.5,  layer.output().get(2), 1E-10);
        }
    }

    @Test
    public void givenInputLayer_whenCheckingDimensions_thenMatchConstructorArg() {
        // Given
        try (InputLayer layer = new InputLayer(5)) {
            // Then
            assertEquals(5, layer.getOutputSize());
            assertEquals(5, layer.getInputSize());
        }
    }

    @Test
    public void givenInputLayerWithoutDropout_whenToString_thenReturnsSimpleFormat() {
        // Given
        try (InputLayer layer = new InputLayer(4)) {
            // Then
            assertEquals("Input(4)", layer.toString());
        }
    }

    @Test
    public void givenInputLayerWithDropout_whenToString_thenIncludesDropoutRate() {
        // Given
        try (InputLayer layer = new InputLayer(4, 0.3)) {
            // Then
            assertEquals("Input(4, 0.30)", layer.toString());
        }
    }

    @Test
    public void givenInputLayer_whenCallingTrainingOps_thenThrowUnsupportedOperationException() {
        // Given: training-specific methods must not be called on an input layer
        try (InputLayer layer = new InputLayer(2)) {
            assertThrows(UnsupportedOperationException.class,
                    () -> layer.backpropagate(null));
            assertThrows(UnsupportedOperationException.class,
                    () -> layer.computeGradient(null));
            assertThrows(UnsupportedOperationException.class,
                    () -> layer.computeGradientUpdate(null, 0.1, 0.0, 1.0));
            assertThrows(UnsupportedOperationException.class,
                    () -> layer.update(1, 0.1, 0.0, 1.0, 0.0, 1E-7));
        }
    }

    @Test
    public void givenInputLayer_whenSerializedAndDeserialized_thenPropagateStillWorks()
            throws IOException, ClassNotFoundException {
        // Given: serialize an InputLayer, then deserialize it
        InputLayer original = new InputLayer(3);

        byte[] bytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
            bytes = baos.toByteArray();
        }
        original.close();

        InputLayer restored;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            restored = (InputLayer) ois.readObject();
        }

        // When: propagate through the restored layer
        try (restored) {
            Vector x = Vector.column(new double[]{7.0, -1.5, 0.0});
            restored.propagate(x);

            // Then: output matches input without NPE in readObject
            assertEquals(7.0,  restored.output().get(0), 1E-10);
            assertEquals(-1.5, restored.output().get(1), 1E-10);
            assertEquals(0.0,  restored.output().get(2), 1E-10);
        }
    }
}
