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

import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

/** Small tensor helpers shared by the DeepSeek layers. */
final class Dsv {
    private Dsv() {
    }

    private static Device defaultDevice = Device.CPU();
    private static final ThreadLocal<Device> DEVICE = ThreadLocal.withInitial(() -> defaultDevice);
    private static final ThreadLocal<Ep> EP = new ThreadLocal<>();
    /** Run the routed experts with the Marlin kernel (CUDA only). */
    static boolean marlin = false;

    /**
     * Expert parallelism of the calling thread's model replica: this rank owns experts
     * {@code [rank * n / world, (rank + 1) * n / world)} and the partial routed outputs are summed over the group.
     *
     * @param group the NCCL group.
     * @param rank  this thread's rank.
     * @param world the number of ranks.
     */
    record Ep(smile.llm.parallel.TensorParallelGroup group, int rank, int world) { }

    /** Device of the calling thread's model tensors (CPU for the reference path). */
    static Device device() {
        return DEVICE.get();
    }

    /** Sets the device for the calling thread, and for threads that have not asked yet. */
    static void setDevice(Device d) {
        defaultDevice = d;
        DEVICE.set(d);
    }

    /** Sets the device for the calling thread only (a rank thread of an expert-parallel run). */
    static void setThreadDevice(Device d) {
        DEVICE.set(d);
    }

    static Ep ep() {
        return EP.get();
    }

    static void setEp(Ep ep) {
        EP.set(ep);
    }

    /**
     * Builds a weight, freeing the temporaries (dequantization intermediates) of the computation at once.
     *
     * @param build computes the weight.
     * @return the weight, owned by the caller.
     */
    static Tensor scoped(java.util.function.Supplier<Tensor> build) {
        smile.util.AutoScope scope = new smile.util.AutoScope();
        Tensor.push(scope);
        try {
            Tensor t = build.get();
            t.promoteToParent();
            return t;
        } finally {
            Tensor.pop();
        }
    }

    /** Debug capture of intermediates (name to a CPU copy) when non-null; see {@link #probe}. */
    static java.util.Map<String, Tensor> probes;
    private static int probeSeq;

    static void probeNext() {
        probeSeq++;
    }

    static void probeReset() {
        probeSeq = -1;
    }

    static void probe(String name, Tensor t) {
        if (probes != null) {
            probes.put("A" + probeSeq + "." + name, host(t));
        }
    }

    /** Moves a freshly built tensor to the model's device. */
    static Tensor dev(Tensor t) {
        return t.to(device());
    }

    static Tensor o(String op, Object... args) {
        return Aten.one(op, args);
    }

    /** A fresh contiguous copy, safe to read into a Java array (the array getters reject views). */
    static Tensor host(Tensor t) {
        return Aten.one("aten::clone", t).to(Device.CPU());
    }

    static Tensor narrow(Tensor t, long dim, long start, long length) {
        return Aten.one("aten::narrow", t, dim, start, length);
    }

    static Tensor cat(long dim, Tensor... ts) {
        return Aten.one("aten::cat", ts, dim);
    }

    /** In-place {@code dst.copy_(src)} (dst is typically a view into a cache). */
    static void assign(Tensor dst, Tensor src) {
        Aten.call("aten::copy_", dst, src, false);
    }

    static Tensor zeros(ScalarType dtype, long... shape) {
        return dev(Aten.one("aten::zeros", shape, dtype));
    }

    static Tensor full(ScalarType dtype, double value, long... shape) {
        return dev(Aten.one("aten::full", shape, value, dtype));
    }

    /** Rotates the last {@code rd} dimensions of {@code x} (a new tensor). */
    static Tensor ropeTail(Tensor x, Tensor freqs, int rd, boolean inverse) {
        long d = x.shape()[x.shape().length - 1];
        Tensor tail = DeepSeekOps.rope(narrow(x, -1, d - rd, rd), freqs, inverse);
        return cat(-1, narrow(x, -1, 0, d - rd), tail);
    }
}
