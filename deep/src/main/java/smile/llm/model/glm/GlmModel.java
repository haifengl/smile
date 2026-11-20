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
        /** The MTP layer's cache ({@code null} without MTP): slot q holds (token q+1, main hidden q). */
        private GlmMla.State mtp;
        /** {@code [1, dim]} the main model's hidden row of the last processed position. */
        private Tensor lastHidden;
        /** {@code [1, dim]} the MTP output of the last absorbed slot. */
        private Tensor mtpHidden;
        /** The first draft token that follows from {@link #mtpHidden}, or -1. */
        private int nextDraft = -1;
        /** The verify rows' hidden states of a speculative round in flight. */
        private Tensor pendingHidden;
        private Mark mark;

        private Session(GlmModel owner, int slot, GlmMla.State[] mla) {
            this.owner = owner;
            this.slot = slot;
            this.mla = mla;
        }

        /**
         * Whether the request carries MTP state.
         *
         * @return {@code true} if the model has an MTP layer.
         */
        public boolean hasMtp() {
            return mtp != null;
        }

        /**
         * Number of MTP slots filled.
         *
         * @return the MTP cache length.
         */
        public long mtpLength() {
            return mtp == null ? 0 : mtp.length;
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
            if (mtp != null) {
                mtp.close();
            }
            for (Tensor t : new Tensor[]{lastHidden, mtpHidden, pendingHidden}) {
                if (t != null) {
                    t.close();
                }
            }
            if (mark != null) {
                mark.close();
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
    private GlmBlock mtpBlock;
    private Tensor enorm;
    private Tensor hnorm;
    private Tensor ehProj;
    private Tensor mtpNorm;
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
        if (args.numMtpLayers() > 0 && mtpWanted()) {
            int li = args.mtpLayerIndex();
            String mp = args.layerPrefix(li) + ".";
            mtpBlock = new GlmBlock(args, ck, li, true);
            enorm = vector(ck, mp + "enorm.weight");
            hnorm = vector(ck, mp + "hnorm.weight");
            ehProj = weight(ck, mp + "eh_proj");
            mtpNorm = vector(ck, mp + "shared_head.norm.weight");
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
        Session session = new Session(this, slot, mla);
        if (mtpBlock != null) {
            session.mtp = mtpBlock.newMlaState();
        }
        return session;
    }

    private synchronized void release(int slot) {
        freeSlots.push(slot);
    }

    /**
     * A copy of one rank's request state at a position: per KDA layer the conv and recurrent state of the request's
     * slot, per sparse layer the latent rows, the complete pooled keys and the open-pool ring. Lives outside every
     * tensor scope; {@link #close()} must run on the rank's thread.
     */
    static final class Snapshot implements AutoCloseable {
        final long length;
        private final Tensor[] parts;
        final long bytes;
        private final boolean mtp;
        private final long mtpLength;

        private Snapshot(long length, Tensor[] parts, boolean mtp, long mtpLength) {
            this.length = length;
            this.parts = parts;
            this.mtp = mtp;
            this.mtpLength = mtpLength;
            long b = 0;
            for (Tensor t : parts) {
                if (t != null) {
                    long n = 1;
                    for (long d : t.shape()) {
                        n *= d;
                    }
                    b += n * (t.dtype() == smile.deep.tensor.ScalarType.Float ? 4 : t.dtype() == smile.deep.tensor.ScalarType.UInt8 ? 1 : 2);
                }
            }
            this.bytes = b;
        }

        @Override
        public void close() {
            for (Tensor t : parts) {
                if (t != null) {
                    t.close();
                }
            }
        }
    }

    /**
     * Copies a request's state (all layers) as of its current length. Parts per layer: KDA {@code {conv, rec}}, sparse
     * {@code {latent, pool, ring}}, nothing for the rest (a layer is either KDA or sparse).
     *
     * @param s the request's session on this rank.
     * @return the copy.
     */
    Snapshot snapshot(Session s) {
        java.util.ArrayList<Tensor> parts = new java.util.ArrayList<>();
        for (int i = 0; i < blocks.length; i++) {
            GlmKda.Pool kp = blocks[i].kdaPool;
            if (kp != null) {
                parts.add(keep(o("aten::clone", o("aten::select.int", kp.conv(), 0L, (long) s.slot))));
                parts.add(keep(o("aten::clone", o("aten::select.int", kp.rec(), 0L, (long) s.slot))));
            } else {
                GlmMla.State st = s.mla[i];
                parts.add(keep(o("aten::clone", st.latent.view())));
                parts.add(keep(o("aten::clone", st.pool.view())));
                parts.add(keep(o("aten::clone", st.ring)));
            }
        }
        if (s.mtp != null) {
            parts.add(keep(o("aten::clone", s.mtp.latent.view())));
            parts.add(keep(o("aten::clone", s.mtp.pool.view())));
            parts.add(keep(o("aten::clone", s.mtp.ring)));
        }
        return new Snapshot(s.length, parts.toArray(Tensor[]::new), s.mtp != null, s.mtp == null ? 0 : s.mtp.length);
    }

    /**
     * Loads a snapshot into a fresh session (length 0), which then continues from the snapshot's length.
     *
     * @param snap the snapshot taken on this rank.
     * @param s    the (empty) session.
     */
    void restore(Snapshot snap, Session s) {
        if (s.length != 0) {
            throw new IllegalStateException("restore needs an empty session, found length " + s.length);
        }
        int k = 0;
        for (int i = 0; i < blocks.length; i++) {
            GlmKda.Pool kp = blocks[i].kdaPool;
            if (kp != null) {
                smile.torch.Aten.call("aten::copy_", o("aten::select.int", kp.conv(), 0L, (long) s.slot), snap.parts[k++], false);
                smile.torch.Aten.call("aten::copy_", o("aten::select.int", kp.rec(), 0L, (long) s.slot), snap.parts[k++], false);
            } else {
                GlmMla.State st = s.mla[i];
                st.latent.append(snap.parts[k++]);
                st.pool.append(snap.parts[k++]);
                smile.torch.Aten.call("aten::copy_", st.ring, snap.parts[k++], false);
                st.length = snap.length;
            }
        }
        if (snap.mtp && s.mtp != null) {
            s.mtp.latent.append(snap.parts[k++]);
            s.mtp.pool.append(snap.parts[k++]);
            smile.torch.Aten.call("aten::copy_", s.mtp.ring, snap.parts[k++], false);
            s.mtp.length = snap.mtpLength;
        }
        s.length = snap.length;
    }

    /**
     * Whether the MTP layer is loaded: opt-in with {@code SMILE_GLM_MTP=1} or {@code -Dsmile.glm.speculative=true}
     * (the eager verify of this first version is slower than plain decoding, see GLM53_PLAN.md).
     *
     * @return {@code true} to load the MTP layer.
     */
    public static boolean mtpWanted() {
        return "1".equals(System.getenv("SMILE_GLM_MTP")) || Boolean.getBoolean("smile.glm.speculative");
    }

    /**
     * Whether the model has an MTP layer loaded.
     *
     * @return {@code true} with MTP.
     */
    public boolean hasMtp() {
        return mtpBlock != null;
    }

    /**
     * Feeds rows into the MTP layer: row {@code i} (slot {@code mtp.length + i}) combines the embedding of token
     * {@code tokens[i]} (the token that follows position {@code slot}) with the main model's hidden row {@code i}; the
     * rows are appended to the MTP cache and the last output becomes {@link Session#mtpHidden}.
     *
     * @param s      the request.
     * @param tokens the following tokens, one per row.
     * @param hidden {@code [>= n, dim]} main hidden rows (post final norm) of the positions the slots belong to.
     */
    void mtpAbsorb(Session s, int[] tokens, Tensor hidden) {
        int n = tokens.length;
        if (n == 0) {
            return;
        }
        long slot0 = s.mtp.length;
        Tensor last = scoped(() -> {
            Tensor ids = Tensor.of(tokens, n).to(device());
            Tensor e = f32(o("aten::index_select", embed, 0L, ids));
            if (slot0 == 0) {
                smile.torch.Aten.call("aten::fill_.Scalar", o("aten::select.int", e, 0L, 0L), 0.0);   // as vLLM: no embedding at position 0
            }
            Tensor h = hidden.size(0) == n ? hidden : narrow(hidden, 0, 0, n);
            Tensor x = linear(cat(1, rms(e, enorm, args.normEps()), rms(h, hnorm, args.normEps())), ehProj);
            Tensor out = rms(mtpBlock.forwardPlain(f32(x), s.mtp), mtpNorm, args.normEps());
            return o("aten::clone", narrow(out, 0, n - 1, 1));
        });
        if (s.mtpHidden != null) {
            s.mtpHidden.close();
        }
        s.mtpHidden = last;
    }

    /**
     * Sets the hidden row the MTP layer will see as "previous hidden state" at the next position (the last row of the
     * main model's output for this request).
     */
    void setLastHidden(Session s, Tensor hidden) {
        Tensor t = scoped(() -> o("aten::clone", narrow(hidden, 0, hidden.size(0) - 1, 1)));
        if (s.lastHidden != null) {
            s.lastHidden.close();
        }
        s.lastHidden = t;
    }

    /** The greedy draft token of the last absorbed MTP slot. */
    int mtpArgmax(Session s) {
        Tensor lg = scoped(() -> logits(s.mtpHidden));
        try (lg) {
            return smile.llm.engine.Sampling.sampleTokenIds(lg, 0.0, 1.0)[0];
        }
    }

    /**
     * Drafts {@code n} greedy tokens after {@code last}. Needs the MTP cache to cover the slots before {@code last}'s
     * position (or all but one: the first round after a prefill, then the slot of {@code last} is absorbed here).
     * Leaves the MTP cache as it was (the slots written for the later drafts use drafted data, not the verified one).
     *
     * @param s    the request.
     * @param last the last committed token (position {@code s.length}).
     * @param n    the number of drafts.
     * @return the drafts, or {@code null} if the MTP state does not fit this position.
     */
    int[] mtpDraft(Session s, int last, int n) {
        long p = s.length;
        if (s.mtp.length == p - 1 && s.nextDraft < 0 && s.lastHidden != null) {
            mtpAbsorb(s, new int[]{last}, s.lastHidden);
            s.nextDraft = mtpArgmax(s);
        }
        if (s.mtp.length != p || s.nextDraft < 0 || s.mtpHidden == null) {
            return null;
        }
        int[] d = new int[n];
        d[0] = s.nextDraft;
        if (n > 1) {
            long len = s.mtp.length;
            Tensor ring = keep(o("aten::clone", s.mtp.ring));
            Tensor savedHidden = s.mtpHidden;
            s.mtpHidden = keep(o("aten::clone", savedHidden));
            for (int i = 1; i < n; i++) {
                mtpAbsorb(s, new int[]{d[i - 1]}, s.mtpHidden);
                d[i] = mtpArgmax(s);
            }
            smile.torch.Aten.call("aten::copy_", s.mtp.ring, ring, false);
            ring.close();
            s.mtp.length = len;
            s.mtp.latent.setLength(len);
            s.mtp.pool.setLength(len >> 2);
            s.mtpHidden.close();
            s.mtpHidden = savedHidden;
        }
        return d;
    }

    /**
     * Absorbs the verified rows of a speculative round into the MTP layer and computes the next first draft.
     *
     * @param s       the request.
     * @param emitted the tokens of the round (accepted drafts and the sampled one); row {@code i} of
     *                {@link Session#pendingHidden} is the main hidden of the position before {@code emitted[i]}.
     */
    void mtpFinish(Session s, int[] emitted) {
        mtpAbsorb(s, emitted, s.pendingHidden);
        s.nextDraft = mtpArgmax(s);
    }


    /**
     * One MTP row for each of several requests: token {@code tokens[i]} with the hidden row {@code hidden[i]}, appended
     * to request {@code i}'s MTP cache (the slot is the cache's length).
     *
     * @return the post-norm MTP outputs {@code [B, dim]} (scoped to the caller).
     */
    private Tensor mtpStepBatch(Session[] ss, int[] tokens, Tensor hidden) {
        int b = ss.length;
        Tensor ids = Tensor.of(tokens, b).to(device());
        Tensor e = f32(o("aten::index_select", embed, 0L, ids));
        Tensor x = linear(cat(1, rms(e, enorm, args.normEps()), rms(hidden, hnorm, args.normEps())), ehProj);
        GlmMla.State[] states = new GlmMla.State[b];
        for (int i = 0; i < b; i++) {
            states[i] = ss[i].mtp;
        }
        return rms(mtpBlock.forwardPlainBatch(f32(x), states), mtpNorm, args.normEps());
    }

    private static Tensor rowsOf(Tensor[] rows) {
        return rows.length == 1 ? rows[0] : cat(0, rows);
    }

    private int[] argmaxRows(Tensor out) {
        Tensor lg = scoped(() -> logits(out));
        try (lg) {
            return smile.llm.engine.Sampling.sampleTokenIds(lg, 0.0, 1.0);
        }
    }

    private static void setMtpHidden(Session s, Tensor row) {
        if (s.mtpHidden != null) {
            s.mtpHidden.close();
        }
        s.mtpHidden = row;
    }

    /**
     * {@link #mtpDraft} for several requests at once: every draft step is one MTP pass over all of them.
     *
     * @param ss    the requests.
     * @param lasts the last committed token of each.
     * @param n     drafts per request.
     * @return the drafts per request ({@code null} entries: that request's MTP state does not fit its position).
     */
    int[][] mtpDraftBatch(Session[] ss, int[] lasts, int n) {
        return mtpDraftBatch(ss, lasts, n, null);
    }

    /**
     * As {@link #mtpDraftBatch(Session[], int[], int)}, the draft steps replaying an MTP graph when {@code g} is given
     * (each step feeds the next on the device; one host read for all drafts).
     */
    int[][] mtpDraftBatch(Session[] ss, int[] lasts, int n, GlmMtpGraph g) {
        int b = ss.length;
        int[][] d = new int[b][];
        // the first round after a prefill still has to absorb the slot of the last token
        java.util.List<Integer> first = new java.util.ArrayList<>();
        for (int i = 0; i < b; i++) {
            Session s = ss[i];
            if (s.mtp.length == s.length - 1 && s.nextDraft < 0 && s.lastHidden != null) {
                first.add(i);
            }
        }
        if (!first.isEmpty()) {
            Session[] sub = new Session[first.size()];
            int[] tok = new int[sub.length];
            Tensor[] hid = new Tensor[sub.length];
            for (int k = 0; k < sub.length; k++) {
                sub[k] = ss[first.get(k)];
                tok[k] = lasts[first.get(k)];
                hid[k] = sub[k].lastHidden;
            }
            if (g != null) {
                Tensor rows = scoped(() -> o("aten::clone", rowsOf(hid)));
                g.load(tok, rows);
                rows.close();
                g.run(sub);
                long[] next;
                try (Tensor cpu = g.next().to(smile.deep.tensor.Device.CPU())) {
                    next = cpu.longArray();
                }
                for (int k = 0; k < sub.length; k++) {
                    setMtpHiddenFrom(sub[k], g.out(), k);
                    sub[k].nextDraft = (int) next[k];
                }
            } else {
                Tensor out = scoped(() -> mtpStepBatch(sub, tok, rowsOf(hid)));
                int[] next = argmaxRows(out);
                for (int k = 0; k < sub.length; k++) {
                    final int row = k;
                    setMtpHidden(sub[k], scoped(() -> o("aten::clone", narrow(out, 0, row, 1))));
                    sub[k].nextDraft = next[k];
                }
                out.close();
            }
        }
        for (int i = 0; i < b; i++) {
            Session s = ss[i];
            if (s.mtp.length != s.length || s.nextDraft < 0 || s.mtpHidden == null) {
                return nullAt(d, i);
            }
            d[i] = new int[n];
            d[i][0] = s.nextDraft;
        }
        if (n > 1 && g != null) {
            long[] len = new long[b];
            Tensor[] rings = new Tensor[b];
            int[] tok = new int[b];
            Tensor[] hid = new Tensor[b];
            for (int i = 0; i < b; i++) {
                len[i] = ss[i].mtp.length;
                rings[i] = mtpRingCopy(ss[i]);
                tok[i] = d[i][0];
                hid[i] = ss[i].mtpHidden;
            }
            Tensor in = scoped(() -> o("aten::clone", rowsOf(hid)));
            g.load(tok, in);
            in.close();
            Tensor[] steps = new Tensor[n - 1];
            for (int step = 1; step < n; step++) {
                g.run(ss);
                steps[step - 1] = keep(o("aten::clone", narrow(g.next(), 0, 0, b)));
                if (step < n - 1) {
                    g.chain();
                }
            }
            long[] vals;
            Tensor all = scoped(() -> cat(0, steps));
            try (Tensor cpu = all.to(smile.deep.tensor.Device.CPU())) {
                vals = cpu.longArray();
            }
            all.close();
            for (Tensor t : steps) {
                t.close();
            }
            for (int step = 1; step < n; step++) {
                for (int i = 0; i < b; i++) {
                    d[i][step] = (int) vals[(step - 1) * b + i];
                }
            }
            for (int i = 0; i < b; i++) {
                mtpTruncate(ss[i], len[i], rings[i]);
            }
        } else if (n > 1) {
            long[] len = new long[b];
            Tensor[] rings = new Tensor[b];
            Tensor[] saved = new Tensor[b];
            for (int i = 0; i < b; i++) {
                len[i] = ss[i].mtp.length;
                rings[i] = keep(o("aten::clone", ss[i].mtp.ring));
                saved[i] = ss[i].mtpHidden;
                ss[i].mtpHidden = keep(o("aten::clone", saved[i]));
            }
            for (int step = 1; step < n; step++) {
                final int st = step;
                int[] tok = new int[b];
                Tensor[] hid = new Tensor[b];
                for (int i = 0; i < b; i++) {
                    tok[i] = d[i][st - 1];
                    hid[i] = ss[i].mtpHidden;
                }
                Tensor out = scoped(() -> mtpStepBatch(ss, tok, rowsOf(hid)));
                int[] next = argmaxRows(out);
                for (int i = 0; i < b; i++) {
                    final int row = i;
                    setMtpHidden(ss[i], scoped(() -> o("aten::clone", narrow(out, 0, row, 1))));
                    d[i][st] = next[i];
                }
                out.close();
            }
            for (int i = 0; i < b; i++) {
                smile.torch.Aten.call("aten::copy_", ss[i].mtp.ring, rings[i], false);
                rings[i].close();
                ss[i].mtp.length = len[i];
                ss[i].mtp.latent.setLength(len[i]);
                ss[i].mtp.pool.setLength(len[i] >> 2);
                ss[i].mtpHidden.close();
                ss[i].mtpHidden = saved[i];
            }
        }
        return d;
    }

    private static int[][] nullAt(int[][] d, int i) {
        d[i] = null;
        return d;
    }

    /**
     * {@link #mtpFinish} for several requests at once: the kept rows are absorbed one position at a time, every position
     * as one MTP pass over the requests that still have rows.
     *
     * @param ss      the requests (each with its verify rows stored by {@link #keepPending}).
     * @param emitted the tokens of the round per request.
     */
    void mtpFinishBatch(Session[] ss, int[][] emitted) {
        mtpFinishBatch(ss, emitted, null);
    }

    /** As {@link #mtpFinishBatch(Session[], int[][])}, every position replaying the MTP graph when {@code g} is given. */
    void mtpFinishBatch(Session[] ss, int[][] emitted, GlmMtpGraph g) {
        if (g != null) {
            mtpFinishGraph(ss, emitted, g);
            return;
        }
        int b = ss.length;
        int rounds = 0;
        for (int[] e : emitted) {
            rounds = Math.max(rounds, e.length);
        }
        for (int j = 0; j < rounds; j++) {
            java.util.List<Integer> live = new java.util.ArrayList<>();
            for (int i = 0; i < b; i++) {
                if (emitted[i].length > j) {
                    live.add(i);
                }
            }
            Session[] sub = new Session[live.size()];
            int[] tok = new int[sub.length];
            Tensor[] hid = new Tensor[sub.length];
            for (int k = 0; k < sub.length; k++) {
                int i = live.get(k);
                sub[k] = ss[i];
                tok[k] = emitted[i][j];
                final int row = j;
                hid[k] = scoped(() -> o("aten::clone", narrow(ss[i].pendingHidden, 0, row, 1)));
            }
            Tensor out = scoped(() -> mtpStepBatch(sub, tok, rowsOf(hid)));
            for (Tensor t : hid) {
                t.close();
            }
            int[] next = null;
            boolean finishing = false;
            for (int k = 0; k < sub.length; k++) {
                finishing |= emitted[live.get(k)].length == j + 1;
            }
            if (finishing) {
                next = argmaxRows(out);
            }
            for (int k = 0; k < sub.length; k++) {
                if (emitted[live.get(k)].length == j + 1) {
                    final int row = k;
                    setMtpHidden(sub[k], scoped(() -> o("aten::clone", narrow(out, 0, row, 1))));
                    sub[k].nextDraft = next[k];
                }
            }
            out.close();
        }
    }


    /**
     * One MTP row per request on device tensors only (the body of an MTP graph): token ids and previous hidden rows in,
     * the post-norm MTP output and its greedy token out.
     *
     * @param ids      int64 {@code [nb]} the following tokens.
     * @param hidden   float32 {@code [nb, dim]} the previous hidden rows.
     * @param tables   int64 {@code [3, nb]} MTP cache addresses.
     * @param lens     int32 {@code [nb]} MTP cache lengths.
     * @param maxPools row stride of the score buffer.
     * @return {@code {out [nb, dim], next int64 [nb]}}.
     */
    Tensor[] mtpGraphBody(Tensor ids, Tensor hidden, Tensor tables, Tensor lens, long maxPools, boolean head) {
        Tensor e = f32(o("aten::index_select", embed, 0L, ids));
        Tensor x = linear(cat(1, rms(e, enorm, args.normEps()), rms(hidden, hnorm, args.normEps())), ehProj);
        Tensor out = rms(mtpBlock.forwardPlainGraph(f32(x), tables, lens, maxPools), mtpNorm, args.normEps());
        if (!head) {
            return new Tensor[]{out};
        }
        Tensor next = o("aten::argmax", logits(out), -1L, false);
        return new Tensor[]{out, next};
    }

    /** MTP cache addresses {@code [3, nb]} flattened (latent, pooled key, ring); the unused rows use the padding session. */
    long[] mtpTables(Session[] rows, int nb) {
        Session pad = padSession();
        long[] out = new long[3 * nb];
        for (int i = 0; i < nb; i++) {
            GlmMla.State st = i < rows.length ? rows[i].mtp : pad.mtp;
            if (i < rows.length) {
                GlmBlock.reserve(st);
            }
            long[] one = new long[3 * nb];
            GlmBlock.addresses(st, one, i, nb);
            for (int t = 0; t < 3; t++) {
                out[t * nb + i] = one[t * nb + i];
            }
        }
        return out;
    }

    int[] mtpLengths(Session[] rows, int nb) {
        int[] out = new int[nb];
        for (int i = 0; i < rows.length; i++) {
            out[i] = (int) rows[i].mtp.length;
        }
        return out;
    }

    /** The host bookkeeping after an MTP graph step of these requests. */
    void commitMtp(Session[] rows) {
        for (Session s : rows) {
            GlmBlock.commit(s.mtp);
        }
    }

    /** The hidden row stored for the last MTP slot of a request ({@code [1, dim]}). */
    Tensor mtpHiddenOf(Session s) {
        return s.mtpHidden;
    }

    /** Sets the stored MTP hidden row of a request to a copy of row {@code row} of {@code out}. */
    void setMtpHiddenFrom(Session s, Tensor out, long row) {
        setMtpHidden(s, scoped(() -> o("aten::clone", narrow(out, 0, row, 1))));
    }

    /** Sets the first draft of a request. */
    void setNextDraft(Session s, int token) {
        s.nextDraft = token;
    }

    /** Saves the open-pool ring of the MTP cache. */
    Tensor mtpRingCopy(Session s) {
        return keep(o("aten::clone", s.mtp.ring));
    }

    /** Returns the MTP cache of a request to {@code len} slots with the ring {@code ring} (which is closed). */
    void mtpTruncate(Session s, long len, Tensor ring) {
        smile.torch.Aten.call("aten::copy_", s.mtp.ring, ring, false);
        ring.close();
        s.mtp.length = len;
        s.mtp.latent.setLength(len);
        s.mtp.pool.setLength(len >> 2);
    }


    private void mtpFinishGraph(Session[] ss, int[][] emitted, GlmMtpGraph g) {
        int b = ss.length;
        int rounds = 0;
        for (int[] e : emitted) {
            rounds = Math.max(rounds, e.length);
        }
        java.util.List<Tensor> nexts = new java.util.ArrayList<>();
        java.util.List<int[]> who = new java.util.ArrayList<>();      // per recorded step: the requests that finish there
        for (int j = 0; j < rounds; j++) {
            java.util.List<Integer> live = new java.util.ArrayList<>();
            for (int i = 0; i < b; i++) {
                if (emitted[i].length > j) {
                    live.add(i);
                }
            }
            Session[] sub = new Session[live.size()];
            int[] tok = new int[sub.length];
            Tensor[] hid = new Tensor[sub.length];
            for (int k = 0; k < sub.length; k++) {
                int i = live.get(k);
                sub[k] = ss[i];
                tok[k] = emitted[i][j];
                hid[k] = narrow(ss[i].pendingHidden, 0, j, 1);
            }
            Tensor rows = sub.length == 1 ? hid[0] : cat(0, hid);
            g.load(tok, rows);
            g.run(sub);
            int[] done = new int[sub.length];
            int finishing = 0;
            for (int k = 0; k < sub.length; k++) {
                if (emitted[live.get(k)].length == j + 1) {
                    setMtpHiddenFrom(sub[k], g.out(), k);
                    done[finishing++] = live.get(k);
                }
            }
            if (finishing > 0) {
                nexts.add(keep(o("aten::clone", narrow(g.next(), 0, 0, sub.length))));
                // position k of this step's tokens belongs to the k-th live request
                int[] map = new int[sub.length];
                java.util.Arrays.fill(map, -1);
                for (int k = 0; k < sub.length; k++) {
                    if (emitted[live.get(k)].length == j + 1) {
                        map[k] = live.get(k);
                    }
                }
                who.add(map);
            }
        }
        if (!nexts.isEmpty()) {
            Tensor all = scoped(() -> cat(0, nexts.toArray(Tensor[]::new)));
            long[] vals;
            try (Tensor cpu = all.to(smile.deep.tensor.Device.CPU())) {
                vals = cpu.longArray();
            }
            all.close();
            int off = 0;
            for (int r = 0; r < nexts.size(); r++) {
                int[] map = who.get(r);
                for (int k = 0; k < map.length; k++) {
                    if (map[k] >= 0) {
                        ss[map[k]].nextDraft = (int) vals[off + k];
                    }
                }
                off += map.length;
                nexts.get(r).close();
            }
        }
    }


    /**
     * Whether a request's MTP cache lags one slot behind the position of its next token (the state after a plain step or a
     * prefill): the slot of the previous token still has to be absorbed together with the token now known.
     */
    boolean mtpPending(Session s) {
        return s.mtp != null && s.lastHidden != null && s.mtp.length == s.length - 1;
    }

    /**
     * Before a plain decode step: absorbs the pending MTP slot of every request that has one (token = the step's input
     * token, hidden = the main hidden row of the previous position), so the MTP cache stays in sync with plain decoding.
     *
     * @param ss    the step's requests.
     * @param lasts the step's input tokens.
     * @param g     an MTP graph with at least as many rows as pending requests, or {@code null} for the eager pass.
     */
    void mtpBeforePlain(Session[] ss, int[] lasts, GlmMtpGraph g) {
        java.util.List<Integer> idx = new java.util.ArrayList<>();
        for (int i = 0; i < ss.length; i++) {
            if (mtpPending(ss[i])) {
                idx.add(i);
            }
        }
        if (idx.isEmpty()) {
            return;
        }
        Session[] sub = new Session[idx.size()];
        int[] tok = new int[sub.length];
        Tensor[] hid = new Tensor[sub.length];
        for (int k = 0; k < sub.length; k++) {
            sub[k] = ss[idx.get(k)];
            tok[k] = lasts[idx.get(k)];
            hid[k] = sub[k].lastHidden;
        }
        // the MTP output of this slot is not needed: a speculative round recomputes the slot of the previous token anyway
        if (g != null) {
            Tensor rows = scoped(() -> o("aten::clone", rowsOf(hid)));
            g.load(tok, rows);
            rows.close();
            g.run(sub);
        } else {
            Tensor out = scoped(() -> mtpStepBatch(sub, tok, rowsOf(hid)));
            out.close();
        }
    }

    /**
     * After a plain decode step: keeps the main hidden row of each request (the next MTP slot needs it with the token the
     * engine samples next) and forgets the draft derived before the step.
     *
     * @param ss     the step's requests.
     * @param hidden {@code [>= n, dim]} the step's hidden rows in request order.
     */
    void mtpAfterPlain(Session[] ss, Tensor hidden) {
        Tensor all = scoped(() -> o("aten::clone", narrow(hidden, 0, 0, ss.length)));
        for (int i = 0; i < ss.length; i++) {
            if (ss[i].lastHidden != null) {
                ss[i].lastHidden.close();
            }
            ss[i].lastHidden = keep(narrow(all, 0, i, 1));      // a view: the storage lives as long as a row of it does
            ss[i].nextDraft = -1;
        }
        all.close();
    }

    /** A light copy of a request's recurrent and ring state for rolling a speculative window back. */
    static final class Mark implements AutoCloseable {
        private final long length;
        private final long[] stateLength;
        private final Tensor[] parts;

        private Mark(long length, long[] stateLength, Tensor[] parts) {
            this.length = length;
            this.stateLength = stateLength;
            this.parts = parts;
        }

        @Override
        public void close() {
            for (Tensor t : parts) {
                if (t != null) {
                    t.close();
                }
            }
        }
    }

    /**
     * Marks the request's current position: KDA conv/recurrent state, MLA open-pool rings and lengths.
     *
     * @param s the request.
     * @return the mark (close it).
     */
    Mark mark(Session s) {
        java.util.ArrayList<Tensor> parts = new java.util.ArrayList<>();
        long[] lens = new long[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            GlmKda.Pool kp = blocks[i].kdaPool;
            if (kp != null) {
                parts.add(keep(o("aten::clone", o("aten::select.int", kp.conv(), 0L, (long) s.slot))));
                parts.add(keep(o("aten::clone", o("aten::select.int", kp.rec(), 0L, (long) s.slot))));
            } else {
                parts.add(keep(o("aten::clone", s.mla[i].ring)));
                lens[i] = s.mla[i].length;
            }
        }
        return new Mark(s.length, lens, parts.toArray(Tensor[]::new));
    }

    /** Returns the request to a {@link #mark}ed position (rows written since then are overwritten later). */
    void rewind(Session s, Mark m) {
        int k = 0;
        for (int i = 0; i < blocks.length; i++) {
            GlmKda.Pool kp = blocks[i].kdaPool;
            if (kp != null) {
                smile.torch.Aten.call("aten::copy_", o("aten::select.int", kp.conv(), 0L, (long) s.slot), m.parts[k++], false);
                smile.torch.Aten.call("aten::copy_", o("aten::select.int", kp.rec(), 0L, (long) s.slot), m.parts[k++], false);
            } else {
                GlmMla.State st = s.mla[i];
                smile.torch.Aten.call("aten::copy_", st.ring, m.parts[k++], false);
                st.length = m.stateLength[i];
                st.latent.setLength(st.length);
                st.pool.setLength(st.length >> 2);
            }
        }
        s.length = m.length;
    }

    /** Stores a mark on the request (replacing an older one). */
    void keepMark(Session s, Mark m) {
        if (s.mark != null) {
            s.mark.close();
        }
        s.mark = m;
    }

    /** The mark stored with {@link #keepMark}. */
    Mark markOf(Session s) {
        return s.mark;
    }

    /** Stores the verify rows of a round and the position they start at. */
    void keepPending(Session s, Tensor hidden) {
        if (s.pendingHidden != null) {
            s.pendingHidden.close();
        }
        s.pendingHidden = scoped(() -> o("aten::clone", hidden));
    }

    /** Frees the round's temporaries. */
    void endRound(Session s) {
        if (s.mark != null) {
            s.mark.close();
            s.mark = null;
        }
        if (s.pendingHidden != null) {
            s.pendingHidden.close();
            s.pendingHidden = null;
        }
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
            if (mtpBlock != null) {
                pad.mtp = mtpBlock.newMlaState();
                GlmBlock.reserve(pad.mtp);
            }
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
        return prepareDecode(rows, nb, 1);
    }

    /** As {@link #prepareDecode(Session[], int)} for a window of {@code extra} tokens per request. */
    long[] prepareDecode(Session[] rows, int nb, int extra) {
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
                    GlmBlock.reserve(st, extra);
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


    /**
     * Allocates the inputs and saved buffers of a verify-window graph.
     *
     * @param b        requests (the graph bucket).
     * @param rows     tokens per request (the last token plus the drafts).
     * @param maxPools row stride of the score buffers.
     * @return the window.
     */
    GlmWindow newWindow(int b, int rows, long maxPools) {
        return new GlmWindow(blocks, mlaLayerCount(), b, rows, maxPools);
    }

    /**
     * Writes the inputs of a verify window: real requests first, the padding session after.
     *
     * @param w      the window.
     * @param sess   the requests (at most {@code w.b}).
     * @param tokens {@code rows} token ids per request.
     */
    void fillWindow(GlmWindow w, Session[] sess, int[][] tokens) {
        long[] idv = new long[w.n];
        for (int i = 0; i < tokens.length; i++) {
            for (int j = 0; j < w.rows; j++) {
                idv[i * w.rows + j] = tokens[i][j];
            }
        }
        int[] lens = lengths(sess, w.b);
        int[] lensRow = new int[w.n];
        for (int i = 0; i < w.b; i++) {
            for (int j = 0; j < w.rows; j++) {
                lensRow[i * w.rows + j] = lens[i] + j;
            }
        }
        smile.torch.Aten.call("aten::copy_", w.ids, Tensor.of(idv, w.n).to(device()), false);
        smile.torch.Aten.call("aten::copy_", w.slots, Tensor.of(slots(sess, w.b), w.b).to(device()), false);
        smile.torch.Aten.call("aten::copy_", w.lensReq, Tensor.of(lens, w.b).to(device()), false);
        smile.torch.Aten.call("aten::copy_", w.lensRow, Tensor.of(lensRow, w.n).to(device()), false);
        long[] t = prepareDecode(sess, w.b, w.rows);
        smile.torch.Aten.call("aten::copy_", w.req, Tensor.of(t, w.req.size(0), w.b).to(device()), false);
        int layers = mlaLayerCount();
        long[] rp = new long[layers * 2 * w.n];
        for (int mi = 0; mi < layers; mi++) {
            for (int i = 0; i < w.b; i++) {
                for (int j = 0; j < w.rows; j++) {
                    rp[(mi * 2) * w.n + i * w.rows + j] = t[(mi * 3) * w.b + i];
                    rp[(mi * 2 + 1) * w.n + i * w.rows + j] = t[(mi * 3 + 1) * w.b + i];
                }
            }
        }
        smile.torch.Aten.call("aten::copy_", w.rowPtrs, Tensor.of(rp, w.rowPtrs.size(0), w.n).to(device()), false);
    }

    /**
     * The verify window on device tensors only (the body of a graph): every layer on the {@code b * rows} rows.
     *
     * @param w the window.
     * @return {@code {hidden [n, dim], logits [n, vocab]}}.
     */
    Tensor[] forwardWindow(GlmWindow w) {
        Tensor emb = f32(o("aten::index_select", embed, 0L, w.ids));
        Tensor streams = o("aten::contiguous", o("aten::expand", unsqueeze(emb, 1),
                new long[]{w.n, args.hcMult(), args.dim()}, false));
        for (int i = 0; i < blocks.length; i++) {
            GlmBlock b = blocks[i];
            Tensor in = streams;
            streams = layerScoped(() -> b.forwardWindow(in, w));
        }
        Tensor hidden = rms(o("aten::mean.dim", streams, new long[]{1}, false), finalNorm, args.normEps());
        return new Tensor[]{hidden, logits(hidden)};
    }

    /**
     * Keeps the first {@code accept[i]} rows of the window just run for request {@code i}: the KDA states, the MLA rings,
     * and the host bookkeeping (lengths).
     *
     * @param w      the window (after a replay).
     * @param sess   the requests of the replay.
     * @param accept rows kept per request (at least 1).
     */
    void commitWindow(GlmWindow w, Session[] sess, int[] accept) {
        int[] acc = new int[w.b];
        java.util.Arrays.fill(acc, 1);
        System.arraycopy(accept, 0, acc, 0, accept.length);
        smile.torch.Aten.call("aten::copy_", w.accept, Tensor.of(acc, w.b).to(device()), false);
        for (GlmBlock b : blocks) {
            b.windowCommit(w);
        }
        for (int i = 0; i < sess.length; i++) {
            for (GlmMla.State st : sess[i].mla) {
                if (st != null) {
                    st.length += accept[i];
                    st.latent.setLength(st.length);
                    st.pool.setLength(st.length >> 2);
                }
            }
            sess[i].length += accept[i];
        }
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
    Tensor[] forwardGraph(Tensor ids, Tensor slots, Tensor lens, Tensor tables, long maxPools) {
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
        Tensor hidden = rms(mean, finalNorm, args.normEps());
        return new Tensor[]{hidden, logits(hidden)};
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
