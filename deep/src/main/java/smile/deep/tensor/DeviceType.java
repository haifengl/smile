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

import org.bytedeco.pytorch.global.torch;

/**
 * The compute device type.
 *
 * @author Haifeng Li
 */
public enum DeviceType {
    /** CPU */
    CPU(torch.DeviceType.CPU),
    /** NVIDIA GPU */
    CUDA(torch.DeviceType.CUDA),
    /** GPU for macOS devices with Metal programming framework. */
    MPS(torch.DeviceType.MPS);

    /** PyTorch device type. */
    final torch.DeviceType value;

    /** Constructor. */
    DeviceType(torch.DeviceType device) {
        this.value = device;
    }

    /**
     * Returns the byte value of device type,
     * which is compatible with PyTorch.
     * @return the byte value of device type.
     */
    public byte value() {
        return value.value;
    }
}
