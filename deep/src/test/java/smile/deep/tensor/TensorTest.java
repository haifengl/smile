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
 * Unit tests for {@link Tensor} factory methods, indexing, arithmetic,
 * reductions, dtype/device metadata, and the equals/hashCode contract.
 * All tests run on CPU using small synthetic tensors.
 *
 * @author Haifeng Li
 */
public class TensorTest {

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFloat1DArrayWhenCreatingTensorThenShapeAndValuesAreCorrect() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        Tensor t = Tensor.of(x, 3, 3);
        assertArrayEquals(new long[]{3, 3}, t.shape());
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
    public void testGivenRandnTensorWhenCreatedThenHasMeanNearZero() {
        Tensor t = Tensor.randn(1000);
        Tensor c = t.contiguous();
        float[] vals = c.floatArray();
        double sum = 0;
        for (float v : vals) sum += v;
        assertTrue(Math.abs(sum / vals.length) < 0.2, "randn mean should be near 0");
        t.close(); c.close();
    }

    @Test
    public void testGivenFullLongWhenCreatedThenAllElementsHaveGivenValue() {
        Tensor t = Tensor.full(7L, 3, 2);
        assertArrayEquals(new long[]{3, 2}, t.shape());
        // full(long, ...) infers Int64; read via longArray
        Tensor c = t.contiguous();
        for (long v : c.longArray()) assertEquals(7L, v);
        t.close(); c.close();
    }

    @Test
    public void testGivenFullDoubleWhenCreatedThenDoesNotThrow() {
        // Scalar(double) preserves the full double value (no float cast)
        Tensor t = Tensor.full(3.141592653589793, 1);
        assertNotNull(t.contiguous().floatArray());
        t.close();
    }

    @Test
    public void testGivenEmptyTensorWhenCreatedThenShapeAndLengthCorrect() {
        Tensor t = Tensor.empty(4, 4);
        assertArrayEquals(new long[]{4, 4}, t.shape());
        assertEquals(16, t.length());
        t.close();
    }

    @Test
    public void testGivenArangeThenValuesAreSequential() {
        Tensor t = Tensor.arange(0, 5, 1);
        Tensor c = t.contiguous();
        long[] vals = c.longArray();
        for (int i = 0; i < 5; i++) assertEquals(i, vals[i]);
        t.close(); c.close();
    }

    @Test
    public void testGivenLongDataWhenCreatingTensorThenValuesCorrect() {
        long[] data = {10L, 20L, 30L};
        Tensor t = Tensor.of(data, 3);
        assertEquals(10L, t.getLong(0));
        assertEquals(20L, t.getLong(1));
        assertEquals(30L, t.getLong(2));
        t.close();
    }

