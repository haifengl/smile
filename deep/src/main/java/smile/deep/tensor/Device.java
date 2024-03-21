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

import org.bytedeco.pytorch.global.torch;

/**
 * The compute device on which a tensor is stored.
 *
 * @author Haifeng Li
 */
public class Device {
    /** Device type. */
    final DeviceType type;
    /** PyTorch device. */
    final org.bytedeco.pytorch.Device value;

    /**
     * Constructor.
     * @param type the compute device type.
     */
    public Device(DeviceType type) {
        this(type, (byte) (type == DeviceType.CPU ? 0 : -1));
    }

    /**
     * Constructor.
     * @param type the compute device type.
     * @param index the CUDA device index.
     */
    public Device(DeviceType type, byte index) {
        this.type = type;
        this.value = new org.bytedeco.pytorch.Device(type.value, index);
    }

    /** Returns the PyTorch device object. */
    public org.bytedeco.pytorch.Device asTorch() {
        return this.value;
    }

    /**
     * Returns the CPU device.
     *
     * @return the compute device.
     */
    public static Device CPU() {
        return new Device(DeviceType.CPU);
    }

    /**
     * Returns the GPU for MacOS devices with Metal programming framework.
     *
     * @return the compute device.
     */
    public static Device MPS() {
        return new Device(DeviceType.MPS);
    }

    /**
     * Returns the default NVIDIA CUDA device.
     *
     * @return the compute device.
     */
    public static Device CUDA() {
        return CUDA((byte) 0);
    }

    /**
     * Returns the NVIDIA CUDA device.
     *
     * @param index the CUDA device index.
     * @return the compute device.
     */
    public static Device CUDA(byte index) {
        return new Device(DeviceType.CUDA, index);
    }

    /**
     * Sets Tensor to be allocated on this device. This does not affect factory
     * function calls which are called with an explicit device argument.
     * Factory calls will be performed as if they were passed device as
     * an argument.
     *
     * The default device is initially CPU. If you set the default tensor
     * device to another device (e.g., CUDA) without a device index,
     * tensors will be allocated on whatever the current device for
     * the device type.
     */
    public void setDefaultDevice() {
        torch.device(value);
    }

    public DeviceType type() {
        return type;
    }

    /**
     * Returns the device index or ordinal, which identifies the specific
     * compute device when there is more than one of a certain type. The
     * device index is optional, and in its defaulted state represents
     * (abstractly) "the current device". Further, there are two constraints
     * on the value of the device index, if one is explicitly stored:
     * 1. A negative index represents the current device, a non-negative
     * index represents a specific, concrete device, 2. When the device
     * type is CPU, the device index must be zero.
     *
     * @return the device index.
     */
    public byte index() {
        return value.index();
    }
}
