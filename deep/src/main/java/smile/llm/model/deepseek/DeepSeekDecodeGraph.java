/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */
package smile.llm.model.deepseek;

import java.lang.foreign.MemorySegment;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

/**
 * One rank's CUDA graph of the batched decode step for a batch size {@code nb} and a position capacity {@code lenCap}.
 *
 * <p>The decode step is thousands of tiny launches, so replaying a captured graph removes the host cost that bounds it.
 * Everything the graph reads from the host lives in static device tensors that are updated before each replay: the token
 * ids and positions, the pointer block that addresses each sequence's caches (rebuilt only when the batch composition
 * changes), and the engram table rows (looked up on the host from the checkpoint). Rows {@code n..nb-1} of a smaller batch
 * run on a dummy sequence and are ignored.
 *
 * @author Haifeng Li
 */
final class DeepSeekDecodeGraph {
    private static final int KINDS = DeepSeekAttention.GraphPtrs.KINDS;

    private final DeepSeekModel model;
    private final int layers;
    final int nb;
    final int lenCap;
    private final Tensor ids;
    private final Tensor pos;
    private final Tensor pre0;
    private final Tensor ptrBlock;
    private final Tensor[] engramEmb;
    private final DeepSeekAttention.GraphPtrs ptrs;
    private Tensor logits;
    /** Verify graphs: the main hidden states of every row and each row's argmax token; {@code jIdx} is each row's index in its block. */
    final boolean verify;
    private Tensor jIdx;
    private Tensor tap;
    private Tensor argmax;
    private MemorySegment graph;
    /** GPU stamps of the sections of one replay ({@code SMILE_DS_STAMP=1}): the names, in stamp order. */
    private static final boolean STAMP = System.getenv("SMILE_DS_STAMP") != null;
    private Tensor stampBuf;
    private java.util.List<String> stampNames;
    private final java.util.Map<String, long[]> stampSum = new java.util.LinkedHashMap<>();
    private long stampReplays;
    /** The sessions the pointer block currently describes. */
    private DeepSeekModel.Session[] composition;

    private DeepSeekDecodeGraph(DeepSeekModel model, DeepSeekModel.Session pad, int nb, int lenCap, int hc, boolean verify) {
        this.model = model;
        this.layers = model.layerCount();
        this.nb = nb;
        this.lenCap = lenCap;
        this.verify = verify;
        long[] padIds = new long[nb];
        long[] padPos = new long[nb];
        java.util.Arrays.fill(padPos, 1L);
        this.ids = Dsv.dev(Tensor.of(padIds, 1, nb));
        this.pos = Dsv.dev(Tensor.of(padPos, nb));
        float[] one = new float[nb * hc];
        for (int i = 0; i < nb; i++) {
            one[i * hc] = 1f;
        }
        this.pre0 = Dsv.dev(Tensor.of(one, 1, nb, hc));
        this.ptrBlock = Dsv.dev(Tensor.of(new long[layers * KINDS * nb], (long) layers * KINDS * nb));
        this.ptrs = new DeepSeekAttention.GraphPtrs(ptrBlock, nb);
        if (verify) {
            this.ptrs.verify = true;
            this.jIdx = Dsv.dev(Tensor.of(new long[nb], nb));
            this.jIdx.detachFromScopes();
            this.ptrs.jIdx = jIdx;
            this.ptrs.rowKv = new Tensor[layers];
            this.ptrs.rowScore = new Tensor[layers];
            for (int l = 0; l < layers; l++) {
                if (model.hasCompressorState(l)) {
                    ptrs.rowKv[l] = Dsv.zeros(smile.deep.tensor.ScalarType.Float, nb, model.compressorWidth());
                    ptrs.rowScore[l] = Dsv.zeros(smile.deep.tensor.ScalarType.Float, nb, model.compressorWidth());
                    ptrs.rowKv[l].detachFromScopes();
                    ptrs.rowScore[l].detachFromScopes();
                }
            }
        }
        this.engramEmb = model.engramLookup(new int[0], new int[0], new DeepSeekModel.Session[0], pad, nb);
    }

    /**
     * Warms the step up eagerly (kernels, workspaces), then captures it. Must run on the rank's thread, outside any
     * tensor scope, and all ranks capture together (the step contains NCCL collectives).
     */
    static DeepSeekDecodeGraph capture(DeepSeekModel model, DeepSeekModel.Session pad, int nb, int lenCap, int hc,
                                       int deviceIndex) {
        return capture(model, pad, nb, lenCap, hc, deviceIndex, false);
    }

