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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

import static smile.llm.model.deepseek.Dsv.o;

/**
 * DeepSeek-V4.1 text model for validation: a (possibly truncated) stack of {@link DeepSeekBlock}s over a
 * {@code hc}-fold residual stream, single sequence, CPU-friendly, float32 weights. It reproduces the reference
 * forward pass step by step and is checked against a pure-PyTorch port of it; it is not the serving implementation.
 *
 * @author Haifeng Li
 */
public final class DeepSeekModel implements AutoCloseable {
    private final DeepSeekModelArgs args;
    private final DeepSeekCheckpoint ck;
    private Tensor embed;
    private final Tensor normWeight;
    private final Tensor head;
    /** The head in its checkpoint dtype (bf16): a bf16 GEMM with float32 output gives the same products at half the traffic. */
    private final Tensor headBf16;
    private volatile boolean headMmUnavailable;
    private final List<DeepSeekBlock> blocks = new ArrayList<>();
    private final DeepSeekEngram.Hash engramHash;
    /** The DSpark draft head, or {@code null} when the checkpoint has none or it is turned off ({@code SMILE_DS_DRAFT=0}). */
    private DeepSeekDraft draft;
    /** The main hidden states captured by the last {@link #forward} (for the draft), or {@code null}. */
    private Tensor tapOut;

    /**
     * Loads the first {@code args.numLayers()} layers.
     *
     * @param args       model arguments (use {@link DeepSeekModelArgs#slice} and {@link DeepSeekModelArgs#withMaxSeqLen}).
     * @param checkpoint the checkpoint directory.
     * @param engramRows source of engram table rows by layer (called for layers that have an engram).
     * @throws IOException if the checkpoint cannot be read.
     */
    DeepSeekModel(DeepSeekModelArgs args, Path checkpoint, java.util.function.IntFunction<DeepSeekEngram.Rows> engramRows)
            throws IOException {
        this.args = args;
        this.ck = new DeepSeekCheckpoint(checkpoint);
        this.embed = ck.dev("embed.weight");
        this.normWeight = ck.dev("norm.weight");
        Tensor h0 = ck.dev("head.weight");
        if (h0.dtype() == ScalarType.BFloat16 && Dsv.device().isCUDA()) {
            this.headBf16 = h0;
            this.head = null;
        } else {
            this.headBf16 = null;
            this.head = DeepSeekOps.f32(h0);
        }
        this.engramHash = args.engramLayers().length == 0 ? null
                : new DeepSeekEngram.Hash(args, DeepSeekEngramTokenMap.load(checkpoint.resolve("tokenizer.json"), args.vocabSize()));
        for (int i = 0; i < args.numLayers(); i++) {
            blocks.add(new DeepSeekBlock(args, ck, i, args.engramSlot(i) >= 0 ? engramRows.apply(i) : null));
        }
        if (!"0".equals(System.getenv("SMILE_DS_DRAFT")) && DeepSeekDraft.available(args, ck)) {
            this.draft = new DeepSeekDraft(args, ck);
        }
    }

    /** Whether this model has a DSpark draft head. */
    boolean hasDraft() {
        return draft != null;
    }

    DeepSeekDraft draft() {
        return draft;
    }

    /** Token embedding rows {@code [shape..., dim]} of {@code ids}. */
    Tensor embedRows(long[] ids, long... shape) {
        return o("aten::embedding", embed, Dsv.dev(Tensor.of(ids, shape)));
    }

    /** Gives a padding session draft rings (zeros), so a draft graph's unused slots have somewhere to point. */
    void ensureDraftState(Session s) {
        if (draft != null && s.draft == null) {
            s.draft = draft.newState();
            for (DeepSeekAttention.State st : s.draft) {
                st.detach();
            }
        }
    }

    /** Token embedding rows of an int64 id tensor {@code [..]}: {@code [.., dim]}. */
    Tensor embedTensor(Tensor ids) {
        return o("aten::embedding", embed, ids);
    }

    /** The main hidden states ({@code [1, rows, targets * dim]}) the last forward captured, once. */
    Tensor takeTap() {
        Tensor t = tapOut;
        tapOut = null;
        return t;
    }

