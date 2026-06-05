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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;
import smile.util.AutoScope;
import smile.util.Tuple2;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static smile.deep.tensor.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * A Tensor is a multidimensional array containing elements of a single data type.
 *
 * <p>This class is a thin, idiomatic wrapper over the {@code smile_torch} native
 * library, accessed through the Foreign Function and Memory API. Each instance
 * owns an {@code ST_Tensor} handle that is released by {@link #close()}, by the
 * enclosing {@link AutoScope}, or as a last resort by a {@link Cleaner} once the
 * wrapper becomes unreachable.
 *
 * @author Haifeng Li
 */
public class Tensor implements AutoCloseable {
    /** A thread-local scope stack controls the lifecycle of tensors, providing timely deallocation. */
    private static final ThreadLocal<Deque<AutoScope>> scopes =
            ThreadLocal.withInitial(ArrayDeque::new);
    /** Default options such as device and dtype. */
    private static Options defaultOptions;
    /** Native {@code ST_Tensor} handle. */
    final MemorySegment handle;
    /** Releases the native handle once this tensor becomes unreachable. */
    private final Cleaner.Cleanable cleanable;

    /**
     * Sets the default options to create tensors. This does not affect
     * factory function calls which are called with an explicit options
     * argument.
     * @param options the construction options of a tensor.
     */
    public static void setDefaultOptions(Options options) {
        defaultOptions = options;
    }

    /**
     * Checks if the CUDA device supports bf16. On pre-ampere hardware
     * bf16 works, but doesn't provide speed-ups compared to fp32 matmul
     * operations, and some matmul operations are failing outright, so
     * this check is more like "guaranteed to work and be performant"
     * than "works somehow".
     * @return true if bf16 works and is performant.
     */
    public static boolean isBF16Supported() {
        return smile_cuda_is_bf16_supported() != 0;
    }

    /**
     * Disables gradient calculation. Disabling gradient calculation is useful
     * for inference, when you are sure that you will not call backward.
     * It will reduce memory consumption for computations that would otherwise
     * have requireGrad(true).
     * <p>
     * In this mode, the result of every computation will have requireGrad(false),
     * even when the inputs have requireGrad(true).
     * <p>
     * This context manager is thread-local; it will not affect computation in
     * other threads.
     *
     * @return no grad guard to be used with try-with scope.
     */
    public static NoGradGuard noGradGuard() {
        return new NoGradGuard();
    }

    /**
     * Pushes a scope onto the top of the tensor scope stack.
     * Newly created tensors will be automatically added to this scope.
     *
     * @param scope a scope to automatically release tensors.
     */
    public static void push(AutoScope scope) {
        scopes.get().push(scope);
    }

    /**
     * Removes the scope at the top of the tensor stack. All tensors
     * added to this scope will be released.
     * @return the top level scope.
     */
    public static AutoScope pop() {
        var scope = scopes.get().pop();
        scope.close();
        return scope;
    }

    /**
     * Constructor.
     * @param handle the native {@code ST_Tensor} handle.
     */
    public Tensor(MemorySegment handle) {
        this.handle = check(handle);
        this.cleanable = Native.CLEANER.register(this, new Native.FreeTensor(this.handle));
        Deque<AutoScope> stack = scopes.get();
        if (!stack.isEmpty()) {
            stack.peek().add(this);
        }
    }

    /** Prints the tensor on the standard output. */
    public void print() {
        smile_torch_print(handle);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return other instanceof Tensor t && handle.address() == t.handle.address();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(handle.address());
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    @Override
    public String toString() {
        String type = dtypeOptional().map(Enum::name).orElse("?");
        return String.format("Tensor(dtype=%s, shape=%s)", type, Arrays.toString(shape()));
    }

    /**
     * Returns the native {@code ST_Tensor} handle.
     * @return the native {@code ST_Tensor} handle.
     */
    public MemorySegment handle() {
        return handle;
    }

    /**
     * Sets if autograd should record operations on this tensor.
     * @param required the flag indicating if autograd should record
     *                operations on this tensor.
     * @return this tensor.
     */
    public Tensor setRequireGrad(boolean required) {
        smile_tensor_set_requires_grad(handle, required ? 1 : 0);
        return this;
    }

    /**
     * Returns true if autograd should record operations on this tensor.
     * @return true if autograd should record operations on this tensor.
     */
    public boolean getRequireGrad() {
        return smile_tensor_requires_grad(handle) != 0;
    }

    /**
     * Returns a new tensor, detached from the current graph.
     * The result will never require gradient.
     *
     * @return a new tensor that doesn't require gradient.
     */
    public Tensor detach() {
        return new Tensor(smile_tensor_detach(handle));
    }

    /**
     * Clone the tensor with a different data type.
     * @param dtype the element data type of new tensor.
     * @return The cloned tensor.
     */
    public Tensor to(ScalarType dtype) {
        return new Tensor(smile_tensor_to_dtype(handle, dtype.code));
    }

    /**
     * Clone the tensor to a device.
     * @param device the compute device of new tensor.
     * @return The cloned tensor.
     */
    public Tensor to(Device device) {
        return to(device, dtype());
    }

    /**
     * Clone the tensor to a device with a different data type.
     * @param device the compute device of new tensor.
     * @param dtype the element data type of new tensor.
     * @return The cloned tensor.
     */
    public Tensor to(Device device, ScalarType dtype) {
        MemorySegment dev = device.toNative();
        try {
            return new Tensor(smile_tensor_to_device(handle, dev, dtype.code));
        } finally {
            smile_device_free(dev);
        }
    }

    /**
     * Returns the element data type.
     * @return the element data type, or empty if the type is not mapped.
     */
    public Optional<ScalarType> dtypeOptional() {
        return ScalarType.of(smile_tensor_dtype(handle));
    }

    /**
     * Returns the element data type.
     * @return the element data type.
     * @throws IllegalStateException if the native dtype has no Java mapping.
     */
    public ScalarType dtype() {
        return dtypeOptional().orElseThrow(() ->
                new IllegalStateException("Unknown native scalar type: " + smile_tensor_dtype(handle)));
    }

    /**
     * Returns the device on which the tensor is.
     * @return the device.
     */
    public Device device() {
        MemorySegment device = Native.tensorDevice(handle);
        try {
            return Device.fromNative(device);
        } finally {
            smile_device_free(device);
        }
    }

    /**
     * Returns the number of dimensions of tensor.
     * @return the number of dimensions of tensor
     */
    public int dim() {
        return smile_tensor_dim(handle);
    }

    /**
     * Returns the shape of the tensor.
     * @return the shape of the tensor.
     */
    public long[] shape() {
        int dim = smile_tensor_dim(handle);
        if (dim <= 0) return new long[0];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(JAVA_LONG, dim);
            smile_tensor_shape(handle, buf, dim);
            return buf.toArray(JAVA_LONG);
        }
    }

    /**
     * Returns the size of given dimension.
     * @param dim dimension index.
     * @return the size of given dimension.
     */
    public long size(int dim) {
        return smile_tensor_size(handle, dim);
    }

    /**
     * Returns the number of tensor elements.
     * @return the number of tensor elements.
     */
    public long length() {
        long length = 1;
        for (var size : shape()) {
            length *= size;
        }
        return length;
    }

    /**
     * Returns the number of elements as an int, used by array extraction methods.
     * @throws ArithmeticException if the number of elements exceeds Integer.MAX_VALUE.
     */
    private int lengthAsInt() {
        long len = length();
        if (len > Integer.MAX_VALUE) {
            throw new ArithmeticException(
                "Tensor has " + len + " elements, which exceeds Integer.MAX_VALUE. " +
                "Use a smaller tensor or extract a slice first.");
        }
        return (int) len;
    }

    /**
     * Returns a new tensor with the negative of the elements of input.
     * @return the output tensor.
     */
    public Tensor neg() {
        return new Tensor(smile_tensor_neg(handle));
    }

    /**
     * Returns the tensor with the negative of the elements of input.
     * @return this tensor.
     */
    public Tensor neg_() {
        smile_tensor_neg_(handle);
        return this;
    }

    /**
     * Returns a contiguous in memory tensor containing the same data as this tensor.
     * @return a contiguous in memory tensor containing the same data as this tensor.
     */
    public Tensor contiguous() {
        return new Tensor(smile_tensor_contiguous(handle));
    }

    /**
     * Returns a new view of this tensor with singleton dimensions
     * expanded to a larger size.
     *
     * @param size the desired expanded size.
     * @return the tensor view with the expanded size.
     */
    public Tensor expand(long... size) {
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_expand(handle, longs(arena, size), size.length));
        }
    }

    /**
     * Returns a tensor with the same data and number of elements
     * but with the specified shape. This method returns a view
     * if shape is compatible with the current shape.
     *
     * @param shape the new shape of tensor.
     * @return the tensor with the specified shape.
     */
    public Tensor reshape(long... shape) {
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_reshape(handle, longs(arena, shape), shape.length));
        }
    }

    /**
     * Flattens the tensor by reshaping it into a one-dimensional tensor.
     * This function may return the original object, a view, or copy.
     * @return the tensor with the specified shape.
     */
    public Tensor flatten() {
        return flatten(0);
    }

    /**
     * Flattens the tensor by reshaping it into a one-dimensional tensor.
     * Only dimensions starting with startDim and ending with endDim are
     * flattened. The order of elements in input is unchanged.
     * This function may return the original object, a view, or copy.
     *
     * @param startDim the first dim to flatten.
     * @return the tensor with the specified shape.
     */
    public Tensor flatten(int startDim) {
        return flatten(startDim, -1);
    }

    /**
     * Flattens the tensor by reshaping it into a one-dimensional tensor.
     * Only dimensions starting with startDim and ending with endDim are
     * flattened. The order of elements in input is unchanged.
     * This function may return the original object, a view, or copy.
     *
     * @param startDim the first dim to flatten.
     * @param endDim the last dim to flatten
     * @return the tensor with the specified shape.
     */
    public Tensor flatten(int startDim, int endDim) {
        return new Tensor(smile_tensor_flatten(handle, startDim, endDim));
    }

    /** Computes the gradients. */
    public void backward() {
        smile_tensor_backward(handle);
    }

    /**
     * Fills this tensor with the specified value.
     * @param x the value.
     * @return this tensor.
     */
    public Tensor fill_(int x) {
        MemorySegment s = iscalar(x);
        try {
            smile_tensor_fill_(handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Fills this tensor with the specified value.
     * @param x the value.
     * @return this tensor.
     */
    public Tensor fill_(double x) {
        MemorySegment s = fscalar(x);
        try {
            smile_tensor_fill_(handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Draws binary random numbers (0 or 1) from a Bernoulli distribution.
     * @param p Bernoulli probability.
     * @return this tensor.
     */
    public Tensor bernoulli_(double p) {
        smile_tensor_bernoulli_(handle, p);
        return this;
    }

    /**
     * Returns a view of the original tensor input with its dimensions permuted.
     * @param dims The desired ordering of dimensions.
     * @return the permuted tensor.
     */
    public Tensor permute(long... dims) {
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_permute(handle, longs(arena, dims), dims.length));
        }
    }

    /**
     * Updates a portion of tensor.
     * @param source the new sub-tensor values.
     * @param indices the indices along the dimensions.
     * @return the output tensor.
     */
    public Tensor put(Tensor source, Index... indices) {
        Tensor out = new Tensor(smile_tensor_clone(handle));
        return out.put_(source, indices);
    }

    /**
     * Updates a portion of tensor.
     * @param source the new sub-tensor value.
     * @param index the sub-tensor index.
     * @return the output tensor.
     */
    public Tensor put(Tensor source, Tensor index) {
        Tensor out = new Tensor(smile_tensor_clone(handle));
        return out.put_(source, index);
    }

    /**
     * Updates a portion of tensor in place.
     * @param source the new sub-tensor values.
     * @param indices the indices along the dimensions.
     * @return this tensor.
     */
    public Tensor put_(Tensor source, Index... indices) {
        MemorySegment vec = indexVec(indices);
        try {
            smile_tensor_index_put_(handle, vec, source.handle);
            return this;
        } finally {
            smile_tensor_index_vec_free(vec);
        }
    }

    /**
     * Updates a portion of tensor in place.
     * @param source the new sub-tensor value.
     * @param index the sub-tensor index.
     * @return this tensor.
     */
    public Tensor put_(Tensor source, Tensor index) {
        MemorySegment idx = smile_tensor_index_from_tensor(index.handle);
        MemorySegment vec = smile_tensor_index_vec_create();
        try {
            smile_tensor_index_vec_push(vec, idx);
            smile_tensor_index_put_(handle, vec, source.handle);
            return this;
        } finally {
            smile_tensor_index_vec_free(vec);
            smile_tensor_index_free(idx);
        }
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(byte x, int... indices) {
        return putScalar(iscalar(x), toLong(indices));
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(byte x, long... indices) {
        return putScalar(iscalar(x), indices);
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(short x, int... indices) {
        return putScalar(iscalar(x), toLong(indices));
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(short x, long... indices) {
        return putScalar(iscalar(x), indices);
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(int x, int... indices) {
        return putScalar(iscalar(x), toLong(indices));
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(int x, long... indices) {
        return putScalar(iscalar(x), indices);
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(long x, int... indices) {
        return putScalar(iscalar(x), toLong(indices));
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(long x, long... indices) {
        return putScalar(iscalar(x), indices);
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(float x, int... indices) {
        return putScalar(fscalar(x), toLong(indices));
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(float x, long... indices) {
        return putScalar(fscalar(x), indices);
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(double x, int... indices) {
        return putScalar(fscalar(x), toLong(indices));
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(double x, long... indices) {
        return putScalar(fscalar(x), indices);
    }

    /** Scatters a scalar (consuming the handle) at the given integer indices. */
    private Tensor putScalar(MemorySegment scalar, long[] indices) {
        MemorySegment vec = indexVec(indices);
        try {
            smile_tensor_index_put_scalar_(handle, vec, scalar);
            return this;
        } finally {
            smile_tensor_index_vec_free(vec);
            smile_scalar_free(scalar);
        }
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param indices the indices along the dimensions.
     * @return the sub-tensor.
     */
    public Tensor get(int... indices) {
        return new Tensor(indexed(toLong(indices)));
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param indices the indices along the dimensions.
     * @return the sub-tensor.
     */
    public Tensor get(long... indices) {
        return new Tensor(indexed(indices));
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param indices the indices along the dimensions.
     * @return the sub-tensor.
     */
    public Tensor get(Index... indices) {
        MemorySegment vec = indexVec(indices);
        try {
            return new Tensor(smile_tensor_index(handle, vec));
        } finally {
            smile_tensor_index_vec_free(vec);
        }
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param index the tensor index.
     * @return the sub-tensor.
     */
    public Tensor get(Tensor index) {
        MemorySegment idx = smile_tensor_index_from_tensor(index.handle);
        MemorySegment vec = smile_tensor_index_vec_create();
        try {
            smile_tensor_index_vec_push(vec, idx);
            return new Tensor(smile_tensor_index(handle, vec));
        } finally {
            smile_tensor_index_vec_free(vec);
            smile_tensor_index_free(idx);
        }
    }

    /**
     * Returns the byte value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public byte getByte(int... indices) {
        return getByte(toLong(indices));
    }

    /**
     * Returns the byte value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public byte getByte(long... indices) {
        MemorySegment sub = indexed(indices);
        try {
            return smile_tensor_item_byte(sub);
        } finally {
            smile_tensor_free(sub);
        }
    }

    /**
     * Returns the short value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public short getShort(int... indices) {
        return getShort(toLong(indices));
    }

    /**
     * Returns the short value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public short getShort(long... indices) {
        MemorySegment sub = indexed(indices);
        try {
            return smile_tensor_item_short(sub);
        } finally {
            smile_tensor_free(sub);
        }
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public int getInt(int... indices) {
        return getInt(toLong(indices));
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public int getInt(long... indices) {
        MemorySegment sub = indexed(indices);
        try {
            return smile_tensor_item_int(sub);
        } finally {
            smile_tensor_free(sub);
        }
    }

    /**
     * Returns the long value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public long getLong(int... indices) {
        return getLong(toLong(indices));
    }

    /**
     * Returns the long value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public long getLong(long... indices) {
        MemorySegment sub = indexed(indices);
        try {
            return smile_tensor_item_long(sub);
        } finally {
            smile_tensor_free(sub);
        }
    }

    /**
     * Returns the float value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public float getFloat(int... indices) {
        return getFloat(toLong(indices));
    }

    /**
     * Returns the float value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public float getFloat(long... indices) {
        MemorySegment sub = indexed(indices);
        try {
            return smile_tensor_item_float(sub);
        } finally {
            smile_tensor_free(sub);
        }
    }

    /**
     * Returns the double value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public double getDouble(int... indices) {
        return getDouble(toLong(indices));
    }

    /**
     * Returns the double value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public double getDouble(long... indices) {
        MemorySegment sub = indexed(indices);
        try {
            return smile_tensor_item_double(sub);
        } finally {
            smile_tensor_free(sub);
        }
    }

    /**
     * Returns the boolean value when the tensor holds a single value.
     * @return the boolean value when the tensor holds a single value.
     */
    public boolean boolValue() {
        return smile_tensor_item_bool(handle) != 0;
    }

    /**
     * Returns the byte value when the tensor holds a single value.
     * @return the byte value when the tensor holds a single value.
     */
    public byte byteValue() {
        return smile_tensor_item_byte(handle);
    }

    /**
     * Returns the short value when the tensor holds a single value.
     * @return the short value when the tensor holds a single value.
     */
    public short shortValue() {
        return smile_tensor_item_short(handle);
    }

    /**
     * Returns the int value when the tensor holds a single value.
     * @return the int value when the tensor holds a single value.
     */
    public int intValue() {
        return smile_tensor_item_int(handle);
    }

    /**
     * Returns the long value when the tensor holds a single value.
     * @return the long value when the tensor holds a single value.
     */
    public long longValue() {
        return smile_tensor_item_long(handle);
    }

    /**
     * Returns the float value when the tensor holds a single value.
     * @return the float value when the tensor holds a single value.
     */
    public float floatValue() {
        return smile_tensor_item_float(handle);
    }

    /**
     * Returns the double value when the tensor holds a single value.
     * @return the double value when the tensor holds a single value.
     */
    public double doubleValue() {
        return smile_tensor_item_double(handle);
    }

    /**
     * Returns the byte array of tensor elements
     * @return the byte array of tensor elements.
     */
    public byte[] byteArray() {
        checkContiguous();
        // Bool tensors use the same storage layout as Byte but have a different dtype.
        // Cast to Int8 so data_ptr_byte() succeeds.
        if (smile_tensor_dtype(handle) == ScalarType.BOOL_CODE) {
            try (Tensor t = to(ScalarType.Int8)) {
                return t.byteArray();
            }
        }
        int n = lengthAsInt();
        MemorySegment data = check(smile_tensor_data_ptr_byte(handle));
        return data.reinterpret(n).toArray(JAVA_BYTE);
    }

    /**
     * Returns the short integer array of tensor elements
     * @return the short integer array of tensor elements.
     */
    public short[] shortArray() {
        checkContiguous();
        int n = lengthAsInt();
        MemorySegment data = check(smile_tensor_data_ptr_short(handle));
        return data.reinterpret(n * 2L).toArray(JAVA_SHORT);
    }

    /**
     * Returns the integer array of tensor elements
     * @return the integer array of tensor elements.
     */
    public int[] intArray() {
        checkContiguous();
        int n = lengthAsInt();
        MemorySegment data = check(smile_tensor_data_ptr_int(handle));
        return data.reinterpret(n * 4L).toArray(JAVA_INT);
    }

    /**
     * Returns the long integer array of tensor elements
     * @return the long integer array of tensor elements.
     */
    public long[] longArray() {
        checkContiguous();
        int n = lengthAsInt();
        MemorySegment data = check(smile_tensor_data_ptr_long(handle));
        return data.reinterpret(n * 8L).toArray(JAVA_LONG);
    }

    /**
     * Returns the float array of tensor elements
     * @return the float array of tensor elements.
     */
    public float[] floatArray() {
        checkContiguous();
        int n = lengthAsInt();
        MemorySegment data = check(smile_tensor_data_ptr_float(handle));
        return data.reinterpret(n * 4L).toArray(JAVA_FLOAT);
    }

    /**
     * Returns the double array of tensor elements
     * @return the double array of tensor elements.
     */
    public double[] doubleArray() {
        checkContiguous();
        int n = lengthAsInt();
        MemorySegment data = check(smile_tensor_data_ptr_double(handle));
        return data.reinterpret(n * 8L).toArray(JAVA_DOUBLE);
    }

    /** Throws if the tensor is a non-contiguous view that cannot be copied directly. */
    private void checkContiguous() {
        if (smile_tensor_is_view(handle) != 0) {
            throw new UnsupportedOperationException("Cannot copy a non-contiguous tensor view to array; call contiguous() first.");
        }
    }

    /**
     * Returns a new tensor with a dimension of size one inserted at the
     * specified position.
     * <p>
     * The returned tensor shares the same underlying data with this tensor.
     * <p>
     * A dim value within the range [-input.dim() - 1, input.dim() + 1) can be
     * used. Negative dim will correspond to unsqueeze() applied at
     * dim = dim + input.dim() + 1.
     *
     * @param dim the index at which to insert the singleton dimension.
     * @return the output tensor.
     */
    public Tensor unsqueeze(long dim) {
        return new Tensor(smile_tensor_unsqueeze(handle, dim));
    }

    /**
     * Returns a tensor that is a transposed version of input. The given
     * dimensions dim0 and dim1 are swapped.
     * <p>
     * If input is a strided tensor then the resulting out tensor shares
     * its underlying storage with the input tensor, so changing the content
     * of one would change the content of the other.
     * <p>
     * If input is a sparse tensor then the resulting out tensor does not
     * share the underlying storage with the input tensor.
     * <p>
     * If input is a sparse tensor with compressed layout (SparseCSR,
     * SparseBSR, SparseCSC or SparseBSC) the arguments dim0 and dim1 must
     * be both batch dimensions, or must both be sparse dimensions. The
     * batch dimensions of a sparse tensor are the dimensions preceding
     * the sparse dimensions.
     *
     * @param dim0 the first dimension to be transposed.
     * @param dim1 the second dimension to be transposed.
     * @return the output tensor.
     */
    public Tensor transpose(long dim0, long dim1) {
        return new Tensor(smile_tensor_transpose(handle, dim0, dim1));
    }

    /**
     * Returns the upper triangular part of a matrix (2-D tensor) or batch of
     * matrices input, the other elements of the result tensor out are set to 0.
     * @param diagonal The parameter diagonal controls which diagonal to consider.
     *                If diagonal = 0, all elements on and above the main diagonal
     *                are retained. A positive value excludes just as many diagonals
     *                above the main diagonal, and similarly a negative value includes
     *                just as many diagonals below the main diagonal.
     * @return the output tensor.
     */
    public Tensor triu(long diagonal) {
        return new Tensor(smile_tensor_triu(handle, diagonal));
    }

    /**
     * Returns the upper triangular part of a matrix (2-D tensor) or batch of
     * matrices input, the other elements of the result tensor out are set to 0.
     * @param diagonal The parameter diagonal controls which diagonal to consider.
     *                If diagonal = 0, all elements on and above the main diagonal
     *                are retained. A positive value excludes just as many diagonals
     *                above the main diagonal, and similarly a negative value includes
     *                just as many diagonals below the main diagonal.
     * @return this tensor.
     */
    public Tensor triu_(long diagonal) {
        smile_tensor_triu_(handle, diagonal);
        return this;
    }

    /**
     * Returns a view tensor that shares the same underlying data with this
     * base tensor. Supporting View avoids explicit data copy, thus allows us
     * to do fast and memory efficient reshaping, slicing and element-wise
     * operations.
     * @param shape the shape of view tensor.
     * @return the view tensor.
     */
    public Tensor view(long... shape) {
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_view(handle, longs(arena, shape), shape.length));
        }
    }

    /**
     * Returns a view of tensor as a complex tensor.
     * @return the complex tensor view.
     */
    public Tensor viewAsComplex() {
        return new Tensor(smile_torch_view_as_complex(handle));
    }

    /**
     * Returns a view of tensor as a real tensor.
     * @return the real tensor view.
     */
    public Tensor viewAsReal() {
        return new Tensor(smile_torch_view_as_real(handle));
    }

    /**
     * Returns the indices of the maximum value of a tensor across a dimension.
     *
     * @param dim the dimension to reduce.
     * @param keepDim whether the output tensor has dim retained or not.
     * @return the indices of the maximum value of a tensor across a dimension.
     */
    public Tensor argmax(int dim, boolean keepDim) {
        return new Tensor(smile_tensor_argmax(handle, dim, keepDim ? 1 : 0, 1));
    }

    /**
     * Returns the k largest elements.
     *
     * @param k the number of largest elements.
     * @return the values and indices of the largest k elements.
     */
    public Tuple2<Tensor, Tensor> topk(int k) {
        return topk(k, -1, true, true);
    }

    /**
     * Returns the k largest elements along a given dimension.
     *
     * @param k the number of largest elements.
     * @param dim the dimension to sort along.
     * @param largest controls whether to return largest or smallest elements.
     * @param sorted  controls whether to return the elements in sorted order.
     * @return the values and indices of the largest k elements.
     */
    public Tuple2<Tensor, Tensor> topk(int k, int dim, boolean largest, boolean sorted) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment values = arena.allocate(C_POINTER);
            MemorySegment indices = arena.allocate(C_POINTER);
            if (smile_tensor_topk(handle, k, dim, largest ? 1 : 0, sorted ? 1 : 0, values, indices) != 0) {
                throw new RuntimeException(Native.lastError());
            }
            return new Tuple2<>(new Tensor(values.get(C_POINTER, 0)), new Tensor(indices.get(C_POINTER, 0)));
        }
    }

    /**
     * Performs top-p (nucleus) sampling on a probability distribution.
     * Top-p sampling selects the smallest set of tokens whose cumulative
     * probability mass exceeds the threshold p. The distribution is
     * renormalized based on the selected tokens.
     * @param p Probability threshold for top-p sampling.
     * @return Sampled token indices.
     */
    public Tensor topp(double p) {
        try (var scope = new AutoScope()) {
            var sort = sort(-1, true);
            var probsSort = scope.add(sort._1());
            var probsIdx = scope.add(sort._2());
            var probsSum = scope.add(probsSort.cumsum(-1));

            var mask = scope.add(probsSum.sub_(probsSort).gt(p));
            // probsSort[mask] = 0
            MemorySegment idx = smile_tensor_index_from_tensor(mask.handle);
            MemorySegment vec = smile_tensor_index_vec_create();
            MemorySegment zero = fscalar(0.0);
            try {
                smile_tensor_index_vec_push(vec, idx);
                smile_tensor_index_put_scalar_(probsSort.handle, vec, zero);
            } finally {
                smile_scalar_free(zero);
                smile_tensor_index_vec_free(vec);
                smile_tensor_index_free(idx);
            }

            var sum = scope.add(probsSort.sum(-1, true));
            probsSort.div_(sum);
            var sample = scope.add(probsSort.multinomial(1));
            return probsIdx.gather(-1, sample);
        }
    }

    /** Sorts the elements along a dimension, returning (values, indices). */
    private Tuple2<Tensor, Tensor> sort(long dim, boolean descending) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment values = arena.allocate(C_POINTER);
            MemorySegment indices = arena.allocate(C_POINTER);
            if (smile_torch_sort(handle, dim, descending ? 1 : 0, values, indices) != 0) {
                throw new RuntimeException(Native.lastError());
            }
            return new Tuple2<>(new Tensor(values.get(C_POINTER, 0)), new Tensor(indices.get(C_POINTER, 0)));
        }
    }

    /** Returns the cumulative sum along a dimension. */
    private Tensor cumsum(long dim) {
        return new Tensor(smile_torch_cumsum(handle, dim));
    }

    /** Draws samples from a multinomial distribution. */
    private Tensor multinomial(long numSamples) {
        return new Tensor(smile_torch_multinomial(handle, numSamples));
    }

    /**
     * Stacks tensors in sequence horizontally (column wise).
     * @param tensors the tensors to concatenate.
     * @return the output tensor.
     */
    public static Tensor hstack(Tensor... tensors) {
        MemorySegment vec = tensorVec(tensors);
        try {
            return new Tensor(smile_torch_hstack(vec));
        } finally {
            smile_tensor_vec_free(vec);
        }
    }

    /**
     * Stacks tensors in sequence vertically (row wise).
     * @param tensors the tensors to concatenate.
     * @return the output tensor.
     */
    public static Tensor vstack(Tensor... tensors) {
        MemorySegment vec = tensorVec(tensors);
        try {
            return new Tensor(smile_torch_vstack(vec));
        } finally {
            smile_tensor_vec_free(vec);
        }
    }

    /**
     * Returns a complex tensor whose elements are Cartesian coordinates
     * corresponding to the polar coordinates with abs and angle.
     * @param abs The absolute value the complex tensor. Must be float or double.
     * @param angle The angle of the complex tensor. Must be same dtype as abs.
     * @return the complex tensor.
     */
    public static Tensor polar(Tensor abs, Tensor angle) {
        return new Tensor(smile_torch_polar(abs.handle, angle.handle));
    }

    /**
     * Computes the cross entropy loss between input logits and target.
     *
     * @param input Predicted unnormalized logits.
     * @param target Ground truth class indices or class probabilities.
     * @param reduction Specifies the reduction to apply to the output:
     *                 "none" | "mean" | "sum". "none": no reduction will
     *                 be applied, "mean": the sum of the output will be
     *                 divided by the number of elements in the output,
     *                 "sum": the output will be summed.
     * @param ignoreIndex Specifies a target value that is ignored and does
     *                   not contribute to the input gradient. Note that
     *                   ignoreIndex is only applicable when the target
     *                   contains class indices.
     * @return the cross entropy loss between input logits and target.
     */
    public static Tensor crossEntropy(Tensor input, Tensor target, String reduction, long ignoreIndex) {
        int kind = switch (reduction) {
            case "none" -> 0;
            case "mean" -> 1;
            case "sum" -> 2;
            default -> throw new IllegalArgumentException("Invalid reduction: " + reduction);
        };
        return new Tensor(smile_torch_cross_entropy(input.handle, target.handle, ignoreIndex, kind));
    }

    /**
     * Returns a tensor of elements selected from either input or other,
     * depending on condition.
     *
     * @param condition a boolean tensor. When true (nonzero), yield input,
     *                 otherwise yield other.
     * @param input value selected at indices where condition is true.
     * @param other value selected at indices where condition is false.
     * @return the output tensor.
     */
    public static Tensor where(Tensor condition, Tensor input, Tensor other) {
        return new Tensor(smile_torch_where_tt(condition.handle, input.handle, other.handle));
    }

    /**
     * Returns a tensor of elements selected from either input or other,
     * depending on condition.
     *
     * @param condition a boolean tensor. When true (nonzero), yield input,
     *                 otherwise yield other.
     * @param input value selected at indices where condition is true.
     * @param other value selected at indices where condition is false.
     * @return the output tensor.
     */
    public static Tensor where(Tensor condition, int input, int other) {
        try (Tensor in = full(input)) {
            MemorySegment s = iscalar(other);
            try {
                return new Tensor(smile_torch_where_ts(condition.handle, in.handle, s));
            } finally {
                smile_scalar_free(s);
            }
        }
    }

    /**
     * Returns a tensor of elements selected from either input or other,
     * depending on condition.
     *
     * @param condition a boolean tensor. When true (nonzero), yield input,
     *                 otherwise yield other.
     * @param input value selected at indices where condition is true.
     * @param other value selected at indices where condition is false.
     * @return the output tensor.
     */
    public static Tensor where(Tensor condition, double input, double other) {
        try (Tensor in = full(input)) {
            MemorySegment s = fscalar(other);
            try {
                return new Tensor(smile_torch_where_ts(condition.handle, in.handle, s));
            } finally {
                smile_scalar_free(s);
            }
        }
    }

    /**
     * Returns the matrix product of two tensors.
     * @param other another tensor.
     * @return the matrix product of two tensors.
     */
    public Tensor matmul(Tensor other) {
        return new Tensor(smile_tensor_matmul(handle, other.handle));
    }

    /**
     * Returns the outer product of two tensors.
     * @param other another tensor.
     * @return the outer product of two tensors.
     */
    public Tensor outer(Tensor other) {
        return new Tensor(smile_tensor_outer(handle, other.handle));
    }

    /**
     * Computes element-wise equality.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor eq(int other) {
        return scalarCompare(iscalar(other), Comparison.EQ);
    }

    /**
     * Computes element-wise equality.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor eq(double other) {
        return scalarCompare(fscalar(other), Comparison.EQ);
    }

    /**
     * Computes element-wise equality.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor eq(Tensor other) {
        return new Tensor(smile_tensor_eq_t(handle, other.handle));
    }

    /**
     * Computes element-wise inequality.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor ne(int other) {
        return scalarCompare(iscalar(other), Comparison.NE);
    }

    /**
     * Computes element-wise inequality.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor ne(double other) {
        return scalarCompare(fscalar(other), Comparison.NE);
    }

    /**
     * Computes element-wise inequality.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor ne(Tensor other) {
        return new Tensor(smile_tensor_ne_t(handle, other.handle));
    }

    /**
     * Computes element-wise less-than comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor lt(double other) {
        return scalarCompare(fscalar(other), Comparison.LT);
    }

    /**
     * Computes element-wise less-than comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor lt(int other) {
        return scalarCompare(iscalar(other), Comparison.LT);
    }

    /**
     * Computes element-wise less-than comparison.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor lt(Tensor other) {
        return new Tensor(smile_tensor_lt_t(handle, other.handle));
    }

    /**
     * Computes element-wise less-than-or-equal-to comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor le(int other) {
        return scalarCompare(iscalar(other), Comparison.LE);
    }

    /**
     * Computes element-wise less-than-or-equal-to comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor le(double other) {
        return scalarCompare(fscalar(other), Comparison.LE);
    }

    /**
     * Computes element-wise less-than-or-equal-to comparison.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor le(Tensor other) {
        return new Tensor(smile_tensor_le_t(handle, other.handle));
    }

    /**
     * Computes element-wise greater-than comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor gt(int other) {
        return scalarCompare(iscalar(other), Comparison.GT);
    }

    /**
     * Computes element-wise greater-than comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor gt(double other) {
        return scalarCompare(fscalar(other), Comparison.GT);
    }

    /**
     * Computes element-wise greater-than comparison.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor gt(Tensor other) {
        return new Tensor(smile_tensor_gt_t(handle, other.handle));
    }

    /**
     * Computes element-wise greater-than-or-equal-to comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor ge(int other) {
        return scalarCompare(iscalar(other), Comparison.GE);
    }

    /**
     * Computes element-wise greater-than-or-equal-to comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor ge(double other) {
        return scalarCompare(fscalar(other), Comparison.GE);
    }

    /**
     * Computes element-wise greater-than-or-equal-to comparison.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor ge(Tensor other) {
        return new Tensor(smile_tensor_ge_t(handle, other.handle));
    }

    /** The scalar comparison operators. */
    private enum Comparison { EQ, NE, LT, LE, GT, GE }

    /** Applies a scalar comparison (consuming the scalar handle). */
    private Tensor scalarCompare(MemorySegment s, Comparison op) {
        try {
            MemorySegment result = switch (op) {
                case EQ -> smile_tensor_eq_s(handle, s);
                case NE -> smile_tensor_ne_s(handle, s);
                case LT -> smile_tensor_lt_s(handle, s);
                case LE -> smile_tensor_le_s(handle, s);
                case GT -> smile_tensor_gt_s(handle, s);
                case GE -> smile_tensor_ge_s(handle, s);
            };
            return new Tensor(result);
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns the sum of all elements in the tensor.
     * @return the sum of all elements.
     */
    public Tensor sum() {
        return new Tensor(smile_tensor_sum(handle));
    }

    /**
     * Returns the sum along a dimension in the tensor.
     * @param dim the dimension to reduce.
     * @param keepDim whether the output tensor has dim retained or not.
     * @return the output tensor.
     */
    public Tensor sum(int dim, boolean keepDim) {
        int dtype = smile_tensor_dtype(handle);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dims = arena.allocateFrom(JAVA_LONG, dim);
            return new Tensor(smile_tensor_sum_dims(handle, dims, 1, keepDim ? 1 : 0, dtype));
        }
    }

    /**
     * Returns the mean of all elements in the tensor.
     * @return the mean of all elements.
     */
    public Tensor mean() {
        return new Tensor(smile_tensor_mean(handle));
    }

    /**
     * Returns the mean along a dimension in the tensor.
     * @param dim the dimension to reduce.
     * @param keepDim whether the output tensor has dim retained or not.
     * @return the output tensor.
     */
    public Tensor mean(int dim, boolean keepDim) {
        int dtype = smile_tensor_dtype(handle);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dims = arena.allocateFrom(JAVA_LONG, dim);
            return new Tensor(smile_tensor_mean_dims(handle, dims, 1, keepDim ? 1 : 0, dtype));
        }
    }

    /**
     * Returns the reciprocal of the square-root of each of the elements in the tensor.
     * @return the output tensor.
     */
    public Tensor rsqrt() {
        return new Tensor(smile_tensor_rsqrt(handle));
    }

    /**
     * Returns the reciprocal of the square-root of each of the elements in the tensor.
     * @return this tensor.
     */
    public Tensor rsqrt_() {
        smile_tensor_rsqrt_(handle);
        return this;
    }

    /**
     * Returns the minimum value of all elements in the tensor.
     * @return the minimum scalar tensor.
     */
    public Tensor min() {
        return new Tensor(smile_tensor_min(handle));
    }

    /**
     * Returns the maximum value of all elements in the tensor.
     * @return the maximum scalar tensor.
     */
    public Tensor max() {
        return new Tensor(smile_tensor_max(handle));
    }

    /**
     * Returns a new tensor with the absolute value of the elements of input.
     * @return the output tensor.
     */
    public Tensor abs() {
        return new Tensor(smile_tensor_abs(handle));
    }

    /**
     * Computes the absolute value of the elements of input in place.
     * @return this tensor.
     */
    public Tensor abs_() {
        smile_tensor_abs_(handle);
        return this;
    }

    /**
     * Returns a new tensor with the natural logarithm of the elements of input.
     * @return the output tensor.
     */
    public Tensor log() {
        return new Tensor(smile_tensor_log(handle));
    }

    /**
     * Computes the natural logarithm of the elements of input in place.
     * @return this tensor.
     */
    public Tensor log_() {
        smile_tensor_log_(handle);
        return this;
    }

    /**
     * Clamps all elements in input into the range [{@code min}, {@code max}].
     * @param min lower-bound of the range to be clamped to.
     * @param max upper-bound of the range to be clamped to.
     * @return the output tensor.
     */
    public Tensor clamp(double min, double max) {
        MemorySegment lo = fscalar(min);
        MemorySegment hi = fscalar(max);
        try {
            return new Tensor(smile_tensor_clamp(handle, 1, lo, 1, hi));
        } finally {
            smile_scalar_free(lo);
            smile_scalar_free(hi);
        }
    }

    /**
     * Clamps all elements in input into the range [{@code min}, {@code max}] in place.
     * @param min lower-bound of the range to be clamped to.
     * @param max upper-bound of the range to be clamped to.
     * @return this tensor.
     */
    public Tensor clamp_(double min, double max) {
        MemorySegment lo = fscalar(min);
        MemorySegment hi = fscalar(max);
        try {
            smile_tensor_clamp_(handle, 1, lo, 1, hi);
            return this;
        } finally {
            smile_scalar_free(lo);
            smile_scalar_free(hi);
        }
    }

    /**
     * Returns the exponential of elements in the tensor.
     * @return the output tensor.
     */
    public Tensor exp() {
        return new Tensor(smile_tensor_exp(handle));
    }

    /**
     * Returns the exponential of elements in the tensor in place.
     * @return this tensor.
     */
    public Tensor exp_() {
        smile_tensor_exp_(handle);
        return this;
    }

    /**
     * Writes all values from the tensor src into this tensor at the indices
     * specified in the index tensor. For each value in src, its output index
     * is specified by its index in src for dimension != dim and by the
     * corresponding value in index for dimension = dim.
     * <p>
     * This is the reverse operation of the manner described in gather().
     *
     * @param dim the axis along which to index.
     * @param index the indices of elements to scatter, can be either empty or
     *             of the same dimensionality as src. When empty, the operation
     *             returns self unchanged.
     * @param source the source elements to scatter and reduce.
     * @param reduce the reduction operation to apply for non-unique indices
     *              ("sum", "prod", "mean", "amax", or "amin").
     * @return the output tensor.
     */
    public Tensor scatterReduce(int dim, Tensor index, Tensor source, String reduce) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment r = arena.allocateFrom(reduce);
            return new Tensor(Native.scatterReduce(handle, dim, index.handle, source.handle, r));
        }
    }

    /**
     * Writes all values from the tensor src into this tensor at the indices
     * specified in the index tensor. For each value in src, its output index
     * is specified by its index in src for dimension != dim and by the
     * corresponding value in index for dimension = dim.
     * <p>
     * This is the reverse operation of the manner described in gather().
     *
     * @param dim the axis along which to index.
     * @param index the indices of elements to scatter, can be either empty or
     *             of the same dimensionality as src. When empty, the operation
     *             returns self unchanged.
     * @param source the source elements to scatter and reduce.
     * @param reduce the reduction operation to apply for non-unique indices
     *              ("sum", "prod", "mean", "amax", or "amin").
     * @return this tensor.
     */
    public Tensor scatterReduce_(int dim, Tensor index, Tensor source, String reduce) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment r = arena.allocateFrom(reduce);
            Native.scatterReduceInplace(handle, dim, index.handle, source.handle, r);
            return this;
        }
    }

    /**
     * Gathers values along an axis specified by dim.
     *
     * @param dim the axis along which to index.
     * @param index the indices of elements to gather.
     * @return the output tensor.
     */
    public Tensor gather(int dim, Tensor index) {
        return new Tensor(smile_torch_gather(handle, dim, index.handle));
    }

    /**
     * Returns A + b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor add(float other) {
        return addScalar(fscalar(other));
    }

    /**
     * Returns A += b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor add_(float other) {
        return addScalarInplace(fscalar(other));
    }

    /**
     * Returns A + b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor add(double other) {
        return addScalar(fscalar(other));
    }

    /**
     * Returns A += b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor add_(double other) {
        return addScalarInplace(fscalar(other));
    }

    private Tensor addScalar(MemorySegment s) {
        try {
            return new Tensor(smile_tensor_add_s(handle, s));
        } finally {
            smile_scalar_free(s);
        }
    }

    private Tensor addScalarInplace(MemorySegment s) {
        try {
            smile_tensor_add_s_(handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns A + B.
     * @param other another tensor.
     * @return the output tensor.
     */
    public Tensor add(Tensor other) {
        return new Tensor(smile_tensor_add_t(handle, other.handle));
    }

    /**
     * Returns A += B.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor add_(Tensor other) {
        smile_tensor_add_t_(handle, other.handle);
        return this;
    }

    /**
     * Returns A + alpha * B.
     * @param other another tensor.
     * @param alpha the scaling factor.
     * @return the output tensor.
     */
    public Tensor add(Tensor other, double alpha) {
        MemorySegment s = fscalar(alpha);
        try {
            return new Tensor(smile_tensor_add_t_s(handle, other.handle, s));
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns A += alpha * B.
     * @param other another tensor.
     * @param alpha the scaling factor.
     * @return this tensor.
     */
    public Tensor add_(Tensor other, double alpha) {
        MemorySegment s = fscalar(alpha);
        try {
            smile_tensor_add_t_s_(handle, other.handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns A - b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor sub(float other) {
        return subScalar(fscalar(other));
    }

    /**
     * Returns A - b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor sub_(float other) {
        return subScalarInplace(fscalar(other));
    }

    /**
     * Returns A - b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor sub(double other) {
        return subScalar(fscalar(other));
    }

    /**
     * Returns A -= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor sub_(double other) {
        return subScalarInplace(fscalar(other));
    }

    private Tensor subScalar(MemorySegment s) {
        try {
            return new Tensor(smile_tensor_sub_s(handle, s));
        } finally {
            smile_scalar_free(s);
        }
    }

    private Tensor subScalarInplace(MemorySegment s) {
        try {
            smile_tensor_sub_s_(handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns A - B.
     * @param other another tensor.
     * @return the output tensor.
     */
    public Tensor sub(Tensor other) {
        return new Tensor(smile_tensor_sub_t(handle, other.handle));
    }

    /**
     * Returns A -= B.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor sub_(Tensor other) {
        smile_tensor_sub_t_(handle, other.handle);
        return this;
    }

    /**
     * Returns A - alpha * B.
     * @param other another tensor.
     * @param alpha the scaling factor.
     * @return the output tensor.
     */
    public Tensor sub(Tensor other, double alpha) {
        MemorySegment s = fscalar(alpha);
        try {
            return new Tensor(smile_tensor_sub_t_s(handle, other.handle, s));
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns A -= alpha * B.
     * @param other another tensor.
     * @param alpha the scaling factor.
     * @return this tensor.
     */
    public Tensor sub_(Tensor other, double alpha) {
        MemorySegment s = fscalar(alpha);
        try {
            smile_tensor_sub_t_s_(handle, other.handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns A * b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor mul(float other) {
        return mulScalar(fscalar(other));
    }

    /**
     * Returns A *= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor mul_(float other) {
        return mulScalarInplace(fscalar(other));
    }

    /**
     * Returns A * b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor mul(double other) {
        return mulScalar(fscalar(other));
    }

    /**
     * Returns A *= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor mul_(double other) {
        return mulScalarInplace(fscalar(other));
    }

    private Tensor mulScalar(MemorySegment s) {
        try {
            return new Tensor(smile_tensor_mul_s(handle, s));
        } finally {
            smile_scalar_free(s);
        }
    }

    private Tensor mulScalarInplace(MemorySegment s) {
        try {
            smile_tensor_mul_s_(handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns A * B element wisely.
     * @param other another tensor.
     * @return the output tensor.
     */
    public Tensor mul(Tensor other) {
        return new Tensor(smile_tensor_mul_t(handle, other.handle));
    }

    /**
     * Returns A *= B element wisely.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor mul_(Tensor other) {
        smile_tensor_mul_t_(handle, other.handle);
        return this;
    }

    /**
     * Returns A / b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor div(float other) {
        return divScalar(fscalar(other));
    }

    /**
     * Returns A /= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor div_(float other) {
        return divScalarInplace(fscalar(other));
    }

    /**
     * Returns A / b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor div(double other) {
        return divScalar(fscalar(other));
    }

    /**
     * Returns A /= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor div_(double other) {
        return divScalarInplace(fscalar(other));
    }

    private Tensor divScalar(MemorySegment s) {
        try {
            return new Tensor(smile_tensor_div_s(handle, s));
        } finally {
            smile_scalar_free(s);
        }
    }

    private Tensor divScalarInplace(MemorySegment s) {
        try {
            smile_tensor_div_s_(handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns A / B element wisely.
     * @param other another tensor.
     * @return the output tensor.
     */
    public Tensor div(Tensor other) {
        return new Tensor(smile_tensor_div_t(handle, other.handle));
    }

    /**
     * Returns A /= B element wisely.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor div_(Tensor other) {
        smile_tensor_div_t_(handle, other.handle);
        return this;
    }

    /**
     * Returns a new tensor with the power of the elements of input.
     * @param exponent the exponent value.
     * @return a new tensor with the power of the elements of input.
     */
    public Tensor pow(double exponent) {
        MemorySegment s = fscalar(exponent);
        try {
            return new Tensor(smile_tensor_pow_s(handle, s));
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Computes the power of the elements of input in place.
     * @param exponent the exponent value.
     * @return this tensor.
     */
    public Tensor pow_(double exponent) {
        MemorySegment s = fscalar(exponent);
        try {
            smile_tensor_pow_s_(handle, s);
            return this;
        } finally {
            smile_scalar_free(s);
        }
    }

    /**
     * Returns a new tensor with the cosine of the elements of input.
     * @return a new tensor with the cosine of the elements of input.
     */
    public Tensor cos() {
        return new Tensor(smile_tensor_cos(handle));
    }

    /**
     * Computes the cosine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor cos_() {
        smile_tensor_cos_(handle);
        return this;
    }

    /**
     * Returns a new tensor with the sine of the elements of input.
     * @return a new tensor with the sine of the elements of input.
     */
    public Tensor sin() {
        return new Tensor(smile_tensor_sin(handle));
    }

    /**
     * Computes the sine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor sin_() {
        smile_tensor_sin_(handle);
        return this;
    }

    /**
     * Returns a new tensor with the arccosine of the elements of input.
     * @return a new tensor with the arccosine of the elements of input.
     */
    public Tensor acos() {
        return new Tensor(smile_tensor_acos(handle));
    }

    /**
     * Computes the arccosine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor acos_() {
        smile_tensor_acos_(handle);
        return this;
    }

    /**
     * Returns a new tensor with the arcsine of the elements of input.
     * @return a new tensor with the arcsine of the elements of input.
     */
    public Tensor asin() {
        return new Tensor(smile_tensor_asin(handle));
    }

    /**
     * Computes the arcsine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor asin_() {
        smile_tensor_asin_(handle);
        return this;
    }

    /**
     * Tests if each element of this tensor is in other tensor. Returns a
     * boolean tensor of the same shape.
     * @param other another tensor.
     * @return a boolean tensor.
     */
    public Tensor isin(Tensor other) {
        return new Tensor(smile_torch_isin(handle, other.handle));
    }

    /**
     * Tests if all elements in the tensor are true.
     * @return the output tensor.
     */
    public boolean all() {
        MemorySegment result = check(smile_tensor_all(handle));
        try {
            return smile_tensor_item_bool(result) != 0;
        } finally {
            smile_tensor_free(result);
        }
    }

    /**
     * Returns logical NOT of this tensor.
     * @return a new tensor of logical not results.
     */
    public Tensor not() {
        return new Tensor(smile_tensor_logical_not(handle));
    }

    /**
     * Computes logical NOT of this tensor in place.
     * @return this tensor.
     */
    public Tensor not_() {
        smile_tensor_logical_not_(handle);
        return this;
    }

    /**
     * Returns logical AND of two boolean tensors.
     * @param other another tensor.
     * @return a new tensor of logical and results.
     */
    public Tensor and(Tensor other) {
        return new Tensor(smile_tensor_logical_and(handle, other.handle));
    }

    /**
     * Returns logical AND of two boolean tensors.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor and_(Tensor other) {
        smile_tensor_logical_and_(handle, other.handle);
        return this;
    }

    /**
     * Returns logical OR of two boolean tensors.
     * @param other another tensor.
     * @return a new tensor of logical and results.
     */
    public Tensor or(Tensor other) {
        return new Tensor(smile_tensor_logical_or(handle, other.handle));
    }

    /**
     * Returns logical OR of two boolean tensors.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor or_(Tensor other) {
        smile_tensor_logical_or_(handle, other.handle);
        return this;
    }

    /**
     * Rescales a tensor so that the elements lie in the range [0,1] and sum to 1.
     * @param dim the dimension along which softmax will be computed.
     * @return this tensor.
     */
    public Tensor softmax(int dim) {
        return new Tensor(smile_torch_softmax(handle, dim));
    }

    /**
     * Randomly zeroes some elements of the input tensor
     * with probability p.
     *
     * @param p the probability of an element to be zeroed.
     * @return a new tensor after random dropouts.
     */
    public Tensor dropout(double p) {
        return new Tensor(smile_torch_dropout(handle, p, 0));
    }

    /**
     * Randomly zeroes some elements in place
     * with probability p.
     *
     * @param p the probability of an element to be zeroed.
     * @return this tensor.
     */
    public Tensor dropout_(double p) {
        smile_tensor_free(check(smile_torch_dropout(handle, p, 1)));
        return this;
    }

    /**
     * Returns a tensor filled with all zeros. The returned Tensor has the
     * data type and device as this tensor.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public Tensor newZeros(long... shape) {
        if (shape.length == 0) shape = shape();
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_new_zeros(handle, longs(arena, shape), shape.length));
        }
    }

    /**
     * Returns a tensor filled with all ones. The returned Tensor has the
     * data type and device as this tensor.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public Tensor newOnes(long... shape) {
        if (shape.length == 0) shape = shape();
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_new_ones(handle, longs(arena, shape), shape.length));
        }
    }

    /**
     * Returns an identity matrix.
     * @param shape the dimension of the resulting matrix.
     * @return the created tensor.
     */
    public static Tensor eye(long shape) {
        if (defaultOptions != null) return eye(defaultOptions, shape);
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_eye(arena.allocateFrom(JAVA_LONG, shape), 1, MemorySegment.NULL));
        }
    }

    /**
     * Returns an identity matrix.
     * @param options Tensor creation options.
     * @param shape the dimension of the resulting matrix.
     * @return the created tensor.
     */
    public static Tensor eye(Options options, long shape) {
        MemorySegment opts = options.toNative();
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_eye(arena.allocateFrom(JAVA_LONG, shape), 1, opts));
        } finally {
            smile_tensor_options_free(opts);
        }
    }

    /**
     * Returns a tensor filled with the given value.
     * @param value the value to fill the output tensor with.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor full(long value, long... shape) {
        MemorySegment opts = defaultOptions == null ? MemorySegment.NULL : defaultOptions.toNative();
        MemorySegment s = iscalar(value);
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_full(longs(arena, shape), shape.length, s, opts));
        } finally {
            smile_scalar_free(s);
            if (opts.address() != 0) smile_tensor_options_free(opts);
        }
    }

    /**
     * Returns a tensor filled with the given value.
     * @param value the value to fill the output tensor with.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor full(double value, long... shape) {
        MemorySegment opts = defaultOptions == null ? MemorySegment.NULL : defaultOptions.toNative();
        MemorySegment s = fscalar(value);
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_full(longs(arena, shape), shape.length, s, opts));
        } finally {
            smile_scalar_free(s);
            if (opts.address() != 0) smile_tensor_options_free(opts);
        }
    }

    /**
     * Returns a tensor with uninitialized data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor empty(long... shape) {
        if (defaultOptions != null) return empty(defaultOptions, shape);
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_empty(longs(arena, shape), shape.length, MemorySegment.NULL));
        }
    }

    /**
     * Returns a tensor with uninitialized data.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor empty(Options options, long... shape) {
        return factory(Tensor::emptyNative, options, shape);
    }

    /**
     * Returns a tensor filled with all zeros.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor zeros(long... shape) {
        if (defaultOptions != null) return zeros(defaultOptions, shape);
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_zeros(longs(arena, shape), shape.length, MemorySegment.NULL));
        }
    }

    /**
     * Returns a tensor filled with all zeros.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor zeros(Options options, long... shape) {
        return factory(Tensor::zerosNative, options, shape);
    }

    /**
     * Returns a tensor filled with all ones.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor ones(long... shape) {
        if (defaultOptions != null) return ones(defaultOptions, shape);
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_ones(longs(arena, shape), shape.length, MemorySegment.NULL));
        }
    }

    /**
     * Returns a tensor filled with all ones.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor ones(Options options, long... shape) {
        return factory(Tensor::onesNative, options, shape);
    }

    /**
     * Returns a tensor filled with values drawn from a uniform distribution on [0, 1).
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor rand(long... shape) {
        if (defaultOptions != null) return rand(defaultOptions, shape);
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_rand(longs(arena, shape), shape.length, MemorySegment.NULL));
        }
    }

    /**
     * Returns a tensor filled with values drawn from a uniform distribution on [0, 1).
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor rand(Options options, long... shape) {
        return factory(Tensor::randNative, options, shape);
    }

    /**
     * Returns a tensor filled with values drawn from a unit normal distribution.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor randn(long... shape) {
        if (defaultOptions != null) return randn(defaultOptions, shape);
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_randn(longs(arena, shape), shape.length, MemorySegment.NULL));
        }
    }

    /**
     * Returns a tensor filled with values drawn from a unit normal distribution.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor randn(Options options, long... shape) {
        return factory(Tensor::randnNative, options, shape);
    }

    /** A native tensor factory of the form {@code f(shape, ndim, opts)}. */
    @FunctionalInterface
    private interface TensorFactory {
        MemorySegment apply(MemorySegment shape, int ndim, MemorySegment opts);
    }

    private static Tensor factory(TensorFactory f, Options options, long[] shape) {
        MemorySegment opts = options.toNative();
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(f.apply(longs(arena, shape), shape.length, opts));
        } finally {
            smile_tensor_options_free(opts);
        }
    }

    private static MemorySegment emptyNative(MemorySegment shape, int ndim, MemorySegment opts) {
        return smile_tensor_empty(shape, ndim, opts);
    }

    private static MemorySegment zerosNative(MemorySegment shape, int ndim, MemorySegment opts) {
        return smile_tensor_zeros(shape, ndim, opts);
    }

    private static MemorySegment onesNative(MemorySegment shape, int ndim, MemorySegment opts) {
        return smile_tensor_ones(shape, ndim, opts);
    }

    private static MemorySegment randNative(MemorySegment shape, int ndim, MemorySegment opts) {
        return smile_tensor_rand(shape, ndim, opts);
    }

    private static MemorySegment randnNative(MemorySegment shape, int ndim, MemorySegment opts) {
        return smile_tensor_randn(shape, ndim, opts);
    }

    /**
     * Returns a 1-D tensor of size (end - start) / step with values from the
     * interval [start, end) taken with common difference step beginning from
     * start.
     * @param start the starting value for the set of points.
     * @param end the ending value for the set of points.
     * @param step the gap between each pair of adjacent points.
     * @return a 1-D tensor.
     */
    public static Tensor arange(long start, long end, long step) {
        MemorySegment opts = smile_tensor_options_create();
        try {
            smile_tensor_options_dtype(opts, ScalarType.Int64.code);
            return new Tensor(smile_tensor_arange(start, end, step, opts));
        } finally {
            smile_tensor_options_free(opts);
        }
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(boolean[] data, long... shape) {
        if (shape.length == 0) shape = new long[] { data.length };
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment d = arena.allocate(JAVA_BYTE, data.length);
            for (int i = 0; i < data.length; i++) {
                d.set(JAVA_BYTE, i, (byte) (data[i] ? 1 : 0));
            }
            return new Tensor(smile_tensor_from_bool(d, arena.allocateFrom(JAVA_LONG, shape), shape.length));
        }
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(byte[] data, long... shape) {
        if (shape.length == 0) shape = new long[] { data.length };
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_from_byte(arena.allocateFrom(JAVA_BYTE, data),
                    arena.allocateFrom(JAVA_LONG, shape), shape.length));
        }
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(short[] data, long... shape) {
        if (shape.length == 0) shape = new long[] { data.length };
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_from_short(arena.allocateFrom(JAVA_SHORT, data),
                    arena.allocateFrom(JAVA_LONG, shape), shape.length));
        }
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(int[] data, long... shape) {
        if (shape.length == 0) shape = new long[] { data.length };
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_from_int(arena.allocateFrom(JAVA_INT, data),
                    arena.allocateFrom(JAVA_LONG, shape), shape.length));
        }
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(long[] data, long... shape) {
        if (shape.length == 0) shape = new long[] { data.length };
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_from_long(arena.allocateFrom(JAVA_LONG, data),
                    arena.allocateFrom(JAVA_LONG, shape), shape.length));
        }
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(float[] data, long... shape) {
        if (shape.length == 0) shape = new long[] { data.length };
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_from_float(arena.allocateFrom(JAVA_FLOAT, data),
                    arena.allocateFrom(JAVA_LONG, shape), shape.length));
        }
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(double[] data, long... shape) {
        if (shape.length == 0) shape = new long[] { data.length };
        try (Arena arena = Arena.ofConfined()) {
            return new Tensor(smile_tensor_from_double(arena.allocateFrom(JAVA_DOUBLE, data),
                    arena.allocateFrom(JAVA_LONG, shape), shape.length));
        }
    }

    // =========================================================================
    // Internal FFM helpers
    // =========================================================================

    /** Allocates a {@code int64_t[]} argument, or NULL for an empty array. */
    private static MemorySegment longs(Arena arena, long[] values) {
        return values.length == 0 ? MemorySegment.NULL : arena.allocateFrom(JAVA_LONG, values);
    }

    /** Creates an integer scalar handle that the caller must free. */
    private static MemorySegment iscalar(long value) {
        return check(smile_scalar_from_int(value));
    }

    /** Creates a floating-point scalar handle that the caller must free. */
    private static MemorySegment fscalar(double value) {
        return check(smile_scalar_from_float(value));
    }

    private static long[] toLong(int[] indices) {
        long[] result = new long[indices.length];
        for (int i = 0; i < indices.length; i++) {
            result[i] = indices[i];
        }
        return result;
    }

    /** Builds an index vector from integer indices; the caller must free it. */
    private static MemorySegment indexVec(long[] indices) {
        MemorySegment vec = smile_tensor_index_vec_create();
        for (long i : indices) {
            MemorySegment idx = smile_tensor_index_from_int(i);
            smile_tensor_index_vec_push(vec, idx);
            smile_tensor_index_free(idx);
        }
        return vec;
    }

    /** Builds an index vector from indices; the caller must free it. */
    private static MemorySegment indexVec(Index[] indices) {
        MemorySegment vec = smile_tensor_index_vec_create();
        for (Index i : indices) {
            smile_tensor_index_vec_push(vec, i.handle);
        }
        return vec;
    }

    /** Indexes this tensor with integer indices, returning an owned handle. */
    private MemorySegment indexed(long[] indices) {
        MemorySegment vec = indexVec(indices);
        try {
            return check(smile_tensor_index(handle, vec));
        } finally {
            smile_tensor_index_vec_free(vec);
        }
    }

    /** Builds a tensor vector from the given tensors; the caller must free it. */
    private static MemorySegment tensorVec(Tensor[] tensors) {
        MemorySegment vec = smile_tensor_vec_create();
        for (Tensor t : tensors) {
            smile_tensor_vec_push(vec, t.handle);
        }
        return vec;
    }

    /**
     * A class that encapsulates the construction axes of a tensor.
     * With construction axis we mean a particular property of a tensor
     * that can be configured before its construction (and sometimes
     * changed afterward).
     */
    public static class Options {
        /** The data type of the elements, or null for the default. */
        private ScalarType dtype;
        /** The compute device, or null for the default. */
        private Device device;
        /** The memory layout, or null for the default. */
        private Layout layout;
        /** Whether gradients are required, or null for the default. */
        private Boolean requireGrad;

        /** Constructor with default values for every axis. */
        public Options() {
        }

        /**
         * Sets the data type of the elements stored in the tensor.
         * @param type the data type.
         * @return this options object.
         */
        public Options dtype(ScalarType type) {
            this.dtype = type;
            return this;
        }

        /**
         * Sets a compute device on which a tensor is stored.
         * @param device a compute device.
         * @return this options object.
         */
        public Options device(Device device) {
            this.device = device;
            return this;
        }

        /**
         * Sets strided (dense) or sparse tensor.
         * @param layout the tensor layout.
         * @return this options object.
         */
        public Options layout(Layout layout) {
            this.layout = layout;
            return this;
        }

        /**
         * Set true if gradients need to be computed for this tensor.
         * @param required the flag indicating if gradients need to be
         *                computed for this tensor.
         * @return this options object.
         */
        public Options requireGradients(boolean required) {
            this.requireGrad = required;
            return this;
        }

        /**
         * Materializes a native {@code ST_TensorOptions} handle for these
         * options. The caller must free it with {@code smile_tensor_options_free}.
         * @return a new {@code ST_TensorOptions} handle.
         */
        MemorySegment toNative() {
            MemorySegment opts = check(smile_tensor_options_create());
            if (dtype != null) smile_tensor_options_dtype(opts, dtype.code);
            if (layout != null) smile_tensor_options_layout(opts, layout.code);
            if (requireGrad != null) smile_tensor_options_requires_grad(opts, requireGrad ? 1 : 0);
            if (device != null) {
                MemorySegment dev = device.toNative();
                try {
                    smile_tensor_options_device(opts, dev);
                } finally {
                    smile_device_free(dev);
                }
            }
            return opts;
        }
    }
}