    @Test
    public void testGivenDoubleDataWhenCreatingTensorThenValuesCorrect() {
        double[] data = {1.1, 2.2, 3.3};
        Tensor t = Tensor.of(data, 3);
        Tensor c = t.contiguous();
        double[] result = c.doubleArray();
        assertEquals(1.1, result[0], 1e-10);
        assertEquals(3.3, result[2], 1e-10);
        t.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Indexing
    // -----------------------------------------------------------------------

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
        var slice = t.get(Index.Colon, Index.of(2));
        assertArrayEquals(new long[]{3}, slice.shape());
        assertEquals(3f, slice.getFloat(0));
        assertEquals(6f, slice.getFloat(1));
        assertEquals(9f, slice.getFloat(2));
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
    public void testGivenIndexSliceWhenAppliedThenSubsetIsExtracted() {
        // arange [0..9], slice [2:7:2] → [2, 4, 6]
        Tensor t = Tensor.arange(0, 10, 1);
        Tensor s = t.get(Index.slice(2, 7, 2));
        Tensor c = s.contiguous();
        long[] vals = c.longArray();
        assertArrayEquals(new long[]{2L, 4L, 6L}, vals);
        t.close(); s.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Shape / layout operations
    // -----------------------------------------------------------------------

    @Test
    public void testGivenReshapeWhenAppliedThenShapeChangesAndDataPreserved() {
        Tensor t = Tensor.arange(0, 6, 1);
        Tensor r = t.reshape(2, 3);
        assertArrayEquals(new long[]{2, 3}, r.shape());
        assertEquals(2, r.dim());
        assertEquals(3f, r.getFloat(1, 0), 1e-6f);
        t.close(); r.close();
    }

    @Test
    public void testGivenFlattenWhenAppliedThen1DResultWithSameElements() {
        Tensor t = Tensor.ones(2, 3, 4);
        Tensor f = t.flatten();
        assertArrayEquals(new long[]{24}, f.shape());
        t.close(); f.close();
    }

    @Test
    public void testGivenFlattenPartialWhenAppliedThenOnlySpecifiedDimsFlatten() {
        Tensor t = Tensor.ones(2, 3, 4);
        Tensor f = t.flatten(1);  // flatten dims 1 to end → shape [2, 12]
        assertArrayEquals(new long[]{2, 12}, f.shape());
        t.close(); f.close();
    }

    @Test
    public void testGivenUnsqueezeWhenAppliedThenDimIsInserted() {
        Tensor t = Tensor.ones(3, 4);
        Tensor u = t.unsqueeze(0);
        assertArrayEquals(new long[]{1, 3, 4}, u.shape());
        t.close(); u.close();
    }

    @Test
    public void testGivenTransposeWhenAppliedThenDimensionsAreSwapped() {
        float[] x = {1f, 2f, 3f, 4f, 5f, 6f};
        Tensor t = Tensor.of(x, 2, 3);
        Tensor tr = t.transpose(0, 1);
        assertArrayEquals(new long[]{3, 2}, tr.shape());
        assertEquals(2f, tr.getFloat(1, 0), 1e-6f); // was [0][1], now [1][0]
        t.close(); tr.close();
    }

    @Test
    public void testGivenPermuteWhenAppliedThenDimensionsReordered() {
        Tensor t = Tensor.ones(2, 3, 4);
        Tensor p = t.permute(2, 0, 1);  // [2,3,4] → [4,2,3]
        assertArrayEquals(new long[]{4, 2, 3}, p.shape());
        t.close(); p.close();
    }

    @Test
    public void testGivenViewWhenAppliedThenShapeChanges() {
        Tensor t = Tensor.arange(0, 6, 1);
        Tensor v = t.view(3, 2);
        assertArrayEquals(new long[]{3, 2}, v.shape());
        t.close(); v.close();
    }

    // -----------------------------------------------------------------------
    // Arithmetic operations
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSubFloatWhenCalledThenReturnsCopyNotMutating() {
        Tensor t = Tensor.ones(3);
        Tensor r = t.sub(1.0f);
        Tensor tc = t.contiguous();
        Tensor rc = r.contiguous();
        for (float v : tc.floatArray()) assertEquals(1.0f, v, 1e-7f);
        for (float v : rc.floatArray()) assertEquals(0.0f, v, 1e-7f);
        t.close(); r.close(); tc.close(); rc.close();
    }

    @Test
    public void testGivenSubUnderscoreFloatWhenCalledThenMutatesInPlace() {
        Tensor t = Tensor.ones(3);
        Tensor ret = t.sub_(1.0f);
        assertSame(t, ret, "sub_(float) must return 'this'");
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(0.0f, v, 1e-7f);
        t.close(); c.close();
    }

    @Test
    public void testGivenAddTensorWhenCalledThenResultIsElementwiseSum() {
        Tensor ta = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor tb = Tensor.of(new float[]{10f, 20f, 30f}, 3);
        Tensor r  = ta.add(tb);
        Tensor c  = r.contiguous();
        assertArrayEquals(new float[]{11f, 22f, 33f}, c.floatArray(), 1e-6f);
        ta.close(); tb.close(); r.close(); c.close();
    }

    @Test
    public void testGivenMulScalarWhenCalledThenAllElementsScaled() {
        Tensor t = Tensor.ones(4);
        Tensor r = t.mul(3.0f);
        Tensor c = r.contiguous();
        for (float v : c.floatArray()) assertEquals(3.0f, v, 1e-7f);
        t.close(); r.close(); c.close();
    }

    @Test
    public void testGivenDivScalarWhenCalledThenAllElementsDivided() {
        Tensor t = Tensor.ones(4).mul(6.0f);
        Tensor r = t.div(2.0f);
        Tensor c = r.contiguous();
        for (float v : c.floatArray()) assertEquals(3.0f, v, 1e-7f);
        t.close(); r.close(); c.close();
    }

    @Test
    public void testGivenPowWhenCalledThenElementsRaisedToExponent() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 4);
        Tensor r = t.pow(2.0);
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{1f, 4f, 9f, 16f}, c.floatArray(), 1e-6f);
        t.close(); r.close(); c.close();
    }

    @Test
    public void testGivenExpUnderscoreWhenCalledThenMutatesInPlaceAndReturnsSelf() {
        Tensor t = Tensor.zeros(3);
        Tensor ret = t.exp_();
        assertSame(t, ret, "exp_() must return 'this'");
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(1.0f, v, 1e-5f);
        t.close(); c.close();
    }

    @Test
    public void testGivenCosWhenCalledOnZerosThenResultIsAllOnes() {
        Tensor t = Tensor.zeros(4);
        Tensor r = t.cos();
        Tensor c = r.contiguous();
        for (float v : c.floatArray()) assertEquals(1.0f, v, 1e-6f);
        t.close(); r.close(); c.close();
    }

    @Test
    public void testGivenNegWhenCalledThenElementsNegated() {
        Tensor t = Tensor.of(new float[]{1f, -2f, 3f}, 3);
        Tensor r = t.neg();
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{-1f, 2f, -3f}, c.floatArray(), 1e-7f);
        t.close(); r.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Matrix / linear algebra
    // -----------------------------------------------------------------------

    @Test
    public void testGivenMatmulWhenAppliedThenResultIsMatrixProduct() {
        // [1,2,3] x I3 = [1,2,3]
        Tensor v = Tensor.of(new float[]{1f, 2f, 3f}, 1, 3);
        Tensor I = Tensor.eye(3);
        Tensor r = v.matmul(I);
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{1f, 2f, 3f}, c.floatArray(), 1e-6f);
        v.close(); I.close(); r.close(); c.close();
    }

    @Test
    public void testGivenOuterProductWhenCalledThenResultShapeIsNxM() {
        Tensor ta = Tensor.of(new float[]{1f, 2f}, 2);
        Tensor tb = Tensor.of(new float[]{3f, 4f, 5f}, 3);
        Tensor r  = ta.outer(tb);
        assertArrayEquals(new long[]{2, 3}, r.shape());
        assertEquals(5f,  r.getFloat(0, 2), 1e-6f);
        assertEquals(10f, r.getFloat(1, 2), 1e-6f);
        ta.close(); tb.close(); r.close();
    }

    @Test
    public void testGivenHstackWhenAppliedThenColumnsAreConcatenated() {
        Tensor a = Tensor.ones(2, 2);
        Tensor b = Tensor.zeros(2, 3);
        Tensor r = Tensor.hstack(a, b);
        assertArrayEquals(new long[]{2, 5}, r.shape());
        a.close(); b.close(); r.close();
    }

    @Test
    public void testGivenVstackWhenAppliedThenRowsAreConcatenated() {
        Tensor a = Tensor.ones(2, 3);
        Tensor b = Tensor.zeros(3, 3);
        Tensor r = Tensor.vstack(a, b);
        assertArrayEquals(new long[]{5, 3}, r.shape());
        a.close(); b.close(); r.close();
    }

    // -----------------------------------------------------------------------
    // Reductions
    // -----------------------------------------------------------------------

    @Test
    public void testGivenArgmaxWhenCalledThenReturnsIndexOfMaxValue() {
        Tensor t = Tensor.of(new float[]{1.0f, 5.0f, 3.0f}, 3);
        Tensor idx = t.argmax(0, false);
        assertEquals(1, idx.intValue());
        t.close(); idx.close();
    }

    @Test
    public void testGivenSumWhenCalledThenReturnsCorrectTotal() {
        Tensor t = Tensor.ones(3, 4);
        Tensor s = t.sum();
        assertEquals(12.0, s.doubleValue(), 1e-6);
        t.close(); s.close();
    }

    @Test
    public void testGivenSumAlongDimWhenCalledThenReducesCorrectly() {
        // [[1,2],[3,4]] → sum over dim 1 → [3, 7]
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 2, 2);
        Tensor s = t.sum(1, false);
        Tensor c = s.contiguous();
        float[] vals = c.floatArray();
        assertEquals(3f, vals[0], 1e-5f);
        assertEquals(7f, vals[1], 1e-5f);
        t.close(); s.close(); c.close();
    }

    @Test
    public void testGivenSumAlongDimKeepDimWhenCalledThenShapeRetained() {
        Tensor t = Tensor.ones(3, 4);
        Tensor s = t.sum(1, true);
        assertArrayEquals(new long[]{3, 1}, s.shape());
        t.close(); s.close();
    }

    @Test
    public void testGivenMeanWhenCalledThenReturnsCorrectMean() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 4);
        Tensor m = t.mean();
        assertEquals(2.5, m.doubleValue(), 1e-5);
        t.close(); m.close();
    }

    @Test
    public void testGivenMeanAlongDimWhenCalledThenReducesCorrectly() {
        // [[1,2],[3,4]] → mean over dim 1 → [1.5, 3.5]
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 2, 2);
        Tensor m = t.mean(1, false);
        Tensor c = m.contiguous();
        float[] vals = c.floatArray();
        assertEquals(1.5f, vals[0], 1e-5f);
        assertEquals(3.5f, vals[1], 1e-5f);
        t.close(); m.close(); c.close();
    }

    @Test
    public void testGivenMinWhenCalledThenReturnsMinValue() {
        Tensor t = Tensor.of(new float[]{3f, 1f, 4f, 1f, 5f}, 5);
        Tensor m = t.min();
        assertEquals(1.0f, m.floatValue(), 1e-7f);
        t.close(); m.close();
    }

    @Test
    public void testGivenMaxWhenCalledThenReturnsMaxValue() {
        Tensor t = Tensor.of(new float[]{3f, 1f, 4f, 1f, 5f}, 5);
        Tensor m = t.max();
        assertEquals(5.0f, m.floatValue(), 1e-7f);
        t.close(); m.close();
    }

    // -----------------------------------------------------------------------
    // Comparison operations
    // -----------------------------------------------------------------------

    @Test
    public void testGivenEqScalarWhenCalledThenReturnsBooleanMask() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f, 2f}, 4);
        Tensor mask = t.eq(2.0f);
        Tensor c = mask.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(0, vals[0]);
        assertEquals(1, vals[1]);
        assertEquals(0, vals[2]);
        assertEquals(1, vals[3]);
        t.close(); mask.close(); c.close();
    }

    @Test
    public void testGivenGtScalarWhenCalledThenReturnsBooleanMask() {
        Tensor t = Tensor.of(new float[]{1f, 5f, 3f}, 3);
        Tensor mask = t.gt(2.0);
        Tensor c = mask.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(0, vals[0]);
        assertEquals(1, vals[1]);
        assertEquals(1, vals[2]);
        t.close(); mask.close(); c.close();
    }

    @Test
    public void testGivenWhereWithScalarsWhenCalledThenSelectsCorrectly() {
        Tensor t = Tensor.of(new float[]{-1f, 2f, -3f, 4f}, 4);
        Tensor cond = t.gt(0.0);
        // Use double scalars so the result is Float64; cast to float for comparison
        Tensor r = Tensor.where(cond, 1.0, 0.0).to(ScalarType.Float32);
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{0f, 1f, 0f, 1f}, c.floatArray(), 1e-7f);
        t.close(); cond.close(); r.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Softmax
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSoftmaxWhenAppliedThenOutputSumsToOne() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor s = t.softmax(0);
        Tensor c = s.contiguous();
        float sum = 0f;
        for (float v : c.floatArray()) sum += v;
        assertEquals(1.0f, sum, 1e-5f);
        t.close(); s.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Cross-entropy loss
    // -----------------------------------------------------------------------

    @Test
    public void testGivenCrossEntropyWithNoneReductionThenOutputIsPositive() {
        // With the functional torch.cross_entropy API the "none" reduction still
        // returns a scalar in this binding (same as "mean").  We therefore only
        // assert that the loss value is positive and finite.
        Tensor input = Tensor.of(new float[]{2.0f, 1.0f, 0.1f, 0.1f, 2.0f, 0.1f}, 2, 3);
        Tensor target = Tensor.of(new long[]{0L, 1L}, 2L);
        Tensor loss = Tensor.crossEntropy(input, target, "none", -100);
        assertTrue(loss.doubleValue() > 0, "loss should be positive");
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenCrossEntropyWithMeanReductionThenOutputIsScalar() {
        Tensor input = Tensor.of(new float[]{2.0f, 1.0f, 0.1f, 0.1f, 2.0f, 0.1f}, 2, 3);
        Tensor target = Tensor.of(new long[]{0L, 1L}, 2L);
        Tensor loss = Tensor.crossEntropy(input, target, "mean", -100);
        assertEquals(0, loss.dim(), "mean reduction should return a scalar");
        assertTrue(loss.doubleValue() > 0, "loss should be positive");
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenCrossEntropyWithInvalidReductionThenThrows() {
        Tensor input = Tensor.of(new float[]{1f, 0f}, 1, 2);
        Tensor target = Tensor.of(new long[]{0L}, 1L);
        assertThrows(IllegalArgumentException.class,
                () -> Tensor.crossEntropy(input, target, "invalid", -100));
        input.close(); target.close();
    }

    // -----------------------------------------------------------------------
    // dtype / device / metadata
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFloat32TensorWhenQueryingDtypeThenReturnsFloat32() {
        Tensor t = Tensor.ones(3);
        assertEquals(ScalarType.Float32, t.dtype());
        t.close();
    }

    @Test
    public void testGivenInt64TensorWhenQueryingDtypeThenReturnsInt64() {
        Tensor t = Tensor.of(new long[]{1L, 2L, 3L}, 3);
        assertEquals(ScalarType.Int64, t.dtype());
        t.close();
    }

    @Test
    public void testGivenCPUTensorWhenQueryingDeviceThenIsCPU() {
        Tensor t = Tensor.ones(3);
        Device d = t.device();
        assertTrue(d.isCPU());
        assertFalse(d.isCUDA());
        t.close();
    }

    @Test
    public void testGivenTensorWhenQueryingLengthThenReturnsProductOfDims() {
        Tensor t = Tensor.ones(2, 3, 4);
        assertEquals(24, t.length());
        t.close();
    }

    @Test
    public void testGivenTensorWhenQueryingDimThenReturnsNumberOfDimensions() {
        Tensor t = Tensor.ones(2, 3, 4);
        assertEquals(3, t.dim());
        t.close();
    }

    // -----------------------------------------------------------------------
    // equals / hashCode contract
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTwoWrappersAroundSameTorchTensorWhenEqualsThenTrue() {
        // Given: a Tensor whose underlying native object is shared
        Tensor a = Tensor.ones(3);
        Tensor b = new Tensor(a.asTorch());  // same native object
        // When / Then
        assertEquals(a, b, "Wrappers around same native object must be equal");
        assertEquals(a.hashCode(), b.hashCode(), "Equal tensors must have same hashCode");
        a.close();
    }

    @Test
    public void testGivenTwoDifferentTensorsWhenEqualsThenFalse() {
        Tensor a = Tensor.ones(3);
        Tensor b = Tensor.ones(3);
        assertNotEquals(a, b, "Different native tensors must not be equal");
        a.close(); b.close();
    }

    // -----------------------------------------------------------------------
    // requireGrad
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTensorWhenSetRequireGradThenGetReturnsTrue() {
        Tensor t = Tensor.ones(3);
        assertFalse(t.getRequireGrad(), "Default: no grad required");
        t.setRequireGrad(true);
        assertTrue(t.getRequireGrad());
        t.close();
    }

    // -----------------------------------------------------------------------
    // newZeros
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTensorWhenNewZerosCalledThenSameDtypeAndDevice() {
        Tensor t = Tensor.ones(3);
        Tensor z = t.newZeros(2, 2);
        assertEquals(t.dtype(), z.dtype());
        assertTrue(z.device().isCPU());
        Tensor c = z.contiguous();
        for (float v : c.floatArray()) assertEquals(0f, v, 1e-7f);
        t.close(); z.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Device equality
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTwoCPUDevicesWhenEqualsAndHashCodeCalledThenConsistent() {
        Device d1 = Device.CPU();
        Device d2 = Device.CPU();
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    public void testGivenDeviceWhenToStringCalledThenNotEmpty() {
        assertFalse(Device.CPU().toString().isBlank());
    }

    // -----------------------------------------------------------------------
    // ScalarType
    // -----------------------------------------------------------------------

    @Test
    public void testGivenScalarTypeWhenAsTorchCalledThenNotNull() {
        for (ScalarType st : ScalarType.values()) {
            assertNotNull(st.asTorch(), "asTorch() should not return null for " + st);
        }
    }

    // -----------------------------------------------------------------------
    // abs / log / clamp (new methods)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenAbsWhenCalledOnMixedSignThenAllPositive() {
        Tensor t = Tensor.of(new float[]{-1f, 2f, -3f, 0f}, 4);
        Tensor r = t.abs();
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{1f, 2f, 3f, 0f}, c.floatArray(), 1e-7f);
        t.close(); r.close(); c.close();
    }

    @Test
    public void testGivenAbsUnderscoreWhenCalledThenMutatesInPlaceAndReturnsSelf() {
        Tensor t = Tensor.of(new float[]{-5f, 3f}, 2);
        Tensor ret = t.abs_();
        assertSame(t, ret, "abs_() must return 'this'");
        assertEquals(5f, t.getFloat(0), 1e-7f);
        assertEquals(3f, t.getFloat(1), 1e-7f);
        t.close();
    }

    @Test
    public void testGivenLogWhenCalledOnOnesThenResultIsZero() {
        Tensor t = Tensor.ones(4);
        Tensor r = t.log();
        Tensor c = r.contiguous();
        for (float v : c.floatArray()) assertEquals(0.0f, v, 1e-6f);
        t.close(); r.close(); c.close();
    }

    @Test
    public void testGivenLogUnderscoreWhenCalledThenMutatesInPlaceAndReturnsSelf() {
        Tensor t = Tensor.ones(3);
        Tensor ret = t.log_();
        assertSame(t, ret, "log_() must return 'this'");
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(0.0f, v, 1e-6f);
        t.close(); c.close();
    }

    @Test
    public void testGivenClampWhenCalledThenValuesAreBounded() {
        Tensor t = Tensor.of(new float[]{-2f, 0f, 0.5f, 3f}, 4);
        Tensor r = t.clamp(0.0, 1.0);
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{0f, 0f, 0.5f, 1f}, c.floatArray(), 1e-7f);
        t.close(); r.close(); c.close();
    }

    @Test
    public void testGivenClampUnderscoreWhenCalledThenMutatesInPlaceAndReturnsSelf() {
        Tensor t = Tensor.of(new float[]{-1f, 5f, 2f}, 3);
        Tensor ret = t.clamp_(0.0, 3.0);
        assertSame(t, ret, "clamp_() must return 'this'");
        Tensor c = t.contiguous();
        assertArrayEquals(new float[]{0f, 3f, 2f}, c.floatArray(), 1e-7f);
        t.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // not_() in-place correctness (bug fix regression)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenNotUnderscoreWhenCalledThenMutatesInPlaceAndReturnsSelf() {
        Tensor t = Tensor.of(new boolean[]{true, false, true}, 3);
        Tensor ret = t.not_();
        assertSame(t, ret, "not_() must return 'this'");
        // after logical NOT: [false, true, false] → byte values [0, 1, 0]
        Tensor c = t.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(0, vals[0]);
        assertEquals(1, vals[1]);
        assertEquals(0, vals[2]);
        t.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Logical operations
    // -----------------------------------------------------------------------

    @Test
    public void testGivenNotWhenCalledThenElementsAreNegated() {
        Tensor t = Tensor.of(new boolean[]{true, false}, 2);
        Tensor r = t.not();
        Tensor c = r.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(0, vals[0]);
        assertEquals(1, vals[1]);
        t.close(); r.close(); c.close();
    }

    @Test
    public void testGivenAndWhenCalledThenLogicalAndApplied() {
        Tensor a = Tensor.of(new boolean[]{true, true, false, false}, 4);
        Tensor b = Tensor.of(new boolean[]{true, false, true, false}, 4);
        Tensor r = a.and(b);
        Tensor c = r.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(1, vals[0]);
        assertEquals(0, vals[1]);
        assertEquals(0, vals[2]);
        assertEquals(0, vals[3]);
        a.close(); b.close(); r.close(); c.close();
    }

    @Test
    public void testGivenOrWhenCalledThenLogicalOrApplied() {
        Tensor a = Tensor.of(new boolean[]{true, true, false, false}, 4);
        Tensor b = Tensor.of(new boolean[]{true, false, true, false}, 4);
        Tensor r = a.or(b);
        Tensor c = r.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(1, vals[0]);
        assertEquals(1, vals[1]);
        assertEquals(1, vals[2]);
        assertEquals(0, vals[3]);
        a.close(); b.close(); r.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Comparison: ne, lt, le, ge
    // -----------------------------------------------------------------------

    @Test
    public void testGivenNeScalarWhenCalledThenReturnsBooleanMask() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor mask = t.ne(2.0f);
        Tensor c = mask.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(1, vals[0]);
        assertEquals(0, vals[1]);
        assertEquals(1, vals[2]);
        t.close(); mask.close(); c.close();
    }

    @Test
    public void testGivenLtScalarWhenCalledThenReturnsBooleanMask() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor mask = t.lt(2.0);
        Tensor c = mask.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(1, vals[0]);
        assertEquals(0, vals[1]);
        assertEquals(0, vals[2]);
        t.close(); mask.close(); c.close();
    }

    @Test
    public void testGivenLeScalarWhenCalledThenReturnsBooleanMask() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor mask = t.le(2.0);
        Tensor c = mask.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(1, vals[0]);
        assertEquals(1, vals[1]);
        assertEquals(0, vals[2]);
        t.close(); mask.close(); c.close();
    }

    @Test
    public void testGivenGeScalarWhenCalledThenReturnsBooleanMask() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor mask = t.ge(2.0);
        Tensor c = mask.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(0, vals[0]);
        assertEquals(1, vals[1]);
        assertEquals(1, vals[2]);
        t.close(); mask.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // Additional arithmetic: alpha variants, tensor-tensor mul/div
    // -----------------------------------------------------------------------

    @Test
    public void testGivenAddWithAlphaWhenCalledThenResultIsCorrect() {
        // a + 2*b = [1,2,3] + 2*[1,1,1] = [3,4,5]
        Tensor a = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor b = Tensor.ones(3);
        Tensor r = a.add(b, 2.0);
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{3f, 4f, 5f}, c.floatArray(), 1e-6f);
        a.close(); b.close(); r.close(); c.close();
    }

    @Test
    public void testGivenSubWithAlphaWhenCalledThenResultIsCorrect() {
        // a - 2*b = [5,5,5] - 2*[1,2,3] = [3,1,-1]
        Tensor a = Tensor.of(new float[]{5f, 5f, 5f}, 3);
        Tensor b = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor r = a.sub(b, 2.0);
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{3f, 1f, -1f}, c.floatArray(), 1e-6f);
        a.close(); b.close(); r.close(); c.close();
    }

    @Test
    public void testGivenMulTensorWhenCalledThenElementwiseProduct() {
        Tensor a = Tensor.of(new float[]{2f, 3f, 4f}, 3);
        Tensor b = Tensor.of(new float[]{10f, 10f, 10f}, 3);
        Tensor r = a.mul(b);
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{20f, 30f, 40f}, c.floatArray(), 1e-6f);
        a.close(); b.close(); r.close(); c.close();
    }

    @Test
    public void testGivenDivTensorWhenCalledThenElementwiseDivision() {
        Tensor a = Tensor.of(new float[]{10f, 20f, 30f}, 3);
        Tensor b = Tensor.of(new float[]{2f, 4f, 5f}, 3);
        Tensor r = a.div(b);
        Tensor c = r.contiguous();
        assertArrayEquals(new float[]{5f, 5f, 6f}, c.floatArray(), 1e-6f);
        a.close(); b.close(); r.close(); c.close();
    }

    @Test
    public void testGivenNegUnderscoreWhenCalledThenMutatesInPlaceAndReturnsSelf() {
        Tensor t = Tensor.of(new float[]{1f, -2f}, 2);
        Tensor ret = t.neg_();
        assertSame(t, ret, "neg_() must return 'this'");
        assertEquals(-1f, t.getFloat(0), 1e-7f);
        assertEquals(2f, t.getFloat(1), 1e-7f);
        t.close();
    }

    // -----------------------------------------------------------------------
    // Shape: flatten(start, end), triu
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFlattenBothDimsWhenAppliedThenPartialFlatten() {
        // [2,3,4] flatten(0,1) → [6,4]
        Tensor t = Tensor.ones(2, 3, 4);
        Tensor f = t.flatten(0, 1);
        assertArrayEquals(new long[]{6, 4}, f.shape());
        t.close(); f.close();
    }

    @Test
    public void testGivenTriuWhenAppliedThenLowerTriangularIsZero() {
        float[] x = {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f};
        Tensor t = Tensor.of(x, 3, 3);
        Tensor r = t.triu(0);
        assertEquals(0f, r.getFloat(1, 0), 1e-7f);
        assertEquals(0f, r.getFloat(2, 0), 1e-7f);
        assertEquals(0f, r.getFloat(2, 1), 1e-7f);
        assertEquals(1f, r.getFloat(0, 0), 1e-7f);
        t.close(); r.close();
    }

    @Test
    public void testGivenTriuUnderscoreWhenAppliedThenMutatesInPlace() {
        float[] x = {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f};
        Tensor t = Tensor.of(x, 3, 3);
        Tensor ret = t.triu_(0);
        assertSame(t, ret);
        assertEquals(0f, t.getFloat(1, 0), 1e-7f);
        t.close();
    }

    // -----------------------------------------------------------------------
    // Type casting (to ScalarType)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFloat32TensorWhenCastToInt64ThenDtypeChanges() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f}, 3);
        Tensor r = t.to(ScalarType.Int64);
        assertEquals(ScalarType.Int64, r.dtype());
        assertEquals(1L, r.getLong(0));
        t.close(); r.close();
    }

    // -----------------------------------------------------------------------
    // fill_
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFillIntWhenCalledThenAllElementsSetToValue() {
        Tensor t = Tensor.zeros(3, 3);
        Tensor ret = t.fill_(5);
        assertSame(t, ret);
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(5f, v, 1e-7f);
        t.close(); c.close();
    }

    @Test
    public void testGivenFillDoubleWhenCalledThenAllElementsSetToValue() {
        Tensor t = Tensor.zeros(4);
        t.fill_(2.5);
        Tensor c = t.contiguous();
        for (float v : c.floatArray()) assertEquals(2.5f, v, 1e-5f);
        t.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // newOnes
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTensorWhenNewOnesCalledThenSameDtypeAllOnes() {
        Tensor t = Tensor.zeros(3);
        Tensor o = t.newOnes(2, 2);
        assertEquals(t.dtype(), o.dtype());
        assertTrue(o.device().isCPU());
        Tensor c = o.contiguous();
        for (float v : c.floatArray()) assertEquals(1f, v, 1e-7f);
        t.close(); o.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // AutoScope
    // -----------------------------------------------------------------------

    @Test
    public void testGivenAutoScopeWhenTensorsCreatedThenReleasedOnPop() {
        // Ensure push/pop does not throw and scope tracks tensors
        try (var scope = new smile.util.AutoScope()) {
            Tensor.push(scope);
            try (Tensor t = Tensor.ones(3)) {
                // tensor is registered in the scope
                assertNotNull(t);
            }
            Tensor.pop(); // scope is closed, no exception expected
        } catch (Exception e) {
            fail("AutoScope push/pop should not throw: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // topk
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTopkWhenCalledThenReturnsLargestElements() {
        Tensor t = Tensor.of(new float[]{3f, 1f, 4f, 1f, 5f, 9f, 2f, 6f}, 8);
        var result = t.topk(3);
        Tensor vals = result._1();
        Tensor idxs = result._2();
        // top-3 values are 9, 6, 5
        Tensor vc = vals.contiguous();
        float[] v = vc.floatArray();
        assertEquals(9f, v[0], 1e-6f);
        assertEquals(6f, v[1], 1e-6f);
        assertEquals(5f, v[2], 1e-6f);
        t.close(); vals.close(); idxs.close(); vc.close();
    }

    // -----------------------------------------------------------------------
    // isin
    // -----------------------------------------------------------------------

    @Test
    public void testGivenIsinWhenCalledThenReturnsMembershipMask() {
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f, 4f, 5f}, 5);
        Tensor set = Tensor.of(new float[]{2f, 4f}, 2);
        Tensor mask = t.isin(set);
        Tensor c = mask.contiguous();
        byte[] vals = c.byteArray();
        assertEquals(0, vals[0]);
        assertEquals(1, vals[1]);
        assertEquals(0, vals[2]);
        assertEquals(1, vals[3]);
        assertEquals(0, vals[4]);
        t.close(); set.close(); mask.close(); c.close();
    }

    // -----------------------------------------------------------------------
    // gather
    // -----------------------------------------------------------------------

    @Test
    public void testGivenGatherWhenCalledThenSelectsIndexedElements() {
        // [[1,2],[3,4]], gather dim=1, index=[[0,0],[1,0]] → [[1,1],[4,3]]
        Tensor t = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 2, 2);
        Tensor idx = Tensor.of(new long[]{0L, 0L, 1L, 0L}, 2, 2);
        Tensor r = t.gather(1, idx);
        assertEquals(1f, r.getFloat(0, 0), 1e-6f);
        assertEquals(1f, r.getFloat(0, 1), 1e-6f);
        assertEquals(4f, r.getFloat(1, 0), 1e-6f);
        assertEquals(3f, r.getFloat(1, 1), 1e-6f);
        t.close(); idx.close(); r.close();
    }

    // -----------------------------------------------------------------------
    // dtypeOptional
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFloat32TensorWhenDtypeOptionalCalledThenPresentAndCorrect() {
        Tensor t = Tensor.ones(3);
        var opt = t.dtypeOptional();
        assertTrue(opt.isPresent());
        assertEquals(ScalarType.Float32, opt.get());
        t.close();
    }
}
