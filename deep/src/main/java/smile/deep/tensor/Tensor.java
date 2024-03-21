/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep.tensor;

import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.global.torch;

/**
 * A Tensor is a multi-dimensional array containing elements of a single data type.
 *
 * @author Haifeng Li
 */
public class Tensor {
    /** PyTorch Tensor handle. */
    org.bytedeco.pytorch.Tensor value;

    /**
     * Constructor.
     * @param tensor PyTorch Tensor object.
     */
    Tensor(org.bytedeco.pytorch.Tensor tensor) {
        this.value = tensor;
    }

    /** Prints the tensor on the standard output. */
    public void print() {
        torch.print(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public Tensor clone() {
        return Tensor.of(value.to());
    }

    /** Returns the PyTorch tensor object. */
    public org.bytedeco.pytorch.Tensor asTorch() {
        return this.value;
    }

    /**
     * Returns a new tensor, detached from the current graph.
     * The result will never require gradient.
     *
     * @return a new tensor that doesn't require gradient.
     */
    public Tensor detach() {
        return Tensor.of(value.detach());
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
     * Clone the tensor to a device with a different data type.
     * @param device the compute device of new tensor.
     * @param dtype the element data type of new tensor.
     * @return The cloned tensor.
     */
    public Tensor to(Device device, ScalarType dtype) {
        return Tensor.of(value.to(device.value, dtype.value));
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
    public long size(long dim) {
        return value.size(dim);
    }

    /**
     * Returns a tensor with the same data and number of elements
     * but with the specified shape. This method returns a view
     * if shape is compatible with the current shape.
     *
     * @return the tensor with the specified shape.
     */
    public Tensor reshape(long... shape) {
        return Tensor.of(value.reshape(shape));
    }

    /** Computes the gradients. */
    public void backward() {
        value.backward();
    }

    /**
     * Returns a tensor index vector.
     * @param indices the indices along the dimensions.
     * @return the index vector.
     */
    private TensorIndexVector index(Index... indices) {
        TensorIndexVector index = new TensorIndexVector();
        for (Index i : indices) {
            index.put(new TensorIndex(i.value));
        }
        return index;
    }

    /**
     * Updates a portion of tensor in place.
     * @param x the new sub-tensor values.
     * @param indices the indices along the dimensions.
     * @return this tensor.
     */
    public Tensor put_(Tensor x, Index... indices) {
        value.index_put_(index(indices), x.value);
        return this;
    }

    /**
     * Returns a portion of tensor given the indices.
     * @param indices the indices along the dimensions.
     * @return the sub-tensor.
     */
    public Tensor get(Index... indices) {
        return Tensor.of(value.index(index(indices)));
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param index the element index.
     * @return the element value.
     */
    public int getInt(long... index) {
        org.bytedeco.pytorch.Tensor x = value;
        for (long i : index) {
            x = x.get(i);
        }
        return x.item_int();
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param index the element index.
     * @return the element value.
     */
    public long getLong(long... index) {
        org.bytedeco.pytorch.Tensor x = value;
        for (long i : index) {
            x = x.get(i);
        }
        return x.item_long();
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param index the element index.
     * @return the element value.
     */
    public float getFloat(long... index) {
        org.bytedeco.pytorch.Tensor x = value;
        for (long i : index) {
            x = x.get(i);
        }
        return x.item_float();
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param index the element index.
     * @return the element value.
     */
    public double getDouble(long... index) {
        org.bytedeco.pytorch.Tensor x = value;
        for (long i : index) {
            x = x.get(i);
        }
        return x.item_double();
    }

    /** Returns the int value when the tensor holds a single value. */
    public int toInt() {
        return value.item_int();
    }

    /** Returns the long value when the tensor holds a single value. */
    public long toLong() {
        return value.item_long();
    }

    /** Returns the float value when the tensor holds a single value. */
    public float toFloat() {
        return value.item_float();
    }

    /** Returns the double value when the tensor holds a single value. */
    public double toDouble() {
        return value.item_double();
    }

    /**
     * Returns a new tensor with a dimension of size one inserted at the
     * specified position.
     *
     * The returned tensor shares the same underlying data with this tensor.
     *
     * A dim value within the range [-input.dim() - 1, input.dim() + 1) can be
     * used. Negative dim will correspond to unsqueeze() applied at
     * dim = dim + input.dim() + 1.
     *
     * @param dim the index at which to insert the singleton dimension.
     * @return the output tensor.
     */
    public Tensor unsqueeze(long dim) {
        return Tensor.of(value.unsqueeze(dim));
    }

    /**
     * Returns a tensor that is a transposed version of input. The given
     * dimensions dim0 and dim1 are swapped.
     *
     * If input is a strided tensor then the resulting out tensor shares
     * its underlying storage with the input tensor, so changing the content
     * of one would change the content of the other.
     *
     * If input is a sparse tensor then the resulting out tensor does not
     * share the underlying storage with the input tensor.
     *
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
        return Tensor.of(value.transpose(dim0, dim1));
    }

    /**
     * Returns the indices of the maximum value of a tensor across a dimension.
     *
     * @param dim the dimension to reduce.
     * @param keepDim whether the output tensor has dim retained or not.
     * @return the indices of the maximum value of a tensor across a dimension.
     */
    public Tensor argmax(int dim, boolean keepDim) {
        return Tensor.of(value.argmax(new LongOptional(dim), keepDim));
    }

    /**
     * Computes element-wise equality.
     * @param other the tensor to compare.
     * @return the output tensor.
     */
    public Tensor eq(Tensor other) {
        return Tensor.of(value.eq(other.value));
    }

    /**
     * Returns the sum of all elements in the tensor.
     * @return the sum of all elements.
     */
    public Tensor sum() {
        return Tensor.of(value.sum());
    }

    /**
     * Returns the exponential of elements in the tensor.
     * @return the output tensor.
     */
    public Tensor exp() {
        return Tensor.of(value.exp());
    }

    /**
     * Returns the exponential of elements in the tensor in place.
     * @return this tensor.
     */
    public Tensor exp_() {
        return Tensor.of(value.exp_());
    }

    /**
     * Returns A + b.
     * @param other a scalar value.
     * @return the output tensor.
     */
    public Tensor add(double other) {
        return Tensor.of(value.add(new Scalar(other)));
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
        return Tensor.of(value.add(other.value));
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
        return Tensor.of(value.add(other.value, new Scalar(alpha)));
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
        return Tensor.of(value.sub(new Scalar(other)));
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
        return Tensor.of(value.sub(other.value));
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
        return Tensor.of(value.sub(other.value, new Scalar(alpha)));
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
    public Tensor mul(double other) {
        return Tensor.of(value.mul(new Scalar(other)));
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
        return Tensor.of(value.mul(other.value));
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
    public Tensor div(double other) {
        return Tensor.of(value.div(new Scalar(other)));
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
        return Tensor.of(value.div(other.value));
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
     * Returns a new tensor with the cosine of the elements of input.
     * @return a new tensor with the cosine of the elements of input.
     */
    public Tensor cos() {
        return Tensor.of(value.cos());
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
        return Tensor.of(value.cos());
    }

    /**
     * Computes the sine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor sin_() {
        value.cos_();
        return this;
    }

    /**
     * Returns a new tensor with the arccosine of the elements of input.
     * @return a new tensor with the arccosine of the elements of input.
     */
    public Tensor acos() {
        return Tensor.of(value.acos());
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
        return Tensor.of(value.acos());
    }

    /**
     * Computes the arcsine of the elements of input in place.
     * @return this tensor.
     */
    public Tensor asin_() {
        value.acos_();
        return this;
    }

    /**
     * Randomly zeroes some of the elements of the input tensor
     * with probability p.
     *
     * @param p the probability of an element to be zeroed.
     * @return a new tensor after random dropouts.
     */
    public Tensor dropout(double p) {
        return Tensor.of(torch.dropout(value, p, false));
    }

    /**
     * Randomly zeroes some of the elements in place
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
     * Returns an identity matrix.
     * @param shape the dimension of the resulting matrix.
     * @return the created tensor.
     */
    public static Tensor eye(long shape) {
        return Tensor.of(torch.eye(shape));
    }

    /**
     * Returns an identity matrix.
     * @param options Tensor creation options.
     * @param shape the dimension of the resulting matrix.
     * @return the created tensor.
     */
    public static Tensor eye(Options options, long shape) {
        return Tensor.of(torch.eye(shape, options.value));
    }

    /**
     * Returns a tensor filled with all zeros.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor zeros(long... shape) {
        return Tensor.of(torch.zeros(shape));
    }

    /**
     * Returns a tensor filled with all zeros.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor zeros(Options options, long... shape) {
        return Tensor.of(torch.zeros(shape, options.value));
    }

    /**
     * Returns a tensor filled with all ones.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor ones(long... shape) {
        return Tensor.of(torch.ones(shape));
    }

    /**
     * Returns a tensor filled with all ones.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor ones(Options options, long... shape) {
        return Tensor.of(torch.ones(shape, options.value));
    }

    /**
     * Returns a tensor filled with values drawn from a uniform distribution on [0, 1).
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor rand(long... shape) {
        return Tensor.of(torch.rand(shape));
    }

    /**
     * Returns a tensor filled with values drawn from a uniform distribution on [0, 1).
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor rand(Options options, long... shape) {
        return Tensor.of(torch.rand(shape, options.value));
    }

    /**
     * Returns a tensor filled with values drawn from a unit normal distribution.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor randn(long... shape) {
        return Tensor.of(torch.randn(shape));
    }

    /**
     * Returns a tensor filled with values drawn from a unit normal distribution.
     * @param options Tensor creation options.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor randn(Options options, long... shape) {
        return Tensor.of(torch.randn(shape, options.value));
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
    public static Tensor arange(int start, int end, int step) {
        return Tensor.of(torch.arange(new Scalar(start), new Scalar(end), new Scalar(step)));
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
        return Tensor.of(torch.arange(new Scalar(start), new Scalar(end), new Scalar(step)));
    }

    /**
     * Returns a 1-D tensor of size (end - start) / step with values from the
     * interval [start, end) taken with common difference step beginning from
     * start.
     *
     * Note that step is subject to floating point rounding errors when
     * comparing against end. To avoid inconsistency, we advise subtracting
     * a small epsilon from end in such cases.
     *
     * @param start the starting value for the set of points.
     * @param end the ending value for the set of points.
     * @param step the gap between each pair of adjacent points.
     * @return a 1-D tensor.
     */
    public static Tensor arange(float start, float end, float step) {
        return Tensor.of(torch.arange(new Scalar(start), new Scalar(end), new Scalar(step)));
    }

    /**
     * Returns a 1-D tensor of size (end - start) / step with values from the
     * interval [start, end) taken with common difference step beginning from
     * start.
     *
     * Note that step is subject to floating point rounding errors when
     * comparing against end. To avoid inconsistency, we advise subtracting
     * a small epsilon from end in such cases.
     *
     * @param start the starting value for the set of points.
     * @param end the ending value for the set of points.
     * @param step the gap between each pair of adjacent points.
     * @return a 1-D tensor.
     */
    public static Tensor arange(double start, double end, double step) {
        return Tensor.of(torch.arange(new Scalar(start), new Scalar(end), new Scalar(step)));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(byte[] data, long... shape) {
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(short[] data, long... shape) {
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(int[] data, long... shape) {
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(long[] data, long... shape) {
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(float[] data, long... shape) {
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with given data and shape.
     * @param data the initialization data.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor of(double[] data, long... shape) {
        return new Tensor(org.bytedeco.pytorch.Tensor.create(data, shape));
    }

    /**
     * Returns a tensor with a PyTorch tensor object.
     * @param tensor PyTorch tensor object.
     * @return the created tensor.
     */
    public static Tensor of(org.bytedeco.pytorch.Tensor tensor) {
        return new Tensor(tensor);
    }

    /**
     * Creates a tensor instance.
     * @param tensor PyTorch Tensor object.
     * @param device the compute device of this Tensor.
     * @param dtype the element data type of this Tensor.
     * @return the tensor instance.
     */
    public static Tensor of(org.bytedeco.pytorch.Tensor tensor, Device device, ScalarType dtype) {
        return new Tensor(tensor.to(device.value, dtype.value));
    }

    /**
     * A class that encapsulates the construction axes of a Tensor.
     * With construction axis we mean a particular property of a Tensor
     * that can be configured before its construction (and sometimes changed
     * afterwards).
     */
    public static class Options {
        /** PyTorch options object. */
        TensorOptions value;
        /** Constructor with default values for every axis. */
        public Options() {
            this.value = new TensorOptions();
        }

        /** Sets the data type of the elements stored in the tensor. */
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

        /** Sets strided (dense) or sparse tensor. */
        public Options layout(Layout layout) {
            value = value.layout(new LayoutOptional(layout.value));
            return this;
        }

        /** Set true if gradients need to be computed for this Tensor. */
        public Options requireGradients(boolean required) {
            value = value.requires_grad(new BoolOptional(required));
            return this;
        }
    }
}
