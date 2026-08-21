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
package smile.deep;

import smile.deep.tensor.Device;
import smile.torch.Native;
import static smile.torch.smile_torch_h.smile_cuda_device_count;
import static smile.torch.smile_torch_h.smile_cuda_is_available;
import static smile.torch.smile_torch_h.smile_cuda_total_memory;

/**
 * NVIDIA CUDA helper functions.
 *
 * @author Haifeng Li
 */
public interface CUDA {
    /**
     * Returns true if CUDA is available.
     * @return true if CUDA is available.
     */
    static boolean isAvailable() {
        return smile_cuda_is_available() != 0;
    }

    /**
     * Returns the number of CUDA devices.
     * @return the number of CUDA devices.
     */
    static long deviceCount() {
        return smile_cuda_device_count();
    }

    /**
     * Returns the total global memory of a CUDA device in bytes.
     * @param deviceIndex the CUDA device index.
     * @return total memory in bytes, or {@code -1} on error.
     */
    static long totalMemory(int deviceIndex) {
        return smile_cuda_total_memory(deviceIndex);
    }

    /**
     * Returns the currently free device memory of a CUDA device in bytes.
     * @param deviceIndex the CUDA device index.
     * @return free memory in bytes.
     */
    static long freeMemory(int deviceIndex) {
        return Native.cudaMemGetInfo(deviceIndex)[0];
    }

    /**
     * Returns CUDACachingAllocator live and reserved bytes for a device.
     *
     * @param deviceIndex the CUDA device index.
     * @return {@code long[2]} of {@code {allocatedBytes, reservedBytes}}.
     */
    static long[] allocatorStats(int deviceIndex) {
        return Native.cudaAllocatorStats(deviceIndex);
    }

    /**
     * Returns the default CUDA device.
     *
     * @return the default CUDA device.
     */
    static Device device() {
        return Device.CUDA();
    }

    /**
     * Returns the CUDA device of given index.
     *
     * @param index the CUDA device index.
     * @return the CUDA device.
     */
    static Device device(byte index) {
        return Device.CUDA(index);
    }
}
