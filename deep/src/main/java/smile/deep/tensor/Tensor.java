/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep.tensor;

import java.util.Arrays;
import java.util.Stack;
import org.bytedeco.cuda.cudart.cudaDeviceProp;
import org.bytedeco.cuda.global.cudart;
import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.global.torch;
import org.bytedeco.pytorch.global.torch_cuda;
import smile.util.AutoScope;
import smile.util.Tuple2;

/**
 * A Tensor is a multidimensional array containing elements of a single data type.
 *
 * @author Haifeng Li
 */
public class Tensor implements AutoCloseable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Tensor.class);
    /** A scope controls the lifecycle of tensors, providing timely deallocation. */
    private static final Stack<AutoScope> scopes = new Stack<>();
    /** Default options such as device and dtype. */
    private static Options defaultOptions;
    /** PyTorch Tensor handle. */
    final org.bytedeco.pytorch.Tensor value;

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
        try {
            var device = torch_cuda.current_device();
            var prop = new cudaDeviceProp();
            var code = cudart.cudaGetDeviceProperties(prop, device);
            if (code != cudart.CUDA_SUCCESS) return false;

            // The version is returned as (1000 major + 10 minor).
            int[] version = new int[1];
            code = cudart.cudaRuntimeGetVersion(version);
            if (code != cudart.CUDA_SUCCESS) return false;

            return version[0] >= 11000 && prop.major() >= 8;
        } catch (Throwable ex) {
            // UnsatisfiedLinkError
            logger.info("Failed to get device properties: {}", ex.getMessage());
            return false;
        }
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
        scopes.push(scope);
    }

    /**
     * Removes the scope at the top of the tensor stack. All tensors
     * added to this scope will be released.
     * @return the top level scope.
     */
    public static AutoScope pop() {
        var scope = scopes.pop();
        scope.close();
        return scope;
    }

    /**
     * Constructor.
     * @param tensor PyTorch Tensor object.
     */
    public Tensor(org.bytedeco.pytorch.Tensor tensor) {
        this.value = tensor;
        if (!scopes.isEmpty()) {
            scopes.peek().add(this);
        }
    }

    /** Prints the tensor on the standard output. */
    public void print() {
        torch.print(value);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        if (other instanceof Tensor t) {
            return value == t.value;
        }
        return false;
    }

    @Override
    public void close() {
        if (!value.isNull()) {
            value.close();
        }
    }

    @Override
    public String toString() {
        return String.format("%s%s", value, Arrays.toString(value.shape()));
    }

    /**
     * Returns the PyTorch tensor object.
     * @return the PyTorch tensor object.
     */
    public org.bytedeco.pytorch.Tensor asTorch() {
        return this.value;
    }

    /**
     * Sets if autograd should record operations on this tensor.
     * @param required the flag indicating if autograd should record
     *                operations on this tensor.
     * @return this tensor.
     */
    public Tensor setRequireGrad(boolean required) {
        value.set_requires_grad(required);
        return this;
    }

    /**
     * Returns true if autograd should record operations on this tensor.
     * @return true if autograd should record operations on this tensor.
     */
    public boolean getRequireGrad() {
        return value.requires_grad();
    }

    /**
     * Returns a new tensor, detached from the current graph.
     * The result will never require gradient.
     *
     * @return a new tensor that doesn't require gradient.
     */
    public Tensor detach() {
        return new Tensor(value.detach());
    }

    /**
     * Clone the tensor with a different data type.
     * @param dtype the element data type of new tensor.
     * @return The cloned tensor.
     */
    public Tensor to(ScalarType dtype) {
        return new Tensor(value.to(dtype.value));
    }

    /**
     * Clone the tensor to a device.
     * @param device the compute device of new tensor.
     * @return The cloned tensor.
     */
    public Tensor to(Device device) {
        return new Tensor(value.to(device.value, value.dtype()));
    }

    /**
     * Clone the tensor to a device with a different data type.
     * @param device the compute device of new tensor.
     * @param dtype the element data type of new tensor.
     * @return The cloned tensor.
     */
    public Tensor to(Device device, ScalarType dtype) {
        return new Tensor(value.to(device.value, dtype.value));
    }

    /**
     * Returns the element data type.
     * @return the element data type.
     */
    public ScalarType dtype() {
        byte typeValue = value.dtype().toScalarType().value;
        for (ScalarType dtype : ScalarType.values()) {
            if (dtype.value.value == typeValue) {
                return dtype;
            }
        }
        return null;
    }

    /**
     * Returns the device on which the tensor is.
     * @return the device.
     */
    public Device device() {
        return new Device(value.device());
    }

    /**
     * Returns the number of dimensions of tensor.
     * @return the number of dimensions of tensor
     */
    public int dim() {
        return (int) value.dim();
    }

    /**
     * Returns the shape of the tensor.
     * @return the shape of the tensor.
     */
    public long[] shape() {
        return value.shape();
    }

    /**
     * Returns the size of given dimension.
     * @param dim dimension index.
     * @return the size of given dimension.
     */
    public long size(int dim) {
        return value.size(dim);
    }

    /**
     * Returns the number of tensor elements.
     * @return the number of tensor elements.
     */
    public long length() {
        long length = 1;
        for (var size : value.shape()) {
            length *= size;
        }
        return length;
    }

    /**
     * Returns a new tensor with the negative of the elements of input.
     * @return the output tensor.
     */
    public Tensor neg() {
        return new Tensor(value.neg());
    }

    /**
     * Returns the tensor with the negative of the elements of input.
     * @return this tensor.
     */
    public Tensor neg_() {
        value.neg_();
        return this;
    }

    /**
     * Returns a contiguous in memory tensor containing the same data as this tensor.
     * @return a contiguous in memory tensor containing the same data as this tensor.
     */
    public Tensor contiguous() {
        return new Tensor(value.contiguous());
    }

    /**
     * Returns a new view of this tensor with singleton dimensions
     * expanded to a larger size.
     *
     * @param size the desired expanded size.
     * @return the tensor view with the expanded size.
     */
    public Tensor expand(long... size) {
        return new Tensor(value.expand(size));
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
        return new Tensor(value.reshape(shape));
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
        return new Tensor(value.flatten(startDim, -1));
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
        return new Tensor(value.flatten(startDim, endDim));
    }

    /** Computes the gradients. */
    public void backward() {
        value.backward();
    }

    /**
     * Fills this tensor with the specified value.
     * @param x the value.
     * @return this tensor.
     */
    public Tensor fill_(int x) {
        value.fill_(new Scalar(x));
        return this;
    }

    /**
     * Fills this tensor with the specified value.
     * @param x the value.
     * @return this tensor.
     */
    public Tensor fill_(double x) {
        value.fill_(new Scalar(x));
        return this;
    }

    /**
     * Draws binary random numbers (0 or 1) from a Bernoulli distribution.
     * @param p Bernoulli probability.
     * @return this tensor.
     */
    public Tensor bernoulli_(double p) {
        value.bernoulli_(p, null);
        return this;
    }

    /**
     * Returns a view of the original tensor input with its dimensions permuted.
     * @param dims The desired ordering of dimensions.
     * @return the permuted tensor.
     */
    public Tensor permute(long... dims) {
        return new Tensor(value.permute(dims));
    }

    /**
     * Returns a tensor index vector.
     * @param indices the indices along the dimensions.
     * @return the index vector.
     */
    private TensorIndexVector indexVector(int... indices) {
        TensorIndexVector vector = new TensorIndexVector();
        for (var index : indices) {
            vector.push_back(new TensorIndex(index));
        }
        return vector;
    }

    /**
     * Returns a tensor index vector.
     * @param indices the indices along the dimensions.
     * @return the index vector.
     */
    private TensorIndexVector indexVector(long... indices) {
        TensorIndexVector vector = new TensorIndexVector();
        for (var index : indices) {
            vector.push_back(new TensorIndex(index));
        }
        return vector;
    }

    /**
     * Returns a tensor index vector.
     * @param indices the indices along the dimensions.
     * @return the index vector.
     */
    private TensorIndexVector indexVector(Tensor... indices) {
        TensorIndexVector vector = new TensorIndexVector();
        for (var index : indices) {
            vector.push_back(new TensorIndex(index.value));
        }
        return vector;
    }

    /**
     * Returns a tensor index vector.
     * @param indices the indices along the dimensions.
     * @return the index vector.
     */
    private TensorIndexVector indexVector(Index... indices) {
        TensorIndexVector vector = new TensorIndexVector();
        for (var index : indices) {
            vector.push_back(index.value);
        }
        return vector;
    }

    /**
     * Returns a tensor index vector.
     * @param indices the indices along the dimensions.
     * @return the index vector.
     */
    private TensorOptionalList indexList(Index... indices) {
        TensorOptionalList list = new TensorOptionalList();
        for (Index index : indices) {
            list.push_back(new TensorOptional(index.value));
        }
        return list;
    }

    /**
     * Updates a portion of tensor.
     * @param source the new sub-tensor values.
     * @param indices the indices along the dimensions.
     * @return the output tensor.
     */
    public Tensor put(Tensor source, Index... indices) {
        return new Tensor(value.index_put(indexList(indices), source.value));
    }

    /**
     * Updates a portion of tensor.
     * @param source the new sub-tensor value.
     * @param index the sub-tensor index.
     * @return the output tensor.
     */
    public Tensor put(Tensor source, Tensor index) {
        return new Tensor(value.put(index.value, source.value));
    }

    /**
     * Updates a portion of tensor in place.
     * @param source the new sub-tensor values.
     * @param indices the indices along the dimensions.
     * @return this tensor.
     */
    public Tensor put_(Tensor source, Index... indices) {
        value.index_put_(indexVector(indices), source.value);
        return this;
    }

    /**
     * Updates a portion of tensor in place.
     * @param source the new sub-tensor value.
     * @param index the sub-tensor index.
     * @return this tensor.
     */
    public Tensor put_(Tensor source, Tensor index) {
        value.put_(index.value, source.value);
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(byte x, int... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(byte x, long... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(short x, int... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(short x, long... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(int x, int... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(int x, long... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(long x, int... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(long x, long... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(float x, int... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(float x, long... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(double x, int... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param x the new element value.
     * @param indices the element indices.
     * @return this tensor.
     */
    public Tensor put_(double x, long... indices) {
        value.index_put_(indexVector(indices), new Scalar((x)));
        return this;
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param indices the indices along the dimensions.
     * @return the sub-tensor.
     */
    public Tensor get(int... indices) {
        return new Tensor(value.index(indexVector(indices)));
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param indices the indices along the dimensions.
     * @return the sub-tensor.
     */
    public Tensor get(long... indices) {
        return new Tensor(value.index(indexVector(indices)));
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param indices the indices along the dimensions.
     * @return the sub-tensor.
     */
    public Tensor get(Index... indices) {
        return new Tensor(value.index(indexVector(indices)));
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param index the tensor index.
     * @return the sub-tensor.
     */
    public Tensor get(Tensor index) {
        TensorIndexVector indexVector = new TensorIndexVector(new TensorIndex(index.value));
        return new Tensor(value.index(indexVector));
    }

    /**
     * Returns the byte value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public byte getByte(int... indices) {
        return value.index(indexVector(indices)).item_byte();
    }

    /**
     * Returns the byte value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public byte getByte(long... indices) {
        return value.index(indexVector(indices)).item_byte();
    }

    /**
     * Returns the short value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public short getShort(int... indices) {
        return value.index(indexVector(indices)).item_short();
    }

    /**
     * Returns the short value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public short getShort(long... indices) {
        return value.index(indexVector(indices)).item_short();
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public int getInt(int... indices) {
        return value.index(indexVector(indices)).item_int();
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public int getInt(long... indices) {
        return value.index(indexVector(indices)).item_int();
    }

    /**
     * Returns the long value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public long getLong(int... indices) {
        return value.index(indexVector(indices)).item_long();
    }

    /**
     * Returns the long value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public long getLong(long... indices) {
        return value.index(indexVector(indices)).item_long();
    }

    /**
     * Returns the float value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public float getFloat(int... indices) {
        return value.index(indexVector(indices)).item_float();
    }

    /**
     * Returns the float value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public float getFloat(long... indices) {
        return value.index(indexVector(indices)).item_float();
    }

    /**
     * Returns the double value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public double getDouble(int... indices) {
        return value.index(indexVector(indices)).item_double();
    }

    /**
     * Returns the double value of element at given index.
     *
     * @param indices the indices along the dimensions.
     * @return the element value.
     */
    public double getDouble(long... indices) {
        return value.index(indexVector(indices)).item_double();
    }

    /**
     * Returns the boolean value when the tensor holds a single value.
     * @return the boolean value when the tensor holds a single value.
     */
    public boolean boolValue() {
        return value.item_bool();
    }

    /**
     * Returns the byte value when the tensor holds a single value.
     * @return the byte value when the tensor holds a single value.
     */
    public byte byteValue() {
        return value.item_byte();
    }

    /**
     * Returns the short value when the tensor holds a single value.
     * @return the short value when the tensor holds a single value.
     */
    public short shortValue() {
        return value.item_short();
    }

    /**
     * Returns the int value when the tensor holds a single value.
     * @return the int value when the tensor holds a single value.
     */
    public int intValue() {
        return value.item_int();
    }

    /**
     * Returns the long value when the tensor holds a single value.
     * @return the long value when the tensor holds a single value.
     */
    public long longValue() {
        return value.item_long();
    }

    /**
     * Returns the float value when the tensor holds a single value.
     * @return the float value when the tensor holds a single value.
     */
    public float floatValue() {
        return value.item_float();
    }

    /**
     * Returns the double value when the tensor holds a single value.
     * @return the double value when the tensor holds a single value.
     */
    public double doubleValue() {
        return value.item_double();
    }

    /**
     * Returns the byte array of tensor elements
     * @return the byte array of tensor elements.
     */
    public byte[] byteArray() {
        if (value.is_view()) {
            throw new UnsupportedOperationException("copy tensor view to array");
        }
        var array = new byte[(int) length()];
        var data = value.data_ptr_byte();
        data.get(array);
        return array;
    }

    /**
     * Returns the short integer array of tensor elements
     * @return the short integer array of tensor elements.
     */
    public short[] shortArray() {
        if (value.is_view()) {
            throw new UnsupportedOperationException("copy tensor view to array");
        }
        var array = new short[(int) length()];
        var data = value.data_ptr_short();
        data.get(array);
        return array;
    }

    /**
     * Returns the integer array of tensor elements
     * @return the integer array of tensor elements.
     */
    public int[] intArray() {
        if (value.is_view()) {
            throw new UnsupportedOperationException("copy tensor view to array");
        }
        var array = new int[(int) length()];
        var data = value.data_ptr_int();
        data.get(array);
        return array;
    }

    /**
     * Returns the long integer array of tensor elements
     * @return the long integer array of tensor elements.
     */
    public long[] longArray() {
        if (value.is_view()) {
            throw new UnsupportedOperationException("copy tensor view to array");
        }
        var array = new long[(int) length()];
        var data = value.data_ptr_long();
        data.get(array);
        return array;
    }

    /**
     * Returns the float array of tensor elements
     * @return the float array of tensor elements.
     */
    public float[] floatArray() {
        if (value.is_view()) {
            throw new UnsupportedOperationException("copy tensor view to array");
        }
        var array = new float[(int) length()];
        var data = value.data_ptr_float();
        data.get(array);
        return array;
    }

    /**
     * Returns the double array of tensor elements
     * @return the double array of tensor elements.
     */
    public double[] doubleArray() {
        if (value.is_view()) {
            throw new UnsupportedOperationException("copy tensor view to array");
        }
        var array = new double[(int) length()];
        var data = value.data_ptr_double();
        data.get(array);
        return array;
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
        return new Tensor(value.unsqueeze(dim));
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
        return new Tensor(value.transpose(dim0, dim1));
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
        return new Tensor(value.triu(diagonal));
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
        value.triu_(diagonal);
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
    public Tensor view(long...shape) {
        return new Tensor(value.view(shape));
    }

    /**
     * Returns a view of tensor as a complex tensor.
     * @return the complex tensor view.
     */
    public Tensor viewAsComplex() {
        return new Tensor(torch.view_as_complex(value));
    }

    /**
     * Returns a view of tensor as a real tensor.
     * @return the real tensor view.
     */
    public Tensor viewAsReal() {
        return new Tensor(torch.view_as_real(value));
    }

    /**
     * Returns the indices of the maximum value of a tensor across a dimension.
     *
     * @param dim the dimension to reduce.
     * @param keepDim whether the output tensor has dim retained or not.
     * @return the indices of the maximum value of a tensor across a dimension.
     */
    public Tensor argmax(int dim, boolean keepDim) {
        return new Tensor(value.argmax(new LongOptional(dim), keepDim));
    }

    /**
     * Returns the k largest elements.
     *
     * @param k the number of largest elements.
     * @return the values and indices of the largest k elements.
     */
    public Tuple2<Tensor, Tensor> topk(int k) {
        var topk = value.topk(k);
        return new Tuple2<>(new Tensor(topk.get0()), new Tensor(topk.get1()));
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
        var topk = value.topk(k, dim, largest, sorted);
        return new Tuple2<>(new Tensor(topk.get0()), new Tensor(topk.get1()));
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
            var sort = torch.sort(value, -1, true);
            var probsSort = scope.add(sort.get0());
            var probsIdx = scope.add(sort.get1());
            var probsSum = scope.add(torch.cumsum(probsSort, -1));

            var mask = scope.add(probsSum.sub_(probsSort).gt(new Scalar(p)));
            TensorIndexVector indexVector = new TensorIndexVector();
            indexVector.push_back(new TensorIndex(mask));
            probsSort.index_put_(indexVector, new Scalar(0.0f));

            var sum = scope.add(probsSort.sum(new long[]{-1}, true, new ScalarTypeOptional()));
            probsSort.div_(sum);
            var sample = scope.add(torch.multinomial(probsSort, 1));
            sample = torch.gather(probsIdx, -1, sample);
            return new Tensor(sample);
        }
    }

    /**
     * Stacks tensors in sequence horizontally (column wise).
     * @param tensors the tensors to concatenate.
     * @return the output tensor.
     */
    public static Tensor hstack(Tensor... tensors) {
        var vector = new TensorVector();
        for (var tensor : tensors) {
            vector.push_back(tensor.value);
        }
        return new Tensor(torch.hstack(vector));
    }

    /**
     * Stacks tensors in sequence vertically (row wise).
     * @param tensors the tensors to concatenate.
     * @return the output tensor.
     */
    public static Tensor vstack(Tensor... tensors) {
        var vector = new TensorVector();
        for (var tensor : tensors) {
            vector.push_back(tensor.value);
        }
        return new Tensor(torch.vstack(vector));
    }

    /**
     * Returns a complex tensor whose elements are Cartesian coordinates
     * corresponding to the polar coordinates with abs and angle.
     * @param abs The absolute value the complex tensor. Must be float or double.
     * @param angle The angle of the complex tensor. Must be same dtype as abs.
     * @return the complex tensor.
     */
    public static Tensor polar(Tensor abs, Tensor angle) {
        return new Tensor(torch.polar(abs.value, angle.value));
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
        var kind = switch (reduction) {
            case "none", "mean" -> new kMean();
            case "sum" -> new kSum();
            default -> throw new IllegalArgumentException("Invalid reduction: " + reduction);
        };
        var options = new CrossEntropyLossOptions();
        options.ignore_index().put(ignoreIndex);
        options.reduction().put(kind);
        return new Tensor(torch.cross_entropy(input.value, target.value, options));
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
        return new Tensor(torch.where(condition.value, input.value, other.value));
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
        return new Tensor(torch.where(condition.value, new Scalar(input), new Scalar(other)));
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
        return new Tensor(torch.where(condition.value, new Scalar(input), new Scalar(other)));
    }

    /**
     * Returns the matrix product of two tensors.
     * @param other another tensor.
     * @return the matrix product of two tensors.
     */
    public Tensor matmul(Tensor other) {
        return new Tensor(value.matmul(other.value));
    }

    /**
     * Returns the outer product of two tensors.
     * @param other another tensor.
     * @return the outer product of two tensors.
     */
    public Tensor outer(Tensor other) {
        return new Tensor(value.outer(other.value));
    }

    /**
     * Computes element-wise equality.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor eq(int other) {
        return new Tensor(value.eq(new Scalar(other)));
    }

    /**
     * Computes element-wise equality.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor eq(double other) {
        return new Tensor(value.eq(new Scalar(other)));
    }

    /**
     * Computes element-wise equality.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor eq(Tensor other) {
        return new Tensor(value.eq(other.value));
    }

    /**
     * Computes element-wise inequality.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor ne(int other) {
        return new Tensor(value.ne(new Scalar(other)));
    }

    /**
     * Computes element-wise inequality.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor ne(double other) {
        return new Tensor(value.ne(new Scalar(other)));
    }

    /**
     * Computes element-wise inequality.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor ne(Tensor other) {
        return new Tensor(value.ne(other.value));
    }

    /**
     * Computes element-wise less-than comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor lt(double other) {
        return new Tensor(value.lt(new Scalar(other)));
    }

    /**
     * Computes element-wise less-than comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor lt(int other) {
        return new Tensor(value.lt(new Scalar(other)));
    }

    /**
     * Computes element-wise less-than comparison.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor lt(Tensor other) {
        return new Tensor(value.lt(other.value));
    }

    /**
     * Computes element-wise less-than-or-equal-to comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor le(int other) {
        return new Tensor(value.le(new Scalar(other)));
    }

    /**
     * Computes element-wise less-than-or-equal-to comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor le(double other) {
        return new Tensor(value.le(new Scalar(other)));
    }

    /**
     * Computes element-wise less-than-or-equal-to comparison.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor le(Tensor other) {
        return new Tensor(value.le(other.value));
    }

    /**
     * Computes element-wise greater-than comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor gt(int other) {
        return new Tensor(value.gt(new Scalar(other)));
    }

    /**
     * Computes element-wise greater-than comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor gt(double other) {
        return new Tensor(value.gt(new Scalar(other)));
    }

    /**
     * Computes element-wise greater-than comparison.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor gt(Tensor other) {
        return new Tensor(value.gt(other.value));
    }

    /**
     * Computes element-wise greater-than-or-equal-to comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor ge(int other) {
        return new Tensor(value.ge(new Scalar(other)));
    }

    /**
     * Computes element-wise greater-than-or-equal-to comparison.
     * @param other the scalar to compare.
     * @return the output tensor.
     */
    public Tensor ge(double other) {
        return new Tensor(value.ge(new Scalar(other)));
    }

    /**
     * Computes element-wise greater-than-or-equal-to comparison.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor ge(Tensor other) {
        return new Tensor(value.ge(other.value));
    }

    /**
     * Returns the sum of all elements in the tensor.
     * @return the sum of all elements.
     */
    public Tensor sum() {
        return new Tensor(value.sum());
    }

    /**
     * Returns the mean of all elements in the tensor.
     * @return the mean of all elements.
     */
    public Tensor mean() {
        return new Tensor(value.mean());
    }

    /**
     * Returns the mean along a dimension in the tensor.
     * @param dim the dimension to reduce.
     * @param keepDim whether the output tensor has dim retained or not.
     * @return the output tensor.
     */
    public Tensor mean(int dim, boolean keepDim) {
        return new Tensor(value.mean(new long[]{dim}, keepDim, new ScalarTypeOptional(value.dtype())));
    }

    /**
     * Returns the reciprocal of the square-root of each of the elements in the tensor.
     * @return the output tensor.
     */
    public Tensor rsqrt() {
        return new Tensor(value.rsqrt());
    }

    /**
     * Returns the reciprocal of the square-root of each of the elements in the tensor.
     * @return this tensor.
     */
    public Tensor rsqrt_() {
        value.rsqrt_();
        return this;
    }

    /**
     * Returns the exponential of elements in the tensor.
     * @return the output tensor.
     */
    public Tensor exp() {
        return new Tensor(value.exp());
    }

    /**
     * Returns the exponential of elements in the tensor in place.
     * @return this tensor.
     */
    public Tensor exp_() {
        return new Tensor(value.exp_());
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
        return new Tensor(value.scatter_reduce(dim, index.value, source.value, reduce));
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
        value.scatter_reduce_(dim, index.value, source.value, reduce);
        return this;
    }

    /**
     * Gathers values along an axis specified by dim.
     *
     * @param dim the axis along which to index.
     * @param index the indices of elements to gather.
     * @return the output tensor.
     */
    public Tensor gather(int dim, Tensor index) {
        return new Tensor(value.gather(dim, index.value));
    }

    /**
     * Returns A + b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor add(float other) {
        return new Tensor(value.add(new Scalar(other)));
    }

    /**
     * Returns A += b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor add_(float other) {
        value.add_(new Scalar(other));
        return this;
    }

    /**
     * Returns A + b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor add(double other) {
        return new Tensor(value.add(new Scalar(other)));
    }

    /**
     * Returns A += b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor add_(double other) {
        value.add_(new Scalar(other));
        return this;
    }

    /**
     * Returns A + B.
     * @param other another tensor.
     * @return the output tensor.
     */
    public Tensor add(Tensor other) {
        return new Tensor(value.add(other.value));
    }

    /**
     * Returns A += B.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor add_(Tensor other) {
        value.add_(other.value);
        return this;
    }

    /**
     * Returns A + alpha * B.
     * @param other another tensor.
     * @param alpha the scaling factor.
     * @return the output tensor.
     */
    public Tensor add(Tensor other, double alpha) {
        return new Tensor(value.add(other.value, new Scalar(alpha)));
    }

    /**
     * Returns A += alpha * B.
     * @param other another tensor.
     * @param alpha the scaling factor.
     * @return this tensor.
     */
    public Tensor add_(Tensor other, double alpha) {
        value.add_(other.value, new Scalar(alpha));
        return this;
    }

    /**
     * Returns A - b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor sub(float other) {
        return new Tensor(value.sub(new Scalar(other)));
    }

    /**
     * Returns A - b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor sub_(float other) {
        return new Tensor(value.sub(new Scalar(other)));
    }

    /**
     * Returns A -= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor sub(double other) {
        value.sub_(new Scalar(other));
        return this;
    }

    /**
     * Returns A -= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor sub_(double other) {
        value.sub_(new Scalar(other));
        return this;
    }

    /**
     * Returns A - B.
     * @param other another tensor.
     * @return the output tensor.
     */
    public Tensor sub(Tensor other) {
        return new Tensor(value.sub(other.value));
    }

    /**
     * Returns A -= B.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor sub_(Tensor other) {
        value.sub_(other.value);
        return this;
    }

    /**
     * Returns A - alpha * B.
     * @param other another tensor.
     * @param alpha the scaling factor.
     * @return the output tensor.
     */
    public Tensor sub(Tensor other, double alpha) {
        return new Tensor(value.sub(other.value, new Scalar(alpha)));
    }

    /**
     * Returns A -= alpha * B.
     * @param other another tensor.
     * @param alpha the scaling factor.
     * @return this tensor.
     */
    public Tensor sub_(Tensor other, double alpha) {
        value.sub_(other.value, new Scalar(alpha));
        return this;
    }

    /**
     * Returns A * b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor mul(float other) {
        return new Tensor(value.mul(new Scalar(other)));
    }

    /**
     * Returns A *= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor mul_(float other) {
        value.mul_(new Scalar(other));
        return this;
    }

    /**
     * Returns A * b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor mul(double other) {
        return new Tensor(value.mul(new Scalar(other)));
    }

    /**
     * Returns A *= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor mul_(double other) {
        value.mul_(new Scalar(other));
        return this;
    }

    /**
     * Returns A * B element wisely.
     * @param other another tensor.
     * @return the output tensor.
     */
    public Tensor mul(Tensor other) {
        return new Tensor(value.mul(other.value));
    }

    /**
     * Returns A *= B element wisely.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor mul_(Tensor other) {
        value.mul_(other.value);
        return this;
    }

    /**
     * Returns A / b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor div(float other) {
        return new Tensor(value.div(new Scalar(other)));
    }

    /**
     * Returns A /= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor div_(float other) {
        value.div_(new Scalar(other));
        return this;
    }

    /**
     * Returns A / b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor div(double other) {
        return new Tensor(value.div(new Scalar(other)));
    }

    /**
     * Returns A /= b.
     * @param other a scalar value.
     * @return this tensor.
     */
    public Tensor div_(double other) {
        value.div_(new Scalar(other));
        return this;
    }

    /**
     * Returns A / B element wisely.
     * @param other another tensor.
     * @return the output tensor.
     */
    public Tensor div(Tensor other) {
        return new Tensor(value.div(other.value));
    }

    /**
     * Returns A /= B element wisely.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor div_(Tensor other) {
        value.div_(other.value);
        return this;
    }

    /**
     * Returns a new tensor with the power of the elements of input.
     * @param exponent the exponent value.
     * @return a new tensor with the power of the elements of input.
     */
    public Tensor pow(double exponent) {
        return new Tensor(value.pow(new Scalar(exponent)));
    }

    /**
     * Computes the power of the elements of input in place.
     * @param exponent the exponent value.
     * @return this tensor.
     */
    public Tensor pow_(double exponent) {
        value.pow_(new Scalar(exponent));
        return this;
    }

    /**
     * Returns a new tensor with the cosine of the elements of input.
     * @return a new tensor with the cosine of the elements of input.
     */
    public Tensor cos() {
        return new Tensor(value.cos());
    }

    /**
     * Computes the cosine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor cos_() {
        value.cos_();
        return this;
    }

    /**
     * Returns a new tensor with the sine of the elements of input.
     * @return a new tensor with the sine of the elements of input.
     */
    public Tensor sin() {
        return new Tensor(value.sin());
    }

    /**
     * Computes the sine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor sin_() {
        value.sin_();
        return this;
    }

    /**
     * Returns a new tensor with the arccosine of the elements of input.
     * @return a new tensor with the arccosine of the elements of input.
     */
    public Tensor acos() {
        return new Tensor(value.acos());
    }

    /**
     * Computes the arccosine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor acos_() {
        value.acos_();
        return this;
    }

    /**
     * Returns a new tensor with the arcsine of the elements of input.
     * @return a new tensor with the arcsine of the elements of input.
     */
    public Tensor asin() {
        return new Tensor(value.asin());
    }

    /**
     * Computes the arcsine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor asin_() {
        value.asin_();
        return this;
    }

    /**
     * Tests if each element of this tensor is in other tensor. Returns a
     * boolean tensor of the same shape.
     * @param other another tensor.
     * @return a boolean tensor.
     */
    public Tensor isin(Tensor other) {
        return new Tensor(torch.isin(value, other.value));
    }

    /**
     * Tests if all elements in the tensor are true.
     * @return the output tensor.
     */
    public boolean all() {
        return value.all().item_bool();
    }

    /**
     * Returns logical NOT of this tensor.
     * @return a new tensor of logical not results.
     */
    public Tensor not() {
        return new Tensor(value.logical_not());
    }

    /**
     * Returns logical NOT of this tensor.
     * @return a new tensor of logical not results.
     */
    public Tensor not_() {
        return new Tensor(value.logical_not_());
    }

    /**
     * Returns logical AND of two boolean tensors.
     * @param other another tensor.
     * @return a new tensor of logical and results.
     */
    public Tensor and(Tensor other) {
        return new Tensor(value.logical_and(other.value));
    }

    /**
     * Returns logical AND of two boolean tensors.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor and_(Tensor other) {
        value.logical_and_(other.value);
        return this;
    }

    /**
     * Returns logical OR of two boolean tensors.
     * @param other another tensor.
     * @return a new tensor of logical and results.
     */
    public Tensor or(Tensor other) {
        return new Tensor(value.logical_or(other.value));
    }

    /**
     * Returns logical OR of two boolean tensors.
     * @param other another tensor.
     * @return this tensor.
     */
    public Tensor or_(Tensor other) {
        value.logical_or_(other.value);
        return this;
    }

    /**
     * Rescales a tensor so that the elements lie in the range [0,1] and sum to 1.
     * @param dim the dimension along which softmax will be computed.
     * @return this tensor.
     */
    public Tensor softmax(int dim) {
        return new Tensor(torch.softmax(value, dim));
    }

    /**
     * Randomly zeroes some elements of the input tensor
     * with probability p.
     *
     * @param p the probability of an element to be zeroed.
     * @return a new tensor after random dropouts.
     */
    public Tensor dropout(double p) {
        return new Tensor(torch.dropout(value, p, false));
    }

    /**
     * Randomly zeroes some elements in place
     * with probability p.
     *
     * @param p the probability of an element to be zeroed.
     * @return this tensor.
     */
    public Tensor dropout_(double p) {
        torch.dropout(value, p, true);
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
        return new Tensor(value.new_zeros(shape));
    }

    /**
     * Returns a tensor filled with all ones. The returned Tensor has the
     * data type and device as this tensor.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public Tensor newOnes(long... shape) {
        if (shape.length == 0) shape = shape();
        return new Tensor(value.new_ones(shape));
    }

    /**
     * Returns an identity matrix.
     * @param shape the dimension of the resulting matrix.
     * @return the created tensor.
     */
    public static Tensor eye(long shape) {
        if (defaultOptions != null) return eye(defaultOptions, shape);
        return new Tensor(torch.eye(shape));
    }

    /**
     * Returns an identity matrix.
     * @param options Tensor creation options.
     * @param shape the dimension of the resulting matrix.
     * @return the created tensor.
     */
    public static Tensor eye(Options options, long shape) {
        return new Tensor(torch.eye(shape, options.value));
    }

    /**
     * Returns a tensor filled with the given value.
     * @param value the value to fill the output tensor with.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor full(long value, long... shape) {
        var tensor = defaultOptions == null ?
                torch.full(shape, new Scalar(value)) :
                torch.full(shape, new Scalar(value), defaultOptions.value);
        return new Tensor(tensor);
    }

    /**
     * Returns a tensor filled with the given value.
     * @param value the value to fill the output tensor with.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor full(double value, long... shape) {
        var tensor = defaultOptions == null ?
                torch.full(shape, new Scalar((float) value)) :
                torch.full(shape, new Scalar((float) value), defaultOptions.value);
        return new Tensor(tensor);
    }

    /**
     * Returns a tensor with uninitialized data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor empty(long... shape) {
        if (defaultOptions != null) return empty(defaultOptions, shape);
        return new Tensor(torch.empty(shape));
    }

    /**
     * Returns a tensor with uninitialized data.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor empty(Options options, long... shape) {
        return new Tensor(torch.empty(shape, options.value, null));
    }

    /**
     * Returns a tensor filled with all zeros.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor zeros(long... shape) {
        if (defaultOptions != null) return zeros(defaultOptions, shape);
        return new Tensor(torch.zeros(shape));
    }

    /**
     * Returns a tensor filled with all zeros.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor zeros(Options options, long... shape) {
        return new Tensor(torch.zeros(shape, options.value));
    }

    /**
     * Returns a tensor filled with all ones.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor ones(long... shape) {
        if (defaultOptions != null) return ones(defaultOptions, shape);
        return new Tensor(torch.ones(shape));
    }

    /**
     * Returns a tensor filled with all ones.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor ones(Options options, long... shape) {
        return new Tensor(torch.ones(shape, options.value));
    }

    /**
     * Returns a tensor filled with values drawn from a uniform distribution on [0, 1).
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor rand(long... shape) {
        if (defaultOptions != null) return rand(defaultOptions, shape);
        return new Tensor(torch.rand(shape));
    }

    /**
     * Returns a tensor filled with values drawn from a uniform distribution on [0, 1).
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor rand(Options options, long... shape) {
        return new Tensor(torch.rand(shape, options.value));
    }

    /**
     * Returns a tensor filled with values drawn from a unit normal distribution.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor randn(long... shape) {
        if (defaultOptions != null) return randn(defaultOptions, shape);
        return new Tensor(torch.randn(shape));
    }

    /**
     * Returns a tensor filled with values drawn from a unit normal distribution.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor randn(Options options, long... shape) {
        return new Tensor(torch.randn(shape, options.value));
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
        return new Tensor(torch.arange(new Scalar(start), new Scalar(end), new Scalar(step)));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(boolean[] data, long... shape) {
        if (shape.length == 0) {
            shape = new long[] { data.length };
        }
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(byte[] data, long... shape) {
        if (shape.length == 0) {
            shape = new long[] { data.length };
        }
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(short[] data, long... shape) {
        if (shape.length == 0) {
            shape = new long[] { data.length };
        }
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(int[] data, long... shape) {
        if (shape.length == 0) {
            shape = new long[] { data.length };
        }
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(long[] data, long... shape) {
        if (shape.length == 0) {
            shape = new long[] { data.length };
        }
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(float[] data, long... shape) {
        if (shape.length == 0) {
            shape = new long[] { data.length };
        }
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(double[] data, long... shape) {
        if (shape.length == 0) {
            shape = new long[] { data.length };
        }
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * A class that encapsulates the construction axes of a tensor.
     * With construction axis we mean a particular property of a tensor
     * that can be configured before its construction (and sometimes
     * changed afterward).
     */
    public static class Options {
        /** PyTorch options object. */
        TensorOptions value;
        /** Constructor with default values for every axis. */
        public Options() {
            this.value = new TensorOptions();
        }

        /**
         * Sets the data type of the elements stored in the tensor.
         * @param type the data type.
         * @return this options object.
         */
        public Options dtype(ScalarType type) {
            value = value.dtype(new ScalarTypeOptional(type.value));
            return this;
        }

        /**
         * Sets a compute device on which a tensor is stored.
         * @param device a compute device.
         * @return this options object.
         */
        public Options device(Device device) {
            value = value.device(new DeviceOptional(device.value));
            return this;
        }

        /**
         * Sets strided (dense) or sparse tensor.
         * @param layout the tensor layout.
         * @return this options object.
         */
        public Options layout(Layout layout) {
            value = value.layout(new LayoutOptional(layout.value));
            return this;
        }

        /**
         * Set true if gradients need to be computed for this tensor.
         * @param required the flag indicating if gradients need to be
         *                computed for this tensor.
         * @return this options object.
         */
        public Options requireGradients(boolean required) {
            value = value.requires_grad(new BoolOptional(required));
            return this;
        }
    }
}
