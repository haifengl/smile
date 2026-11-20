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
 * A CUDA graph of one MTP row for each of {@code nb} requests: the embedding and hidden projection, the MTP layer on the
 * decode kernels, the final norm and the greedy token. Every draft step and every position of the post-verify absorb is
 * one replay; a step's token and hidden output can feed the next step on the device ({@link #chain}). Unused rows run on
 * the padding session's MTP cache.
 *
 * @author Haifeng Li
 */
final class GlmMtpGraph {
    final int nb;
    private final GlmModel model;
    private final long maxPools;
    private final int dim;
    private final Tensor ids;
    private final Tensor hidden;
    private final Tensor lens;
    private final Tensor tables;
    private Tensor out;
    private Tensor next;
    private MemorySegment graph;

    private GlmMtpGraph(GlmModel model, int nb, long maxPools, int dim) {
        this.model = model;
        this.nb = nb;
        this.maxPools = maxPools;
        this.dim = dim;
        this.ids = keep(Tensor.of(new long[nb], nb).to(device()));
        this.hidden = keep(zeros(smile.deep.tensor.ScalarType.Float, nb, dim));
        this.lens = keep(Tensor.of(new int[nb], nb).to(device()));
        this.tables = keep(Tensor.of(new long[3 * nb], 3, nb).to(device()));
    }

    /** Captures the graph (on the rank thread; all ranks of a group together, see {@link GlmDecodeGraph#capture}). */
    static GlmMtpGraph capture(GlmModel model, int nb, long maxPools, int dim, int deviceIndex,
                               java.util.concurrent.CyclicBarrier barrier, boolean head) throws Exception {
        GlmMtpGraph g = new GlmMtpGraph(model, nb, maxPools, dim);
        g.fill(new GlmModel.Session[0]);
        try (var guard = Tensor.noGradGuard()) {
            Tensor[] warm = scopedAll(() -> model.mtpGraphBody(g.ids, g.hidden, g.tables, g.lens, maxPools, head));
            Aten.one("aten::sum", warm[0]).doubleValue();
            for (Tensor t : warm) {
                t.close();
            }
            if (barrier != null) {
                barrier.await(5, java.util.concurrent.TimeUnit.MINUTES);
            }
            g.graph = Native.cudaGraphCreate();
            Native.cudaGraphCaptureBegin(g.graph, deviceIndex);
            Tensor[] r;
            try {
                r = scopedAll(() -> model.mtpGraphBody(g.ids, g.hidden, g.tables, g.lens, maxPools, head));
            } finally {
                Native.cudaGraphCaptureEnd(g.graph);
            }
            g.out = r[0];
            g.next = r.length > 1 ? r[1] : null;
        }
        g.out.detachFromScopes();
        if (g.next != null) {
            g.next.detachFromScopes();
        }
        return g;
    }

    private void fill(GlmModel.Session[] rows) {
        Aten.call("aten::copy_", lens, Tensor.of(model.mtpLengths(rows, nb), nb).to(device()), false);
        Aten.call("aten::copy_", tables, Tensor.of(model.mtpTables(rows, nb), 3, nb).to(device()), false);
    }

    /**
     * Sets the next step's inputs from the host: the tokens and the hidden rows of the real requests.
     *
     * @param tokens the following token of each request.
     * @param rows   {@code [n, dim]} the previous hidden rows.
     */
    void load(int[] tokens, Tensor rows) {
        long[] idv = new long[nb];
        for (int i = 0; i < tokens.length; i++) {
            idv[i] = tokens[i];
        }
        Aten.call("aten::copy_", ids, Tensor.of(idv, nb).to(device()), false);
        Aten.call("aten::copy_", narrow(hidden, 0, 0, rows.size(0)), rows, false);
    }

    /** Feeds the last step's token and output into the next step (device to device). */
    void chain() {
        Aten.call("aten::copy_", ids, next, false);
        Aten.call("aten::copy_", hidden, out, false);
    }

    /** Runs one step for these requests (their MTP caches grow by one slot). */
    void run(GlmModel.Session[] rows) {
        fill(rows);
        Native.cudaGraphReplay(graph);
        model.commitMtp(rows);
    }

    /** The outputs of the last step {@code [nb, dim]} (a view of the graph's buffer). */
    Tensor out() {
        return out;
    }

    /** The greedy tokens of the last step, int64 {@code [nb]} (a view of the graph's buffer). */
    Tensor next() {
        return next;
    }

    void close() {
        if (graph != null) {
            Native.cudaGraphDestroy(graph);
            graph = null;
        }
        for (Tensor t : new Tensor[]{out, next, ids, hidden, lens, tables}) {
            if (t != null) {
                t.close();
            }
        }
    }
}