    /** As above for a verify step over {@code nb} rows (the rows of each sequence follow each other). */
    static DeepSeekDecodeGraph capture(DeepSeekModel model, DeepSeekModel.Session pad, int nb, int lenCap, int hc,
                                       int deviceIndex, boolean verify) {
        DeepSeekDecodeGraph g = new DeepSeekDecodeGraph(model, pad, nb, lenCap, hc, verify);
        g.fillPointers(new DeepSeekModel.Session[0], 0, pad);
        boolean aux = verify;
        try (var guard = Tensor.noGradGuard()) {
            Dsv.scoped(() -> model.forwardBatchGraph(g.ids, g.pos, g.pre0, nb, lenCap, g.ptrs, g.engramEmb, aux)[0]).close();
            g.graph = Native.cudaGraphCreate();
            if (STAMP) {
                g.stampBuf = Dsv.dev(Tensor.of(new long[4096], 4096));
            }
            Native.cudaGraphCaptureBegin(g.graph, deviceIndex);
            try {
                if (STAMP) {
                    Dsv.beginStamps(g.stampBuf);
                }
                Tensor[] out = new Tensor[1];
                Dsv.scoped(() -> {
                    out[0] = null;
                    Tensor[] r = model.forwardBatchGraph(g.ids, g.pos, g.pre0, nb, lenCap, g.ptrs, g.engramEmb, aux);
                    g.logits = r[0];
                    if (aux) {
                        g.tap = r[1];
                        g.argmax = r[2];
                        if (g.tap != null) {
                            g.tap.detachFromScopes();
                        }
                        g.argmax.detachFromScopes();
                    }
                    return r[0];
                });
            } finally {
                if (STAMP) {
                    g.stampNames = Dsv.endStamps();
                }
                Native.cudaGraphCaptureEnd(g.graph);
            }
        }
        g.logits.detachFromScopes();
        return g;
    }

    private static Tensor pick(DeepSeekAttention.State st, int kind) {
        return switch (kind) {
            case DeepSeekAttention.GraphPtrs.WINDOW -> st.window();
            case DeepSeekAttention.GraphPtrs.COMPRESS -> st.compress();
            case DeepSeekAttention.GraphPtrs.KV_STATE -> st.kvState();
            case DeepSeekAttention.GraphPtrs.SCORE -> st.score();
            default -> st.indexK();
        };
    }

    /** Writes the cache pointers of rows {@code 0..n-1} ({@code rows}) and the padding rows ({@code pad}). */
    private void fillPointers(DeepSeekModel.Session[] rows, int n, DeepSeekModel.Session pad) {
        DeepSeekModel.Session[] perRow = new DeepSeekModel.Session[nb];
        for (int i = 0; i < nb; i++) {
            perRow[i] = i < n ? rows[i] : pad;
        }
        fillPointers(perRow);
    }

    /** Time and count of pointer-table rebuilds (the batch composition changed), for {@link DeepSeek#specStats}. */
    static final java.util.concurrent.atomic.AtomicLong FILL_NS = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong FILLS = new java.util.concurrent.atomic.AtomicLong();

    /** Writes the cache pointers of every row ({@code perRow.length == nb}). */
    private void fillPointers(DeepSeekModel.Session[] perRow) {
        long t0 = System.nanoTime();
        fillPointers0(perRow);
        FILL_NS.addAndGet(System.nanoTime() - t0);
        FILLS.incrementAndGet();
    }

    private void fillPointers0(DeepSeekModel.Session[] perRow) {
        long[] all = new long[layers * KINDS * nb];
        Tensor[] ts = new Tensor[nb];
        for (int l = 0; l < layers; l++) {
            for (int kind = 0; kind < KINDS; kind++) {
                for (int i = 0; i < nb; i++) {
                    ts[i] = pick(DeepSeekModel.layerStates(perRow[i])[l], kind);
                }
                System.arraycopy(Native.dsDataPtrs(ts), 0, all, (l * KINDS + kind) * nb, nb);
            }
        }
        Aten.call("aten::copy_", ptrBlock, Dsv.dev(Tensor.of(all, all.length)), false);
    }

    /**
     * One decode step: uploads the inputs, replays the graph and returns the logits of the first {@code n} rows
     * ({@code [n, vocab]}, owned by the caller).
     */
    Tensor replay(int[] tokens, int[] positions, DeepSeekModel.Session[] rows, DeepSeekModel.Session pad) {
        int n = tokens.length;
        long tk = System.nanoTime();
        boolean same = composition != null && composition.length == n;
        for (int i = 0; same && i < n; i++) {
            same = composition[i] == rows[i];
        }
        if (!same) {
            fillPointers(rows, n, pad);
            composition = rows.clone();
        }
        long[] idv = new long[nb];
        long[] pv = new long[nb];
        for (int i = 0; i < nb; i++) {
            idv[i] = i < n ? tokens[i] : 0;
            pv[i] = i < n ? positions[i] : 1;
        }
        Aten.call("aten::copy_", ids, Dsv.dev(Tensor.of(idv, 1, nb)), false);
        Aten.call("aten::copy_", pos, Dsv.dev(Tensor.of(pv, nb)), false);
        tk = Dsv.tick("replay.inputs", tk);
        if (model.hasEngram()) {
            Tensor[] e = model.engramLookup(tokens, positions, rows, pad, nb);
            tk = Dsv.tick("replay.engram.lookup", tk);
            for (int s = 0; s < e.length; s++) {
                Aten.call("aten::copy_", engramEmb[s], e[s], false);
            }
            tk = Dsv.tick("replay.engram.copy", tk);
        }
        Native.cudaGraphReplay(graph);
        tk = Dsv.tick("replay.launch", tk);
        accumulateStamps();
        return Aten.one("aten::clone", Dsv.narrow(logits, 0, 0, n));
    }

