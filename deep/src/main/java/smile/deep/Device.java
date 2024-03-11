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

import org.bytedeco.pytorch.global.torch;

/**
 * The compute device on which a tensor is stored.
 *
 * @author Haifeng Li
 */
public class Device {
    /** PyTorch device. */
    org.bytedeco.pytorch.Device value;

    /**
     * Constructor.
     * @param type the compute device type.
     */
    public Device(DeviceType type) {
        this(type, (byte) 0);
    }

    /**
     * Constructor.
     * @param type the compute device type.
     * @param index the CUDA device index.
     */
    public Device(DeviceType type, byte index) {
        this.value = new org.bytedeco.pytorch.Device(type.value, index);
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
}
