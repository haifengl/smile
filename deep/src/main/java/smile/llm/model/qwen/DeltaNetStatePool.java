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
package smile.llm.model.qwen;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

/**
 * Per-request DeltaNet recurrent and causal-conv state for linear-attention layers.
 *
 * <p>Recurrent state is typically float32 (fused CUDA kernel / stable in-place updates).
 * Conv left-context must match the compute dtype (bf16/fp16) so decode
 * {@code concat(convState, hidden)} does not promote activations to float and
 * break later bf16 linear layers.
 *
 * <p>Multi-request continuous batching assigns each {@code requestId} a stable
 * home row. {@link #activateStep} packs those rows into {@code [0, B)} for the
 * mixer; {@link #scatterActive} writes them back after the forward.
 *
 * @author Haifeng Li
 */
public class DeltaNetStatePool implements AutoCloseable {
    final int numLinearLayers;
    final int numVHeads;
    final int keyHeadDim;
    final int valueHeadDim;
    final int convDim;
    final int convStateLen;
    final int maxBatchSize;
    final Device device;
    final ScalarType recurrentDtype;
    final ScalarType convDtype;

    /** Recurrent states {@code [B, V, Kdim, Vdim]} per linear layer. */
    final Tensor[] recurrent;
    /** Conv left-context {@code [B, C, K-1]} per linear layer. */
    final Tensor[] conv;

    private int boundBatch;
    /** requestId → home row. */
    private final Map<Integer, Integer> requestRows = new HashMap<>();
    private final BitSet freeRows;
    /** Compact working rows for the current {@link #activateStep}. */
    private int[] activeHomeRows = new int[0];

    /**
     * Constructor using the same dtype for recurrent and conv buffers (tests).
     */
    public DeltaNetStatePool(int numLinearLayers, int numVHeads, int keyHeadDim, int valueHeadDim,
                             int convDim, int convKernel, int maxBatchSize,
                             Device device, ScalarType dtype) {
        this(numLinearLayers, numVHeads, keyHeadDim, valueHeadDim, convDim, convKernel,
                maxBatchSize, device, dtype, dtype);
    }

    /**
     * Constructor with separate recurrent / conv dtypes.
     */
    public DeltaNetStatePool(int numLinearLayers, int numVHeads, int keyHeadDim, int valueHeadDim,
                             int convDim, int convKernel, int maxBatchSize,
                             Device device, ScalarType recurrentDtype, ScalarType convDtype) {
        if (numLinearLayers < 1) throw new IllegalArgumentException("numLinearLayers must be >= 1");
        if (convKernel < 1) throw new IllegalArgumentException("convKernel must be >= 1");
        this.numLinearLayers = numLinearLayers;
        this.numVHeads = numVHeads;
        this.keyHeadDim = keyHeadDim;
        this.valueHeadDim = valueHeadDim;
        this.convDim = convDim;
        this.convStateLen = Math.max(0, convKernel - 1);
        this.maxBatchSize = maxBatchSize;
        this.device = device;
        this.recurrentDtype = recurrentDtype;
        this.convDtype = convDtype;
        this.recurrent = new Tensor[numLinearLayers];
        this.conv = new Tensor[numLinearLayers];
        this.freeRows = new BitSet(maxBatchSize);
        freeRows.set(0, maxBatchSize);
        var recurrentOpts = new Tensor.Options()
                .device(device).dtype(recurrentDtype).requireGradients(false);
        var convOpts = new Tensor.Options()
                .device(device).dtype(convDtype).requireGradients(false);
        for (int i = 0; i < numLinearLayers; i++) {
            recurrent[i] = Tensor.zeros(recurrentOpts, maxBatchSize, numVHeads, keyHeadDim, valueHeadDim);
            recurrent[i].detachFromScopes();
            if (convStateLen > 0) {
                conv[i] = Tensor.zeros(convOpts, maxBatchSize, convDim, convStateLen);
                conv[i].detachFromScopes();
            } else {
                conv[i] = null;
            }
        }
        this.boundBatch = 0;
    }

    /**
     * Zeros all states and records the active batch size (legacy exclusive generate).
     *
     * @param batchSize active batch size.
     */
    public void reset(int batchSize) {
        if (batchSize < 1 || batchSize > maxBatchSize) {
            throw new IllegalArgumentException("batchSize out of range: " + batchSize);
        }
        this.boundBatch = batchSize;
        this.activeHomeRows = new int[batchSize];
        for (int i = 0; i < batchSize; i++) {
            activeHomeRows[i] = i;
        }
        for (int i = 0; i < numLinearLayers; i++) {
            recurrent[i].fill_(0.0);
            if (conv[i] != null) {
                conv[i].fill_(0.0);
            }
        }
    }

    /**
     * Binds a stable home row for {@code requestId} and zeros it.
     *
     * @param requestId id aligned with {@link smile.llm.cache.KvCachePool#bindRequest}.
     * @return home row index.
     */
    public int bindRequest(int requestId) {
        if (requestRows.containsKey(requestId)) {
            return requestRows.get(requestId);
        }
        int row = freeRows.nextSetBit(0);
        if (row < 0 || row >= maxBatchSize) {
            throw new IllegalStateException("DeltaNetStatePool exhausted (maxBatchSize=" + maxBatchSize + ")");
        }
        freeRows.clear(row);
        requestRows.put(requestId, row);
        zeroRow(row);
        return row;
    }

    /**
     * Releases the home row for {@code requestId}.
     *
     * @param requestId previously bound id.
     */
    public void unbindRequest(int requestId) {
        Integer row = requestRows.remove(requestId);
        if (row == null) {
            return;
        }
        zeroRow(row);
        freeRows.set(row);
        if (requestRows.isEmpty()) {
            boundBatch = 0;
            activeHomeRows = new int[0];
        }
    }

