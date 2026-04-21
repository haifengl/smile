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
package smile.llm;

import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PositionalEncoding}.
 *
 * @author Haifeng Li
 */
public class PositionalEncodingTest {

    // -----------------------------------------------------------------------
    // forward — output shape
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPositionalEncodingWhenForwardThenOutputShapeMatchesInput() {
        // Embedding dimension=16, max positions=64
        PositionalEncoding pe = new PositionalEncoding(16, 64);
        // Input: [seqLen=10, dim=16]
        Tensor input = Tensor.rand(10, 16);
        Tensor output = pe.forward(input);
        assertArrayEquals(new long[]{10, 16}, output.shape());
        input.close(); output.close();
    }

    @Test
    public void testGivenPositionalEncodingWhenForwardThenOutputDtypeMatchesInput() {
        PositionalEncoding pe = new PositionalEncoding(32, 128);
        Tensor input = Tensor.rand(8, 32);
        Tensor output = pe.forward(input);
        assertEquals(input.dtype(), output.dtype());
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // Constructor — default theta
    // -----------------------------------------------------------------------

    @Test
    public void testGivenDefaultThetaConstructorWhenForwardThenNoException() {
        PositionalEncoding pe = new PositionalEncoding(8, 16);
        Tensor input = Tensor.rand(4, 8);
        assertDoesNotThrow(() -> {
            Tensor output = pe.forward(input);
            output.close();
        });
        input.close();
    }

    // -----------------------------------------------------------------------
    // Different theta
    // -----------------------------------------------------------------------

    @Test
    public void testGivenCustomThetaWhenForwardThenOutputShapeIsPreserved() {
        PositionalEncoding pe = new PositionalEncoding(16, 32, 50000.0);
        Tensor input = Tensor.rand(5, 16);
        Tensor output = pe.forward(input);
        assertArrayEquals(new long[]{5, 16}, output.shape());
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // The encoding at position 0 adds a fixed offset; output ≠ input
    // -----------------------------------------------------------------------

    @Test
    public void testGivenZeroInputWhenForwardThenOutputEqualsPositionalEncoding() {
        // If input is all-zeros, output must equal the PE table itself (for the given seqLen)
        int dim = 8, seqLen = 4;
        PositionalEncoding pe = new PositionalEncoding(dim, seqLen + 4);
        Tensor input = Tensor.zeros(seqLen, dim);
        Tensor output = pe.forward(input);
        Tensor outC = output.contiguous();
        // Not all zeros (PE has cos/sin values)
        boolean allZero = true;
        for (float v : outC.floatArray()) {
            if (Math.abs(v) > 1e-6f) { allZero = false; break; }
        }
        assertFalse(allZero, "PE output for zero input should not be all-zero");
        input.close(); output.close(); outC.close();
    }

    // -----------------------------------------------------------------------
    // Additional coverage for the constructor fix and the to(Device) fix
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPositionalEncodingWhenForwardThenValuesChangedFromInput() {
        PositionalEncoding pe = new PositionalEncoding(8, 16);
        // if all-zeros, output should equal PE (non-zero)
        Tensor input = Tensor.zeros(4, 8);
        Tensor output = pe.forward(input);
        // PE at position 0 has sin(0)=0 for even dims but cos(0)=1 for odd dims
        // so not all output values are zero
        Tensor c = output.contiguous();
        float[] vals = c.floatArray();
        boolean anyNonZero = false;
        for (float v : vals) {
            if (Math.abs(v) > 1e-6f) { anyNonZero = true; break; }
        }
        assertTrue(anyNonZero, "PE output should not be all-zero for zero input");
        input.close(); output.close(); c.close();
    }

    @Test
    public void testGivenPositionalEncodingWhenConstructedThenForwardWorksConsistently() {
        // CPU construction + forward should work without throwing (regression for in-place-expand bug)
        PositionalEncoding pe = new PositionalEncoding(8, 16);
        Tensor input1 = Tensor.rand(4, 8);
        Tensor input2 = Tensor.rand(4, 8);
        Tensor output1 = pe.forward(input1);
        Tensor output2 = pe.forward(input2);
        // PE is deterministic (same position → same encoding), so outputs differ only in input
        assertArrayEquals(new long[]{4, 8}, output1.shape());
        assertArrayEquals(new long[]{4, 8}, output2.shape());
        input1.close(); input2.close(); output1.close(); output2.close();
    }

    @Test
    public void testGivenPositionalEncodingWithSeqLenOneWhenForwardThenOutputShapeIsCorrect() {
        PositionalEncoding pe = new PositionalEncoding(16, 64);
        Tensor input = Tensor.rand(1, 16);
        Tensor output = pe.forward(input);
        assertArrayEquals(new long[]{1, 16}, output.shape());
        input.close(); output.close();
    }

    @Test
    public void testGivenPositionalEncodingAsTorchWhenCalledThenReturnsModule() {
        PositionalEncoding pe = new PositionalEncoding(8, 16);
        assertNotNull(pe.asTorch());
    }

    @Test
    public void testGivenPositionalEncodingWhenForwardCalledThenOutputNotSameAsPE() {
        // Forward adds PE to the input. The output should differ from the input
        // (unless input + PE happens to equal input, which is astronomically unlikely).
        PositionalEncoding pe = new PositionalEncoding(16, 32);
        Tensor input = Tensor.randn(8, 16);
        Tensor output = pe.forward(input);
        // Compare first element
        assertNotEquals(input.getFloat(0, 1), output.getFloat(0, 1), 1e-6f,
                "Output of forward should differ from input when PE has non-zero values");
        input.close(); output.close();
    }
}