    /**
     * DSpark: after a prompt's prefill, seeds the sequence's draft rings from the main hidden states of its tokens and
     * keeps the last one.
     *
     * @param tap {@code [1, len, targets * dim]} the prompt's main hidden states (may be a view of a larger tensor).
     */
    void seedDraft(Session s, Tensor tap) {
        if (draft == null) {
            return;
        }
        if (s.draft == null) {
            s.draft = draft.newState();
            for (DeepSeekAttention.State st : s.draft) {
                st.detach();
            }
        }
        draft.seed(s.draft, tap);
        long len = tap.size(1);
        if (s.lastTap != null) {
            s.lastTap.close();
        }
        s.lastTap = o("aten::clone", o("aten::narrow", tap, 1L, len - 1, 1L));
        s.lastTap.detachFromScopes();
        s.draftPos = (int) len - 1;
    }

    /**
     * DSpark: the main model processed position {@code pos}; keep its hidden state {@code tap [1, 1, targets * dim]} as
     * the draft's latest, after writing the previous latest into the rings (it is no longer the last position).
     */
    void advanceDraft(Session s, Tensor tap, int pos) {
        if (draft == null || s.draft == null) {
            return;
        }
        if (s.lastTap != null && s.draftPos >= 0) {
            draft.absorb(s.draft, s.lastTap, s.draftPos);
            s.lastTap.close();
        }
        s.lastTap = o("aten::clone", tap);
        s.lastTap.detachFromScopes();
        s.draftPos = pos;
    }

