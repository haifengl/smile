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

import org.bytedeco.cuda.global.cudart;
import org.bytedeco.pytorch.global.torch;
import org.bytedeco.pytorch.global.torch_cuda;

/**
 * The compute device on which a tensor is stored.
 *
 * @author Haifeng Li
 */
public class Device {
    static {
        try {
            // Initializes the driver API and must be called before any other
            // function from the driver API in the current process.
            // Currently, the Flags parameter must be 0.
            cudart.cuInit(0);
        } catch (Throwable ex) {
            // cudart may not be available, e.g. on macOS
        }
    }

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

    /**
     * Constructor.
     * @param device PyTorch device object.
     */
    Device(org.bytedeco.pytorch.Device device) {
        this.value = device;
        if (device.is_cpu()) {
            type = DeviceType.CPU;
        } else if (device.is_cuda()) {
            type = DeviceType.CUDA;
        } else if (device.is_mps()) {
            type = DeviceType.MPS;
        } else {
            throw new IllegalArgumentException("Unsupported device: " + device);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Device x) {
            return value.equals(x.value);
        }
        return false;
    }

    @Override
    public String toString() {
        return value.str().getString();
    }

    /**
     * Returns the number of threads used for intraop parallelism on CPU.
     * @return the number of threads used for intraop parallelism on CPU.
     */
    public static int getNumThreads() {
        return torch.get_num_threads();
    }

    /**
     * Sets the number of threads used for intraop parallelism on CPU.
     * @param n the number of threads used for intraop parallelism on CPU.
     */
    public static void setNumThreads(int n) {
        torch.set_num_threads(n);
    }

    /**
     * Returns true if the device is CUDA.
     * @return true if the device is CUDA.
     */
    public boolean isCUDA() {
        return value.is_cuda();
    }

    /**
     * Returns true if the device is CPU.
     * @return true if the device is CPU.
     */
    public boolean isCPU() {
        return value.is_cpu();
    }

    /**
     * Returns true if the device is MPS.
     * @return true if the device is MPS.
     */
    public boolean isMPS() {
        return value.is_mps();
    }

    /** Releases all unoccupied cached memory. */
    public void emptyCache() {
        if (isCUDA()) {
            torch_cuda.getAllocator().emptyCache();
        } else if (isMPS()) {
            torch.getMPSHooks().emptyCache();
        }
    }

    /**
     * Returns the PyTorch device object.
     * @return the PyTorch device object.
     */
    public org.bytedeco.pytorch.Device asTorch() {
        return this.value;
    }

    /**
     * Returns the preferred (most powerful) device.
     * @return the preferred (most powerful) device.
     */
    public static Device preferredDevice() {
        if (torch.cuda_is_available()) {
            return Device.CUDA();
        // MPS is slower than CPU
        // } else if (torch.hasMPS()) {
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
     * Sets Tensor to be allocated on this device. This does not affect factory
     * function calls which are called with an explicit device argument.
     * Factory calls will be performed as if they were passed device as
     * an argument.
     * <p>
     * The default device is initially CPU. If you set the default tensor
     * device to another device (e.g., CUDA) without a device index,
     * tensors will be allocated on whatever the current device for
     * the device type.
     */
    public void setDefaultDevice() {
        torch.device(value);
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
        return value.index();
    }
}