    /**
     * One verify step: {@code rowsPerSeq} rows per sequence, row {@code j * seqs + i} being the {@code j}-th token of
     * sequence {@code i}. Uploads the inputs, replays and returns the argmax token of every row.
     *
     * @param tokens    the token of every row ({@code nb} entries, padding rows included).
     * @param positions the position of every row.
     * @param sessions  the sequence of every row (the padding session for padding rows).
     */
    /** Host phases of the verify replays in nanoseconds: inputs, engram lookup, replay + wait; see {@link DeepSeek#specStats}. */
    static final long[] VERIFY_NS = new long[3];

    long[] replayVerify(int[] tokens, int[] positions, int[] jRow, DeepSeekModel.Session[] sessions, DeepSeekModel.Session pad) {
        long tq = System.nanoTime();
        boolean same = composition != null && composition.length == nb;
        for (int i = 0; same && i < nb; i++) {
            same = composition[i] == sessions[i];
        }
        if (!same) {
            fillPointers(sessions);
            composition = sessions.clone();
        }
        long[] idv = new long[nb];
        long[] pv = new long[nb];
        for (int i = 0; i < nb; i++) {
            idv[i] = tokens[i];
            pv[i] = positions[i];
        }
        Aten.call("aten::copy_", ids, Dsv.dev(Tensor.of(idv, 1, nb)), false);
        Aten.call("aten::copy_", pos, Dsv.dev(Tensor.of(pv, nb)), false);
        long[] jv = new long[nb];
        for (int i = 0; i < nb; i++) {
            jv[i] = jRow[i];
        }
        Aten.call("aten::copy_", jIdx, Dsv.dev(Tensor.of(jv, nb)), false);
        long te = System.nanoTime();
        if (model.hasEngram()) {
            Tensor[] e = model.engramLookup(tokens, positions, sessions, pad, nb);
            for (int s = 0; s < e.length; s++) {
                Aten.call("aten::copy_", engramEmb[s], e[s], false);
            }
        }
        long tr = System.nanoTime();
        Native.cudaGraphReplay(graph);
        long[] result = Dsv.host(argmax).longArray();
        accumulateStamps();
        if (Thread.currentThread().getName().endsWith("0") || true) {
            synchronized (VERIFY_NS) {
                VERIFY_NS[0] += te - tq;
                VERIFY_NS[1] += tr - te;
                VERIFY_NS[2] += System.nanoTime() - tr;
            }
        }
        return result;
    }

    /** The logits of the first {@code rows} rows. */
    Tensor rowLogits(int rows) {
        return Dsv.narrow(logits, 0, 0, rows);
    }

    /**
     * After the verify step: writes each compressor's unfinished group from the rows each sequence processed, puts the
     * accepted rows' main hidden states into the draft rings and keeps the last one as the draft's latest.
     *
     * @param base      the first row of each sequence's block.
     * @param last      the index (in its block) of each sequence's last processed row.
     * @param positions the position of each sequence's first row.
     */
    void commit(int[] base, int[] last, DeepSeekModel.Session[] sessions, int[] positions) {
        int n = last.length;
        long[] b = new long[n];
        long[] p = new long[n];
        long[] l = new long[n];
        for (int i = 0; i < n; i++) {
            b[i] = base[i];
            p[i] = positions[i];
            l[i] = last[i];
        }
        model.commitCompressors(ptrs, Dsv.dev(Tensor.of(b, n)), Dsv.dev(Tensor.of(p, n)), Dsv.dev(Tensor.of(l, n)));
        if (tap != null) {
            model.commitDraftBatch(java.util.Arrays.copyOf(sessions, n), tap, base, last, positions);
        }
    }

    private void accumulateStamps() {
        if (stampBuf != null) {
            long[] t = Dsv.host(stampBuf).longArray();
            for (int i = 1; i < stampNames.size(); i++) {
                long[] acc = stampSum.computeIfAbsent(stampNames.get(i), k -> new long[2]);
                acc[0] += t[i] - t[i - 1];
                acc[1]++;
            }
            stampSum.computeIfAbsent("TOTAL (start..end)", k -> new long[2])[0] += t[stampNames.size() - 1] - t[0];
            stampReplays++;
        }
    }

    /** GPU time per section per replay (microseconds), when {@code SMILE_DS_STAMP} is set. */
    String stampReport() {
        StringBuilder sb = new StringBuilder();
        for (var e : stampSum.entrySet()) {
            sb.append(String.format("STAMP %-22s %9.1f us/replay  (%d stamps/replay)%n", e.getKey(),
                    e.getValue()[0] / 1e3 / Math.max(1, stampReplays), e.getValue()[1] / Math.max(1, stampReplays)));
        }
        return sb.toString();
    }

    void close() {
        if (graph != null) {
            Native.cudaGraphDestroy(graph);
            graph = null;
        }
    }
}
