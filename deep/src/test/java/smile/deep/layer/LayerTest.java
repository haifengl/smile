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
package smile.deep.layer;

import org.junit.jupiter.api.*;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;
/**
 * Lightweight unit tests for layer factory methods and SequentialBlock.
 * All tests run on CPU with small synthetic tensors — no GPU or dataset needed.
 */
public class LayerTest {

    private static final int BATCH = 4;
    private static final int IN    = 8;
    private static final int OUT   = 4;

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /** Create a random float32 tensor of shape [batch, features]. */
    private Tensor randn(int batch, int features) {
        // Tensor.rand returns float32 by default
        return Tensor.rand(batch, features);
    }

    // -----------------------------------------------------------------------
    // Linear layer
    // -----------------------------------------------------------------------

    @Test
    public void testGivenLinearLayerWhenForwardThenOutputShapeIsCorrect() {
        // Given
        LinearLayer layer = Layer.linear(IN, OUT);
        Tensor input = randn(BATCH, IN);

        // When
        Tensor output = layer.forward(input);

        // Then
        assertEquals(BATCH, output.size(0));
        assertEquals(OUT,   output.size(1));
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // Activation shortcuts
    // -----------------------------------------------------------------------

    @Test
    public void testGivenReluLayerWhenForwardThenOutputShapeIsCorrect() {
        SequentialBlock block = Layer.relu(IN, OUT);
        Tensor input = randn(BATCH, IN);
        Tensor output = block.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(OUT,   output.size(1));
        input.close(); output.close();
    }

    @Test
    public void testGivenReluLayerWithDropoutWhenForwardThenOutputShapeIsCorrect() {
        SequentialBlock block = Layer.relu(IN, OUT, 0.3);
        Tensor input = randn(BATCH, IN);
        Tensor output = block.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(OUT,   output.size(1));
        input.close(); output.close();
    }

    @Test
    public void testGivenGeluLayerWhenForwardThenOutputShapeIsCorrect() {
        SequentialBlock block = Layer.gelu(IN, OUT);
        Tensor input = randn(BATCH, IN);
        Tensor output = block.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(OUT,   output.size(1));
        input.close(); output.close();
    }

    /**
     * Regression test: {@code Layer.silu(int, int)} previously used {@code GELU}
     * instead of {@code SiLU}. Verify via toString() that the correct activation
     * is registered.
     */
    @Test
    public void testGivenSiluLayerWhenInspectingToStringThenContainsSilu() {
        SequentialBlock block = Layer.silu(IN, OUT);
        String repr = block.toString();
        assertTrue(repr.contains("SiLU") || repr.contains("silu") || repr.contains("Silu"),
                "silu block should reference SiLU, but was: " + repr);
    }

    @Test
    public void testGivenSiluLayerWhenForwardThenOutputShapeIsCorrect() {
        SequentialBlock block = Layer.silu(IN, OUT);
        Tensor input = randn(BATCH, IN);
        Tensor output = block.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(OUT,   output.size(1));
        input.close(); output.close();
    }

    @Test
    public void testGivenTanhLayerWhenForwardThenAllOutputsInMinusOneToOne() {
        SequentialBlock block = Layer.tanh(IN, OUT);
        Tensor input = randn(BATCH, IN);
        Tensor output = block.forward(input);
        // tanh outputs must be in [-1, 1]
        Tensor contiguous = output.contiguous();
        float[] vals = contiguous.floatArray();
        for (float v : vals) {
            assertTrue(v >= -1.0f - 1e-5f && v <= 1.0f + 1e-5f,
                    "tanh output out of [-1,1]: " + v);
        }
        input.close(); output.close(); contiguous.close();
    }

    @Test
    public void testGivenSigmoidLayerWhenForwardThenAllOutputsInZeroToOne() {
        SequentialBlock block = Layer.sigmoid(IN, OUT);
        Tensor input = randn(BATCH, IN);
        Tensor output = block.forward(input);
        Tensor contiguous = output.contiguous();
        float[] vals = contiguous.floatArray();
        for (float v : vals) {
            assertTrue(v >= -1e-5f && v <= 1.0f + 1e-5f,
                    "sigmoid output out of [0,1]: " + v);
        }
        input.close(); output.close(); contiguous.close();
    }

    @Test
    public void testGivenLogSoftmaxLayerWhenForwardThenAllOutputsNonPositive() {
        SequentialBlock block = Layer.logSoftmax(IN, OUT);
        Tensor input = randn(BATCH, IN);
        Tensor output = block.forward(input);
        Tensor contiguous = output.contiguous();
        float[] vals = contiguous.floatArray();
        for (float v : vals) {
            assertTrue(v <= 1e-5f, "log-softmax output must be <= 0, but was: " + v);
        }
        input.close(); output.close(); contiguous.close();
    }

    @Test
    public void testGivenLeakyLayerWhenForwardThenOutputShapeIsCorrect() {
        SequentialBlock block = Layer.leaky(IN, OUT, 0.01);
        Tensor input = randn(BATCH, IN);
        Tensor output = block.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(OUT,   output.size(1));
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // SequentialBlock
    // -----------------------------------------------------------------------

    @Test
    public void testGivenEmptySequentialBlockWhenAddingLayersThenForwardProducesCorrectShape() {
        SequentialBlock seq = new SequentialBlock();
        seq.add(Layer.linear(IN, OUT));
        Tensor input = randn(BATCH, IN);
        Tensor output = seq.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(OUT,   output.size(1));
        input.close(); output.close();
    }

    @Test
    public void testGivenMultiLayerSequentialBlockWhenForwardThenShapeMatchesFinalLayer() {
        SequentialBlock seq = new SequentialBlock(
                Layer.relu(IN, 16),
                Layer.relu(16, 8),
                Layer.linear(8, OUT)
        );
        Tensor input = randn(BATCH, IN);
        Tensor output = seq.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(OUT,   output.size(1));
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // Normalization layers
    // -----------------------------------------------------------------------

    @Test
    public void testGivenBatchNorm1dWhenForwardThenOutputShapeIsPreserved() {
        BatchNorm1dLayer bn = Layer.batchNorm1d(IN);
        Tensor input = randn(BATCH, IN);
        bn.asTorch().train(true);
        Tensor output = bn.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(IN,    output.size(1));
        input.close(); output.close();
    }

    @Test
    public void testGivenDropoutLayerInEvalModeWhenForwardThenOutputEqualsInput() {
        // In eval mode, dropout is a no-op
        DropoutLayer drop = Layer.dropout(0.5);
        drop.asTorch().eval();
        Tensor input = randn(BATCH, IN);
        Tensor output = drop.forward(input);
        Tensor inC  = input.contiguous();
        Tensor outC = output.contiguous();
        float[] inVals  = inC.floatArray();
        float[] outVals = outC.floatArray();
        assertArrayEquals(inVals, outVals, 1e-6f);
        input.close(); output.close(); inC.close(); outC.close();
    }

    // -----------------------------------------------------------------------
    // Embedding layer
    // -----------------------------------------------------------------------

    @Test
    public void testGivenEmbeddingLayerWhenForwardThenOutputShapeIsCorrect() {
        // Given: vocabulary of 10 tokens, embedding dim 8
        EmbeddingLayer emb = Layer.embedding(10, 8);
        // Input: 4 token indices as a 1-D int64 tensor
        long[] indices = {0L, 3L, 7L, 9L};
        Tensor input = Tensor.of(indices, 4);   // shape [4]
        Tensor output = emb.forward(input);
        // Then: shape [4, 8]
        assertEquals(4, output.size(0));
        assertEquals(8, output.size(1));
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // Normalization layers: GroupNorm and RMSNorm (via factory)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenGroupNormLayerWhenForwardThenOutputShapeIsPreserved() {
        // GroupNorm expects input [N, C, *] where C divisible by groups
        // Use shape [BATCH, 4, 4] — 4 channels, 2 groups
        GroupNormLayer gn = Layer.groupNorm(2, 4);
        gn.asTorch().train(true);
        Tensor input = Tensor.rand(BATCH, 4, 4);
        Tensor output = gn.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(4, output.size(1));
        assertEquals(4, output.size(2));
        input.close(); output.close();
    }

    @Test
    public void testGivenRmsNormLayerWhenForwardThenOutputShapeIsPreserved() {
        // RMSNorm normalizes over the last dimension
        RMSNormLayer rms = Layer.rmsNorm(IN);
        Tensor input = Tensor.rand(BATCH, IN);
        Tensor output = rms.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(IN,    output.size(1));
        input.close(); output.close();
    }

    @Test
    public void testGivenBatchNorm2dWhenForwardThenOutputShapeIsPreserved() {
        // BatchNorm2d: input [N, C, H, W]
        BatchNorm2dLayer bn = Layer.batchNorm2d(IN);
        bn.asTorch().train(true);
        Tensor input = Tensor.rand(BATCH, IN, 4, 4);
        Tensor output = bn.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(IN,    output.size(1));
        assertEquals(4,     output.size(2));
        assertEquals(4,     output.size(3));
        input.close(); output.close();
    }

    @Test
    public void testGivenConv2dLayerWhenForwardThenOutputShapeIsCorrect() {
        // conv2d with kernel=3, stride=1, padding=1 keeps spatial dims for "same"-style
        int inC = 3, outC = 8, H = 16, W = 16;
        Conv2dLayer conv = Layer.conv2d(inC, outC, 3, 1, 1, 1, 1, true, "zeros");
        Tensor input = Tensor.rand(BATCH, inC, H, W);
        Tensor output = conv.forward(input);
        assertEquals(BATCH, output.size(0));
        assertEquals(outC,  output.size(1));
        assertEquals(H,     output.size(2));
        assertEquals(W,     output.size(3));
        input.close(); output.close();
    }
}

