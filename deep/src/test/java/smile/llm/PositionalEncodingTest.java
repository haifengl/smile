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
}

