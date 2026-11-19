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

import java.util.ArrayList;

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
        long t0 = System.nanoTime();
        try {
            Tensor t = build.get();
            t.promoteToParent();
            return t;
        } finally {
            t0 = tick("scope.build", t0);
            Tensor.pop();
            tick("scope.close", t0);
        }
    }

    /** Runs {@code body} in its own scope (temporaries freed at once) and returns its non-tensor result. */
    static <T> T scoped0(java.util.function.Supplier<T> body) {
        smile.util.AutoScope scope = new smile.util.AutoScope();
        Tensor.push(scope);
        try {
            return body.get();
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Builds a packed layer, freeing the temporaries of the repack at once (the layer itself is not a tensor, so it
     * stays with its owner).
     */
    static <T> T scopedLayer(java.util.function.Supplier<T> build) {
        smile.util.AutoScope scope = new smile.util.AutoScope();
        Tensor.push(scope);
        try {
            return build.get();
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

    private static final ThreadLocal<java.util.LinkedHashMap<String, Tensor>> CACHE = ThreadLocal.withInitial(
            () -> new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Tensor> e) {
                    if (size() > 128) {
                        e.getValue().close();
                        return true;
                    }
                    return false;
                }
            });

    /**
     * A small per-thread (= per device) cache of tensors that are identical for every layer of a decode step (position
     * indices, window slots); the host-to-device copy that builds them is a blocking transfer. The tensor outlives the
     * current scope and must not be modified or closed by the caller.
     */
    private static final ThreadLocal<int[]> LAYER_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * Runs one layer of a long forward in its own scope: the layer's temporaries are freed when it returns instead of
     * piling up until the whole pass ends (prefill memory grows with prompt length times layers otherwise). The
     * results are promoted to the caller's scope; a tensor that must outlive the layer (shared with later layers) goes
     * through {@link #keep}.
     */
    static Tensor[] layerScoped(java.util.function.Supplier<Tensor[]> layer) {
        smile.util.AutoScope scope = new smile.util.AutoScope();
        Tensor.push(scope);
        LAYER_DEPTH.get()[0]++;
        try {
            Tensor[] r = layer.get();
            for (Tensor t : r) {
                t.promoteToParent();
            }
            return r;
        } finally {
            LAYER_DEPTH.get()[0]--;
            Tensor.pop();
        }
    }

    /** Lets {@code t} survive the enclosing {@link #layerScoped} layer (no-op outside one). */
    static Tensor keep(Tensor t) {
        if (LAYER_DEPTH.get()[0] > 0 && t != null) {
            t.promoteToParent();
        }
        return t;
    }

    static Tensor cached(String key, java.util.function.Supplier<Tensor> make) {
        var m = CACHE.get();
        Tensor t = m.get(key);
        if (t == null) {
            t = make.get();
            t.detachFromScopes();
            m.put(key, t);
        }
        return t;
    }

    /** Adds the time since {@code t0} (a {@code System.nanoTime()}) to the {@code Aten.STATS} entry {@code name}; no-op unless stats are on. */
    private static final boolean TICK_SYNC = System.getenv("SMILE_DS_SYNC") != null;

    /** GPU timestamps taken while a graph is captured (see {@link #beginStamps}); {@code null} when off. */
    private static final ThreadLocal<Object[]> STAMPS = new ThreadLocal<>();

    /** Starts recording section names and GPU timestamps into {@code buf} (int64) for the calling thread. */
    static void beginStamps(Tensor buf) {
        STAMPS.set(new Object[]{buf, new ArrayList<String>()});
        stamp("start");
    }

    /** Stops stamping and returns the section names, in stamp order (stamp 0 is the start). */
    @SuppressWarnings("unchecked")
    static java.util.List<String> endStamps() {
        Object[] st = STAMPS.get();
        STAMPS.remove();
        return st == null ? null : (java.util.List<String>) st[1];
    }

    /** Marks the end of the section {@code name} with a GPU timestamp (no-op unless stamping). */
    @SuppressWarnings("unchecked")
    static void stamp(String name) {
        Object[] st = STAMPS.get();
        if (st == null) {
            return;
        }
        java.util.List<String> names = (java.util.List<String>) st[1];
        smile.torch.Native.dsStamp((Tensor) st[0], names.size());
        names.add(name);
    }

    static long tick(String name, long t0) {
        stamp(name);
        if (TICK_SYNC && smile.torch.Aten.STATS != null) {
            host(zeros(ScalarType.Float, 1)).floatArray();   // wait for the device: section times include the GPU work
        }
        long now = System.nanoTime();
        var stats = smile.torch.Aten.STATS;
        if (stats != null) {
            long[] c = stats.computeIfAbsent("~" + name, k -> new long[2]);
            synchronized (c) {
                c[0]++;
                c[1] += now - t0;
            }
        }
        return now;
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

    /** {@code arange(n)} (int64) on the device of {@code like}, without a host copy when the kernel is available. */
    static Tensor iota(Tensor like, long n) {
        if (like.device().isCUDA() && smile.torch.Native.dsGraphAvailable()) {
            return smile.torch.Native.dsIota(like, n);
        }
        return dev(Aten.one("aten::arange", n));
    }

    static Tensor zeros(ScalarType dtype, long... shape) {
        return dev(Aten.one("aten::zeros", shape, dtype));
    }

    static Tensor full(ScalarType dtype, double value, long... shape) {
        return dev(Aten.one("aten::full", shape, value, dtype));
    }

    /** Rotates the last {@code rd} dimensions of {@code x} (a new tensor). */
    static Tensor ropeTail(Tensor x, Tensor freqs, int rd, boolean inverse) {
        Tensor fused = smile.torch.Native.dsRopeTail(x, freqs, rd, inverse);
        if (fused != null) {
            return fused;
        }
        long d = x.shape()[x.shape().length - 1];
        Tensor tail = DeepSeekOps.rope(narrow(x, -1, d - rd, rd), freqs, inverse);
        return cat(-1, narrow(x, -1, 0, d - rd), tail);
    }
}