    /**
     * Packs bound request rows into working slots {@code [0, B)} for a forward.
     *
     * @param requestIds bound request ids (order = batch).
     */
    public void activateStep(int... requestIds) {
        if (requestIds == null || requestIds.length == 0) {
            throw new IllegalArgumentException("requestIds must be non-empty");
        }
        if (requestIds.length > maxBatchSize) {
            throw new IllegalArgumentException("activate batch exceeds maxBatchSize");
        }
        int[] homes = new int[requestIds.length];
        for (int i = 0; i < requestIds.length; i++) {
            Integer row = requestRows.get(requestIds[i]);
            if (row == null) {
                throw new IllegalArgumentException("Unknown DeltaNet request id: " + requestIds[i]);
            }
            homes[i] = row;
        }
        // Gather home → working [0, B).
        for (int i = 0; i < homes.length; i++) {
            if (homes[i] != i) {
                copyRow(homes[i], i);
            }
        }
        this.activeHomeRows = homes;
        this.boundBatch = homes.length;
    }

    /**
     * Writes working slots {@code [0, B)} back to each request's home row.
     * Call after every forward that used {@link #activateStep}.
     */
    public void scatterActive() {
        if (activeHomeRows == null || activeHomeRows.length == 0) {
            return;
        }
        for (int i = 0; i < activeHomeRows.length; i++) {
            int home = activeHomeRows[i];
            if (home != i) {
                copyRow(i, home);
            }
        }
    }

    private void zeroRow(int row) {
        try (var r = Index.of(row)) {
            for (int i = 0; i < numLinearLayers; i++) {
                try (Tensor view = recurrent[i].get(r)) {
                    view.fill_(0.0);
                }
                if (conv[i] != null) {
                    try (Tensor view = conv[i].get(r)) {
                        view.fill_(0.0);
                    }
                }
            }
        }
    }

    private void copyRow(int from, int to) {
        if (from == to) {
            return;
        }
        try (var src = Index.of(from);
             var dst = Index.of(to)) {
            for (int i = 0; i < numLinearLayers; i++) {
                try (Tensor s = recurrent[i].get(src)) {
                    recurrent[i].put_(s, dst);
                }
                if (conv[i] != null) {
                    try (Tensor s = conv[i].get(src)) {
                        conv[i].put_(s, dst);
                    }
                }
            }
        }
    }

    /**
     * Clears the active-request binding after exclusive generate finishes.
     */
    public void unbind() {
        this.boundBatch = 0;
        this.activeHomeRows = new int[0];
        requestRows.clear();
        freeRows.clear();
        freeRows.set(0, maxBatchSize);
    }

    /** @return bound batch size, or {@code 0} if unbound. */
    public int boundBatch() {
        return boundBatch;
    }

    /** @return number of multi-request bindings. */
    public int boundRequestCount() {
        return requestRows.size();
    }

    /**
     * @param linearLayerId ordinal among linear-attention layers.
     * @return recurrent state {@code [maxBatch, V, Kdim, Vdim]} (first {@link #boundBatch} rows active).
     */
    public Tensor recurrent(int linearLayerId) {
        return recurrent[linearLayerId];
    }

    /**
     * Recurrent rows packed by {@link #activateStep} into {@code [0, boundBatch)}.
     *
     * <p>Mixer forwards must use this (not {@link #recurrent}) so batch matmul
     * sees {@code state.shape()[0] == query.shape()[0]}.
     *
     * @param linearLayerId ordinal among linear-attention layers.
     * @return view {@code [boundBatch, V, Kdim, Vdim]} into the pool buffer.
     */
    public Tensor activeRecurrent(int linearLayerId) {
        if (boundBatch <= 0) {
            throw new IllegalStateException("DeltaNetStatePool not activated");
        }
        Tensor full = recurrent[linearLayerId];
        long rows = full.shape()[0];
        if (boundBatch == rows) {
            return full;
        }
        try (var span = Index.slice(0, boundBatch)) {
            Tensor active = full.get(span);
            active.detachFromScopes();
            return active;
        }
    }

    /**
     * Conv rows packed by {@link #activateStep} into {@code [0, boundBatch)}.
     *
     * @param linearLayerId ordinal among linear-attention layers.
     * @return view {@code [boundBatch, C, K-1]}, or {@code null} if unused.
     */
    public Tensor activeConv(int linearLayerId) {
        if (conv[linearLayerId] == null) {
            return null;
        }
        if (boundBatch <= 0) {
            throw new IllegalStateException("DeltaNetStatePool not activated");
        }
        Tensor full = conv[linearLayerId];
        long rows = full.shape()[0];
        if (boundBatch == rows) {
            return full;
        }
        try (var span = Index.slice(0, boundBatch)) {
            Tensor active = full.get(span);
            active.detachFromScopes();
            return active;
        }
    }

    /**
     * @param linearLayerId ordinal among linear-attention layers.
     * @return conv state {@code [maxBatch, C, K-1]}, or {@code null} if unused.
     */
    public Tensor conv(int linearLayerId) {
        return conv[linearLayerId];
    }

    /** @return linear layer count. */
    public int numLinearLayers() {
        return numLinearLayers;
    }

    @Override
    public void close() {
        requestRows.clear();
        freeRows.clear();
        for (int i = 0; i < numLinearLayers; i++) {
            if (recurrent[i] != null) {
                recurrent[i].close();
                recurrent[i] = null;
            }
            if (conv[i] != null) {
                conv[i].close();
                conv[i] = null;
            }
        }
    }
}
