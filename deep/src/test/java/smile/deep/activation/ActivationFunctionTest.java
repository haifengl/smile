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
package smile.deep.activation;

import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for all activation functions in {@code smile.deep.activation}.
 *
 * <p>Each test follows the Given/When/Then structure and uses small synthetic
 * CPU tensors — no GPU or dataset required.
 */
public class ActivationFunctionTest {

    private static final float DELTA = 1e-4f;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Creates a 1-D float32 tensor from the given values. */
    private Tensor tensor(float... values) {
        return Tensor.of(values, values.length);
    }

    /** Returns the float array of a tensor's contiguous storage. */
    private float[] floats(Tensor t) {
        Tensor c = t.contiguous();
        float[] arr = c.floatArray();
        c.close();
        return arr;
    }

    // -----------------------------------------------------------------------
    // ReLU
    // -----------------------------------------------------------------------

    @Test
    public void testGivenReLUWhenForwardThenNegativesAreZeroed() {
        // Given
        ReLU relu = new ReLU(false);
        Tensor input = tensor(-2f, -1f, 0f, 1f, 2f);

        // When
        Tensor output = relu.forward(input);

        // Then
        float[] out = floats(output);
        assertArrayEquals(new float[]{0f, 0f, 0f, 1f, 2f}, out, DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenReLUInplaceWhenForwardThenSameObjectReturned() {
        // Given
        ReLU relu = new ReLU(true);
        Tensor input = tensor(-1f, 0f, 1f);

        // When
        Tensor output = relu.forward(input);

        // Then — inplace must return the same tensor
        assertSame(input, output);
        assertArrayEquals(new float[]{0f, 0f, 1f}, floats(output), DELTA);
        input.close();
    }

    @Test
    public void testGivenReLUWhenInspectingNameThenCorrect() {
        // Given / When
        ReLU relu = new ReLU(false);
        // Then
        assertEquals("ReLU", relu.name());
        assertFalse(relu.isInplace());
    }

    // -----------------------------------------------------------------------
    // GELU
    // -----------------------------------------------------------------------

    @Test
    public void testGivenGELUWhenForwardAtZeroThenOutputIsZero() {
        // Given
        GELU gelu = new GELU(false);
        Tensor input = tensor(0f);

        // When
        Tensor output = gelu.forward(input);

        // Then: GELU(0) = 0
        assertArrayEquals(new float[]{0f}, floats(output), DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenGELUWhenForwardOnLargePositiveThenApproximatesIdentity() {
        // Given — for x >> 0, GELU(x) ≈ x
        GELU gelu = new GELU(false);
        Tensor input = tensor(10f);

        // When
        Tensor output = gelu.forward(input);

        // Then
        assertEquals(10f, floats(output)[0], 0.01f);
        input.close(); output.close();
    }

    @Test
    public void testGivenGELUInplaceWhenForwardThenSameObjectReturned() {
        // Given
        GELU gelu = new GELU(true);
        Tensor input = tensor(1f, 2f);

        // When
        Tensor output = gelu.forward(input);

        // Then
        assertSame(input, output);
        input.close();
    }

    // -----------------------------------------------------------------------
    // SiLU (Swish)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSiLUWhenForwardAtZeroThenOutputIsZero() {
        // Given
        SiLU silu = new SiLU(false);
        Tensor input = tensor(0f);

        // When
        Tensor output = silu.forward(input);

        // Then: SiLU(0) = 0 * sigmoid(0) = 0
        assertArrayEquals(new float[]{0f}, floats(output), DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenSiLUInplaceWhenForwardThenSameObjectReturned() {
        // Given
        SiLU silu = new SiLU(true);
        Tensor input = tensor(-1f, 0f, 1f);

        // When
        Tensor output = silu.forward(input);

        // Then
        assertSame(input, output);
        input.close();
    }

    // -----------------------------------------------------------------------
    // Sigmoid
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSigmoidWhenForwardThenOutputsInZeroToOne() {
        // Given
        Sigmoid sigmoid = new Sigmoid(false);
        Tensor input = tensor(-10f, -1f, 0f, 1f, 10f);

        // When
        Tensor output = sigmoid.forward(input);

        // Then
        for (float v : floats(output)) {
            assertTrue(v >= 0f && v <= 1f, "sigmoid output out of [0,1]: " + v);
        }
        input.close(); output.close();
    }

    @Test
    public void testGivenSigmoidWhenForwardAtZeroThenOutputIsHalf() {
        // Given
        Sigmoid sigmoid = new Sigmoid(false);
        Tensor input = tensor(0f);

        // When
        Tensor output = sigmoid.forward(input);

        // Then: sigmoid(0) = 0.5
        assertEquals(0.5f, floats(output)[0], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenSigmoidInplaceWhenForwardThenSameObjectReturned() {
        // Given
        Sigmoid sigmoid = new Sigmoid(true);
        Tensor input = tensor(0f, 1f);

        // When
        Tensor output = sigmoid.forward(input);

        // Then
        assertSame(input, output);
        input.close();
    }

    // -----------------------------------------------------------------------
    // Tanh
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTanhWhenForwardThenOutputsInMinusOneToOne() {
        // Given
        Tanh tanh = new Tanh(false);
        Tensor input = tensor(-5f, -1f, 0f, 1f, 5f);

        // When
        Tensor output = tanh.forward(input);

        // Then
        for (float v : floats(output)) {
            assertTrue(v >= -1f && v <= 1f, "tanh output out of [-1,1]: " + v);
        }
        input.close(); output.close();
    }

    @Test
    public void testGivenTanhWhenForwardAtZeroThenOutputIsZero() {
        // Given
        Tanh tanh = new Tanh(false);
        Tensor input = tensor(0f);

        // When
        Tensor output = tanh.forward(input);

        // Then
        assertEquals(0f, floats(output)[0], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenTanhInplaceWhenForwardThenSameObjectReturned() {
        // Given
        Tanh tanh = new Tanh(true);
        Tensor input = tensor(0f, 1f);

        // When
        Tensor output = tanh.forward(input);

        // Then
        assertSame(input, output);
        input.close();
    }

    // -----------------------------------------------------------------------
    // LeakyReLU
    // -----------------------------------------------------------------------

    @Test
    public void testGivenLeakyReLUWhenForwardOnNegativesThenScaledBySlope() {
        // Given — slope 0.1
        LeakyReLU leaky = new LeakyReLU(0.1, false);
        Tensor input = tensor(-2f, -1f, 0f, 1f, 2f);

        // When
        Tensor output = leaky.forward(input);

        // Then
        float[] out = floats(output);
        assertEquals(-0.2f, out[0], DELTA);
        assertEquals(-0.1f, out[1], DELTA);
        assertEquals(0f,    out[2], DELTA);
        assertEquals(1f,    out[3], DELTA);
        assertEquals(2f,    out[4], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenLeakyReLUDefaultConstructorWhenForwardThenSlopeIs0_01() {
        // Given
        LeakyReLU leaky = new LeakyReLU();
        Tensor input = tensor(-100f);

        // When
        Tensor output = leaky.forward(input);

        // Then: default slope = 0.01
        assertEquals(-1f, floats(output)[0], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenLeakyReLUInplaceWhenForwardThenSameObjectReturned() {
        // Given
        LeakyReLU leaky = new LeakyReLU(0.01, true);
        Tensor input = tensor(-1f, 0f, 1f);

        // When
        Tensor output = leaky.forward(input);

        // Then
        assertSame(input, output);
        input.close();
    }

    @Test
    public void testGivenLeakyReLUWhenNameThenContainsSlope() {
        // Given / When
        LeakyReLU leaky = new LeakyReLU(0.2, false);

        // Then
        assertTrue(leaky.name().contains("0.2000"), "name should include slope, got: " + leaky.name());
    }

    // -----------------------------------------------------------------------
    // ELU
    // -----------------------------------------------------------------------

    @Test
    public void testGivenELUWhenForwardOnPositivesThenIdentity() {
        // Given
        ELU elu = new ELU(1.0, false);
        Tensor input = tensor(0f, 1f, 2f);

        // When
        Tensor output = elu.forward(input);

        // Then: ELU(x) = x for x >= 0
        assertArrayEquals(new float[]{0f, 1f, 2f}, floats(output), DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenELUWhenForwardOnNegativesThenSaturates() {
        // Given — alpha=1
        ELU elu = new ELU(1.0, false);
        Tensor input = tensor(-100f);

        // When
        Tensor output = elu.forward(input);

        // Then: ELU(-inf) -> -1 (alpha * (exp(-inf) - 1) ≈ -1)
        assertEquals(-1f, floats(output)[0], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenELUInplaceWhenForwardThenSameObjectReturned() {
        // Given
        ELU elu = new ELU(1.0, true);
        Tensor input = tensor(1f, 2f);

        // When
        Tensor output = elu.forward(input);

        // Then
        assertSame(input, output);
        input.close();
    }

    @Test
    public void testGivenELUWhenNegativeAlphaThenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ELU(-0.1, false));
    }

    // -----------------------------------------------------------------------
    // Hardswish
    // -----------------------------------------------------------------------

    @Test
    public void testGivenHardswishWhenForwardAtZeroThenOutputIsZero() {
        // Given
        Hardswish hs = new Hardswish(false);
        Tensor input = tensor(0f);

        // When
        Tensor output = hs.forward(input);

        // Then: hardswish(0) = 0 * relu6(3)/6 = 0
        assertEquals(0f, floats(output)[0], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenHardswishWhenForwardOnLargePositiveThenApproximatesIdentity() {
        // Given — for x >= 3, hardswish(x) = x
        Hardswish hs = new Hardswish(false);
        Tensor input = tensor(5f, 10f);

        // When
        Tensor output = hs.forward(input);

        // Then
        float[] out = floats(output);
        assertEquals(5f,  out[0], DELTA);
        assertEquals(10f, out[1], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenHardswishWhenForwardOnSmallNegativeThenOutputIsZero() {
        // Given — for x <= -3, hardswish(x) = 0
        Hardswish hs = new Hardswish(false);
        Tensor input = tensor(-5f, -10f);

        // When
        Tensor output = hs.forward(input);

        // Then
        for (float v : floats(output)) {
            assertEquals(0f, v, DELTA);
        }
        input.close(); output.close();
    }

    @Test
    public void testGivenHardswishInplaceWhenForwardThenSameObjectReturned() {
        // Given
        Hardswish hs = new Hardswish(true);
        Tensor input = tensor(1f, 2f);

        // When
        Tensor output = hs.forward(input);

        // Then
        assertSame(input, output);
        input.close();
    }

    // -----------------------------------------------------------------------
    // Mish
    // -----------------------------------------------------------------------

    @Test
    public void testGivenMishWhenForwardAtZeroThenOutputIsZero() {
        // Given
        Mish mish = new Mish(false);
        Tensor input = tensor(0f);

        // When
        Tensor output = mish.forward(input);

        // Then: mish(0) = 0 * tanh(softplus(0)) = 0
        assertEquals(0f, floats(output)[0], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenMishWhenForwardOnLargePositiveThenApproximatesIdentity() {
        // Given
        Mish mish = new Mish(false);
        Tensor input = tensor(10f);

        // When
        Tensor output = mish.forward(input);

        // Then: mish(10) ≈ 10
        assertEquals(10f, floats(output)[0], 0.01f);
        input.close(); output.close();
    }

    @Test
    public void testGivenMishInplaceWhenForwardThenSameObjectReturned() {
        // Given
        Mish mish = new Mish(true);
        Tensor input = tensor(1f, 2f);

        // When
        Tensor output = mish.forward(input);

        // Then
        assertSame(input, output);
        input.close();
    }

    // -----------------------------------------------------------------------
    // Softmax
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSoftmaxWhenForwardThenOutputSumsToOne() {
        // Given — 2-D [1, 4] tensor
        Softmax softmax = new Softmax();
        Tensor input = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 1, 4);

        // When
        Tensor output = softmax.forward(input);

        // Then
        float[] out = floats(output);
        float sum = 0f;
        for (float v : out) {
            assertTrue(v >= 0f, "softmax output must be >= 0: " + v);
            sum += v;
        }
        assertEquals(1f, sum, DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenSoftmaxWithCustomDimWhenForwardThenSumsToOneAlongThatDim() {
        // Given — dim=0, shape [4, 1]
        Softmax softmax = new Softmax(0);
        Tensor input = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 4, 1);

        // When
        Tensor output = softmax.forward(input);

        // Then: sum along dim 0 = 1
        float sum = 0f;
        for (float v : floats(output)) sum += v;
        assertEquals(1f, sum, DELTA);
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // LogSoftmax
    // -----------------------------------------------------------------------

    @Test
    public void testGivenLogSoftmaxWhenForwardThenAllOutputsNonPositive() {
        // Given — 2-D [1, 4]
        LogSoftmax lsm = new LogSoftmax();
        Tensor input = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 1, 4);

        // When
        Tensor output = lsm.forward(input);

        // Then: log(softmax) <= 0
        for (float v : floats(output)) {
            assertTrue(v <= 1e-5f, "log-softmax must be <= 0: " + v);
        }
        input.close(); output.close();
    }

    @Test
    public void testGivenLogSoftmaxWithCustomDimWhenForwardThenAllOutputsNonPositive() {
        // Given — dim=0, shape [4, 1]
        LogSoftmax lsm = new LogSoftmax(0);
        Tensor input = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 4, 1);

        // When
        Tensor output = lsm.forward(input);

        // Then
        for (float v : floats(output)) {
            assertTrue(v <= 1e-5f, "log-softmax must be <= 0: " + v);
        }
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // GLU
    // -----------------------------------------------------------------------

    @Test
    public void testGivenGLUWhenForwardThenOutputHalfInputLastDim() {
        // Given — GLU splits last dim in half
        GLU glu = new GLU();
        Tensor input = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 1, 4);

        // When
        Tensor output = glu.forward(input);

        // Then: output last-dim = 4/2 = 2
        assertEquals(1, output.size(0));
        assertEquals(2, output.size(1));
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // LogSigmoid
    // -----------------------------------------------------------------------

    @Test
    public void testGivenLogSigmoidWhenForwardThenAllOutputsNonPositive() {
        // Given
        LogSigmoid ls = new LogSigmoid();
        Tensor input = tensor(-5f, -1f, 0f, 1f, 5f);

        // When
        Tensor output = ls.forward(input);

        // Then: log(sigmoid(x)) <= 0 for all x
        for (float v : floats(output)) {
            assertTrue(v <= 1e-5f, "log-sigmoid must be <= 0: " + v);
        }
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // HardShrink
    // -----------------------------------------------------------------------

    @Test
    public void testGivenHardShrinkWhenForwardThenValuesWithinLambdaAreZeroed() {
        // Given — lambda=0.5: values in (-0.5, 0.5) -> 0
        HardShrink hs = new HardShrink(0.5);
        Tensor input = tensor(-1f, -0.3f, 0f, 0.3f, 1f);

        // When
        Tensor output = hs.forward(input);

        // Then
        float[] out = floats(output);
        assertEquals(-1f, out[0], DELTA);
        assertEquals(0f,  out[1], DELTA);
        assertEquals(0f,  out[2], DELTA);
        assertEquals(0f,  out[3], DELTA);
        assertEquals(1f,  out[4], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenHardShrinkWhenNegativeLambdaThenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new HardShrink(-0.1));
    }

    // -----------------------------------------------------------------------
    // SoftShrink
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSoftShrinkWhenForwardThenValuesWithinLambdaAreZeroed() {
        // Given — lambda=0.5: values in [-0.5, 0.5] -> 0, else x - sign(x)*lambda
        SoftShrink ss = new SoftShrink(0.5);
        Tensor input = tensor(-1f, -0.3f, 0f, 0.3f, 1f);

        // When
        Tensor output = ss.forward(input);

        // Then
        float[] out = floats(output);
        assertEquals(-0.5f, out[0], DELTA);
        assertEquals(0f,    out[1], DELTA);
        assertEquals(0f,    out[2], DELTA);
        assertEquals(0f,    out[3], DELTA);
        assertEquals(0.5f,  out[4], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenSoftShrinkWhenNegativeLambdaThenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SoftShrink(-0.1));
    }

    // -----------------------------------------------------------------------
    // TanhShrink
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTanhShrinkWhenForwardAtZeroThenOutputIsZero() {
        // Given
        TanhShrink ts = new TanhShrink();
        Tensor input = tensor(0f);

        // When
        Tensor output = ts.forward(input);

        // Then: tanhshrink(0) = 0 - tanh(0) = 0
        assertEquals(0f, floats(output)[0], DELTA);
        input.close(); output.close();
    }

    @Test
    public void testGivenTanhShrinkWhenForwardOnLargePositiveThenApproximatesXMinusOne() {
        // Given — for large x, tanh(x) ≈ 1, so tanhshrink(x) ≈ x - 1
        TanhShrink ts = new TanhShrink();
        Tensor input = tensor(100f);

        // When
        Tensor output = ts.forward(input);

        // Then
        assertEquals(99f, floats(output)[0], 0.01f);
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // ActivationFunction base
    // -----------------------------------------------------------------------

    @Test
    public void testGivenActivationFunctionWhenNameAndInplaceQueriedThenCorrect() {
        // Given
        ReLU relu   = new ReLU(true);
        GELU gelu   = new GELU(false);
        SiLU silu   = new SiLU(true);

        // Then
        assertEquals("ReLU", relu.name());
        assertTrue(relu.isInplace());

        assertEquals("GELU", gelu.name());
        assertFalse(gelu.isInplace());

        assertEquals("SiLU", silu.name());
        assertTrue(silu.isInplace());
    }
}

