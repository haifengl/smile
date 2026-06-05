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

import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import smile.torch.smile_torch_h;

/**
 * Indexing a tensor.
 *
 * @author Haifeng Li
 */
public class Index implements AutoCloseable {
    /** Sentinel used by the native slice constructor for an unset bound. */
    private static final long NONE = Long.MIN_VALUE;

    /** Native {@code ST_TensorIndex} handle. */
    final MemorySegment handle;
    /** Releases the native handle once this index becomes unreachable. */
    private final Cleaner.Cleanable cleanable;

    /**
     * Constructor.
     * @param handle the native {@code ST_TensorIndex} handle.
     */
    Index(MemorySegment handle) {
        this.handle = Native.check(handle);
        this.cleanable = Native.CLEANER.register(this, new Native.FreeIndex(this.handle));
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    /**
     * The ellipsis (...) is used to slice higher-dimensional data structures as in numpy.
     * It's designed to mean at this point, insert as many full slices (:) to extend
     * the multidimensional slice to all dimensions.
     */
    public static final Index Ellipsis = new Index(smile_torch_h.smile_tensor_index_ellipsis());

    /**
     * The colon (:) is used to slice all elements of a dimension.
     */
    public static final Index Colon = new Index(smile_torch_h.smile_tensor_index_slice(NONE, NONE, NONE));

    /**
     * The None is used to insert a singleton dimension ("unsqueeze"
     * a dimension).
     *
     * <p>This relies on the {@code smile_tensor_index_none} native function. If
     * the loaded {@code smile_torch} library predates that function, this
     * constant is {@code null} until the binary is rebuilt.
     */
    public static final Index None = new Index(smile_torch_h.smile_tensor_index_none());

    /**
     * Returns the index of a single element in a dimension.
     *
     * @param i the element index.
     * @return the index.
     */
    public static Index of(int i) {
        return new Index(smile_torch_h.smile_tensor_index_from_int(i));
    }

    /**
     * Returns the index of a single element in a dimension.
     *
     * @param i the element index.
     * @return the index.
     */
    public static Index of(long i) {
        return new Index(smile_torch_h.smile_tensor_index_from_int(i));
    }

    /**
     * Returns the index of multiple elements in a dimension.
     *
     * @param indices the indices of multiple elements.
     * @return the index.
     */
    public static Index of(int... indices) {
        try (Tensor t = Tensor.of(indices)) {
            return new Index(smile_torch_h.smile_tensor_index_from_tensor(t.handle));
        }
    }

    /**
     * Returns the index of multiple elements in a dimension.
     *
     * @param indices the indices of multiple elements.
     * @return the index.
     */
    public static Index of(long... indices) {
        try (Tensor t = Tensor.of(indices)) {
            return new Index(smile_torch_h.smile_tensor_index_from_tensor(t.handle));
        }
    }

    /**
     * Returns the index of multiple elements in a dimension.
     *
     * @param indices the boolean flags to select multiple elements.
     *               The length of array should match that of the
     *               corresponding dimension of tensor.
     * @return the index.
     */
    public static Index of(boolean... indices) {
        try (Tensor t = Tensor.of(indices)) {
            return new Index(smile_torch_h.smile_tensor_index_from_tensor(t.handle));
        }
    }

    /**
     * Returns the tensor index along a dimension.
     *
     * @param index the tensor index.
     * @return the index.
     */
    public static Index of(Tensor index) {
        return new Index(smile_torch_h.smile_tensor_index_from_tensor(index.handle));
    }

    /**
     * Returns the slice index for [start, end) with incremental step 1.
     *
     * @param start the inclusive start index.
     * @param end the exclusive end index.
     * @return the slice index.
     */
    public static Index slice(Integer start, Integer end) {
        return slice(start, end, 1);
    }

    /**
     * Returns the slice index for [start, end) with the given incremental step.
     *
     * @param start the inclusive start index.
     * @param end the exclusive end index.
     * @param step the incremental step.
     * @return the slice index.
     */
    public static Index slice(Integer start, Integer end, Integer step) {
        return new Index(smile_torch_h.smile_tensor_index_slice(
                start == null ? NONE : start.longValue(),
                end == null ? NONE : end.longValue(),
                step == null ? NONE : step.longValue()
        ));
    }

    /**
     * Returns the slice index for [start, end) with incremental step 1.
     *
     * @param start the inclusive start index.
     * @param end the exclusive end index.
     * @return the slice index.
     */
    public static Index slice(Long start, Long end) {
        return slice(start, end, 1L);
    }

    /**
     * Returns the slice index for [start, end) with the given incremental step.
     *
     * @param start the inclusive start index.
     * @param end the exclusive end index.
     * @param step the incremental step.
     * @return the slice index.
     */
    public static Index slice(Long start, Long end, Long step) {
        return new Index(smile_torch_h.smile_tensor_index_slice(
                start == null ? NONE : start,
                end == null ? NONE : end,
                step == null ? NONE : step
        ));
    }
}
