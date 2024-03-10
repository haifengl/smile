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
package smile.deep;

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
    private Tensor(org.bytedeco.pytorch.Tensor tensor) {
        this.value = tensor;
    }

    /**
     * Clone the tensor with a different data type and/or device.
     * @param options New Tensor construction options.
     * @return The cloned tensor.
     */
    public Tensor clone(Options options) {
        return new Tensor(value.to(options.value, true, true, new MemoryFormatOptional(torch.MemoryFormat.Preserve)));
    }

    /**
     * Returns a new Tensor, detached from the current graph.
     * The result will never require gradient.
     *
     * @return a new Tensor that doesn't require gradient.
     */
    public Tensor detach() {
        return new Tensor(value.detach());
    }

    public Tensor add(float other) {
        value.add(new Scalar(other));
        return this;
    }

    public Tensor add(float other, float alpha) {
        value.add(new Scalar(other), new Scalar(alpha));
        return this;
    }

    public Tensor add(Tensor other) {
        value.add(other.value);
        return this;
    }

    public Tensor add(Tensor other, float alpha) {
        value.add(other.value, new Scalar(alpha));
        return this;
    }

    public Tensor sub(float other) {
        value.sub(new Scalar(other));
        return this;
    }

    public Tensor sub(float other, float alpha) {
        value.sub(new Scalar(other), new Scalar(alpha));
        return this;
    }

    public Tensor sub(Tensor other) {
        value.sub(other.value);
        return this;
    }

    public Tensor sub(Tensor other, float alpha) {
        value.sub(other.value, new Scalar(alpha));
        return this;
    }

    /**
     * Returns an identity matrix.
     * @param shape the dimension of the resulting matrix.
     * @return the created tensor.
     */
    public static Tensor eye(long shape) {
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
     * Returns a tensor filled with all zeros.
     * @param shape the dimensional shape of the resulting tensor.
     * @return the created tensor.
     */
    public static Tensor zeros(long... shape) {
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

        /** Sets a compute device on which a tensor is stored. */
        public Options device(DeviceType device) {
            return device(device, (byte) 0);
        }

        /**
         * Sets a compute device on which a tensor is stored.
         * @param device device type.
         * @param index device ordinal.
         * @return this options object.
         */
        public Options device(DeviceType device, byte index) {
            value = value.device(new DeviceOptional(new Device(device.value, index)));
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
