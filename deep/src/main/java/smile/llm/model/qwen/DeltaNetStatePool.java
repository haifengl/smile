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
    /**
     * Set only around the primary MTP verify-window forward
     * ({@code Qwen.verifyWindowOnline}). While {@code true}, {@link GatedDeltaNet}
     * runs its per-position loop and retains per-position checkpoints instead
     * of the normal batched {@code S>1} path; decode ({@code S=1}), prefill,
     * and every other call site are unaffected since this defaults to
     * {@code false} everywhere else.
     */
    private boolean verifyWindowActive;
    /** requestId → home row. */
    private final Map<Integer, Integer> requestRows = new HashMap<>();
    private final BitSet freeRows;
    /** Compact working rows for the current {@link #activateStep}. */
    private int[] activeHomeRows = new int[0];

    /**
     * Constructor using the same dtype for recurrent and conv buffers (tests).
     *
     * @param numLinearLayers number of linear-attention layers.
     * @param numVHeads       number of value heads.
     * @param keyHeadDim      key head dimension.
     * @param valueHeadDim    value head dimension.
     * @param convDim         causal conv channel dimension.
     * @param convKernel      causal conv kernel size.
     * @param maxBatchSize    maximum concurrent home rows.
     * @param device          storage device.
     * @param dtype           dtype for both recurrent and conv buffers.
     */
    public DeltaNetStatePool(int numLinearLayers, int numVHeads, int keyHeadDim, int valueHeadDim,
                             int convDim, int convKernel, int maxBatchSize,
                             Device device, ScalarType dtype) {
        this(numLinearLayers, numVHeads, keyHeadDim, valueHeadDim, convDim, convKernel,
                maxBatchSize, device, dtype, dtype);
    }

    /**
     * Constructor with separate recurrent / conv dtypes.
     *
     * @param numLinearLayers number of linear-attention layers.
     * @param numVHeads       number of value heads.
     * @param keyHeadDim      key head dimension.
     * @param valueHeadDim    value head dimension.
     * @param convDim         causal conv channel dimension.
     * @param convKernel      causal conv kernel size.
     * @param maxBatchSize    maximum concurrent home rows.
     * @param device          storage device.
     * @param recurrentDtype  dtype for recurrent state buffers.
     * @param convDtype       dtype for conv state buffers.
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
     * Sets whether the primary MTP verify-window forward is in flight.
     *
     * @param active {@code true} around the verify-window forward only.
     */
    public void setVerifyWindowActive(boolean active) {
        this.verifyWindowActive = active;
    }

    /**
     * Returns whether the primary MTP verify-window forward is in flight.
     *
     * @return {@code true} when {@link GatedDeltaNet} should run its
     *         per-position verify loop instead of the batched {@code S>1} path.
     */
    public boolean verifyWindowActive() {
        return verifyWindowActive;
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

    /** Lazily allocated backups for {@link #withPreservedActive}. */
    private Tensor[] recurrentBackup;
    private Tensor[] convBackup;

    /**
     * Runs {@code action} without permanently mutating the active DeltaNet working rows.
     *
     * <p>Used for CUDA graph prefetch forwards that share the current
     * {@link #activateStep} packing.
     *
     * @param action work to run while active rows are preserved.
     */
    public void withPreservedActive(Runnable action) {
        int b = boundBatch;
        if (b <= 0) {
            action.run();
            return;
        }
        ensureActiveBackup();
        try (var span = Index.slice(0, b)) {
            for (int i = 0; i < numLinearLayers; i++) {
                try (Tensor src = recurrent[i].get(span);
                     Tensor dst = recurrentBackup[i].get(span)) {
                    smile.torch.Native.copy_(dst, src);
                }
                if (conv[i] != null && convBackup[i] != null) {
                    try (Tensor src = conv[i].get(span);
                         Tensor dst = convBackup[i].get(span)) {
                        smile.torch.Native.copy_(dst, src);
                    }
                }
            }
        }
        try {
            action.run();
        } finally {
            try (var span = Index.slice(0, b)) {
                for (int i = 0; i < numLinearLayers; i++) {
                    try (Tensor src = recurrentBackup[i].get(span);
                         Tensor dst = recurrent[i].get(span)) {
                        smile.torch.Native.copy_(dst, src);
                    }
                    if (conv[i] != null && convBackup[i] != null) {
                        try (Tensor src = convBackup[i].get(span);
                             Tensor dst = conv[i].get(span)) {
                            smile.torch.Native.copy_(dst, src);
                        }
                    }
                }
            }
        }
    }

    private void ensureActiveBackup() {
        if (recurrentBackup != null) {
            return;
        }
        recurrentBackup = new Tensor[numLinearLayers];
        convBackup = new Tensor[numLinearLayers];
        var recurrentOpts = new Tensor.Options()
                .device(device).dtype(recurrentDtype).requireGradients(false);
        var convOpts = new Tensor.Options()
                .device(device).dtype(convDtype).requireGradients(false);
        for (int i = 0; i < numLinearLayers; i++) {
            recurrentBackup[i] = Tensor.zeros(recurrentOpts, maxBatchSize, numVHeads,
                    keyHeadDim, valueHeadDim);
            recurrentBackup[i].detachFromScopes();
            if (convStateLen > 0) {
                convBackup[i] = Tensor.zeros(convOpts, maxBatchSize, convDim, convStateLen);
                convBackup[i].detachFromScopes();
            }
        }
    }

    /** Speculative-verify checkpoints: {@code [slot][layer]} over active working rows. */
    private Tensor[][] speculativeRecurrent;
    private Tensor[][] speculativeConv;
    private int speculativeSlots;
    /** Row capacity of speculative checkpoint tensors (not {@link #maxBatchSize}). */
    private int speculativeBatchCapacity;

    /**
     * Ensures {@code numSlots} mid-verify checkpoint buffers sized for the
     * currently activated batch ({@link #boundBatch}), not the full pool
     * {@code maxBatchSize}. Full-pool sizing OOMs under serving configs.
     *
     * <p>Growth-only: never reallocates smaller for a later, smaller
     * {@code boundBatch} (the early-return below already guarantees this),
     * since a shrink would be pointless — the real reason growth matters is
     * that any verify CUDA graph already captured against the old, smaller
     * buffers becomes invalid the moment this reallocates (replay would then
     * write into freed memory) — see the {@code true}-return contract below.
     *
     * @param numSlots checkpoint slots ({@code N+1} for {@code N} draft tokens).
     * @return {@code true} if this call reallocated (the caller must then
     *         invalidate any captured verify CUDA graph via
     *         {@link QwenModel#invalidateVerifyCudaGraphs()} before it can be
     *         replayed again).
     */
    public boolean ensureSpeculativeCheckpoints(int numSlots) {
        if (numSlots < 1) {
            throw new IllegalArgumentException("numSlots must be >= 1");
        }
        int rows = Math.max(1, boundBatch);
        if (speculativeRecurrent != null
                && speculativeSlots >= numSlots
                && speculativeBatchCapacity >= rows) {
            return false;
        }
        closeSpeculativeCheckpoints();
        speculativeSlots = numSlots;
        speculativeBatchCapacity = rows;
        speculativeRecurrent = new Tensor[numSlots][numLinearLayers];
        speculativeConv = new Tensor[numSlots][numLinearLayers];
        var recurrentOpts = new Tensor.Options()
                .device(device).dtype(recurrentDtype).requireGradients(false);
        var convOpts = new Tensor.Options()
                .device(device).dtype(convDtype).requireGradients(false);
        for (int s = 0; s < numSlots; s++) {
            for (int i = 0; i < numLinearLayers; i++) {
                speculativeRecurrent[s][i] = Tensor.zeros(recurrentOpts, rows, numVHeads,
                        keyHeadDim, valueHeadDim);
                speculativeRecurrent[s][i].detachFromScopes();
                if (convStateLen > 0) {
                    speculativeConv[s][i] = Tensor.zeros(convOpts, rows, convDim, convStateLen);
                    speculativeConv[s][i].detachFromScopes();
                }
            }
        }
        return true;
    }

    /**
     * Releases speculative checkpoint buffers (call after a verify round when
     * GPU headroom is tight).
     */
    public void releaseSpeculativeCheckpoints() {
        closeSpeculativeCheckpoints();
    }

    /**
     * Copies active working rows {@code [0, boundBatch)} into checkpoint {@code slot}.
     *
     * @param slot checkpoint index ({@code 0 .. numSlots-1}).
     */
    public void saveCheckpoint(int slot) {
        copyActiveToSlot(slot, true);
    }

    /**
     * Restores active working rows from checkpoint {@code slot}.
     *
     * @param slot checkpoint index ({@code 0 .. numSlots-1}).
     */
    public void restoreCheckpoint(int slot) {
        copyActiveToSlot(slot, false);
    }

    /**
     * Restores each active row {@code i} from its own checkpoint slot
     * {@code slots[i]} — the batched-cohort counterpart of
     * {@link #restoreCheckpoint}, which restores every active row from the
     * same slot. Needed when concurrent requests verified together in one
     * round accept different numbers of draft tokens.
     *
     * @param slots checkpoint slot per active row (length {@code boundBatch}).
     */
    public void restoreCheckpointPerRow(int[] slots) {
        if (speculativeRecurrent == null) {
            throw new IllegalStateException("no speculative checkpoints allocated");
        }
        int b = boundBatch;
        if (b <= 0) {
            return;
        }
        if (slots.length != b) {
            throw new IllegalArgumentException("slots length (" + slots.length
                    + ") must equal boundBatch (" + b + ")");
        }
        for (int row = 0; row < b; row++) {
            int slot = slots[row];
            if (slot < 0 || slot >= speculativeSlots) {
                throw new IllegalStateException("speculative checkpoint slot out of range: " + slot);
            }
            try (var r = Index.of(row)) {
                for (int i = 0; i < numLinearLayers; i++) {
                    try (Tensor src = speculativeRecurrent[slot][i].get(r);
                         Tensor dst = recurrent[i].get(r)) {
                        smile.torch.Native.copy_(dst, src);
                    }
                    if (conv[i] != null && speculativeConv[slot][i] != null) {
                        try (Tensor src = speculativeConv[slot][i].get(r);
                             Tensor dst = conv[i].get(r)) {
                            smile.torch.Native.copy_(dst, src);
                        }
                    }
                }
            }
        }
    }

    /**
     * Copies active working rows {@code [0, boundBatch)} for a single
     * linear-attention layer into checkpoint {@code slot}.
     *
     * <p>Used by the verify-window per-position loop: {@code QwenModel}'s
     * layer stack is depth-sequential, so each layer's own forward (including
     * its internal per-position loop) finishes before the next layer starts.
     * A whole-pool save mid-loop would capture sibling layers' stale state;
     * this saves only the layer that just finished its own position {@code t}.
     *
     * @param slot    checkpoint index ({@code 0 .. numSlots-1}).
     * @param layerId ordinal among linear-attention layers.
     */
    public void saveCheckpointForLayer(int slot, int layerId) {
        if (speculativeRecurrent == null || slot < 0 || slot >= speculativeSlots) {
            throw new IllegalStateException("speculative checkpoint slot out of range: " + slot);
        }
        int b = boundBatch;
        if (b <= 0) {
            return;
        }
        if (b > speculativeBatchCapacity) {
            throw new IllegalStateException(
                    "active batch " + b + " exceeds speculative checkpoint capacity "
                            + speculativeBatchCapacity + "; call ensureSpeculativeCheckpoints after activateStep");
        }
        try (var span = Index.slice(0, b)) {
            try (Tensor src = recurrent[layerId].get(span);
                 Tensor dst = speculativeRecurrent[slot][layerId].get(span)) {
                smile.torch.Native.copy_(dst, src);
            }
            if (conv[layerId] != null && speculativeConv[slot][layerId] != null) {
                try (Tensor src = conv[layerId].get(span);
                     Tensor dst = speculativeConv[slot][layerId].get(span)) {
                    smile.torch.Native.copy_(dst, src);
                }
            }
        }
    }

    private void copyActiveToSlot(int slot, boolean save) {
        if (speculativeRecurrent == null || slot < 0 || slot >= speculativeSlots) {
            throw new IllegalStateException("speculative checkpoint slot out of range: " + slot);
        }
        int b = boundBatch;
        if (b <= 0) {
            return;
        }
        if (b > speculativeBatchCapacity) {
            throw new IllegalStateException(
                    "active batch " + b + " exceeds speculative checkpoint capacity "
                            + speculativeBatchCapacity + "; call ensureSpeculativeCheckpoints after activateStep");
        }
        try (var span = Index.slice(0, b)) {
            for (int i = 0; i < numLinearLayers; i++) {
                if (save) {
                    try (Tensor src = recurrent[i].get(span);
                         Tensor dst = speculativeRecurrent[slot][i].get(span)) {
                        smile.torch.Native.copy_(dst, src);
                    }
                    if (conv[i] != null && speculativeConv[slot][i] != null) {
                        try (Tensor src = conv[i].get(span);
                             Tensor dst = speculativeConv[slot][i].get(span)) {
                            smile.torch.Native.copy_(dst, src);
                        }
                    }
                } else {
                    try (Tensor src = speculativeRecurrent[slot][i].get(span);
                         Tensor dst = recurrent[i].get(span)) {
                        smile.torch.Native.copy_(dst, src);
                    }
                    if (conv[i] != null && speculativeConv[slot][i] != null) {
                        try (Tensor src = speculativeConv[slot][i].get(span);
                             Tensor dst = conv[i].get(span)) {
                            smile.torch.Native.copy_(dst, src);
                        }
                    }
                }
            }
        }
    }

    private void closeSpeculativeCheckpoints() {
        if (speculativeRecurrent == null) {
            return;
        }
        for (int s = 0; s < speculativeSlots; s++) {
            for (int i = 0; i < numLinearLayers; i++) {
                if (speculativeRecurrent[s][i] != null) {
                    speculativeRecurrent[s][i].close();
                }
                if (speculativeConv != null && speculativeConv[s][i] != null) {
                    speculativeConv[s][i].close();
                }
            }
        }
        speculativeRecurrent = null;
        speculativeConv = null;
        speculativeSlots = 0;
        speculativeBatchCapacity = 0;
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

    /**
     * Returns the bound batch size.
     *
     * @return bound batch size, or {@code 0} if unbound.
     */
    public int boundBatch() {
        return boundBatch;
    }

    /**
     * Returns the number of multi-request bindings.
     *
     * @return number of multi-request bindings.
     */
    public int boundRequestCount() {
        return requestRows.size();
    }

    /**
     * Returns the recurrent state buffer for a linear-attention layer.
     *
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
     * Returns the conv state buffer for a linear-attention layer.
     *
     * @param linearLayerId ordinal among linear-attention layers.
     * @return conv state {@code [maxBatch, C, K-1]}, or {@code null} if unused.
     */
    public Tensor conv(int linearLayerId) {
        return conv[linearLayerId];
    }

    /**
     * Returns the linear-attention layer count.
     *
     * @return linear layer count.
     */
    public int numLinearLayers() {
        return numLinearLayers;
    }

    @Override
    public void close() {
        requestRows.clear();
        freeRows.clear();
        closeSpeculativeCheckpoints();
        if (recurrentBackup != null) {
            for (int i = 0; i < numLinearLayers; i++) {
                if (recurrentBackup[i] != null) {
                    recurrentBackup[i].close();
                }
                if (convBackup != null && convBackup[i] != null) {
                    convBackup[i].close();
                }
            }
            recurrentBackup = null;
            convBackup = null;
        }
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
