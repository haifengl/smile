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
import java.util.Objects;
import smile.torch.smile_torch_h;

/**
 * The compute device on which a tensor is stored.
 *
 * <p>A device is an immutable value object identified by its {@link DeviceType}
 * and index. Native {@code ST_Device} handles are created on demand and freed
 * immediately, so {@code Device} instances are cheap to create and hold no
 * native resources.
 *
 * @author Haifeng Li
 */
public class Device {
    /** Device type. */
    final DeviceType type;
    /** The device index (or ordinal). */
    final byte index;

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
        this.index = index;
    }

    /**
     * Creates a new native {@code ST_Device} handle for this device. The caller
     * must free it with {@code smile_device_free}.
     * @return a new {@code ST_Device} handle.
     */
    MemorySegment toNative() {
        return Native.check(smile_torch_h.smile_device_create(type.code, index));
    }

    /**
     * Returns the Java device for a native {@code ST_Device} handle. Does not
     * take ownership of the handle.
     * @param device the {@code ST_Device} handle.
     * @return the device.
     */
    static Device fromNative(MemorySegment device) {
        byte index = smile_torch_h.smile_device_index(device);
        DeviceType type;
        if (smile_torch_h.smile_device_is_cuda(device) != 0) {
            type = DeviceType.CUDA;
        } else if (smile_torch_h.smile_device_is_mps(device) != 0) {
            type = DeviceType.MPS;
        } else if (smile_torch_h.smile_device_is_cpu(device) != 0) {
            type = DeviceType.CPU;
        } else {
            throw new IllegalArgumentException("Unsupported device");
        }
        return new Device(type, index);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Device x) {
            return type == x.type && index == x.index;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, index);
    }

    @Override
    public String toString() {
        MemorySegment device = toNative();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(64);
            if (smile_torch_h.smile_device_str(device, buf, 64) != 0) {
                return type.name().toLowerCase();
            }
            return buf.getString(0);
        } finally {
            smile_torch_h.smile_device_free(device);
        }
    }

    /**
     * Returns the number of threads used for intraop parallelism on CPU.
     * @return the number of threads used for intraop parallelism on CPU.
     */
    public static int getNumThreads() {
        return smile_torch_h.smile_get_num_threads();
    }

    /**
     * Sets the number of threads used for intraop parallelism on CPU.
     * @param n the number of threads used for intraop parallelism on CPU.
     */
    public static void setNumThreads(int n) {
        smile_torch_h.smile_set_num_threads(n);
    }

    /**
     * Returns true if the device is CUDA.
     * @return true if the device is CUDA.
     */
    public boolean isCUDA() {
        return type == DeviceType.CUDA;
    }

    /**
     * Returns true if the device is CPU.
     * @return true if the device is CPU.
     */
    public boolean isCPU() {
        return type == DeviceType.CPU;
    }

    /**
     * Returns true if the device is MPS.
     * @return true if the device is MPS.
     */
    public boolean isMPS() {
        return type == DeviceType.MPS;
    }

    /** Releases all unoccupied cached memory. */
    public void emptyCache() {
        if (isCUDA()) {
            smile_torch_h.smile_cuda_empty_cache();
        } else if (isMPS()) {
            smile_torch_h.smile_mps_empty_cache();
        }
    }

    /**
     * Returns the preferred (most powerful) device.
     * @return the preferred (most powerful) device.
     */
    public static Device preferredDevice() {
        if (smile_torch_h.smile_cuda_is_available() != 0) {
            return Device.CUDA();
        // MPS is slower than CPU
        // } else if (smile_torch_h.smile_mps_is_available() != 0) {
        //    return Device.MPS();
        } else {
            return Device.CPU();
        }
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
     * Returns the GPU for macOS devices with Metal programming framework.
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
     * Returns the device type.
     * @return the device type.
     */
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
        return index;
    }
}
