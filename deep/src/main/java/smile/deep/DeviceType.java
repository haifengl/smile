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
 * The compute device type.
 *
 * @author Haifeng Li
 */
public enum DeviceType {
    CPU(torch.DeviceType.CPU),
    /** NVIDIA GPU */
    CUDA(torch.DeviceType.CUDA),
    /** GPU for MacOS devices with Metal programming framework. */
    MPS(torch.DeviceType.MPS);

    /** PyTorch device type. */
    torch.DeviceType value;

    /** Constructor. */
    DeviceType(torch.DeviceType device) {
        this.value = device;
    }
}
