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

/**
 * The compute device type. The codes map to the {@code ST_DeviceType} values
 * exposed by the {@code smile_torch} native API, which mirror
 * {@code torch::DeviceType}.
 *
 * @author Haifeng Li
 */
public enum DeviceType {
    /** CPU */
    CPU(0),
    /** NVIDIA GPU */
    CUDA(1),
    /** GPU for macOS devices with Metal programming framework. */
    MPS(9);

    /** The native {@code ST_DeviceType} code. */
    final int code;

    /** Constructor. */
    DeviceType(int code) {
        this.code = code;
    }

    /**
     * Returns the byte value of device type, which is compatible with PyTorch.
     * @return the byte value of device type.
     */
    public byte value() {
        return (byte) code;
    }
}
