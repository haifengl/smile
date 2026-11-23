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
package smile.llm.model.inkling;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The MXFP8 quantizer and the Marlin weight-only GEMM against the bf16 product: the relative error of a random matrix
 * (rows not a multiple of 64 are padded) stays within the rounding of E4M3 (a few percent).
 *
 * <p>Opt-in, GPU: {@code INKLING_DEVICE} (any value enables the test).
 *
 * @author Haifeng Li
 */
public class InklingFp8Test {
    @Test
    public void testGivenRandomWeightWhenQuantizedThenProductMatchesBf16() {
        String dev = System.getenv("INKLING_DEVICE");
        Assumptions.assumeTrue(dev != null && InklingFp8.available(), "set INKLING_DEVICE on a GPU with the Marlin MXFP8 kernel");
        Device device = Device.CUDA(Byte.parseByte(dev));
        for (int n : new int[]{1024, 1056, 3072}) {
            Tensor w = Ix.bf16(Tensor.randn(n, 2304).to(device));
            Tensor x = Ix.bf16(Tensor.randn(8, 2304).to(device));
            InklingFp8 q = InklingFp8.quantize(w);
            Tensor y8 = Ix.f32(Ix.narrow(q.forward(x), 1, 0, n)).to(Device.CPU());
            Tensor ref = Ix.f32(Ix.linear(x, w)).to(Device.CPU());
            float[] a = y8.floatArray(), b = ref.floatArray();
            double num = 0, den = 0;
            for (int i = 0; i < a.length; i++) {
                num += (a[i] - b[i]) * (a[i] - b[i]);
                den += b[i] * b[i];
            }
            double rel = Math.sqrt(num / den);
            System.out.printf("n=%d (padded %d): relative rms error %.4f%n", n, q.npad, rel);
            assertTrue(rel < 0.05, "MXFP8 product error " + rel);
            assertEquals((n + 63) / 64 * 64, q.npad);
        }
    }
}
