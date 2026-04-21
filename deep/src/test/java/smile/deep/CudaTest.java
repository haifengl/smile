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
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CUDA}.
 *
 * @author Haifeng Li
 */
public class CudaTest {
    @Test
    public void testGivenCUDAWhenIsAvailableCalledThenDoesNotThrow() {
        // isAvailable() may return true or false depending on the hardware,
        // but it must not throw.
        assertDoesNotThrow(CUDA::isAvailable);
    }

    @Test
    public void testGivenCUDAWhenDeviceCountCalledThenNonNegative() {
        assertTrue(CUDA.deviceCount() >= 0);
    }

    @Test
    public void testGivenCUDAWhenCPUDeviceRequestedThenReturnsCPUDevice() {
        Device d = Device.CPU();
        assertTrue(d.isCPU());
    }
}

