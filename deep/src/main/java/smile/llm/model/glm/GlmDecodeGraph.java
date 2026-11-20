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

import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static smile.llm.model.glm.Gx.*;

/**
 * A CUDA graph of one decode step of {@code nb} requests of a {@link GlmModel} replica: the whole forward (embedding,
 * every layer, the head) captured once and replayed per step, which removes the launch overhead of the hundreds of
 * small operations per layer. The graph reads its inputs from fixed device tensors (tokens, slots, token counts and the
 * addresses of every request's MLA caches), so before a replay the host refreshes those; the KDA states live in
 * pools indexed by slot, and the MLA caches are reached through the address tables, so request growth (a cache
 * buffer moving) needs no recapture. Unused rows run on the model's padding session.
 *
 * @author Haifeng Li
 */
final class GlmDecodeGraph {
    final int nb;
    private final GlmModel model;
    private final long maxPools;
    private final Tensor ids;
    private final Tensor slots;
    private final Tensor lens;
    private final Tensor tables;
    private Tensor logits;
    private Tensor hidden;
    private MemorySegment graph;

    private GlmDecodeGraph(GlmModel model, int nb, long maxPools) {
        this.model = model;
        this.nb = nb;
        this.maxPools = maxPools;
        this.ids = keep(Tensor.of(new long[nb], nb).to(device()));
        this.slots = keep(Tensor.of(new int[nb], nb).to(device()));
        this.lens = keep(Tensor.of(new int[nb], nb).to(device()));
        this.tables = keep(Tensor.of(new long[model.mlaLayerCount() * 3 * nb], (long) model.mlaLayerCount() * 3, nb).to(device()));
    }

    /**
     * Captures the graph (must run on the replica's rank thread; every rank of an expert-parallel group captures
     * together, because the captured step contains collectives).
     *
     * @param model       the replica.
     * @param nb          the batch bucket.
     * @param maxPools    row stride of the score buffers: at least {@code maxSeqLen / 4} rounded up to 512.
     * @param deviceIndex the CUDA device ordinal.
     * @param barrier     shared by all ranks of a group (or {@code null}): every rank finishes and synchronizes its eager
     *                    warm-up, whose collectives need all ranks, before any rank begins capturing.
     * @return the captured graph.
     */
    static GlmDecodeGraph capture(GlmModel model, int nb, long maxPools, int deviceIndex,
                                  java.util.concurrent.CyclicBarrier barrier) throws Exception {
        GlmDecodeGraph g = new GlmDecodeGraph(model, nb, maxPools);
        g.fill(new GlmModel.Session[0], new int[0]);
        try (var guard = Tensor.noGradGuard()) {
            // eager warm-up: libraries initialise their workspaces outside the capture
            Tensor[] warm = scopedAll(() -> model.forwardGraph(g.ids, g.slots, g.lens, g.tables, maxPools));
            Aten.one("aten::sum", warm[1]).doubleValue();    // wait for this rank's warm-up work on the device
            for (Tensor t : warm) {
                t.close();
            }
            if (barrier != null) {
                barrier.await(5, java.util.concurrent.TimeUnit.MINUTES);
            }
            g.graph = Native.cudaGraphCreate();
            Native.cudaGraphCaptureBegin(g.graph, deviceIndex);
            Tensor[] out;
            try {
                out = scopedAll(() -> model.forwardGraph(g.ids, g.slots, g.lens, g.tables, maxPools));
            } finally {
                Native.cudaGraphCaptureEnd(g.graph);
            }
            g.hidden = out[0];
            g.logits = out[1];
        }
        g.hidden.detachFromScopes();
        g.logits.detachFromScopes();
        return g;
    }

    /** Writes the inputs of a step: real rows first, the padding session after. */
    private void fill(GlmModel.Session[] rows, int[] tokens) {
        long[] idv = new long[nb];
        for (int i = 0; i < tokens.length; i++) {
            idv[i] = tokens[i];
        }
        Aten.call("aten::copy_", ids, Tensor.of(idv, nb).to(device()), false);
        Aten.call("aten::copy_", slots, Tensor.of(model.slots(rows, nb), nb).to(device()), false);
        Aten.call("aten::copy_", lens, Tensor.of(model.lengths(rows, nb), nb).to(device()), false);
        long[] t = model.prepareDecode(rows, nb);
        Aten.call("aten::copy_", tables, Tensor.of(t, tables.size(0), nb).to(device()), false);
    }

    /**
     * Runs one decode step.
     *
     * @param tokens the last token of each request.
     * @param rows   the requests' sessions (distinct, at most {@code nb}).
     * @return the logits of the real rows {@code [n, vocab]} (caller owns).
     */
    Tensor replay(int[] tokens, GlmModel.Session[] rows) {
        fill(rows, tokens);
        Native.cudaGraphReplay(graph);
        model.commitDecode(rows);
        return Aten.one("aten::clone", narrow(logits, 0, 0, rows.length));
    }

    /** The hidden rows of the last replay {@code [nb, dim]} (a view of the graph's buffer). */
    Tensor hidden() {
        return hidden;
    }

    /** Destroys the CUDA graph (on the rank thread; before the NCCL communicators it uses are freed). */
    void close() {
        if (graph != null) {
            Native.cudaGraphDestroy(graph);
            graph = null;
        }
        if (logits != null) {
            logits.close();
            logits = null;
        }
        if (hidden != null) {
            hidden.close();
            hidden = null;
        }
    }
}
