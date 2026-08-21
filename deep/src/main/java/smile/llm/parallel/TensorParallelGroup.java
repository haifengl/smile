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
package smile.llm.parallel;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

/**
 * Single-process tensor-parallel group: one {@link ParallelState} and CUDA
 * device per TP rank, plus barrier-synchronized collectives.
 *
 * <p>Phase-2 multi-process PP will replace the barrier backend with true
 * process-group send/recv while keeping {@link #allReduceSumInPlace} for TP.
 *
 * @author Haifeng Li
 */
public final class TensorParallelGroup implements AutoCloseable {
    private final ParallelConfig config;
    private final DeviceMesh mesh;
    private final ParallelState[] states;
    private final Device[] devices;
    private final Tensor[] allReduceSlots;
    private final CyclicBarrier allReduceBarrier;
    private final AtomicReference<RuntimeException> allReduceError = new AtomicReference<>();
    private boolean closed;

    /**
     * Creates a TP group for the given mesh configuration.
     *
     * @param config process-mesh configuration ({@code ppSize} must be 1 in phase 1).
     */
    public TensorParallelGroup(ParallelConfig config) {
        this.config = config;
        this.mesh = new DeviceMesh(config);
        this.states = new ParallelState[config.tpSize()];
        this.devices = new Device[config.tpSize()];
        for (int r = 0; r < config.tpSize(); r++) {
            states[r] = new ParallelState(config, r);
            devices[r] = mesh.device(r);
        }
        this.allReduceSlots = new Tensor[config.tpSize()];
        this.allReduceBarrier = new CyclicBarrier(config.tpSize(), this::runAllReduce);
    }

    /**
     * Returns the process-mesh configuration.
     * @return parallel config.
     */
    public ParallelConfig config() {
        return config;
    }

    /**
     * Returns the device mesh for this group.
     * @return device mesh.
     */
    public DeviceMesh mesh() {
        return mesh;
    }

    /**
     * Returns the tensor-parallel size.
     * @return TP size.
     */
    public int tpSize() {
        return config.tpSize();
    }

    /**
     * Returns the parallel state for a TP rank.
     *
     * @param tpRank tensor-parallel rank.
     * @return per-rank state.
     */
    public ParallelState state(int tpRank) {
        return states[tpRank];
    }

    /**
     * Returns the CUDA device for a TP rank.
     *
     * @param tpRank tensor-parallel rank.
     * @return device for that rank.
     */
    public Device device(int tpRank) {
        return devices[tpRank];
    }

    /**
     * In-place sum all-reduce. Every TP rank's worker thread must call this
     * with its local tensor (same shape/dtype). No-op when {@code tpSize == 1}.
     *
     * @param tpRank calling rank.
     * @param local  local tensor to reduce in place.
     */
    public void allReduceSumInPlace(int tpRank, Tensor local) {
        if (config.tpSize() <= 1) {
            return;
        }
        allReduceSlots[tpRank] = local;
        try {
            allReduceBarrier.await();
        } catch (Exception e) {
            RuntimeException err = allReduceError.getAndSet(null);
            if (err != null) {
                throw err;
            }
            throw new RuntimeException("TP all-reduce barrier failed", e);
        }
        RuntimeException err = allReduceError.getAndSet(null);
        if (err != null) {
            throw err;
        }
    }

    private void runAllReduce() {
        try {
            Native.tpAllReduceSum(allReduceSlots);
        } catch (RuntimeException e) {
            allReduceError.set(e);
        }
    }

    /**
     * Broadcasts {@code locals[root]} to every other slot. Caller must supply
     * the full array (one tensor per rank) from a single coordinating thread,
     * or use per-rank buffers that are already filled.
     *
     * @param locals one tensor per TP rank.
     * @param root   source rank whose tensor is copied to the others.
     */
    public void broadcast(Tensor[] locals, int root) {
        if (config.tpSize() <= 1) {
            return;
        }
        Native.tpBroadcast(locals, root);
    }

    @Override
    public void close() {
        closed = true;
    }

    /**
     * Returns whether {@link #close()} has been called.
     * @return {@code true} if closed.
     */
    public boolean isClosed() {
        return closed;
    }
}
