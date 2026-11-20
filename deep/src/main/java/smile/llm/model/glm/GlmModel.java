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
package smile.llm.model.glm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;

import static smile.llm.model.glm.Gx.*;

/**
 * The GLM-5.3-Flash text model, reference path: float32, one sequence at a time, CPU or one device. Weights are
 * dequantized to float32 when the model is built; a {@link Session} holds the per-request state (KDA conv and
 * recurrent states, MLA latents and indexer keys), so several requests can share one model.
 *
 * @author Haifeng Li
 */
public final class GlmModel implements AutoCloseable {
    /** The state of one request: a slot in the KDA pools and the MLA caches of the sparse layers. */
    public static final class Session implements AutoCloseable {
        private final GlmModel owner;
        private final int slot;
        private final GlmMla.State[] mla;
        private long length;
        private boolean closed;

        private Session(GlmModel owner, int slot, GlmMla.State[] mla) {
            this.owner = owner;
            this.slot = slot;
            this.mla = mla;
        }

        /**
         * Tokens processed so far.
         *
         * @return the position of the next token.
         */
        public long length() {
            return length;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (GlmMla.State s : mla) {
                if (s != null) {
                    s.close();
                }
            }
            owner.release(slot);
        }
    }

    private static final int DEFAULT_SESSIONS = 8;

    private final GlmModelArgs args;
    private final DeepSeekCheckpoint ck;
    private final Tensor embed;       // [vocab, dim] weight dtype (the checkpoint stores bf16)
    private final Tensor finalNorm;
    private final Tensor head;        // [vocab, dim] weight dtype
    private final GlmBlock[] blocks;
    private final java.util.ArrayDeque<Integer> freeSlots = new java.util.ArrayDeque<>();
    private final int maxSessions;

    /**
     * Loads the first {@code args.numLayers()} layers.
     *
     * @param dir  the checkpoint directory.
     * @param args the (possibly truncated) arguments.
     * @throws IOException if the index cannot be read.
     */
    public GlmModel(Path dir, GlmModelArgs args) throws IOException {
        this(dir, args, smile.deep.tensor.Device.CPU());
    }

    /**
     * Loads the first {@code args.numLayers()} layers onto a device (matmul weights are float32 on the CPU and
     * bfloat16 on a GPU; activations, states, gates and the router stay float32).
     *
     * @param dir    the checkpoint directory.
     * @param args   the (possibly truncated) arguments.
     * @param device the device of the model tensors.
     * @throws IOException if the index cannot be read.
     */
    public GlmModel(Path dir, GlmModelArgs args, smile.deep.tensor.Device device) throws IOException {
        this(dir, args, device, null, 0, 1, DEFAULT_SESSIONS);
    }

    /**
     * Loads a replica for one rank of an expert-parallel run: everything except the routed experts is replicated,
     * rank {@code r} of {@code world} owns experts {@code [r * n / world, (r + 1) * n / world)} and the partial routed
     * outputs are summed over the group. Must be constructed, and later driven, by the rank's own thread.
     *
     * @param dir    the checkpoint directory.
     * @param args   the (possibly truncated) arguments.
     * @param device this rank's device.
     * @param group  the NCCL group, or {@code null} for a single process.
     * @param rank   this thread's rank.
     * @param world  the number of ranks.
     * @throws IOException if the index cannot be read.
     */
    public GlmModel(Path dir, GlmModelArgs args, smile.deep.tensor.Device device,
                    smile.llm.parallel.TensorParallelGroup group, int rank, int world) throws IOException {
        this(dir, args, device, group, rank, world, DEFAULT_SESSIONS);
    }