    /**
     * DSpark, after a verify step, for all sequences at once: sequence {@code i} processed rows {@code 0 .. accepted[i]} of
     * its block (positions {@code first[i] + j}); row {@code j} of sequence {@code i} has the main hidden state
     * {@code taps[base[i] + j]}. The rows before the last go into the draft rings, the last becomes the sequence's latest.
     */
    void commitDraftBatch(Session[] sessions, Tensor taps, int[] base, int[] accepted, int[] first) {
        if (draft == null) {
            return;
        }
        int n = sessions.length;
        List<Long> rowIdx = new ArrayList<>();
        List<Long> pos = new ArrayList<>();
        List<DeepSeekAttention.State[]> rings = new ArrayList<>();
        long[] lastRow = new long[n];
        Tensor[] last = new Tensor[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < accepted[i]; j++) {
                rowIdx.add((long) base[i] + j);
                pos.add((long) first[i] + j);
                rings.add(sessions[i].draft);
            }
            lastRow[i] = (long) base[i] + accepted[i];
            last[i] = sessions[i].lastTap;
        }
        if (!rowIdx.isEmpty()) {
            Tensor sel = o("aten::index_select", taps, 0L, Dsv.dev(Tensor.of(rowIdx.stream().mapToLong(Long::longValue).toArray(),
                    rowIdx.size()))).reshape(1, rowIdx.size(), -1);
            draft.absorbRows(rings.toArray(new DeepSeekAttention.State[0][]), sel,
                    pos.stream().mapToLong(Long::longValue).toArray());
        }
        // the latest hidden state of each sequence, written in place
        Tensor src = o("aten::index_select", taps, 0L, Dsv.dev(Tensor.of(lastRow, n))).contiguous();
        smile.torch.Native.dsCacheScatter(src, smile.torch.Native.dsPtrTable(last),
                Dsv.dev(Tensor.of(new long[n], n)), 1, null);
        for (int i = 0; i < n; i++) {
            sessions[i].draftPos = first[i] + accepted[i];
        }
    }

    /** DSpark: drafts the block that follows each sequence's {@code lastIds} token (greedy), from the sequences' draft state. */
    DeepSeekDraft.Result[] draftBatch(int[] lastIds, Session[] sessions) {
        int n = lastIds.length;
        int[] pos = new int[n];
        Tensor[] taps = new Tensor[n];
        DeepSeekAttention.State[][] st = new DeepSeekAttention.State[n][];
        for (int i = 0; i < n; i++) {
            if (sessions[i].draft == null || sessions[i].lastTap == null) {
                throw new IllegalStateException("sequence has no draft state (prefill it first)");
            }
            pos[i] = sessions[i].draftPos;
            taps[i] = sessions[i].lastTap;
            st[i] = sessions[i].draft;
        }
        return draft.draft(this, lastIds, pos, taps, st);
    }

    private boolean isTap(int layer) {
        if (draft == null) {
            return false;
        }
        for (int t : args.dsparkTargetLayers()) {
            if (t == layer) {
                return true;
            }
        }
        return false;
    }

    /**
     * All mutable per-sequence state of the model (each layer's caches and the engram token history). The model runs
     * one sequence at a time; {@link #use} switches which one, so a serving layer can interleave requests.
     */
    public static final class Session implements AutoCloseable {
        private final DeepSeekAttention.State[] layers;
        private final long[] history;
        /** DSpark: this sequence's draft window rings, or {@code null} until seeded. */
        DeepSeekAttention.State[] draft;
        /** DSpark: the main hidden state of the last processed position {@code [1, 1, targets * dim]}. */
        Tensor lastTap;
        /** DSpark: that position. */
        int draftPos = -1;

        private Session(DeepSeekAttention.State[] layers, long[] history) {
            this.layers = layers;
            this.history = history;
        }

        /** Returns the session to the state of an empty sequence, keeping its allocations. */
        public void reset() {
            for (DeepSeekAttention.State s : layers) {
                s.reset();
            }
            if (draft != null) {
                for (DeepSeekAttention.State s : draft) {
                    s.reset();
                }
            }
            if (lastTap != null) {
                lastTap.close();
                lastTap = null;
            }
            draftPos = -1;
            java.util.Arrays.fill(history, 0L);
        }

        @Override
        public void close() {
            for (DeepSeekAttention.State s : layers) {
                s.close();
            }
            if (draft != null) {
                for (DeepSeekAttention.State s : draft) {
                    s.close();
                }
            }
            if (lastTap != null) {
                lastTap.close();
            }
        }
    }

    /**
     * Allocates the state of an empty sequence (call outside any tensor scope).
     *
     * @return the new session state.
     */
    public Session newSession() {
        DeepSeekAttention.State[] s = new DeepSeekAttention.State[blocks.size()];
        DeepSeekAttention.Shared shared = new DeepSeekAttention.Shared();
        for (int i = 0; i < s.length; i++) {
            s[i] = blocks.get(i).newState(shared);
        }
        return new Session(s, engramHash == null ? null : engramHash.newHistory());
    }

    /**
     * Makes {@code session} the sequence the next {@link #forward} calls continue.
     *
     * @param session the session state to activate.
     */
    public void use(Session session) {
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).use(session.layers[i]);
        }
        if (engramHash != null) {
            engramHash.use(session.history);
        }
    }

    /** Test hook: multiplies the embedding table by {@code 1 + eps * N(0,1)} (a stand-in for kernel rounding noise). */
    void perturbEmbedding(double eps) {
        Tensor noise = o("aten::randn_like", embed.to(smile.deep.tensor.ScalarType.Float));
        embed = o("aten::mul.Tensor", embed.to(smile.deep.tensor.ScalarType.Float),
                o("aten::add.Scalar", o("aten::mul.Scalar", noise, eps), 1.0)).to(embed.dtype());
    }

    /**
     * Runs new tokens through the model.
     *
     * @param ids      the new token ids.
     * @param startPos absolute position of the first new token (0 for prefill).
     * @param trace    optional map receiving per-layer intermediates ({@code L<i>.attn_out}, {@code ffn_out},
     *                 {@code engram_out}, {@code out}), or {@code null}.
     * @return the logits of the last new token {@code [1, vocab]} (float32).
     */
    public Tensor forward(int[] ids, int startPos, Map<String, Tensor> trace) {
        int hc = args.hcMult();
        Tensor hashes = engramHash == null ? null : engramHash.forward(ids, startPos);
        long[] idv = new long[ids.length];
        for (int i = 0; i < ids.length; i++) {
            idv[i] = ids[i];
        }
        Tensor h = o("aten::embedding", embed, Dsv.dev(Tensor.of(idv, 1, ids.length)));
        h = o("aten::repeat", h.unsqueeze(2), new long[]{1, 1, hc, 1});
        float[] one = new float[ids.length * hc];
        for (int i = 0; i < ids.length; i++) {
            one[i * hc] = 1f;
        }
        Tensor pre = Dsv.dev(Tensor.of(one, 1, ids.length, hc));
        List<Tensor> taps = draft == null ? null : new ArrayList<>();
        for (DeepSeekBlock block : blocks) {
            if (trace != null) {
                if (block.hasEngram()) {
                    h = block.applyEngram(h, hashes);
                    trace.put("L" + block.layer + ".engram_out", h);
                }
                if (taps != null && isTap(block.layer)) {
                    taps.add(o("aten::mean.dim", h, new long[]{2}, false));
                }
                Tensor[] r = block.forward(h, startPos, pre, trace);
                h = r[0];
                pre = r[1];
                trace.put("L" + block.layer + ".out", h);
                continue;
            }
            // each layer in its own scope: its temporaries (all the prompt's rows) are freed before the next layer
            final Tensor hin = h, pin = pre;
            final List<Tensor> tapList = taps;
            Tensor[] r = Dsv.layerScoped(() -> {
                Tensor x = block.hasEngram() ? block.applyEngram(hin, hashes) : hin;
                if (tapList != null && isTap(block.layer)) {
                    // the draft reads the attention input of its target layers (after the engram), averaged over the copies
                    tapList.add(Dsv.keep(o("aten::mean.dim", x, new long[]{2}, false)));
                }
                return block.forward(x, startPos, pin, null);
            });
            hin.close();
            pin.close();
            h = r[0];
            pre = r[1];
        }
        if (taps != null && !taps.isEmpty()) {
            tapOut = taps.size() == 1 ? taps.get(0) : Dsv.cat(-1, taps.toArray(new Tensor[0]));
        }
        Tensor collapsed = DeepSeekBlock.hcPre(h, pre);
        Tensor normed = DeepSeekOps.rmsNorm(collapsed, normWeight, args.normEps());
        Tensor last = o("aten::select.int", normed, 1L, normed.size(1) - 1);
        return logits(last);
    }

    /**
     * The prompts of several new sequences (each from position 0) in one pass over the layers.
     *
     * @param prompts  the prompts.
     * @param sessions the sequences' states.
     * @return the last position's logits of each prompt, {@code [n, vocab]} (float32).
     */
    public Tensor forwardPrefillBatch(int[][] prompts, Session[] sessions) {
        int n = prompts.length;
        int hc = args.hcMult();
        int total = 0;
        int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            lens[i] = prompts[i].length;
            total += lens[i];
        }
        Tensor hashes = null;
        if (engramHash != null) {
            Tensor[] rows = new Tensor[n];
            for (int i = 0; i < n; i++) {
                engramHash.use(sessions[i].history);
                rows[i] = engramHash.forward(prompts[i], 0);
            }
            hashes = n == 1 ? rows[0] : Dsv.cat(1, rows);
        }
        long[] idv = new long[total];
        int at = 0;
        for (int[] p : prompts) {
            for (int t : p) {
                idv[at++] = t;
            }
        }
        Tensor h = o("aten::embedding", embed, Dsv.dev(Tensor.of(idv, 1, total)));
        h = o("aten::repeat", h.unsqueeze(2), new long[]{1, 1, hc, 1});
        float[] one = new float[total * hc];
        for (int i = 0; i < total; i++) {
            one[i * hc] = 1f;
        }
        Tensor pre = Dsv.dev(Tensor.of(one, 1, total, hc));
        final List<Tensor> taps = draft == null ? null : new ArrayList<>();
        for (DeepSeekBlock block : blocks) {
            final Tensor hin = h, pin = pre;
            final Tensor hs = hashes;
            DeepSeekAttention.State[] states = new DeepSeekAttention.State[n];
            for (int i = 0; i < n; i++) {
                states[i] = sessions[i].layers[block.layer];
            }
            Tensor[] r = Dsv.layerScoped(() -> {
                Tensor x = block.hasEngram() ? block.applyEngram(hin, hs) : hin;
                if (taps != null && isTap(block.layer)) {
                    taps.add(Dsv.keep(o("aten::mean.dim", x, new long[]{2}, false)));
                }
                return block.forwardPrefillBatch(x, lens, states, pin);
            });
            hin.close();
            pin.close();
            h = r[0];
            pre = r[1];
        }
        if (taps != null && !taps.isEmpty()) {
            Tensor tap = taps.size() == 1 ? taps.get(0) : Dsv.cat(-1, taps.toArray(new Tensor[0]));
            int from = 0;
            for (int i = 0; i < n; i++) {
                seedDraft(sessions[i], o("aten::narrow", tap, 1L, (long) from, (long) lens[i]));
                from += lens[i];
            }
        }
        Tensor normed = DeepSeekOps.rmsNorm(DeepSeekBlock.hcPre(h, pre), normWeight, args.normEps());
        long[] lastRows = new long[n];
        int end = 0;
        for (int i = 0; i < n; i++) {
            end += lens[i];
            lastRows[i] = end - 1;
        }
        Tensor last = o("aten::index_select", normed.reshape(total, -1), 0L, Dsv.dev(Tensor.of(lastRows, n)));
        return logits(last);
    }

    /**
     * One decode token for each of several sequences (different positions), in a single pass over the layers.
     *
     * @param ids      each sequence's new token.
     * @param pos      each token's absolute position.
     * @param sessions the sequences' states (not interchangeable with {@link #use}).
     * @return the logits {@code [n, vocab]} (float32).
     */
    public Tensor forwardBatch(int[] ids, int[] pos, Session[] sessions) {
        int n = ids.length;
        int hc = args.hcMult();
        long tm = System.nanoTime();
        Tensor hashes = null;
        if (engramHash != null) {
            Tensor[] rows = new Tensor[n];
            for (int i = 0; i < n; i++) {
                engramHash.use(sessions[i].history);
                rows[i] = engramHash.forward(new int[]{ids[i]}, pos[i]);
            }
            hashes = n == 1 ? rows[0] : Dsv.cat(1, rows);
        }
        long[] idv = new long[n];
        for (int i = 0; i < n; i++) {
            idv[i] = ids[i];
        }
        Tensor h = o("aten::embedding", embed, Dsv.dev(Tensor.of(idv, 1, n)));
        h = o("aten::repeat", h.unsqueeze(2), new long[]{1, 1, hc, 1});
        float[] one = new float[n * hc];
        for (int i = 0; i < n; i++) {
            one[i * hc] = 1f;
        }
        Tensor pre = Dsv.dev(Tensor.of(one, 1, n, hc));
        DeepSeekAttention.BatchShared batch = new DeepSeekAttention.BatchShared();
        tm = Dsv.tick("model.engram+embed", tm);
        List<Tensor> taps = draft == null ? null : new ArrayList<>();
        for (DeepSeekBlock block : blocks) {
            if (block.hasEngram()) {
                h = block.applyEngram(h, hashes);
            }
            if (taps != null && isTap(block.layer)) {
                taps.add(o("aten::mean.dim", h, new long[]{2}, false));
            }
            DeepSeekAttention.State[] states = new DeepSeekAttention.State[n];
            for (int i = 0; i < n; i++) {
                states[i] = sessions[i].layers[block.layer];
            }
            Tensor[] r = block.forwardBatch(h, pos, states, pre, batch);
            h = r[0];
            pre = r[1];
        }
        tm = Dsv.tick("model.blocks", tm);
        if (taps != null && !taps.isEmpty()) {
            tapOut = taps.size() == 1 ? taps.get(0) : Dsv.cat(-1, taps.toArray(new Tensor[0]));
        }
        Tensor normed = DeepSeekOps.rmsNorm(DeepSeekBlock.hcPre(h, pre), normWeight, args.normEps());
        Tensor logits = logits(normed.reshape(n, -1));
        Dsv.tick("model.head", tm);
        return logits;
    }

    /** The caches of one sequence in each layer (for the pointer tables of a captured graph). */
    static DeepSeekAttention.State[] layerStates(Session s) {
        return s.layers;
    }

    /**
     * The vocabulary logits {@code [rows, vocab]} (float32) of normalized hidden states {@code [rows, dim]}. The inputs
     * are bf16 and so is the head, so the bf16 GEMM with float32 output computes exactly the float32 products.
     */
    Tensor logits(Tensor x) {
        if (headBf16 != null && x.dtype() == ScalarType.BFloat16 && !headMmUnavailable) {
            try {
                return o("aten::mm.dtype", x, o("aten::t", headBf16), ScalarType.Float);
            } catch (RuntimeException e) {
                headMmUnavailable = true;
            }
        }
        Tensor w = head != null ? head : DeepSeekOps.f32(headBf16);
        return o("aten::linear", DeepSeekOps.f32(x), w);
    }

    /** Number of backbone layers. */
    int layerCount() {
        return blocks.size();
    }

    /** Whether this model has engram layers (their table rows are read from the checkpoint on the host). */
    boolean hasEngram() {
        return engramHash != null;
    }

    /**
     * The table rows of every engram layer for one decode token of each of {@code nb} rows, in engram slot order
     * ({@code [1, nb, cols, headDim]}): rows {@code n..nb-1} are padding. Reads the checkpoint, so it runs before a
     * captured graph is replayed, not inside it.
     */
    Tensor[] engramLookup(int[] ids, int[] pos, Session[] sessions, Session pad, int nb) {
        if (engramHash == null) {
            return new Tensor[0];
        }
        int n = ids.length;
        long t = System.nanoTime();
        int cols = engramHash.cols();
        int slots = args.engramLayers().length;
        long[][] raw = new long[nb][];
        for (int i = 0; i < nb; i++) {
            engramHash.use((i < n ? sessions[i] : pad).history);
            raw[i] = engramHash.forwardRaw(new int[]{i < n ? ids[i] : 0}, i < n ? pos[i] : 1);
        }
        t = Dsv.tick("engram.hash", t);
        Tensor[] out = new Tensor[slots];
        for (DeepSeekBlock block : blocks) {
            if (block.hasEngram()) {
                int slot = block.engramSlot();
                long[] layerIds = new long[nb * cols];
                for (int i = 0; i < nb; i++) {
                    System.arraycopy(raw[i], slot * cols, layerIds, i * cols, cols);
                }
                out[slot] = block.engramEmbedHost(layerIds, new long[]{1, nb, cols});
            }
        }
        Dsv.tick("engram.rows", t);
        return out;
    }

    /**
     * One decode token for each of {@code nb} sequences as a CUDA-graph-capturable pass: the token ids, positions,
     * cache pointers and engram rows are static device tensors, and sizes depend only on {@code nb} and
     * {@code lenCap} (an upper bound of every position + 1).
     *
     * @param idsT       int64 {@code [1, nb]}.
     * @param posT       int64 {@code [nb]}.
     * @param pre0       float32 {@code [1, nb, hc]}: the first layer's collapse weights (1, 0, 0, ...).
     * @param engramEmb  the engram rows by slot.
     * @return the logits {@code [nb, vocab]} (float32).
     */
    Tensor[] forwardBatchGraph(Tensor idsT, Tensor posT, Tensor pre0, int nb, int lenCap, DeepSeekAttention.GraphPtrs gp,
                               Tensor[] engramEmb, boolean aux) {
        int hc = args.hcMult();
        Tensor h = o("aten::embedding", embed, idsT);
        h = o("aten::repeat", h.unsqueeze(2), new long[]{1, 1, hc, 1});
        Tensor pre = pre0;
        DeepSeekAttention.BatchShared batch = new DeepSeekAttention.BatchShared();
        List<Tensor> taps = aux && draft != null ? new ArrayList<>() : null;
        Dsv.stamp("model.embed");
        for (DeepSeekBlock block : blocks) {
            if (block.hasEngram()) {
                h = block.applyEngramEmb(h, engramEmb[block.engramSlot()]);
                Dsv.stamp("model.engram");
            }
            if (taps != null && isTap(block.layer)) {
                taps.add(o("aten::mean.dim", h, new long[]{2}, false));
            }
            // each layer in its own scope: its temporaries are freed before the next layer, so the graph's memory pool
            // reuses them (otherwise the captured graph holds every layer's temporaries at once)
            final Tensor hin = h, pin = pre;
            Tensor[] r = Dsv.layerScoped(() -> block.forwardBatchGraph(hin, posT, pin, nb, lenCap, gp, batch));
            h = r[0];
            pre = r[1];
        }
        Tensor normed = DeepSeekOps.rmsNorm(DeepSeekBlock.hcPre(h, pre), normWeight, args.normEps());
        Tensor logits = logits(normed.reshape(nb, -1));
        Dsv.stamp("model.head");
        if (!aux) {
            return new Tensor[]{logits};
        }
        Tensor tap = taps == null || taps.isEmpty() ? null
                : (taps.size() == 1 ? taps.get(0) : Dsv.cat(-1, taps.toArray(new Tensor[0]))).reshape(nb, -1);
        return new Tensor[]{logits, tap, o("aten::argmax", logits, -1L, false)};
    }

    /** Whether {@code layer} keeps a compressor partial group (rolled back after a rejected verify tail). */
    boolean hasCompressorState(int layer) {
        return blocks.get(layer).hasCompressorState();
    }

    /** The row width of a compressor layer's saved projections. */
    int compressorWidth() {
        return args.headDim();
    }

    /**
     * After a verify step: every compressor's unfinished group is written from the rows each sequence processed
     * ({@code base[i]} its first row, {@code p0[i]} that row's position, {@code last[i]} its last processed row index).
     */
    void commitCompressors(DeepSeekAttention.GraphPtrs gp, Tensor base, Tensor p0, Tensor last) {
        for (int l = 0; l < blocks.size(); l++) {
            if (blocks.get(l).hasCompressorState()) {
                blocks.get(l).commitCompressor(gp.view(l, DeepSeekAttention.GraphPtrs.KV_STATE),
                        gp.view(l, DeepSeekAttention.GraphPtrs.SCORE), gp.rowKv[l], gp.rowScore[l], base, p0, last);
            }
        }
    }

    @Override
    public void close() throws IOException {
        ck.close();
    }
}
