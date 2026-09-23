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

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;

/**
 * Per-request MTP draft anchor (post-final-norm hidden feeding the MTP
 * head's first draft step), keyed by request id.
 *
 * <p>{@code QwenModel.lastPreNormHidden} is a single field shared by every
 * forward (decode/prefill/verify), for whichever batch of requests that
 * forward just processed — safe only because at most one request was ever
 * mid-speculative-round at a time. Real multi-request batching needs each
 * request's anchor kept durably in its own row, scattered here immediately
 * after the forward that produced it (see {@code Qwen.scatterMtpAnchor}),
 * so a later forward for a different batch can never clobber it.
 *
 * @author Haifeng Li
 */
public class MtpAnchorPool implements AutoCloseable {
    private final int maxBatchSize;
    private final int dim;

    /** Lazily allocated once the anchor's dtype/device is known from a real forward. */
    private Tensor rows;

    private final Map<Integer, Integer> requestRows = new HashMap<>();
    private final BitSet freeRows;

    /**
     * Constructor.
     *
     * @param maxBatchSize maximum concurrently bound requests.
     * @param dim          hidden dimension.
     */
    public MtpAnchorPool(int maxBatchSize, int dim) {
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException("maxBatchSize must be >= 1");
        }
        this.maxBatchSize = maxBatchSize;
        this.dim = dim;
        this.freeRows = new BitSet(maxBatchSize);
        freeRows.set(0, maxBatchSize);
    }

    /**
     * Binds a stable home row for {@code requestId}.
     *
     * @param requestId id aligned with {@link DeltaNetStatePool#bindRequest}.
     * @return home row index.
     */
    public int bindRequest(int requestId) {
        Integer existing = requestRows.get(requestId);
        if (existing != null) {
            return existing;
        }
        int row = freeRows.nextSetBit(0);
        if (row < 0 || row >= maxBatchSize) {
            throw new IllegalStateException("MtpAnchorPool exhausted (maxBatchSize=" + maxBatchSize + ")");
        }
        freeRows.clear(row);
        requestRows.put(requestId, row);
        if (rows != null) {
            zeroRow(row);
        }
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
        if (rows != null) {
            zeroRow(row);
        }
        freeRows.set(row);
    }

    private void zeroRow(int row) {
        try (var r = Index.of(row); Tensor view = rows.get(r)) {
            view.fill_(0.0);
        }
    }

    /**
     * Writes {@code row}'s content into {@code requestId}'s durable anchor.
     * Allocates the pool's backing tensor on the first call ever made on
     * this pool (dtype/device taken from {@code row}); the anchor dimension
     * itself is a fixed model hyperparameter, so no later reallocation is
     * expected.
     *
     * @param requestId bound request id.
     * @param row       anchor row {@code [D]} or {@code [1, D]}.
     */
    public void setRow(int requestId, Tensor row) {
        Integer home = requestRows.get(requestId);
        if (home == null) {
            throw new IllegalArgumentException("Unknown MTP anchor request id: " + requestId);
        }
        Tensor flat = row.dim() == 2 ? row.reshape(dim) : row;
        if (rows == null) {
            rows = Tensor.zeros(
                    new Tensor.Options().device(flat.device()).dtype(flat.dtype()).requireGradients(false),
                    maxBatchSize, dim);
            rows.detachFromScopes();
        }
        try (var r = Index.of(home)) {
            Tensor dest = rows.get(r);
            Tensor src = flat.dtype() == rows.dtype() ? flat : flat.to(rows.dtype());
            smile.torch.Native.copy_(dest, src);
            dest.close();
            if (src != flat) {
                src.close();
            }
        }
        if (flat != row) {
            flat.close();
        }
    }

    /**
     * Returns whether {@code requestId} has a written anchor row yet.
     *
     * @param requestId bound request id.
     * @return {@code true} once {@link #setRow} has been called for this id.
     */
    public boolean hasRow(int requestId) {
        return rows != null && requestRows.containsKey(requestId);
    }

    /**
     * Returns a fresh {@code [D]} copy of {@code requestId}'s anchor row, or
     * {@code null} if never written.
     *
     * @param requestId bound request id.
     * @return owned {@code [1, D]} copy (matching the {@code [B, D]} shape
     *         {@code QwenMtp.draftStep} expects), or {@code null}.
     */
    public Tensor getRow(int requestId) {
        if (rows == null) {
            return null;
        }
        Integer home = requestRows.get(requestId);
        if (home == null) {
            throw new IllegalArgumentException("Unknown MTP anchor request id: " + requestId);
        }
        // Index.slice (not the scalar Index.of) keeps dim 0 as size 1.
        try (var r = Index.slice(home, home + 1); Tensor view = rows.get(r)) {
            Tensor out = view.copy();
            out.promoteToParent();
            return out;
        }
    }

    /**
     * Returns a fresh {@code [B, D]} tensor gathering each request's anchor
     * row in order (batched MTP draft input), or {@code null} if never
     * written.
     *
     * @param requestIds bound request ids (order = batch).
     * @return owned {@code [B, D]} tensor, or {@code null}.
     */
    public Tensor getRows(int[] requestIds) {
        if (rows == null) {
            return null;
        }
        int[] homes = new int[requestIds.length];
        for (int i = 0; i < requestIds.length; i++) {
            Integer home = requestRows.get(requestIds[i]);
            if (home == null) {
                throw new IllegalArgumentException("Unknown MTP anchor request id: " + requestIds[i]);
            }
            homes[i] = home;
        }
        try (Index idx = Index.of(homes)) {
            Tensor out = rows.get(idx);
            out.promoteToParent();
            return out;
        }
    }

    @Override
    public void close() {
        requestRows.clear();
        freeRows.clear();
        if (rows != null) {
            rows.close();
            rows = null;
        }
    }
}
