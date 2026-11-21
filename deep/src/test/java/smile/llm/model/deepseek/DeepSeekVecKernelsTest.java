/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
package smile.llm.model.deepseek;

import java.util.Random;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in ({@code DS_CUDA=true}): hashes the output bits of the vectorized memory-bound kernels (hc_pre, hc_post, fake_quant_fp8, rope_tail,
 * swiglu) on fixed random inputs of prefill-sized shapes and times them. The hashes must be identical with
 * {@code SMILE_DS_VEC=0} (the scalar kernels) and {@code SMILE_DS_VEC=1} (default): the vectorized kernels only change the
 * memory access, not the arithmetic.
 */
@Tag("cuda")
public class DeepSeekVecKernelsTest {
    private static Tensor rand(Device dev, ScalarType dt, long seed, double scale, long... shape) {
        long n = 1;
        for (long s : shape) {
            n *= s;
        }
        float[] a = new float[(int) n];
        Random r = new Random(seed);
        for (int i = 0; i < a.length; i++) {
            a[i] = (float) (r.nextGaussian() * scale * Math.exp(r.nextGaussian()));
        }
        return Tensor.of(a, shape).to(dt).to(dev);
    }

    /** Sums of the values and of their squares in double (on the device): equal outputs give equal sums. */
    private static long hash(Tensor t) {
        Tensor d = t.to(ScalarType.Double);
        double a = Aten.one("aten::sum", d).doubleValue();
        double b = Aten.one("aten::sum", Aten.one("aten::mul.Tensor", d, d)).doubleValue();
        // a position-dependent term (values are weighted by their flat index), so that permuted outputs differ too
        long n = 1;
        for (long sz : d.shape()) {
            n *= sz;
        }
        Tensor w = Aten.one("aten::reshape", Native.dsIota(d, n).to(ScalarType.Double), d.shape());
        double c = Aten.one("aten::sum", Aten.one("aten::mul.Tensor", d, w)).doubleValue();
        return 31 * (31 * Double.doubleToLongBits(a) + Double.doubleToLongBits(b)) + Double.doubleToLongBits(c);
    }

    /** {@code [reps * rows, ...]}: the random block repeated (the kernels see different positions and frequencies anyway). */
    private static Tensor tile(Tensor t, long dim, long reps) {
        long[] r = new long[t.shape().length];
        java.util.Arrays.fill(r, 1);
        r[(int) dim] = reps;
        return Aten.one("aten::repeat", t, r);
    }

    private interface Op {
        Tensor run();
    }

    private static void check(String name, Op op, long bytes) {
        Tensor out = op.run();
        long h = hash(out);
        for (int i = 0; i < 3; i++) {
            op.run().close();
        }
        Aten.one("aten::sum", Aten.one("aten::zeros", new long[]{1}, ScalarType.Float).to(Device.CUDA())).doubleValue();
        long t0 = System.nanoTime();
        int reps = 20;
        Tensor last = null;
        for (int i = 0; i < reps; i++) {
            if (last != null) {
                last.close();
            }
            last = op.run();
        }
        Aten.one("aten::sum", last.to(ScalarType.Float)).doubleValue();
        double us = (System.nanoTime() - t0) / 1e3 / reps;
        System.out.printf("VEC %-22s hash %20d  %8.1f us  %6.0f GB/s%n", name, h, us, bytes / us / 1e3);
        assertTrue(true);
    }

    @Test
    public void testPrintsHashesAndTimes() {
        Assumptions.assumeTrue("true".equals(System.getenv("DS_CUDA")), "set DS_CUDA=true");
        Device cuda = Device.CUDA();
        final long rows = 4096;
        final long dim = 5120;
        Tensor stream = tile(rand(cuda, ScalarType.BFloat16, 1, 1.0, 1, rows / 4, 4, dim), 1, 4);
        Tensor x = rand(cuda, ScalarType.BFloat16, 2, 1.0, 1, rows, dim);
        Tensor pre = rand(cuda, ScalarType.Float, 3, 0.3, 1, rows, 4);
        Tensor post = rand(cuda, ScalarType.Float, 4, 0.3, 1, rows, 4);
        Tensor comb = rand(cuda, ScalarType.Float, 5, 0.3, 1, rows, 4, 4);
        check("hc_pre (first)", () -> Native.dsHcPre(stream, pre), rows * 4 * dim * 2 + rows * dim * 2);
        check("hc_pre", () -> Native.dsHcPre(stream, pre), rows * 4 * dim * 2 + rows * dim * 2);
        check("hc_post", () -> Native.dsHcPost(x, stream, post, comb), rows * 4 * dim * 4 + rows * dim * 2);
        Tensor act = rand(cuda, ScalarType.BFloat16, 6, 2.0, rows, dim);
        check("fake_quant_fp8", () -> Native.dsFakeQuantFp8(act), rows * dim * 4);
        Tensor q = tile(rand(cuda, ScalarType.BFloat16, 7, 1.0, 1, rows / 16, 64, 512), 1, 16);
        Tensor fr = Aten.one("aten::view_as_complex", rand(cuda, ScalarType.Float, 8, 1.0, rows, 32, 2));
        check("rope_tail", () -> Native.dsRopeTail(q, fr, 64, false), rows * 64 * 512 * 4);
        check("rope_tail inverse", () -> Native.dsRopeTail(q, fr, 64, true), rows * 64 * 512 * 4);
        Tensor gu = tile(rand(cuda, ScalarType.BFloat16, 9, 3.0, rows * 6 / 16, 2 * 2304), 0, 16);
        Tensor w = rand(cuda, ScalarType.Float, 10, 0.3, rows * 6);
        check("swiglu", () -> Native.dsSwiglu(gu, null, 10.0, false), rows * 6 * 2304 * 6);
        check("swiglu quant", () -> Native.dsSwiglu(gu, null, 10.0, true), rows * 6 * 2304 * 6);
        check("swiglu w quant", () -> Native.dsSwiglu(gu, w, 10.0, true), rows * 6 * 2304 * 6);
        // odd row counts and a small width: the tails of the grids
        Tensor gu2 = rand(cuda, ScalarType.BFloat16, 11, 3.0, 37, 2 * 64);
        check("swiglu small", () -> Native.dsSwiglu(gu2, null, 10.0, true), 37 * 64 * 6);
        Tensor act2 = rand(cuda, ScalarType.BFloat16, 12, 2.0, 37, 96);
        check("fake_quant_fp8 small", () -> Native.dsFakeQuantFp8(act2), 37 * 96 * 4);
    }
}
