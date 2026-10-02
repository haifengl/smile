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
        displacedRows = new int[0];
        activeIdentity = true;
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
     * <p>Working slots are the same storage as the first {@code B} home rows, so packing has to be
     * careful in two ways. First, all sources are read before any is written (a sequential
     * home-to-slot copy corrupts the cohort itself when homes are not in ascending order, e.g. once
     * freed rows have been reused by later requests). Second, any live request outside the cohort
     * whose home row lies in {@code [0, B)} is stashed and put back by {@link #scatterActive}, so a
     * forward over one cohort never disturbs another's state (a speculating group and a plain-decode
     * group alternate within one engine tick, for example). When the cohort already occupies rows
     * {@code 0..B-1} in order nothing is copied.
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
        // A previous non-identity activation that was never scattered still has bystander rows
        // parked in the stash: put them back first (its cohort updates stay unwritten, as before).
        restoreDisplaced();
        int b = requestIds.length;
        int[] homes = new int[b];
        BitSet cohort = new BitSet(maxBatchSize);
        boolean identity = true;
        for (int i = 0; i < b; i++) {
            Integer row = requestRows.get(requestIds[i]);
            if (row == null) {
                throw new IllegalArgumentException("Unknown DeltaNet request id: " + requestIds[i]);
            }
            homes[i] = row;
            identity &= row == i;
            if (cohort.get(row)) {
                throw new IllegalArgumentException("duplicate DeltaNet request id in cohort");
            }
            cohort.set(row);
        }
        if (!identity) {
            int k = 0;
            int[] displaced = new int[b];
            for (int r = 0; r < b; r++) {
                if (!freeRows.get(r) && !cohort.get(r)) {
                    displaced[k++] = r;
                }
            }
            if (k > 0) {
                ensureStash(k);
                try (Tensor src = Tensor.of(toLongs(displaced, k)).to(device)) {
                    for (int l = 0; l < numLinearLayers; l++) {
                        stashRows(recurrent[l], stashRecurrent[l], src, k);
                        if (conv[l] != null) {
                            stashRows(conv[l], stashConv[l], src, k);
                        }
                    }
                }
            }
            this.displacedRows = java.util.Arrays.copyOf(displaced, k);
            try (Tensor idx = Tensor.of(toLongs(homes, b)).to(device);
                 var span = Index.slice(0, b)) {
                for (int l = 0; l < numLinearLayers; l++) {
                    packRows(recurrent[l], idx, span);
                    if (conv[l] != null) {
                        packRows(conv[l], idx, span);
                    }
                }
            }
        }
        this.activeIdentity = identity;
        this.activeHomeRows = homes;
        this.boundBatch = b;
    }

    private static long[] toLongs(int[] values, int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = values[i];
        }
        return out;
    }

    /** Gathers rows {@code idx} of {@code t} (a full read before any write) into rows {@code [0, b)}. */
    private void packRows(Tensor t, Tensor idx, Index span) {
        try (Tensor gathered = t.get(idx);
             Tensor dst = t.get(span)) {
            smile.torch.Native.copy_(dst, gathered);
        }
    }

    /** {@code stash[0:k] = t[rows]}. */
    private void stashRows(Tensor t, Tensor stash, Tensor rows, int k) {
        try (Tensor gathered = t.get(rows);
             var span = Index.slice(0, k);
             Tensor dst = stash.get(span)) {
            smile.torch.Native.copy_(dst, gathered);
        }
    }

    /** Parked copies of bystander rows displaced by a non-identity activation. */
    private Tensor[] stashRecurrent;
    private Tensor[] stashConv;
    private int stashCapacity;
    private int[] displacedRows = new int[0];
    private boolean activeIdentity = true;

    private void ensureStash(int rows) {
        if (stashRecurrent != null && stashCapacity >= rows) {
            return;
        }
        closeStash();
        int cap = Math.min(maxBatchSize, Math.max(rows, 4));
        stashRecurrent = new Tensor[numLinearLayers];
        stashConv = new Tensor[numLinearLayers];
        var ropts = new Tensor.Options().device(device).dtype(recurrentDtype).requireGradients(false);
        var copts = new Tensor.Options().device(device).dtype(convDtype).requireGradients(false);
        for (int l = 0; l < numLinearLayers; l++) {
            stashRecurrent[l] = Tensor.zeros(ropts, cap, numVHeads, keyHeadDim, valueHeadDim);
            stashRecurrent[l].detachFromScopes();
            if (convStateLen > 0) {
                stashConv[l] = Tensor.zeros(copts, cap, convDim, convStateLen);
                stashConv[l].detachFromScopes();
            }
        }
        stashCapacity = cap;
    }

    private void closeStash() {
        if (stashRecurrent != null) {
            for (int l = 0; l < numLinearLayers; l++) {
                if (stashRecurrent[l] != null) {
                    stashRecurrent[l].close();
                }
                if (stashConv != null && stashConv[l] != null) {
                    stashConv[l].close();
                }
            }
        }
        stashRecurrent = null;
        stashConv = null;
        stashCapacity = 0;
    }

    /** Puts the stashed bystander rows back at their home rows. */
    private void restoreDisplaced() {
        int k = displacedRows.length;
        if (k == 0) {
            return;
        }
        try (Tensor rows = Tensor.of(toLongs(displacedRows, k)).to(device);
             var span = Index.slice(0, k)) {
            for (int l = 0; l < numLinearLayers; l++) {
                try (Tensor src = stashRecurrent[l].get(span)) {
                    recurrent[l].put_(src, rows);
                }
                if (conv[l] != null && stashConv[l] != null) {
                    try (Tensor src = stashConv[l].get(span)) {
                        conv[l].put_(src, rows);
                    }
                }
            }
        }
        displacedRows = new int[0];
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
        if (activeHomeRows == null || activeHomeRows.length == 0 || activeIdentity) {
            return;
        }
        int b = activeHomeRows.length;
        // Snapshot every working row before writing any home (homes and working rows overlap).
        // Bystander rows displaced by this activation stay stashed until the next activateStep
        // puts them back: restoring them here would overwrite the working rows a follow-up
        // forward on the same activation still needs.
        try (Tensor homes = Tensor.of(toLongs(activeHomeRows, b)).to(device);
             var span = Index.slice(0, b)) {
            for (int l = 0; l < numLinearLayers; l++) {
                scatterRows(recurrent[l], homes, span);
                if (conv[l] != null) {
                    scatterRows(conv[l], homes, span);
                }
            }
        }
        activeIdentity = true; // cohort states are now committed to their homes
    }

    private void scatterRows(Tensor t, Tensor homes, Index span) {
        try (Tensor working = t.get(span);
             Tensor snapshot = working.copy()) {
            t.put_(snapshot, homes);
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
    /**
     * Backing storage for the checkpoints: one {@code [slots, rows, ...]} tensor per linear layer.
     * {@link #speculativeRecurrent}{@code [s][i]} is the contiguous view of slot {@code s}, so the
     * per-slot users (fused verify kernels, save/restore of one slot) are unchanged, while a
     * per-row restore across different slots becomes a single gather per layer.
     */
    private Tensor[] speculativeRecurrentBig;
    private Tensor[] speculativeConvBig;
    private int speculativeSlots;
    /** Inclusive range of slots that have storage (see {@link #ensureSpeculativeCheckpointRange}). */
    private int ckptFirstSlot;
    private int ckptLastSlot = -1;
    /** Row capacity of speculative checkpoint tensors (not {@link #maxBatchSize}). */
    private int speculativeBatchCapacity;

    /**
     * Device bytes of the speculative checkpoint buffers for {@code numSlots} slots of {@code rows}
     * rows each (all linear layers, recurrent plus conv state).
     *
     * @param numSlots checkpoint slots.
     * @param rows     batch rows per slot.
     * @return bytes the buffers occupy on this pool's device.
     */
    public long speculativeCheckpointBytes(int numSlots, int rows) {
        long recurrent = (long) numVHeads * keyHeadDim * valueHeadDim * elementBytes(recurrentDtype);
        long convBytes = convStateLen > 0
                ? (long) convDim * convStateLen * elementBytes(convDtype) : 0L;
        return (long) numSlots * rows * numLinearLayers * (recurrent + convBytes);
    }

    private static int elementBytes(ScalarType t) {
        return switch (t) {
            case Float, Int32 -> 4;
            case Double, Int64 -> 8;
            case Half, BFloat16, Int16 -> 2;
            default -> 1;
        };
    }

    /**
     * Additional device bytes {@link #ensureSpeculativeCheckpoints} would have to allocate for
     * {@code numSlots} x {@code max(1, rows)} (0 when the existing buffers already suffice).
     *
     * @param numSlots checkpoint slots required.
     * @param rows     batch rows required.
     * @return extra bytes needed.
     */
    public long speculativeCheckpointGrowthBytes(int numSlots, int rows) {
        return speculativeCheckpointGrowthBytes(0, numSlots - 1, rows);
    }

    /**
     * Additional device bytes {@link #ensureSpeculativeCheckpointRange} would have to allocate for
     * slots {@code firstSlot..lastSlot} of {@code max(1, rows)} rows (0 when already sufficient).
     *
     * @param firstSlot first slot required.
     * @param lastSlot  last slot required.
     * @param rows      batch rows required.
     * @return extra bytes needed.
     */
    public long speculativeCheckpointGrowthBytes(int firstSlot, int lastSlot, int rows) {
        int r = Math.max(1, rows);
        if (hasSpeculativeRange(firstSlot, lastSlot, r)) {
            return 0L;
        }
        long have = speculativeRecurrent == null ? 0L
                : speculativeCheckpointBytes(ckptLastSlot - ckptFirstSlot + 1, speculativeBatchCapacity);
        return Math.max(0L, speculativeCheckpointBytes(lastSlot - firstSlot + 1, r) - have);
    }

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
        return ensureSpeculativeCheckpointRange(0, numSlots - 1);
    }

    /**
     * Allocates checkpoint slots {@code firstSlot..lastSlot} (inclusive) only, for {@code max(1,
     * boundBatch)} rows. Slot numbers keep their meaning ("state after window position
     * {@code slot-1}", slot 0 = pre-window), but slots outside the range have no storage: a save to
     * one is a no-op and a restore from one is an error. In checkpoint-replay mode only slots
     * {@code 1..n} are ever restored from (slot 0 is never read, and the last slot equals the
     * working state), so allocating just those cuts the memory by {@code 2/(n+2)}.
     *
     * @param firstSlot first slot to store.
     * @param lastSlot  last slot to store.
     * @return {@code true} if this call reallocated (see {@link #ensureSpeculativeCheckpoints}).
     */
    public boolean ensureSpeculativeCheckpointRange(int firstSlot, int lastSlot) {
        if (firstSlot < 0 || lastSlot < firstSlot) {
            throw new IllegalArgumentException("invalid checkpoint slot range " + firstSlot + ".." + lastSlot);
        }
        int rows = Math.max(1, boundBatch);
        if (speculativeRecurrent != null && ckptFirstSlot <= firstSlot && ckptLastSlot >= lastSlot
                && speculativeBatchCapacity >= rows) {
            return false;
        }
        closeSpeculativeCheckpoints();
        int numSlots = lastSlot + 1;
        int stored = lastSlot - firstSlot + 1;
        ckptFirstSlot = firstSlot;
        ckptLastSlot = lastSlot;
        speculativeSlots = numSlots;
        speculativeBatchCapacity = rows;
        speculativeRecurrent = new Tensor[numSlots][numLinearLayers];
        speculativeConv = new Tensor[numSlots][numLinearLayers];
        speculativeRecurrentBig = new Tensor[numLinearLayers];
        speculativeConvBig = new Tensor[numLinearLayers];
        var recurrentOpts = new Tensor.Options()
                .device(device).dtype(recurrentDtype).requireGradients(false);
        var convOpts = new Tensor.Options()
                .device(device).dtype(convDtype).requireGradients(false);
        for (int i = 0; i < numLinearLayers; i++) {
            speculativeRecurrentBig[i] = Tensor.zeros(recurrentOpts, stored, rows, numVHeads,
                    keyHeadDim, valueHeadDim);
            speculativeRecurrentBig[i].detachFromScopes();
            if (convStateLen > 0) {
                speculativeConvBig[i] = Tensor.zeros(convOpts, stored, rows, convDim, convStateLen);
                speculativeConvBig[i].detachFromScopes();
            }
            for (int s = firstSlot; s <= lastSlot; s++) {
                try (var slotIdx = Index.of(s - firstSlot)) {
                    speculativeRecurrent[s][i] = speculativeRecurrentBig[i].get(slotIdx);
                    speculativeRecurrent[s][i].detachFromScopes();
                    if (convStateLen > 0) {
                        speculativeConv[s][i] = speculativeConvBig[i].get(slotIdx);
                        speculativeConv[s][i].detachFromScopes();
                    }
                }
            }
        }
        return true;
    }

    /** Whether checkpoint slots {@code firstSlot..lastSlot} are stored for at least {@code rows} rows. */
    public boolean hasSpeculativeRange(int firstSlot, int lastSlot, int rows) {
        return speculativeRecurrent != null && ckptFirstSlot <= firstSlot && ckptLastSlot >= lastSlot
                && speculativeBatchCapacity >= rows;
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
        // One gather per layer and tensor instead of a copy per (row, layer): row r takes slot
        // slots[r] of the {@code [stored, rows, ...]} backing tensor, i.e. flat row
        // (slot - firstSlot) * rowCapacity + r of its {@code [stored * rows, ...]} view. A row whose
        // slot is negative is left untouched (a fully accepted row's working state is already the
        // end-of-window state, which is why its last slot is never stored).
        long[] flat = new long[b];
        long[] rowIdx = new long[b];
        int n = 0;
        for (int row = 0; row < b; row++) {
            int slot = slots[row];
            if (slot < 0) {
                continue;
            }
            if (slot < ckptFirstSlot || slot > ckptLastSlot) {
                throw new IllegalStateException("speculative checkpoint slot " + slot
                        + " has no storage (stored " + ckptFirstSlot + ".." + ckptLastSlot + ")");
            }
            flat[n] = (long) (slot - ckptFirstSlot) * speculativeBatchCapacity + row;
            rowIdx[n] = row;
            n++;
        }
        if (n == 0) {
            return;
        }
        if (n == b) {
            try (Tensor idxCpu = Tensor.of(flat);
                 Tensor idx = idxCpu.to(device);
                 var span = Index.slice(0, b)) {
                for (int i = 0; i < numLinearLayers; i++) {
                    restoreGathered(speculativeRecurrentBig[i], recurrent[i], idx, span);
                    if (conv[i] != null && speculativeConvBig[i] != null) {
                        restoreGathered(speculativeConvBig[i], conv[i], idx, span);
                    }
                }
            }
            return;
        }
        try (Tensor idxCpu = Tensor.of(java.util.Arrays.copyOf(flat, n));
             Tensor idx = idxCpu.to(device);
             Tensor rowsCpu = Tensor.of(java.util.Arrays.copyOf(rowIdx, n));
             Tensor rows = rowsCpu.to(device)) {
            for (int i = 0; i < numLinearLayers; i++) {
                restoreRows(speculativeRecurrentBig[i], recurrent[i], idx, rows);
                if (conv[i] != null && speculativeConvBig[i] != null) {
                    restoreRows(speculativeConvBig[i], conv[i], idx, rows);
                }
            }
        }
    }

    /** {@code working[rows] = big_flat[idx]} for a subset of rows. */
    private void restoreRows(Tensor big, Tensor working, Tensor idx, Tensor rows) {
        long[] shape = big.shape();
        long[] flatShape = new long[shape.length - 1];
        flatShape[0] = shape[0] * shape[1];
        System.arraycopy(shape, 2, flatShape, 1, shape.length - 2);
        try (Tensor flatView = big.reshape(flatShape);
             Tensor gathered = flatView.get(idx)) {
            working.put_(gathered, rows);
        }
    }

    private void restoreGathered(Tensor big, Tensor working, Tensor idx, Index span) {
        long[] shape = big.shape();
        long[] flatShape = new long[shape.length - 1];
        flatShape[0] = shape[0] * shape[1];
        System.arraycopy(shape, 2, flatShape, 1, shape.length - 2);
        try (Tensor flatView = big.reshape(flatShape);
             Tensor gathered = flatView.get(idx);
             Tensor dst = working.get(span)) {
            smile.torch.Native.copy_(dst, gathered);
        }
    }

    /**
     * Returns checkpoint {@code slot}'s full recurrent tensor for a linear layer
     * ({@code [rows, H, K, V]}, float, contiguous), or {@code null} when absent.
     *
     * @param slot    checkpoint index.
     * @param layerId ordinal among linear-attention layers.
     * @return the checkpoint tensor (owned by the pool; do not close), or {@code null}.
     */
    public Tensor speculativeRecurrentSlot(int slot, int layerId) {
        if (speculativeRecurrent == null || slot < 0 || slot >= speculativeSlots) {
            return null;
        }
        return speculativeRecurrent[slot][layerId];
    }

    /**
     * Returns checkpoint {@code slot}'s full conv tensor for a linear layer
     * ({@code [rows, C, K-1]}), or {@code null} when absent.
     *
     * @param slot    checkpoint index.
     * @param layerId ordinal among linear-attention layers.
     * @return the checkpoint tensor (owned by the pool; do not close), or {@code null}.
     */
    public Tensor speculativeConvSlot(int slot, int layerId) {
        if (speculativeConv == null || slot < 0 || slot >= speculativeSlots) {
            return null;
        }
        return speculativeConv[slot][layerId];
    }

    /** Whether per-step checkpoints for {@code slots} slots and {@code rows} rows are allocated. */
    public boolean hasSpeculativeCheckpoints(int slots, int rows) {
        return hasSpeculativeRange(0, slots - 1, rows);
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
        if (speculativeRecurrent == null || slot < 0) {
            throw new IllegalStateException("speculative checkpoint slot out of range: " + slot);
        }
        if (slot < ckptFirstSlot || slot > ckptLastSlot) {
            return; // no storage for this slot in a lean allocation
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
        if (speculativeRecurrent == null || slot < 0) {
            throw new IllegalStateException("speculative checkpoint slot out of range: " + slot);
        }
        if (slot < ckptFirstSlot || slot > ckptLastSlot) {
            if (save) {
                return; // slot has no storage in a lean allocation: nothing to keep
            }
            throw new IllegalStateException("speculative checkpoint slot " + slot + " has no storage (stored "
                    + ckptFirstSlot + ".." + ckptLastSlot + ")");
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
        if (speculativeRecurrentBig != null) {
            for (Tensor t : speculativeRecurrentBig) {
                if (t != null) {
                    t.close();
                }
            }
            speculativeRecurrentBig = null;
        }
        if (speculativeConvBig != null) {
            for (Tensor t : speculativeConvBig) {
                if (t != null) {
                    t.close();
                }
            }
            speculativeConvBig = null;
        }
        speculativeRecurrent = null;
        speculativeConv = null;
        speculativeSlots = 0;
        speculativeBatchCapacity = 0;
        ckptFirstSlot = 0;
        ckptLastSlot = -1;
    }

    /**
     * Clears the active-request binding after exclusive generate finishes.
     */
    public void unbind() {
        displacedRows = new int[0];
        activeIdentity = true;
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