    /**
     * Loads a replica for one rank of an expert-parallel run with room for {@code maxSessions} concurrent requests
     * (each takes a slot of the KDA state pools, about 4.5 MB per KDA layer).
     *
     * @param dir         the checkpoint directory.
     * @param args        the (possibly truncated) arguments.
     * @param device      this rank's device.
     * @param group       the NCCL group, or {@code null} for a single process.
     * @param rank        this thread's rank.
     * @param world       the number of ranks.
     * @param maxSessions the number of request slots.
     * @throws IOException if the index cannot be read.
     */
    public GlmModel(Path dir, GlmModelArgs args, smile.deep.tensor.Device device,
                    smile.llm.parallel.TensorParallelGroup group, int rank, int world, int maxSessions) throws IOException {
        this.args = args;
        this.maxSessions = maxSessions;
        for (int i = maxSessions - 1; i >= 0; i--) {
            freeSlots.push(i);
        }
        ScalarType weights = device.isCUDA() ? ScalarType.BFloat16 : ScalarType.Float;
        if (group == null) {
            Gx.setDevice(device);
            Gx.setWeightType(weights);
            Gx.setEp(null);
        } else {
            Gx.setThreadDevice(device);
            Gx.setThreadWeightType(weights);
            Gx.setEp(new Gx.Ep(group, rank, world));
        }
        this.ck = new DeepSeekCheckpoint(dir);
        String p = "model.language_model.";
        this.embed = weight(ck, p + "embed_tokens");
        this.finalNorm = vector(ck, p + "norm.weight");
        this.head = weight(ck, "lm_head");
        this.blocks = new GlmBlock[args.numLayers()];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = new GlmBlock(args, ck, i);
            blocks[i].allocate(maxSessions + 1);   // the last slot belongs to the padding session of CUDA graphs
        }
    }

    /**
     * Creates the state of a new request.
     *
     * @return an empty session.
     */
    public synchronized Session newSession() {
        if (freeSlots.isEmpty()) {
            throw new IllegalStateException("all " + maxSessions + " request slots are in use");
        }
        int slot = freeSlots.pop();
        GlmMla.State[] mla = new GlmMla.State[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            mla[i] = blocks[i].newMlaState();
            if (blocks[i].kdaPool != null) {
                blocks[i].kdaPool.reset(slot);
            }
        }
        return new Session(this, slot, mla);
    }

    private synchronized void release(int slot) {
        freeSlots.push(slot);
    }

    private Session pad;

    /**
     * The session that fills the unused rows of a batch in a CUDA graph: it owns the extra slot and is never released.
     * It must be used (and created) by the rank thread.
     *
     * @return the padding session.
     */
    synchronized Session padSession() {
        if (pad == null) {
            GlmMla.State[] mla = new GlmMla.State[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                mla[i] = blocks[i].newMlaState();
                if (mla[i] != null) {
                    GlmBlock.reserve(mla[i]);
                }
            }
            pad = new Session(this, maxSessions, mla);
        }
        return pad;
    }

    /** Number of MLA layers. */
    int mlaLayerCount() {
        int n = 0;
        for (GlmBlock b : blocks) {
            if (b.isMla()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Makes the caches of the requests ready for one more token and returns the address tables of a graph batch:
     * {@code [mlaLayers, 3, nb]} flattened (latent, pooled-key, ring), the unused rows pointing at the padding session.
     */
    long[] prepareDecode(Session[] rows, int nb) {
        Session pad = padSession();
        long[] out = new long[mlaLayerCount() * 3 * nb];
        int mi = 0;
        for (int l = 0; l < blocks.length; l++) {
            if (!blocks[l].isMla()) {
                continue;
            }
            for (int i = 0; i < nb; i++) {
                GlmMla.State st = (i < rows.length ? rows[i] : pad).mla[l];
                if (i < rows.length) {
                    GlmBlock.reserve(st);
                }
                long[] one = new long[3 * nb];
                GlmBlock.addresses(st, one, i, nb);
                for (int t = 0; t < 3; t++) {
                    out[(mi * 3 + t) * nb + i] = one[t * nb + i];
                }
            }
            mi++;
        }
        return out;
    }

    /** The host bookkeeping after a decode step of these requests ran in a graph. */
    void commitDecode(Session[] rows) {
        for (Session s : rows) {
            for (GlmMla.State st : s.mla) {
                if (st != null) {
                    GlmBlock.commit(st);
                }
            }
            s.length++;
        }
    }

    /** Tokens before this step of each row (the padding rows are at 0), as the int32 device input of a graph. */
    int[] lengths(Session[] rows, int nb) {
        int[] out = new int[nb];
        for (int i = 0; i < rows.length; i++) {
            out[i] = (int) rows[i].length;
        }
        return out;
    }

    /** The KDA slots of each row of a graph batch (the unused rows use the padding slot). */
    int[] slots(Session[] rows, int nb) {
        int[] out = new int[nb];
        java.util.Arrays.fill(out, maxSessions);
        for (int i = 0; i < rows.length; i++) {
            out[i] = rows[i].slot;
        }
        return out;
    }

    /**
     * One decode step of a graph batch on device tensors only (no host reads): the body that is captured.
     *
     * @param ids      int64 {@code [nb]} last tokens.
     * @param slots    int32 {@code [nb]} KDA slots.
     * @param lens     int32 {@code [nb]} tokens before this step.
     * @param tables   int64 {@code [mlaLayers * 3, nb]} cache addresses per MLA layer.
     * @param maxPools row stride of the indexer score buffers.
     * @return the logits {@code [nb, vocab]}.
     */
    Tensor forwardGraph(Tensor ids, Tensor slots, Tensor lens, Tensor tables, long maxPools) {
        long n = ids.size(0);
        Tensor emb = f32(o("aten::index_select", embed, 0L, ids));
        Tensor streams = o("aten::contiguous", o("aten::expand", unsqueeze(emb, 1),
                new long[]{n, args.hcMult(), args.dim()}, false));
        int mi = 0;
        for (int i = 0; i < blocks.length; i++) {
            GlmBlock b = blocks[i];
            Tensor in = streams;
            if (b.isMla()) {
                Tensor t = narrow(tables, 0, 3L * mi++, 3);
                streams = layerScoped(() -> b.forwardGraph(in, slots, t, lens, maxPools));
            } else {
                streams = layerScoped(() -> b.forwardGraph(in, slots, null, lens, maxPools));
            }
        }
        Tensor mean = o("aten::mean.dim", streams, new long[]{1}, false);
        return logits(rms(mean, finalNorm, args.normEps()));
    }

    /**
     * Number of requests that can be active at once.
     *
     * @return the number of slots.
     */
    public int maxSessions() {
        return maxSessions;
    }

    /**
     * Runs tokens through the model, advancing the session.
     *
     * @param ids     the new token ids (any count: prefill chunk or a decode step).
     * @param session the request's state.
     * @param trace   optional capture of per-layer intermediates (host float32 tensors, keys {@code L<i>.*}).
     * @return the hidden rows after the hyper-connection mean and the final norm {@code [n, dim]}.
     */
    public Tensor forward(int[] ids, Session session, Map<String, Tensor> trace) {
        long n = ids.length;
        Tensor tokens = Tensor.of(ids, n).to(device());
        Tensor emb = f32(o("aten::index_select", embed, 0L, tokens));            // [n, dim]
        Tensor streams = o("aten::contiguous", o("aten::expand", unsqueeze(emb, 1),
                new long[]{n, args.hcMult(), args.dim()}, false));
        for (int i = 0; i < blocks.length; i++) {
            GlmBlock b = blocks[i];
            Tensor in = streams;
            streams = layerScoped(() -> b.forward(in, session.slot, session.mla[b.layer], trace));
        }
        session.length += n;
        Tensor mean = o("aten::mean.dim", streams, new long[]{1}, false);
        return rms(mean, finalNorm, args.normEps());
    }

    /**
     * One decode step of several requests: each row is the next token of its own session.
     *
     * @param ids      the last token of each request.
     * @param sessions the requests' states, one per row (distinct).
     * @return the hidden rows after the hyper-connection mean and the final norm {@code [B, dim]}.
     */
    public Tensor forwardBatch(int[] ids, Session[] sessions) {
        int n = ids.length;
        int[] slots = new int[n];
        for (int i = 0; i < n; i++) {
            slots[i] = sessions[i].slot;
        }
        Tensor tokens = Tensor.of(ids, n).to(device());
        Tensor emb = f32(o("aten::index_select", embed, 0L, tokens));
        Tensor streams = o("aten::contiguous", o("aten::expand", unsqueeze(emb, 1),
                new long[]{n, args.hcMult(), args.dim()}, false));
        for (int i = 0; i < blocks.length; i++) {
            GlmBlock b = blocks[i];
            GlmMla.State[] states = new GlmMla.State[n];
            for (int j = 0; j < n; j++) {
                states[j] = sessions[j].mla[i];
            }
            Tensor in = streams;
            streams = layerScoped(() -> b.forwardBatch(in, slots, states));
        }
        for (Session s : sessions) {
            s.length++;
        }
        Tensor mean = o("aten::mean.dim", streams, new long[]{1}, false);
        return rms(mean, finalNorm, args.normEps());
    }

    /**
     * Logits of hidden rows.
     *
     * @param hidden {@code [m, dim]}.
     * @return {@code [m, vocab]} float32.
     */
    public Tensor logits(Tensor hidden) {
        return linear(hidden, head);
    }

    @Override
    public void close() throws IOException {
        ck.close();
    }
}
