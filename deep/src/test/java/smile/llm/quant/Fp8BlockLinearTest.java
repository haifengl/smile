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
package smile.llm.quant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for block-scaled FP8 linear ({@link Fp8BlockLinear}).
 *
 * @author Haifeng Li
 */
public class Fp8BlockLinearTest {

    @BeforeEach
    public void requireNativeLib() {
        try {
            Tensor.of(new float[]{1f}).close();
        } catch (Throwable t) {
            assumeTrue(false, "libsmile_torch unavailable: " + t.getMessage());
        }
    }

    @Test
    public void testGivenBlockLayoutWhenValidateThenAcceptsQwenShapes() {
        int n = 256;
        int k = 128;
        Tensor w = Tensor.zeros(n, k);
        Tensor s = Tensor.zeros(n / Fp8BlockDequant.BLOCK, k / Fp8BlockDequant.BLOCK);
        assertTrue(Fp8BlockDequant.isBlockScale(w, s));
        w.close();
        s.close();
    }

    @Test
    public void testGivenCudaHopperWhenBlockFp8GemmThenMatchesDequantReference() {
        assumeTrue(Native.scaledMmV2Available() && Native.fp8Quant1x128Available());
        Device dev;
        try {
            dev = Device.CUDA((byte) 0);
            int[] cc = Native.cudaComputeCapability(0);
            assumeTrue(cc[0] >= 9, "block FP8 requires sm_90+, got sm_" + cc[0] + cc[1]);
        } catch (Throwable t) {
            assumeTrue(false, "CUDA unavailable: " + t.getMessage());
            return;
        }

        int n = 256;
        int k = 128;
        float[] wData = new float[n * k];
        float[] xData = new float[4 * k];
        for (int i = 0; i < wData.length; i++) {
            wData[i] = ((i % 23) - 11) * 0.02f;
        }
        for (int i = 0; i < xData.length; i++) {
            xData[i] = ((i % 17) - 8) * 0.03f;
        }

        Tensor wF = Tensor.of(wData).reshape(n, k).to(dev);
        Tensor x = Tensor.of(xData).reshape(4, k).to(dev);

        float[] scaleInv = new float[(n / Fp8BlockDequant.BLOCK) * (k / Fp8BlockDequant.BLOCK)];
        for (int i = 0; i < scaleInv.length; i++) {
            scaleInv[i] = 0.05f + (i % 7) * 0.01f;
        }
        Tensor scaleTensor = Tensor.of(scaleInv)
                .reshape(n / Fp8BlockDequant.BLOCK, k / Fp8BlockDequant.BLOCK)
                .to(dev);

        Tensor wFp8;
        try {
            wFp8 = wF.to(ScalarType.Float8e4m3fn);
        } catch (Throwable t) {
            wF.close();
            x.close();
            scaleTensor.close();
            assumeTrue(false, "Float8e4m3fn unavailable: " + t.getMessage());
            return;
        }
        wF.close();

        Tensor denseW = Fp8BlockDequant.dequant(wFp8, scaleTensor, ScalarType.BFloat16);
        Tensor ref = x.matmul(denseW.transpose(0, 1));

        Fp8BlockLinear linear = new Fp8BlockLinear(
                wFp8, scaleTensor, null, ScalarType.BFloat16);
        Tensor out = linear.forward(x);

        Tensor refCpu = ref.to(Device.CPU());
        Tensor outCpu = out.to(Device.CPU());
        float[] refArr = refCpu.to(ScalarType.Float).floatArray();
        float[] outArr = outCpu.to(ScalarType.Float).floatArray();
        assertEquals(refArr.length, outArr.length);

        float maxErr = 0f;
        for (int i = 0; i < refArr.length; i++) {
            maxErr = Math.max(maxErr, Math.abs(refArr[i] - outArr[i]));
        }
        assertTrue(maxErr < 0.15f,
                "block FP8 GEMM max abs error " + maxErr + " vs dequant+matmul reference");

        linear.close();
        denseW.close();
        ref.close();
        out.close();
        refCpu.close();
        outCpu.close();
        x.close();
        scaleTensor.close();
    }
}
