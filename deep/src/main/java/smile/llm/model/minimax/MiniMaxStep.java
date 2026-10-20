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
package smile.llm.model.minimax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCachePool;

/**
 * Device-side metadata shared by every layer of one forward pass (slot indices, position grids, mask constants).
 * Each layer used to rebuild these on the host and upload them with a blocking copy; building them once per step
 * removes that host work from the per-layer path. The step is bound to the calling thread (one model replica per
 * thread) between {@link #begin} and {@link #close}.
 *
 * @author Haifeng Li
 */
final class MiniMaxStep implements AutoCloseable {
    private static final ThreadLocal<MiniMaxStep> CURRENT = new ThreadLocal<>();

    /** Block-scoring tensors for one {@code (seqlen, length, groups)} shape. */
    record Sparse(Tensor queryPos, Tensor visibleBlocks, Tensor localStart, Tensor blockIdx, Tensor blockGrid) { }

    private final int[] positions;
    private final MiniMaxStep previous;
    private final List<Tensor> owned = new ArrayList<>();
    private final Map<String, Object> cache = new HashMap<>();

    private MiniMaxStep(int[] positions, MiniMaxStep previous) {
        this.positions = positions.clone();
        this.previous = previous;
    }

    /**
     * Starts the metadata scope of a forward pass on this thread.
     *
     * @param positions KV write position per row.
     * @return the step; close it when the pass ends.
     */
    static MiniMaxStep begin(int[] positions) {
        var step = new MiniMaxStep(positions, CURRENT.get());
        CURRENT.set(step);
        return step;
    }

    /** The step of the pass running on this thread whose positions equal {@code positions}, or {@code null}. */
    static MiniMaxStep current(int[] positions) {
        var step = CURRENT.get();
        return step != null && Arrays.equals(step.positions, positions) ? step : null;
    }

    @Override
    public void close() {
        for (Tensor t : owned) {
            t.close();
        }
        owned.clear();
        cache.clear();
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /** Keeps {@code t} alive past the per-layer scope that is current while it is built. */
    private Tensor own(Tensor t) {
        t.detachFromScopes();
        owned.add(t);
        return t;
    }

    @SuppressWarnings("unchecked")
    private <T> T memo(String key, Supplier<T> build) {
        Object v = cache.get(key);
        if (v == null) {
            v = build.get();
            cache.put(key, v);
        }
        return (T) v;
    }

    /** Float scalar {@code value} on {@code device}. */
    Tensor scalar(Device device, ScalarType dtype, double value) {
        return memo("scalar:" + device + ':' + dtype + ':' + value, () -> {
            var opts = new Tensor.Options().device(device).dtype(dtype).requireGradients(false);
            return own(Tensor.zeros(opts, 1).fill_(value));
        });
    }

    /** Query positions as an int32 tensor on the device, {@code [batch]}. */
    Tensor positionsDevice(Device device) {
        return memo("positions:" + device, () -> own(Tensor.of(positions, positions.length).to(device)));
    }

    /** Slots of positions {@code [0, length)} of every bound row, {@code [batch * length]}. */
    Tensor readSlots(KvCachePool pool, int length) {
        return memo("read:" + length, () -> own(Tensor.of(pool.slotIndices(length)).to(pool.device())));
    }

    /**
     * Slots of the positions written this step, {@code [batch * seqlen]}. Slots are reserved by the first K/V
     * write of the pass, so this returns {@code null} until that write happened.
     */
    Tensor writeSlots(KvCachePool pool, int seqlen) {
        String key = "write:" + seqlen;
        Object v = cache.get(key);
        if (v == null) {
            try {
                v = own(Tensor.of(pool.slotIndices(positions, seqlen)).to(pool.device()));
            } catch (RuntimeException e) {
                return null;
            }
            cache.put(key, v);
        }
        return (Tensor) v;
    }

    /** Decode attention mask {@code [B, 1, 1, length]}, shared by every dense layer of the step. */
    Tensor decodeMask(Device device, ScalarType dtype, int length) {
        return memo("decode-mask:" + device + ':' + dtype + ':' + length, () -> {
            float[] data = new float[positions.length * length];
            for (int b = 0; b < positions.length; b++) {
                for (int j = positions[b] + 1; j < length; j++) {
                    data[b * length + j] = Float.NEGATIVE_INFINITY;
                }
            }
            return own(Tensor.of(data, positions.length, 1, 1, length).to(device).to(dtype));
        });
    }

    /** Position grids for MSA block scoring over {@code length} cached keys. */
    Sparse sparse(Device device, int seqlen, int length, int groups, int blockSize, int localBlocks) {
        String key = "sparse:" + device + ':' + seqlen + ':' + length + ':' + groups;
        return memo(key, () -> {
            int batch = positions.length;
            int numBlocks = (length + blockSize - 1) / blockSize;
            long[] qpos = new long[batch * seqlen];
            long[] valid = new long[batch * seqlen];
            long[] localStart = new long[batch * seqlen];
            for (int b = 0; b < batch; b++) {
                for (int t = 0; t < seqlen; t++) {
                    int p = positions[b] + t;
                    int visible = p / blockSize + 1;
                    qpos[b * seqlen + t] = p;
                    valid[b * seqlen + t] = visible;
                    localStart[b * seqlen + t] = Math.max(0, visible - localBlocks);
                }
            }
            long[] blockOf = new long[length];
            for (int j = 0; j < length; j++) {
                blockOf[j] = j / blockSize;
            }
            long[] grid = new long[numBlocks];
            for (int i = 0; i < numBlocks; i++) {
                grid[i] = i;
            }
            Tensor qp = own(Tensor.of(qpos, batch, 1, seqlen, 1).to(device));
            Tensor vis = own(Tensor.of(valid, batch, 1, seqlen, 1).to(device));
            Tensor loc = own(Tensor.of(localStart, batch, 1, seqlen, 1).to(device));
            Tensor idx = own(Tensor.of(blockOf, 1, 1, 1, length).to(device)
                    .expand(batch, groups, seqlen, length).contiguous());
            Tensor blk = own(Tensor.of(grid, 1, 1, 1, numBlocks).to(device));
            return new Sparse(qp, vis, loc, idx, blk);
        });
    }

    /** Key position grid {@code [1, 1, 1, length]}. */
    Tensor keyPos(Device device, int length) {
        return memo("kpos:" + device + ':' + length, () -> {
            long[] kpos = new long[length];
            for (int j = 0; j < length; j++) {
                kpos[j] = j;
            }
            return own(Tensor.of(kpos, 1, 1, 1, length).to(device));
        });
    }
}
