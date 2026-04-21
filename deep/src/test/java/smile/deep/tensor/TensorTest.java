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
package smile.deep.tensor;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Tensor} factory methods, indexing, and arithmetic.
 *
 * @author Haifeng Li
 */
public class TensorTest {

    @Test
    public void testGivenFloat1DArrayWhenCreatingTensorThenShapeAndValuesAreCorrect() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        Tensor t = Tensor.of(x, 3, 3);
        long[] shape = {3, 3};
        assertArrayEquals(shape, t.shape());
        assertEquals(2.0f, t.getFloat(0, 1));
        t.close();
    }

    @Test
    public void testGivenZerosTensorWhenCreatedThenAllElementsAreZero() {
        Tensor t = Tensor.zeros(2, 3);
        assertArrayEquals(new long[]{2, 3}, t.shape());
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(0.0f, v, 1e-7f);
        t.close(); c.close();
    }

    @Test
    public void testGivenOnesTensorWhenCreatedThenAllElementsAreOne() {
        Tensor t = Tensor.ones(3, 4);
        assertArrayEquals(new long[]{3, 4}, t.shape());
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(1.0f, v, 1e-7f);
        t.close(); c.close();
    }

    @Test
    public void testGivenRandTensorWhenCreatedThenAllElementsInZeroOne() {
        Tensor t = Tensor.rand(5, 5);
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) {
            assertTrue(v >= 0.0f && v <= 1.0f, "rand element out of [0,1]: " + v);
        }
        t.close(); c.close();
    }

    @Test
    public void testGivenTensorWhenPutThenValueIsUpdated() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        Tensor t = Tensor.of(x, 3, 3);
        t.put_(10f, 1, 1);
        assertEquals(10f, t.getFloat(1, 1));
        t.close();
    }

    @Test
    public void testGivenTensorWhenSlicingColumnThenShapeIsCorrect() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        Tensor t = Tensor.of(x, 3, 3);
        t.put_(10f, 1, 1);
        var col = t.get(Index.Colon, Index.of(1));
        assertArrayEquals(new long[]{3}, col.shape());
        assertEquals(2f,  col.getFloat(0));
        assertEquals(10f, col.getFloat(1));
        assertEquals(8f,  col.getFloat(2));
        t.close(); col.close();
    }

    @Test
    public void testGivenTensorWhenIndexingWithEllipsisThenLastDimensionSelected() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        Tensor t = Tensor.of(x, 3, 3);
        var col = t.get(Index.Ellipsis, Index.of(2));
        assertArrayEquals(new long[]{3}, col.shape());
        assertEquals(3f, col.getFloat(0));
        assertEquals(6f, col.getFloat(1));
        assertEquals(9f, col.getFloat(2));
        t.close(); col.close();
    }

    @Test
    public void testGivenTensorWhenIndexingWithNoneAddsNewDimension() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        Tensor t = Tensor.of(x, 3, 3);
        var slice = t.get(Index.None, Index.of(2));
        assertArrayEquals(new long[]{1, 3}, slice.shape());
        assertEquals(7f, slice.getFloat(0, 0));
        assertEquals(8f, slice.getFloat(0, 1));
        assertEquals(9f, slice.getFloat(0, 2));
        t.close(); slice.close();
    }

    @Test
    public void testGivenTensorWhenIndexingWithTensorThenRowsAreSelected() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        Tensor t = Tensor.of(x, 3, 3);
        t.put_(10f, 1, 1);
        int[] index = {1, 2};
        var rows = t.get(Tensor.of(index, 2));
        assertArrayEquals(new long[]{2, 3}, rows.shape());
        assertEquals(10f, rows.getFloat(0, 1));
        assertEquals(8f,  rows.getFloat(1, 1));
        t.close(); rows.close();
    }

    @Test
    public void testGivenSubFloatWhenCalledThenReturnsCopyNotMutating() {
        // sub(float) must return a NEW tensor; original must be unchanged
        Tensor t = Tensor.ones(3);
        Tensor r = t.sub(1.0f);
        Tensor tc = t.contiguous();
        Tensor rc = r.contiguous();
        // Original unchanged
        for (float v : tc.floatArray()) assertEquals(1.0f, v, 1e-7f);
        // Result = 0
        for (float v : rc.floatArray()) assertEquals(0.0f, v, 1e-7f);
        t.close(); r.close(); tc.close(); rc.close();
    }

    @Test
    public void testGivenSubUnderscoreFloatWhenCalledThenMutatesInPlace() {
        // sub_(float) must mutate this tensor in-place
        Tensor t = Tensor.ones(3);
        Tensor ret = t.sub_(1.0f);
        assertSame(t, ret, "sub_(float) must return 'this'");
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(0.0f, v, 1e-7f);
        t.close(); c.close();
    }

    @Test
    public void testGivenExpUnderscoreWhenCalledThenMutatesInPlaceAndReturnsSelf() {
        // exp_() must mutate in-place and return 'this'
        Tensor t = Tensor.zeros(3);
        Tensor ret = t.exp_();
        assertSame(t, ret, "exp_() must return 'this'");
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(1.0f, v, 1e-5f); // exp(0) = 1
        t.close(); c.close();
    }

    @Test
    public void testGivenArgmaxWhenCalledThenReturnsIndexOfMaxValue() {
        float[] x = {1.0f, 5.0f, 3.0f};
        Tensor t = Tensor.of(x, 3);
        Tensor idx = t.argmax(0, false);
        assertEquals(1, idx.intValue());
        t.close(); idx.close();
    }

    @Test
    public void testGivenArangeThenValuesAreSequential() {
        Tensor t = Tensor.arange(0, 5, 1);
        Tensor c = t.contiguous();
        float[] vals = c.floatArray();
        for (int i = 0; i < 5; i++) assertEquals(i, vals[i], 1e-7f);
        t.close(); c.close();
    }

    @Test
    public void testGivenCrossEntropyWithNoneReductionThenOutputIsVector() {
        // Regression: "none" case previously mapped to kMean (scalar). Fix: should return per-sample losses.
        // logits[0] = {2, 1, 0.1}, label=0; logits[1] = {0.1, 2, 0.1}, label=1
        float[] flat = {2.0f, 1.0f, 0.1f, 0.1f, 2.0f, 0.1f};
        Tensor input = Tensor.of(flat, 2, 3);
        long[] labels = {0L, 1L};
        Tensor target = Tensor.of(labels, 2L);
        Tensor loss = Tensor.crossEntropy(input, target, "none", -100);
        // With "none" reduction, output should have 2 elements (one per sample)
        assertEquals(1, loss.dim());
        assertEquals(2, loss.size(0));
        input.close(); target.close(); loss.close();
    }
}


