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
 * A CUDA graph of the verify window of a speculative round: {@code nb} request slots of {@code rows} tokens (the last
 * committed token and the drafts), the whole forward captured once. After a replay the host decides how many rows of
 * each request were accepted and {@link #commit} keeps those (KDA states, MLA rings, lengths) without rerunning the model.
 *
 * @author Haifeng Li
 */
final class GlmWindowGraph {
    final int nb;
    final int rows;
    private final GlmModel model;
    private final GlmWindow window;
    private Tensor hidden;
    private Tensor logits;
    private MemorySegment graph;
    /** The MTP layer over the same window (absorbs the kept rows into the MTP caches in one replay), or null. */
    private GlmWindow mtpWindow;
    private Tensor mtpOut;
    private Tensor mtpNext;
    private MemorySegment mtpGraph;

    /** {@code SMILE_GLM_MTP_WINDOW=0} keeps the sequential single-row MTP passes for the absorb. */
    private static final boolean MTP_WINDOW = !"0".equals(System.getenv("SMILE_GLM_MTP_WINDOW"));

    private GlmWindowGraph(GlmModel model, int nb, int rows, long maxPools) {
        this.model = model;
        this.nb = nb;
        this.rows = rows;
        this.window = model.newWindow(nb, rows, maxPools);
    }

    /** Captures the graph (on the rank thread; all ranks of a group together, see {@link GlmDecodeGraph#capture}). */
    static GlmWindowGraph capture(GlmModel model, int nb, int rows, long maxPools, int deviceIndex,
                                  java.util.concurrent.CyclicBarrier barrier) throws Exception {
        GlmWindowGraph g = new GlmWindowGraph(model, nb, rows, maxPools);
        model.fillWindow(g.window, new GlmModel.Session[0], new int[0][]);
        try (var guard = Tensor.noGradGuard()) {
            Tensor[] warm = scopedAll(() -> model.forwardWindow(g.window));
            Aten.one("aten::sum", warm[1]).doubleValue();
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
                out = scopedAll(() -> model.forwardWindow(g.window));
            } finally {
                Native.cudaGraphCaptureEnd(g.graph);
            }
            g.hidden = out[0];
            g.logits = out[1];
        }
        g.hidden.detachFromScopes();
        g.logits.detachFromScopes();
        if (MTP_WINDOW && model.hasMtp()) {
            g.captureMtp(maxPools, deviceIndex, barrier);
        }
        return g;
    }

    private void captureMtp(long maxPools, int deviceIndex, java.util.concurrent.CyclicBarrier barrier) throws Exception {
        mtpWindow = model.newMtpWindow(nb, rows, maxPools);
        model.fillMtpWindow(mtpWindow, new GlmModel.Session[0], new int[0][]);
        try (var guard = Tensor.noGradGuard()) {
            Tensor[] warm = scopedAll(() -> model.mtpWindowBody(mtpWindow, hidden));
            Aten.one("aten::sum", warm[0]).doubleValue();
            for (Tensor t : warm) {
                t.close();
            }
            if (barrier != null) {
                barrier.await(5, java.util.concurrent.TimeUnit.MINUTES);
            }
            mtpGraph = Native.cudaGraphCreate();
            Native.cudaGraphCaptureBegin(mtpGraph, deviceIndex);
            Tensor[] out;
            try {
                out = scopedAll(() -> model.mtpWindowBody(mtpWindow, hidden));
            } finally {
                Native.cudaGraphCaptureEnd(mtpGraph);
            }
            mtpOut = out[0];
            mtpNext = out[1];
        }
        mtpOut.detachFromScopes();
        mtpNext.detachFromScopes();
    }

    /** Whether {@link #absorb} is available (the MTP layer is loaded and its window graph was captured). */
    boolean hasMtpWindow() {
        return mtpGraph != null;
    }

    /**
     * After {@link #commit}: absorbs the kept rows of every request into its MTP cache in one replay (slot
     * {@code mtp.length + j} takes the token {@code emitted[i][j]} and the main hidden row {@code j} of the window), and
     * stores each request's MTP hidden row and first draft of the next round (the output of its last kept row).
     *
     * @param sess    the requests of the replay.
     * @param emitted the tokens kept per request ({@code emitted[i].length} rows are kept).
     */
    void absorb(GlmModel.Session[] sess, int[][] emitted) {
        int b = sess.length;
        int[][] tokens = new int[b][rows];
        int[] accept = new int[b];
        for (int i = 0; i < b; i++) {
            accept[i] = emitted[i].length;
            System.arraycopy(emitted[i], 0, tokens[i], 0, accept[i]);
        }
        model.fillMtpWindow(mtpWindow, sess, tokens);
        Native.cudaGraphReplay(mtpGraph);
        model.commitMtpWindow(mtpWindow, sess, accept);
        long[] vals;
        try (Tensor next = Aten.one("aten::clone", narrow(mtpNext, 0, 0, (long) b * rows));
             Tensor cpu = next.to(smile.deep.tensor.Device.CPU())) {
            vals = cpu.longArray();
        }
        for (int i = 0; i < b; i++) {
            int row = i * rows + accept[i] - 1;
            model.setMtpHiddenFrom(sess[i], mtpOut, row);
            model.setNextDraft(sess[i], (int) vals[row]);
        }
    }

    /**
     * Runs the verify window of a group of requests.
     *
     * @param tokens {@code rows} token ids per request: the last committed token and the drafts.
     * @param sess   the requests' sessions (distinct, at most {@code nb}).
     * @return the logits of the real rows {@code [n * rows, vocab]} (caller owns).
     */
    Tensor replay(int[][] tokens, GlmModel.Session[] sess) {
        long t0 = System.nanoTime();
        model.fillWindow(window, sess, tokens);
        long t1 = System.nanoTime();
        Native.cudaGraphReplay(graph);
        long t2 = System.nanoTime();
        if (mtpGraph == null) {
            for (int i = 0; i < sess.length; i++) {
                model.keepPending(sess[i], narrow(hidden, 0, (long) i * rows, rows));
            }
        }
        Tensor out = Aten.one("aten::clone", narrow(logits, 0, 0, (long) sess.length * rows));
        long t3 = System.nanoTime();
        FILL.addAndGet(t1 - t0);
        LAUNCH.addAndGet(t2 - t1);
        AFTER.addAndGet(t3 - t2);
        CALLS.incrementAndGet();
        return out;
    }

    static final java.util.concurrent.atomic.AtomicLong FILL = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong LAUNCH = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong AFTER = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong CALLS = new java.util.concurrent.atomic.AtomicLong();

    /** Host time per replay: filling the inputs, the replay call, saving the hidden rows and cloning the logits (ms; summed over the ranks). */
    static String hostTimes() {
        long c = Math.max(1, CALLS.get());
        return String.format("fill %.2f, replay call %.2f, after %.2f ms per replay and rank", FILL.get() / 1e6 / c,
                LAUNCH.get() / 1e6 / c, AFTER.get() / 1e6 / c);
    }

    /** Keeps the first {@code accept[i]} rows of each request of the last replay. */
    void commit(GlmModel.Session[] sess, int[] accept) {
        model.commitWindow(window, sess, accept);
    }

    /** Destroys the graph and frees its buffers (on the rank thread, before the communicators). */
    void close() {
        if (graph != null) {
            Native.cudaGraphDestroy(graph);
            graph = null;
        }
        if (mtpGraph != null) {
            Native.cudaGraphDestroy(mtpGraph);
            mtpGraph = null;
        }
        for (Tensor t : new Tensor[]{hidden, logits, mtpOut, mtpNext}) {
            if (t != null) {
                t.close();
            }
        }
        if (mtpWindow != null) {
            mtpWindow.close();
        }
        window.close();
    }
}
