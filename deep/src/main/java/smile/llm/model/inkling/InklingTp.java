/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;
import smile.llm.engine.ModelExecutor;
import smile.llm.engine.Sampling;
import smile.llm.parallel.AuxDevicePlacement;
import smile.llm.template.InklingChatTemplate;
import smile.util.AutoScope;

/**
 * Inkling-2 split over several GPUs behind the continuous-batching engine (Inkling-2-Small on 8 GPUs): attention by head,
 * experts by block, everything else replicated, one thread per GPU in lockstep ({@link InklingRanks}). A request owns
 * one {@link InklingState} per rank; prefill runs the reference path, decode the fused kernels, as one CUDA graph per
 * rank and batch size when graphs are available (the all-reduces are captured with them). Text only, no prefix reuse and
 * no speculation yet.
 *
 * @author Haifeng Li
 */
public final class InklingTp implements LanguageModel, ModelExecutor, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(InklingTp.class);
    private static final boolean GRAPHS = !"0".equals(System.getenv("SMILE_INKLING_GRAPH"));
    private static final boolean FUSED = !"0".equals(System.getenv("SMILE_INKLING_FUSED"));
    private static final int MAX_GRAPH_BATCH = Integer.getInteger("smile.inkling.graph.maxbatch", 16);
    private static final int WARM_GRAPH_BATCH = Integer.getInteger("smile.inkling.graph.warm", 8);

    private final String name;
    private final InklingModelArgs args;
    private final InklingTokenizer tokenizer;
    private final InklingChatTemplate template = new InklingChatTemplate();
    private final byte[] devices;
    private final InklingRanks ranks;
    private final RankContext[] contexts;
    private final Map<Integer, InklingState[]> sessions = new ConcurrentHashMap<>();
    /** Per request: upper bound on state capacity (tokens + margin) so on-demand growth never exceeds the bound window. */
    private final Map<Integer, Integer> maxCapacities = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Object lock = new Object();
    private boolean graphsDisabled;
    private InklingVisionArgs visionArgs;
    private InklingVisionTower vision;
    private final Device visionDevice;
    private final int visionRank;
    private InklingImageProcessor imageProcessor;
    /** Per image request: the image embeddings of the whole prompt on every rank and, per position, how many placeholders precede it. */
    private record ImageState(Tensor[] embeds, int[] before) { }
    private final Map<Integer, ImageState> images = new ConcurrentHashMap<>();
    /** Radix-style prefix reuse: whole-state snapshots (one per rank) at multiples of {@link #SNAPSHOT_EVERY} tokens. */
    private final InklingPrefixCache prefixCache;
    private final Map<Integer, InklingPrefixCache.Entry> prefixMatches = new ConcurrentHashMap<>();
    private volatile boolean prefixReuse = true;
    private static final int SNAPSHOT_EVERY = Integer.getInteger("smile.inkling.prefix.every", 4096);
    private static final int SNAPSHOT_MAX = Integer.getInteger("smile.inkling.prefix.max", 32768);
    private static final int SNAPSHOT_MIN = Integer.getInteger("smile.inkling.prefix.min", 256);
    /** Longest request (tokens) that gets a draft context. */
    private static final int DRAFT_MAX_CONTEXT = Integer.getInteger("smile.inkling.draft.maxctx", 65536);
    private static final boolean SPECULATE = !"0".equals(System.getenv("SMILE_INKLING_SPECULATE"));
    /** The single DSpark draft on its configured device (in-group TP rank or external GPU), or {@code null} when absent. */
    private InklingDraft draft;
    private final Device draftDevice;
    private final int draftRank;
    private final Map<Integer, DraftGraph> draftGraphs = new java.util.HashMap<>();
    private InklingDraft.State padDraft;
    private boolean specDisabled;
    /** Per request: the single draft context on {@link #draftDevice}, and the drafts already computed for its next anchor. */
    private final Map<Integer, InklingDraft.State> draftStates = new ConcurrentHashMap<>();
    private final Map<Integer, int[]> pending = new ConcurrentHashMap<>();
    private static final boolean TIMING = System.getProperty("smile.inkling.timing") != null;
    private long fillNs, replayNs, timedSteps;

    /** What one rank thread owns besides its model: the fused decoder, the captured graphs and a padding state. */
    private final class RankContext {
        final int rank;
        final Device device;
        InklingDecoder decoder;
        InklingState pad;
        final Map<Integer, Graph> graphs = new java.util.HashMap<>();
        final Map<Integer, VerifyGraph> verify = new java.util.HashMap<>();

        RankContext(int rank) {
            this.rank = rank;
            this.device = Device.CUDA(devices[rank]);
        }
    }

    /** A captured decode step of {@code nb} rows of one rank. */
    private final class Graph {
        final int nb;
        final Tensor ids, pos, tbl;
        Tensor logits;
        java.lang.foreign.MemorySegment graph;

        Graph(RankContext c, int nb) {
            this.nb = nb;
            this.ids = Ix.keep(Tensor.of(new long[nb], nb).to(c.device));
            this.pos = Ix.keep(Tensor.of(new int[nb], nb).to(c.device));
            this.tbl = Ix.keep(Tensor.of(new long[args.numApplications() * nb * 8], args.numApplications() * nb * 8L).to(c.device));
        }

        /** Writes the inputs: real rows first, padding rows on the pad state. */
        void fill(RankContext c, InklingState[] states, int[] lastTokens) {
            int apps = args.numApplications();
            long[] idv = new long[nb];
            int[] pv = new int[nb];
            long[] tv = new long[apps * nb * 8];
            for (int i = 0; i < nb; i++) {
                InklingState st = i < states.length ? states[i] : c.pad;
                idv[i] = i < states.length ? lastTokens[i] : 0;
                pv[i] = i < states.length ? st.length : 0;
                long[] t = st.table();
                for (int a = 0; a < apps; a++) {
                    System.arraycopy(t, a * 8, tv, (a * nb + i) * 8, 8);
                }
            }
            smile.torch.Aten.call("aten::copy_", ids, Tensor.of(idv, nb).to(c.device), false);
            smile.torch.Aten.call("aten::copy_", pos, Tensor.of(pv, nb).to(c.device), false);
            smile.torch.Aten.call("aten::copy_", tbl, Tensor.of(tv, tv.length).to(c.device), false);
        }
    }

    /** A captured verify chunk of {@code nb / rowsPer} requests of {@code rowsPer} rows: inputs, taps, kept rows and logits. */
    private final class VerifyGraph {
        final int nb, rowsPer;
        final Tensor ids, pos, tbl, taps, accepted;
        Tensor logits;
        java.lang.foreign.MemorySegment graph;

        VerifyGraph(RankContext c, int nb, int rowsPer, int tapCount) {
            this.nb = nb;
            this.rowsPer = rowsPer;
            this.taps = tapCount > 0
                    ? Ix.keep(Ix.zeros(c.device, smile.deep.tensor.ScalarType.BFloat16, nb, tapCount, args.dim()))
                    : null;
            this.accepted = Ix.keep(Tensor.of(new int[nb / rowsPer], nb / rowsPer).to(c.device));
            this.ids = Ix.keep(Tensor.of(new long[nb], nb).to(c.device));
            this.pos = Ix.keep(Tensor.of(new int[nb], nb).to(c.device));
            this.tbl = Ix.keep(Tensor.of(new long[args.numApplications() * nb * 8], args.numApplications() * nb * 8L).to(c.device));
        }

        /** Writes a chunk's inputs: {@code rows[i]} are the tokens of request {@code i} at its next positions. */
        void fill(RankContext c, InklingState[] states, int[][] rows) {
            int apps = args.numApplications();
            int reqs = nb / rowsPer;
            long[] idv = new long[nb];
            int[] pv = new int[nb];
            long[] tv = new long[apps * nb * 8];
            for (int r = 0; r < reqs; r++) {
                InklingState st = r < states.length ? states[r] : c.pad;
                long[] t = st.table();
                for (int j = 0; j < rowsPer; j++) {
                    int i = r * rowsPer + j;
                    idv[i] = r < states.length ? rows[r][j] : 0;
                    pv[i] = (r < states.length ? st.length : 0) + j;
                    for (int a = 0; a < apps; a++) {
                        System.arraycopy(t, a * 8, tv, (a * nb + i) * 8, 8);
                    }
                }
            }
            smile.torch.Aten.call("aten::copy_", ids, Tensor.of(idv, nb).to(c.device), false);
            smile.torch.Aten.call("aten::copy_", pos, Tensor.of(pv, nb).to(c.device), false);
            smile.torch.Aten.call("aten::copy_", tbl, Tensor.of(tv, tv.length).to(c.device), false);
        }
    }

    /** The captured commit + absorb + draft (or draft-only when DSpark is on an external GPU) of one (requests, rows) shape. */
    private final class DraftGraph {
        final int reqs, rowsPer;
        final VerifyGraph verify;
        final InklingDraft draft;
        final boolean external;
        final Tensor anchor, p0, ptrs, rowT, row8, first, freq;
        final Tensor extTaps, extAccepted;
        Tensor out;
        java.lang.foreign.MemorySegment graph;

        DraftGraph(Device dev, InklingDraft draft, VerifyGraph verify, int reqs, int rowsPer, boolean external) {
            this.verify = verify;
            this.draft = draft;
            this.reqs = reqs;
            this.rowsPer = rowsPer;
            this.external = external;
            int block = draft.drafts() + 1;
            anchor = Ix.keep(Tensor.of(new long[reqs], reqs).to(dev));
            p0 = Ix.keep(Tensor.of(new int[reqs], reqs).to(dev));
            ptrs = Ix.keep(Tensor.of(new long[reqs * draft.pointerStride()], (long) reqs * draft.pointerStride()).to(dev));
            int[] rt = new int[rowsPer];
            int[] r8 = new int[block];
            long[] f = new long[reqs];
            for (int i = 0; i < rt.length; i++) {
                rt[i] = i;
            }
            for (int i = 0; i < r8.length; i++) {
                r8[i] = i;
            }
            for (int i = 0; i < reqs; i++) {
                f[i] = (long) i * rowsPer;
            }
            rowT = Ix.keep(Tensor.of(rt, rt.length).to(dev));
            row8 = Ix.keep(Tensor.of(r8, r8.length).to(dev));
            first = Ix.keep(Tensor.of(f, reqs).to(dev));
            freq = draft.frequencies();
            if (external) {
                extTaps = Ix.keep(Ix.zeros(dev, smile.deep.tensor.ScalarType.BFloat16,
                        (long) reqs * rowsPer, draft.taps().length, args.dim()));
                extAccepted = Ix.keep(Tensor.of(new int[reqs], reqs).to(dev));
            } else {
                extTaps = null;
                extAccepted = null;
            }
        }

        void fill(Device dev, InklingDraft.State[] ds, InklingState[] states, int[] anchors) {
            int stride = draft.pointerStride();
            long[] pt = new long[reqs * stride];
            long[] av = new long[reqs];
            int[] pv = new int[reqs];
            for (int i = 0; i < reqs; i++) {
                boolean real = i < ds.length;
                long[] row = (real ? ds[i] : padDraft).pointers();
                System.arraycopy(row, 0, pt, i * stride, stride);
                av[i] = real ? anchors[i] : 0;
                pv[i] = real ? states[i].length : 0;
            }
            smile.torch.Aten.call("aten::copy_", ptrs, Tensor.of(pt, pt.length).to(dev), false);
            smile.torch.Aten.call("aten::copy_", anchor, Tensor.of(av, reqs).to(dev), false);
            smile.torch.Aten.call("aten::copy_", p0, Tensor.of(pv, reqs).to(dev), false);
        }

        Tensor body(RankContext owner) {
            if (!external) {
                owner.decoder.commit(reqs, rowsPer, verify.tbl, verify.accepted);
            }
            Tensor tapsIn = external ? extTaps : verify.taps;
            Tensor accIn = external ? extAccepted : verify.accepted;
            return draft.round(reqs, rowsPer, tapsIn, accIn, p0, anchor, ptrs, rowT, row8, first, freq);
        }
    }

    private InklingTp(String name, InklingModelArgs args, InklingTokenizer tokenizer, Path dir, byte[] devices,
                      AuxDevicePlacement placement) throws IOException {
        this.name = name;
        this.args = args;
        this.tokenizer = tokenizer;
        this.devices = devices;
        AuxDevicePlacement resolved = placement != null ? placement : AuxDevicePlacement.resolve(devices);
        AuxDevicePlacement.Target visionTarget = resolved.vision();
        AuxDevicePlacement.Target dsparkTarget = resolved.dspark();
        this.visionDevice = visionTarget.device();
        this.visionRank = visionTarget.tpRank();
        this.draftDevice = dsparkTarget.device();
        this.draftRank = dsparkTarget.tpRank();
        // the budget is per rank (every rank holds its share of each snapshot)
        long cacheMb = Long.getLong("smile.inkling.prefix.cache.mb", 4096L);
        this.prefixCache = cacheMb > 0 ? new InklingPrefixCache(cacheMb << 20) : null;
        this.ranks = InklingRanks.load(dir.toString(), args, devices);
        this.contexts = new RankContext[devices.length];
        for (int r = 0; r < contexts.length; r++) {
            contexts[r] = new RankContext(r);
        }
        ranks.onRanks(r -> {
            contexts[r].pad = ranks.model(r).newState(16);
            return null;
        });
        // Load ONE vision tower on the resolved vision device (auto-balanced off rank 0, or explicit in-group/external GPU).
        InklingVisionArgs va = InklingVisionArgs.load(dir.toString());
        InklingCheckpoint ck = new InklingCheckpoint(dir.toString());
        if (va != null && !"0".equals(System.getenv("SMILE_INKLING_VISION")) && ck.hasRaw("model.visual.proj_out.weight")) {
            this.visionArgs = va;
            if (visionRank >= 0) {
                this.vision = ranks.onRank(visionRank, () -> {
                    try {
                        return InklingVisionTower.load(ck, va, visionDevice);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            } else {
                this.vision = InklingVisionTower.load(ck, va, visionDevice);
            }
            this.imageProcessor = new InklingImageProcessor(va);
            logger.info("Inkling vision tower loaded on {} ({} layers, up to {} tiles per image)",
                    visionTarget.describe(), va.layers(), va.maxTokens());
        }
        releaseMemory("weights");
        if (FUSED && InklingDecoder.available()) {
            ranks.onRanks(r -> {
                contexts[r].decoder = new InklingDecoder(ranks.model(r));
                return null;
            });
            logger.info("Inkling decode: fused kernels on {} ranks", devices.length);
            if (SPECULATE) {
                int tapRank = draftTapRank();
                if (draftRank >= 0) {
                    this.draft = ranks.onRank(draftRank, () -> {
                        try {
                            InklingDraft d = InklingDraft.load(dir.toString(), ranks.model(draftRank), draftDevice);
                            if (d != null) {
                                ranks.model(draftRank).tapIds = d.taps();
                                padDraft = d.newState(64);
                            }
                            return d;
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
                } else {
                    InklingDraft d = InklingDraft.load(dir.toString(), ranks.model(0), draftDevice);
                    if (d != null) {
                        this.draft = d;
                        ranks.model(tapRank).tapIds = d.taps();
                        this.padDraft = d.newState(64);
                    }
                }
                if (this.draft != null) {
                    logger.info("Inkling DSpark draft loaded on {} ({} drafts per block, {} taps)",
                            dsparkTarget.describe(), draft.drafts(), draft.taps().length);
                }
            }
            if (GRAPHS && smile.torch.Native.cudaGraphAvailable()) {
                int top = Math.min(MAX_GRAPH_BATCH, WARM_GRAPH_BATCH);
                for (int nb = 1; nb <= top && !graphsDisabled; nb <<= 1) {
                    captureAll(nb);
                }
                if (!graphsDisabled) {
                    logger.info("Inkling decode CUDA graphs captured on every rank for batches 1..{}", top);
                    if (draft != null) {
                        int rows = Math.min(draft.drafts() + 1, InklingState.VERIFY_ROWS);
                        for (int reqs = 1; reqs <= Math.min(4, top) && !specDisabled; reqs <<= 1) {
                            captureSpec(reqs, rows);
                        }
                    }
                }
            }
        } else {
            logger.info("Inkling decode: reference ops (fused kernels unavailable or disabled)");
        }
        releaseMemory("graphs");
    }

    /** Rank whose target hidden-state taps feed DSpark ({@link #draftRank} if in-group, else rank 0). */
    private int draftTapRank() {
        return draftRank >= 0 && draftRank < devices.length ? draftRank : 0;
    }

    /** Runs a draft operation on the draft rank's thread (if in-group) or directly (if external GPU). */
    private <T> T onDraftThread(Supplier<T> task) {
        return draftRank >= 0 ? ranks.onRank(draftRank, task) : task.get();
    }

    /**
     * Frees what loading left behind: tensors that are only unreachable (the loader does not close the full-size copies of
     * what it slices or converts; the Java cleaner frees them after a garbage collection) and the allocator's cached
     * blocks. Without it a rank holds ~25 GB it does not need, which leaves a long prompt or a verify chunk 2 GB of headroom.
     */
    /** Free device bytes below which the allocator caches are emptied before a prefill chunk or a speculative round. */
    private static final long MIN_FREE = Long.getLong("smile.inkling.min.free.mb", 6144L) << 20;

    /**
     * Keeps headroom for the next step: the allocator caches blocks of every size it has seen, and after a few long prompts a
     * rank can hold almost all of its memory without needing it (an out-of-memory failed one long request). Called with the
     * lock held.
     */
    private void ensureHeadroom() {
        long minFree = Long.MAX_VALUE;
        int tightest = -1;
        try {
            for (RankContext c : contexts) {
                long f = smile.torch.Native.cudaMemGetInfo(c.device.index())[0];
                if (f < minFree) {
                    minFree = f;
                    tightest = c.device.index();
                }
            }
            if (vision != null && visionRank < 0 && visionDevice.isCUDA()) {
                long f = smile.torch.Native.cudaMemGetInfo(visionDevice.index())[0];
                if (f < minFree) {
                    minFree = f;
                    tightest = visionDevice.index();
                }
            }
            if (draft != null && draftRank < 0 && draftDevice.isCUDA()) {
                long f = smile.torch.Native.cudaMemGetInfo(draftDevice.index())[0];
                if (f < minFree) {
                    minFree = f;
                    tightest = draftDevice.index();
                }
            }
        } catch (RuntimeException e) {
            return;
        }
        if (minFree < MIN_FREE) {
            long t0 = System.nanoTime();
            ranks.onRanks(r -> {
                contexts[r].device.emptyCache();
                return null;
            });
            if (vision != null && visionRank < 0 && visionDevice.isCUDA()) {
                visionDevice.emptyCache();
            }
            if (draft != null && draftRank < 0 && draftDevice.isCUDA()) {
                draftDevice.emptyCache();
            }
            long after = tightest >= 0 ? smile.torch.Native.cudaMemGetInfo(tightest)[0] : 0;
            logger.info("Inkling: emptied allocator caches in {} ms (cuda:{} {} MiB free before, {} MiB after)",
                    (System.nanoTime() - t0) / 1_000_000, tightest, minFree >> 20, after >> 20);
        }
    }

    private void releaseMemory(String stage) {
        System.gc();
        try {
            Thread.sleep(500);                  // let the cleaner thread run
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ranks.onRanks(r -> {
            contexts[r].device.emptyCache();
            return null;
        });
        if (vision != null && visionRank < 0 && visionDevice.isCUDA()) {
            visionDevice.emptyCache();
        }
        if (draft != null && draftRank < 0 && draftDevice.isCUDA()) {
            draftDevice.emptyCache();
        }
        try {
            long minFree = Long.MAX_VALUE;
            int tightest = contexts[0].device.index();
            for (RankContext c : contexts) {
                long f = smile.torch.Native.cudaMemGetInfo(c.device.index())[0];
                if (f < minFree) {
                    minFree = f;
                    tightest = c.device.index();
                }
            }
            logger.info("Inkling min free GPU memory after {}: {} MiB (cuda:{})", stage, minFree >> 20, tightest);
        } catch (RuntimeException e) {
            logger.debug("free memory not available: {}", e.toString());
        }
    }

    /**
     * Loads Inkling-2 sharded over {@code devices}.
     *
     * @param checkpointDir the checkpoint directory.
     * @param layers        a slice of the first {@code layers} layers for tests ({@code 0}: all).
     * @param maxSeqLen     maximum sequence length; {@code <= 0} uses the model's.
     * @param devices       the CUDA device of each rank.
     * @return the model.
     * @throws IOException if the checkpoint cannot be read.
     */
    public static InklingTp build(String checkpointDir, int layers, int maxSeqLen, byte... devices) throws IOException {
        return build(checkpointDir, layers, maxSeqLen, AuxDevicePlacement.resolve(devices), devices);
    }

    /**
     * Loads Inkling-2 sharded over {@code devices} with explicit auxiliary GPU placement for the vision tower and DSpark draft.
     *
     * @param checkpointDir the checkpoint directory.
     * @param layers        a slice of the first {@code layers} layers for tests ({@code 0}: all).
     * @param maxSeqLen     maximum sequence length; {@code <= 0} uses the model's.
     * @param placement     resolved auxiliary GPU placement.
     * @param devices       the CUDA device of each rank.
     * @return the model.
     * @throws IOException if the checkpoint cannot be read.
     */
    public static InklingTp build(String checkpointDir, int layers, int maxSeqLen, AuxDevicePlacement placement,
                                  byte... devices) throws IOException {
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(checkpointDir, maxSeqLen);
        if (layers > 0) {
            args = args.withLayers(layers, layers >= args.coreEnd() ? args.numPasses() : 1);
        }
        Path dir = Path.of(checkpointDir);
        return new InklingTp(dir.getFileName().toString(), args, InklingTokenizer.of(checkpointDir), dir, devices, placement);
    }

    /** Hyperparameters. */
    public InklingModelArgs args() {
        return args;
    }

    // ------------------------------------------------------------------ CUDA graphs

    /** Captures the decode step of {@code nb} rows on every rank at once; any failure disables graphs everywhere. */
    private void captureAll(int nb) {
        List<Boolean> ok = ranks.onRanks(r -> {
            RankContext c = contexts[r];
            Graph g = new Graph(c, nb);
            try (var guard = Tensor.noGradGuard()) {
                g.fill(c, new InklingState[0], new int[0]);
                // eager warm-up: libraries initialise their workspaces outside the capture
                Tensor warm = Ix.stepScoped(() -> c.decoder.forward(nb, g.ids, g.pos, g.tbl, null));
                smile.torch.Aten.one("aten::sum", warm).doubleValue();
                warm.close();
                g.graph = smile.torch.Native.cudaGraphCreate();
                smile.torch.Native.cudaGraphCaptureBegin(g.graph, c.device.index());
                try {
                    g.logits = Ix.stepScoped(() -> c.decoder.forward(nb, g.ids, g.pos, g.tbl, null));
                } finally {
                    smile.torch.Native.cudaGraphCaptureEnd(g.graph);
                }
                g.logits.detachFromScopes();
                c.graphs.put(nb, g);
                return true;
            } catch (RuntimeException e) {
                logger.warn("Inkling decode CUDA graph capture failed on rank {} for batch {}: {}", r, nb, e.toString());
                return false;
            }
        });
        if (ok.contains(false)) {
            // a rank that could not capture must not leave the others replaying: all ranks fall back together
            graphsDisabled = true;
            ranks.onRanks(r -> {
                for (Graph g : contexts[r].graphs.values()) {
                    if (g.graph != null) {
                        smile.torch.Native.cudaGraphDestroy(g.graph);
                    }
                }
                contexts[r].graphs.clear();
                return null;
            });
            logger.warn("Inkling decode CUDA graphs disabled; decoding eagerly");
        }
    }

    /**
     * Captures the verify chunk on every rank (capturing taps only on the draft tap rank) and the single draft graph
     * on the draft host (in-group rank or external GPU). Any failure disables speculation everywhere.
     */
    private void captureSpec(int reqs, int rowsPer) {
        int key = reqs * 64 + rowsPer;
        long t0 = System.nanoTime();
        final int tapRank = draftTapRank();
        final boolean externalDraft = draftRank < 0;
        List<Boolean> ok = ranks.onRanks(r -> {
            RankContext c = contexts[r];
            int nb = reqs * rowsPer;
            try (var guard = Tensor.noGradGuard()) {
                VerifyGraph g = new VerifyGraph(c, nb, rowsPer, r == tapRank ? draft.taps().length : 0);
                g.fill(c, new InklingState[0], new int[0][]);
                Tensor warm = Ix.stepScoped(() -> c.decoder.forward(nb, rowsPer, g.ids, g.pos, g.tbl, null, g.taps));
                smile.torch.Aten.one("aten::sum", warm).doubleValue();
                warm.close();
                g.graph = smile.torch.Native.cudaGraphCreate();
                smile.torch.Native.cudaGraphCaptureBegin(g.graph, c.device.index());
                try {
                    g.logits = Ix.stepScoped(() -> c.decoder.forward(nb, rowsPer, g.ids, g.pos, g.tbl, null, g.taps));
                } finally {
                    smile.torch.Native.cudaGraphCaptureEnd(g.graph);
                }
                g.logits.detachFromScopes();
                c.verify.put(key, g);
                if (!externalDraft && r == draftRank) {
                    DraftGraph dg = new DraftGraph(c.device, draft, g, reqs, rowsPer, false);
                    dg.fill(c.device, new InklingDraft.State[0], new InklingState[0], new int[0]);
                    Tensor w2 = Ix.stepScoped(() -> dg.body(c));
                    smile.torch.Aten.one("aten::sum", w2).doubleValue();
                    w2.close();
                    dg.graph = smile.torch.Native.cudaGraphCreate();
                    smile.torch.Native.cudaGraphCaptureBegin(dg.graph, c.device.index());
                    try {
                        dg.out = Ix.stepScoped(() -> dg.body(c));
                    } finally {
                        smile.torch.Native.cudaGraphCaptureEnd(dg.graph);
                    }
                    dg.out.detachFromScopes();
                    draftGraphs.put(key, dg);
                }
                return true;
            } catch (RuntimeException e) {
                logger.warn("Inkling verify/draft graph capture failed on rank {} for {} x {}: {}", r, reqs, rowsPer, e.toString());
                return false;
            }
        });
        boolean draftOk = !ok.contains(false);
        if (draftOk && externalDraft) {
            try (var guard = Tensor.noGradGuard()) {
                VerifyGraph g0 = contexts[tapRank].verify.get(key);
                DraftGraph dg = new DraftGraph(draftDevice, draft, g0, reqs, rowsPer, true);
                dg.fill(draftDevice, new InklingDraft.State[0], new InklingState[0], new int[0]);
                Tensor w2 = Ix.stepScoped(() -> dg.body(null));
                smile.torch.Aten.one("aten::sum", w2).doubleValue();
                w2.close();
                dg.graph = smile.torch.Native.cudaGraphCreate();
                smile.torch.Native.cudaGraphCaptureBegin(dg.graph, draftDevice.index());
                try {
                    dg.out = Ix.stepScoped(() -> dg.body(null));
                } finally {
                    smile.torch.Native.cudaGraphCaptureEnd(dg.graph);
                }
                dg.out.detachFromScopes();
                draftGraphs.put(key, dg);
            } catch (RuntimeException e) {
                logger.warn("Inkling external draft graph capture failed on {} for {} x {}: {}",
                        draftDevice, reqs, rowsPer, e.toString());
                draftOk = false;
            }
        }
        if (!draftOk) {
            specDisabled = true;
            logger.warn("Inkling speculative decoding disabled; decoding plainly");
        } else {
            logger.info("Inkling verify CUDA graphs (all ranks) + draft CUDA graph ({}) captured for {} request(s) x {} rows in {} ms",
                    draftDevice, reqs, rowsPer, (System.nanoTime() - t0) / 1_000_000);
        }
    }

    // ------------------------------------------------------------------ LanguageModel

    @Override
    public String family() {
        return "inkling";
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int maxSeqLen() {
        return args.maxSeqLen();
    }

    @Override
    public int[] encodeChat(Message... dialog) {
        return encodeChat(dialog, null);
    }

    @Override
    public int[] encodeChat(Message[] dialog, ChatOptions options) {
        if (dialog == null || dialog.length == 0) {
            throw new IllegalArgumentException("No messages provided.");
        }
        for (Message m : dialog) {
            if (m.hasMedia()) {
                throw new IllegalArgumentException("images are not supported on a sharded Inkling model yet");
            }
        }
        return tokenizer.encodeWithSpecials(template.encode(Arrays.asList(dialog), options));
    }

    @Override
    public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature, double topp, boolean logprobs,
                                   long seed, GenerationListener listener, BooleanSupplier cancelRequested) {
        if (prompt == null || prompt.length == 0) {
            throw new IllegalArgumentException("prompt must not be empty");
        }
        if (logprobs) {
            throw new UnsupportedOperationException("logprobs are not supported yet");
        }
        for (int token : prompt) {
            if (token < 0 || token >= args.unpaddedVocabSize()) {
                throw new IllegalArgumentException("Prompt token id " + token + " out of range for vocab_size "
                        + args.unpaddedVocabSize());
            }
        }
        int promptLen = prompt.length;
        if (promptLen > args.maxSeqLen()) {
            throw new IllegalArgumentException("The prompt length is greater than max_seq_len");
        }
        int maxGen = Math.max(0, Math.min(maxGenLen, args.maxSeqLen() - promptLen));
        int id = bind(prompt, promptLen + maxGen);
        List<Integer> out = new ArrayList<>();
        boolean stopped = false;
        try (var guard = Tensor.noGradGuard()) {
            if (listener != null) {
                listener.onInputTokens(promptLen);
                listener.onCachedInputTokens(0);
            }
            Set<Integer> stops = new HashSet<>();
            for (int s : stopTokens()) {
                stops.add(s);
            }
            if (maxGen > 0) {
                int next = sample(prefill(id, prompt, 0), temperature, topp);
                int pos = promptLen;
                while (true) {
                    if (cancelRequested != null && cancelRequested.getAsBoolean()) {
                        throw new java.util.concurrent.CancellationException("generation cancelled");
                    }
                    if (stops.contains(next)) {
                        stopped = true;
                        break;
                    }
                    out.add(next);
                    if (out.size() >= maxGen) {
                        break;
                    }
                    next = sample(decodeStep(new int[]{id}, new int[]{next}, new int[]{pos++}), temperature, topp);
                }
            }
            int[] completion = out.stream().mapToInt(Integer::intValue).toArray();
            String text = safeDecode(completion);
            if (listener != null) {
                listener.onGeneratedTokens(completion.length);
                if (completion.length > 0) {
                    listener.onText(text);
                }
            }
            return new ChatCompletion(name, text, prompt, completion,
                    stopped ? FinishReason.stop : FinishReason.length, null);
        } finally {
            evict(id);
        }
    }

    @Override
    public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature, double topp, boolean logprobs,
                               long seed, GenerationListener listener, BooleanSupplier cancelRequested) {
        return generate(encodeChat(dialog), maxGenLen, temperature, topp, logprobs, seed, listener, cancelRequested);
    }

    private static int sample(Tensor logits, double temperature, double topp) {
        try (logits) {
            return Sampling.sampleTokenIds(logits, temperature, topp)[0];
        }
    }

    private String safeDecode(int[] tokens) {
        try {
            return tokenizer.tryDecode(tokens, true);
        } catch (Exception e) {
            return tokenizer.decode(tokens);
        }
    }

    // ------------------------------------------------------------------ images

    /**
     * Whether images can be processed (the checkpoint has its vision weights).
     *
     * @return {@code true} when {@link #processMultimodal} may be called.
     */
    public boolean isMultimodal() {
        return visionArgs != null;
    }

    private static final String IMAGE_TOKEN = InklingTokenizer.IMAGE_TOKEN;

    /**
     * Renders a dialog with images: the chat template writes one image token per image, every one is expanded to one token
     * per 40x40 tile (as the reference processor does), and the images are preprocessed into tiles.
     *
     * @param dialog  the messages (image parts only).
     * @param options chat options.
     * @return the expanded prompt and the tiles to encode during prefill.
     * @throws IOException              if an image cannot be read or decoded.
     * @throws IllegalArgumentException if the request is malformed.
     */
    public InklingMultimodal processMultimodal(Message[] dialog, ChatOptions options) throws IOException {
        if (visionArgs == null) {
            throw new IllegalArgumentException("this Inkling checkpoint has no vision weights");
        }
        if (dialog == null || dialog.length == 0) {
            throw new IllegalArgumentException("No messages provided.");
        }
        List<String> urls = new ArrayList<>();
        for (Message m : dialog) {
            for (var part : m.parts()) {
                if (part instanceof smile.llm.ImageUrlPart image) {
                    urls.add(image.url());
                } else if (part instanceof smile.llm.VideoUrlPart) {
                    throw new IllegalArgumentException("Inkling-2 video input is not supported");
                } else if (part instanceof smile.llm.AudioUrlPart) {
                    throw new IllegalArgumentException("Inkling-2 does not accept audio input");
                }
            }
        }
        String text = template.encode(Arrays.asList(dialog), options);
        int markers = 0;
        for (int i = text.indexOf(IMAGE_TOKEN); i >= 0; i = text.indexOf(IMAGE_TOKEN, i + IMAGE_TOKEN.length())) {
            markers++;
        }
        if (markers != urls.size()) {
            throw new IllegalArgumentException("the prompt contains " + markers + " image tokens for " + urls.size()
                    + " images (do not type the placeholder token into message text)");
        }
        List<InklingImageProcessor.Patches> tiles = new ArrayList<>();
        try {
            for (String url : urls) {
                try {
                    tiles.add(imageProcessor.process(smile.llm.model.minimax.MiniMaxMedia.image(url)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("unusable image: " + e.getMessage(), e);
                }
            }
            StringBuilder out = new StringBuilder(text.length() + 16 * tiles.size() * 64);
            int at = 0;
            for (InklingImageProcessor.Patches p : tiles) {
                int next = text.indexOf(IMAGE_TOKEN, at);
                out.append(text, at, next).append(IMAGE_TOKEN.repeat(p.grid()[1] * p.grid()[2]));
                at = next + IMAGE_TOKEN.length();
            }
            out.append(text, at, text.length());
            int[] ids = tokenizer.encodeWithSpecials(out.toString());
            if (ids.length > args.maxSeqLen()) {
                throw new IllegalArgumentException("the prompt (" + ids.length + " tokens with its images) is longer than max_seq_len");
            }
            Tensor pixels = tiles.isEmpty() ? null
                    : tiles.size() == 1 ? tiles.get(0).pixels().copy()
                    : Tensor.vstack(tiles.stream().map(InklingImageProcessor.Patches::pixels).toArray(Tensor[]::new));
            int[][] grids = tiles.stream().map(InklingImageProcessor.Patches::grid).toArray(int[][]::new);
            return new InklingMultimodal(ids, pixels, grids);
        } finally {
            for (var p : tiles) {
                p.pixels().close();
            }
        }
    }

    @Override
    public Tensor prefillMultimodal(int requestId, smile.llm.engine.MultimodalPrompt prompt, int from, int to) {
        InklingMultimodal mm = (InklingMultimodal) prompt;
        InklingState[] st = session(requestId);
        if (st[0].length != from) {
            throw new IllegalStateException("request " + requestId + " is at position " + st[0].length + ", not " + from);
        }
        int[] ids = mm.inputIds();
        boolean last = to == ids.length;
        synchronized (lock) {
            InklingDraft.State noDraft = draftStates.remove(requestId);      // image prompts are not speculated
            if (noDraft != null) {
                onDraftThread(() -> {
                    noDraft.close();
                    return null;
                });
            }
            ImageState is = images.get(requestId);
            if (is == null && mm.hasVision()) {
                // Encode images once on visionDevice, then fan compact embeddings out to every TP rank.
                Tensor encoded;
                if (visionRank >= 0) {
                    encoded = ranks.onRank(visionRank, () -> {
                        try (var guard = Tensor.noGradGuard()) {
                            return Ix.keep(Ix.stepScoped(() -> vision.encode(mm.pixels(), mm.grids())));
                        }
                    });
                } else {
                    try (var guard = Tensor.noGradGuard()) {
                        encoded = Ix.keep(Ix.stepScoped(() -> vision.encode(mm.pixels(), mm.grids())));
                    }
                }
                Tensor[] embeds = new Tensor[devices.length];
                try {
                    for (int r = 0; r < devices.length; r++) {
                        embeds[r] = r == visionRank ? encoded : Ix.keep(encoded.to(contexts[r].device));
                    }
                } catch (Throwable t) {
                    for (int r = 0; r < devices.length; r++) {
                        if (embeds[r] != null && embeds[r] != encoded) {
                            embeds[r].close();
                        }
                    }
                    encoded.close();
                    throw t;
                }
                if (visionRank < 0) {
                    encoded.close();
                }
                int[] before = new int[ids.length + 1];
                for (int i = 0; i < ids.length; i++) {
                    before[i + 1] = before[i] + (ids[i] == visionArgs.imageToken() ? 1 : 0);
                }
                if (before[ids.length] != embeds[0].size(0)) {
                    long n = embeds[0].size(0);
                    ranks.onRanks(r -> {
                        embeds[r].close();
                        return null;
                    });
                    throw new IllegalArgumentException("the prompt has " + before[ids.length] + " image tokens for " + n
                            + " image embeddings");
                }
                is = new ImageState(embeds, before);
                images.put(requestId, is);
            }
            ensureStatesCapacity(st, to, maxCap(requestId));
            int limit = st[0].maxChunk(args);
            int pos = from;
            Tensor logits = null;
            while (pos < to) {
                ensureHeadroom();
                int end = Math.min(to, pos + limit);
                long[] chunk = new long[end - pos];
                List<Long> where = new ArrayList<>();
                for (int i = 0; i < chunk.length; i++) {
                    chunk[i] = ids[pos + i];
                    if (is != null && ids[pos + i] == visionArgs.imageToken()) {
                        where.add((long) i);
                    }
                }
                long[] positions = where.isEmpty() ? null : where.stream().mapToLong(Long::longValue).toArray();
                final ImageState fis = is;
                final int fpos = pos;
                boolean wantLogits = last && end == to;
                Tensor t = ranks.forward(st, chunk, wantLogits ? null : new int[0], null, null,
                        positions == null ? null : r -> Ix.narrow(fis.embeds[r], 0, fis.before[fpos], positions.length), positions);
                if (wantLogits) {
                    logits = t;
                }
                pos = end;
            }
            return logits;
        }
    }

    // ------------------------------------------------------------------ ModelExecutor

    @Override
    public LanguageModel model() {
        return this;
    }

    @Override
    public KvCachePool kvCachePool() {
        return null;
    }

    @Override
    public int padToken() {
        return tokenizer.pad();
    }

    @Override
    public int[] stopTokens() {
        return tokenizer.stopTokens();
    }

    @Override
    public String decode(int[] tokens) {
        return tokenizer.decode(tokens);
    }

    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial) throws java.nio.charset.CharacterCodingException {
        return tokenizer.tryDecode(tokens, skipSpecial);
    }

    private static void timed(String what, long t0) {
        if (TIMING) {
            logger.info("Inkling TP {}: {} ms", what, (System.nanoTime() - t0) / 1_000_000);
        }
    }

    @Override
    public int bind(int[] prompt, int totalCapacity) {
        long t0 = System.nanoTime();
        try {
            return bindImpl(prompt, totalCapacity);
        } finally {
            timed("bind", t0);
        }
    }

    /** Spare per-request states (all ranks): allocating the caches of a request on every rank takes ~0.75 s on a busy server. */
    private final ArrayDeque<InklingState[]> spareStates = new ArrayDeque<>();
    private static final int MAX_SPARE_STATES = Integer.getInteger("smile.inkling.spare.states", 4);
    private static final int POOLED_CAPACITY = 65536;

    /** Rounds a capacity up so that a spare state fits more requests; very long ones are allocated exactly. */
    private static int stateCapacity(int capacity) {
        return capacity > POOLED_CAPACITY ? capacity : Math.max(4096, (capacity + 4095) & ~4095);
    }

    /** Takes the smallest spare state that holds {@code capacity} tokens, or returns {@code null}. Called with the lock held. */
    private InklingState[] takeSpare(int capacity) {
        InklingState[] best = null;
        for (InklingState[] s : spareStates) {
            if (s[0].capacity >= capacity && (best == null || s[0].capacity < best[0].capacity)) {
                best = s;
            }
        }
        if (best != null) {
            spareStates.remove(best);
        }
        return best;
    }

    /** Resets a finished request's states and keeps them for the next one (or frees them). Called with the lock held. */
    private void giveBack(InklingState[] s) {
        if (s[0].capacity <= POOLED_CAPACITY && MAX_SPARE_STATES > 0) {
            ranks.onRanks(r -> {
                s[r].reset();
                return null;
            });
            spareStates.add(s);
            while (spareStates.size() > MAX_SPARE_STATES) {
                InklingState[] old = spareStates.poll();
                ranks.onRanks(r -> {
                    old[r].close();
                    return null;
                });
            }
        } else {
            ranks.onRanks(r -> {
                s[r].close();
                return null;
            });
        }
    }

    private int maxCap(int requestId) {
        return maxCapacities.getOrDefault(requestId, args.maxSeqLen() + InklingState.VERIFY_ROWS);
    }

    private int draftMaxCap(int requestId) {
        int block = draft == null ? 0 : draft.drafts() + 1;
        return Math.min(maxCap(requestId), DRAFT_MAX_CONTEXT) + InklingState.VERIFY_ROWS + block;
    }

    /** Grows a request's per-rank states on demand to hold at least {@code required} tokens. Called with {@code lock} held. */
    private void ensureStatesCapacity(InklingState[] st, int required, int maxCap) {
        if (required <= st[0].capacity) {
            return;
        }
        ensureHeadroom();
        int target = InklingState.nextCapacity(st[0].capacity, required, maxCap);
        ranks.onRanks(r -> {
            st[r].grow(target);
            return null;
        });
    }

    /** Grows a request's draft state on demand to hold at least {@code required} positions. Called with {@code lock} held. */
    private void ensureDraftCapacity(InklingDraft.State ds, int required, int maxCap) {
        if (required <= ds.capacity) {
            return;
        }
        ensureHeadroom();
        int target = InklingState.nextCapacity(ds.capacity, required, maxCap);
        onDraftThread(() -> {
            ds.grow(target);
            return null;
        });
    }

    private int bindImpl(int[] prompt, int totalCapacity) {
        if (totalCapacity > args.maxSeqLen()) {
            throw new IllegalArgumentException("capacity " + totalCapacity + " exceeds max_seq_len " + args.maxSeqLen());
        }
        int logicalMax = Math.max(1, totalCapacity);
        int promptLen = prompt == null ? 0 : prompt.length;
        // a verify chunk may write up to VERIFY_ROWS rows past the committed ones
        boolean hasSpec = draft != null && contexts[0].decoder != null;
        boolean canDraft = hasSpec && promptLen <= DRAFT_MAX_CONTEXT;
        int stateMargin = hasSpec ? InklingState.VERIFY_ROWS : 0;
        int draftMargin = canDraft ? InklingState.VERIFY_ROWS + draft.drafts() + 1 : 0;
        int stateMaxCap = logicalMax + stateMargin;
        int stateInitCap = InklingState.initialCapacity(promptLen + stateMargin, stateMaxCap);
        int draftMaxCap = canDraft ? Math.min(logicalMax, DRAFT_MAX_CONTEXT) + draftMargin : 0;
        int draftInitCap = canDraft ? InklingState.initialCapacity(promptLen + draftMargin, draftMaxCap) : 0;
        InklingState[] states;
        InklingDraft.State ds = null;
        synchronized (lock) {
            long t0 = System.nanoTime();
            states = takeSpare(stateInitCap);
            if (states == null) {
                ensureHeadroom();
                states = ranks.newStates(stateCapacity(stateInitCap));
            }
            timed("newStates, capacity " + stateInitCap, t0);
            long t1 = System.nanoTime();
            if (canDraft) {
                try {
                    ds = onDraftThread(() -> draft.newState(draftInitCap));
                } catch (Throwable t) {
                    giveBack(states);
                    throw t;
                }
            }
            timed("draft newState", t1);
        }
        int id = nextId.getAndIncrement();
        sessions.put(id, states);
        maxCapacities.put(id, stateMaxCap);
        if (ds != null) {
            draftStates.put(id, ds);
        }
        if (prefixCache != null && prefixReuse && prompt != null) {
            // the last prompt token is always processed (its logits are sampled)
            InklingPrefixCache.Entry hit;
            synchronized (prefixCache) {
                hit = prefixCache.acquire(prompt, prompt.length - 1);
            }
            if (hit != null) {
                prefixMatches.put(id, hit);
            }
        }
        return id;
    }

    /**
     * The snapshot positions: 1024, 2048 and 4096, then every {@link #SNAPSHOT_EVERY} tokens. A snapshot ends a prefill chunk
     * and a chunk costs ~0.4 s of overhead whatever its size, so they are sparse; the finer start serves shared system prompts.
     */
    static int nextSnapshot(int pos) {
        for (int p : new int[]{1024, 2048, 4096}) {
            if (pos < p) {
                return p;
            }
        }
        return (pos / SNAPSHOT_EVERY + 1) * SNAPSHOT_EVERY;
    }

    static boolean isSnapshotPosition(int pos) {
        return pos == 1024 || pos == 2048 || pos >= 4096 && pos % SNAPSHOT_EVERY == 0;
    }

    /** One snapshot per rank, with the single draft context snapshot when the request had one covering the whole prefix. */
    private record RankSnapshots(InklingState.Snapshot[] parts, InklingDraft.Snapshot draft)
            implements InklingPrefixCache.Payload {
        @Override
        public long bytes() {
            return parts[0].bytes() + (draft == null ? 0 : draft.bytes());
        }

        @Override
        public void close() {
            for (InklingState.Snapshot p : parts) {
                p.close();
            }
            if (draft != null) {
                draft.close();
            }
        }
    }

    /**
     * Turns prefix reuse on or off.
     *
     * @param enabled whether requests restore stored snapshots and finished requests store theirs.
     */
    public void setPrefixReuseEnabled(boolean enabled) {
        this.prefixReuse = enabled;
    }

    /**
     * Prefix-cache statistics.
     *
     * @return a one-line summary, or an empty string when the cache is off.
     */
    public String prefixCacheStats() {
        if (prefixCache == null) {
            return "";
        }
        synchronized (prefixCache) {
            return prefixCache.stats();
        }
    }

    @Override
    public int prefixLen(int requestId) {
        InklingPrefixCache.Entry hit = prefixMatches.get(requestId);
        return hit == null ? 0 : hit.length();
    }

    /** Loads the matched snapshot into the request's (empty) states; the engine then prefills from its length. */
    @Override
    public void warmPrefix(int requestId, int[] prompt, int prefixLen) {
        long t0 = System.nanoTime();
        try {
            warmPrefixImpl(requestId, prompt, prefixLen);
        } finally {
            timed("warmPrefix", t0);
        }
    }

    private void warmPrefixImpl(int requestId, int[] prompt, int prefixLen) {
        InklingPrefixCache.Entry hit = prefixMatches.remove(requestId);
        if (hit == null) {
            return;
        }
        try {
            if (prefixLen != hit.length()) {
                throw new IllegalStateException("request " + requestId + " resumes at " + prefixLen
                        + " but its stored prefix has " + hit.length() + " tokens");
            }
            InklingState[] st = session(requestId);
            RankSnapshots snap = (RankSnapshots) hit.snapshot;
            InklingState.Snapshot[] parts = snap.parts();
            InklingDraft.State ds = draftStates.get(requestId);
            synchronized (lock) {
                ensureStatesCapacity(st, prefixLen, maxCap(requestId));
                if (ds != null && snap.draft() != null) {
                    ensureDraftCapacity(ds, prefixLen, draftMaxCap(requestId));
                }
                ranks.onRanks(r -> {
                    try (var guard = Tensor.noGradGuard()) {
                        st[r].restore(parts[r]);
                    }
                    return null;
                });
                if (ds != null && snap.draft() != null) {
                    onDraftThread(() -> {
                        try (var guard = Tensor.noGradGuard()) {
                            draft.restore(ds, snap.draft());
                        }
                        return null;
                    });
                }
            }
        } finally {
            synchronized (prefixCache) {
                prefixCache.release(hit);
            }
        }
    }

    /** Stores a snapshot of {@code st}, which has processed exactly {@code tokens} (call with the lock held). */
    private void store(InklingState[] st, InklingDraft.State ds, int[] tokens) {
        if (tokens.length < SNAPSHOT_MIN || st[0].length != tokens.length) {
            return;
        }
        boolean withDraft = ds != null && ds.absorbed >= tokens.length;
        long bytes = InklingState.snapshotBytes(args, tokens.length) / devices.length + (4L << 20)
                + (withDraft ? draft.snapshotBytes(tokens.length) : 0);
        synchronized (prefixCache) {
            if (!prefixCache.fits(bytes) || prefixCache.has(tokens, tokens.length)) {
                return;
            }
        }
        ensureHeadroom();
        InklingState.Snapshot[] parts = new InklingState.Snapshot[devices.length];
        InklingDraft.Snapshot dpart = null;
        try {
            ranks.onRanks(r -> {
                try (var guard = Tensor.noGradGuard()) {
                    parts[r] = st[r].snapshot(args);
                }
                return null;
            });
            if (withDraft) {
                dpart = onDraftThread(() -> {
                    try (var guard = Tensor.noGradGuard()) {
                        return draft.snapshot(ds, tokens.length);
                    }
                });
            }
        } catch (Throwable t) {
            ranks.closeAll(parts);
            if (dpart != null) {
                dpart.close();
            }
            throw t;
        }
        synchronized (prefixCache) {
            prefixCache.put(tokens, new RankSnapshots(parts, dpart));
        }
    }

    private InklingState[] session(int requestId) {
        InklingState[] s = sessions.get(requestId);
        if (s == null) {
            throw new IllegalArgumentException("unknown request " + requestId);
        }
        return s;
    }

    @Override
    public Tensor prefill(int requestId, int[] prompt, int prefixLen) {
        return prefillChunk(requestId, prompt, prefixLen, prompt.length);
    }

    @Override
    public Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        long t0 = System.nanoTime();
        try {
            return prefillChunkImpl(requestId, prompt, from, to);
        } finally {
            timed("prefillChunk " + from + ".." + to, t0);
        }
    }

    private Tensor prefillChunkImpl(int requestId, int[] prompt, int from, int to) {
        InklingState[] st = session(requestId);
        if (st[0].length != from) {
            throw new IllegalStateException("request " + requestId + " is at position " + st[0].length + ", not " + from);
        }
        boolean last = to == prompt.length;
        synchronized (lock) {
            int stateMargin = draft != null && contexts[0].decoder != null ? InklingState.VERIFY_ROWS : 0;
            ensureStatesCapacity(st, to + stateMargin, maxCap(requestId));
            InklingDraft.State ds = draftStates.get(requestId);
            if (ds != null) {
                if (to > DRAFT_MAX_CONTEXT) {
                    draftStates.remove(requestId);
                    pending.remove(requestId);
                    final InklingDraft.State dropped = ds;
                    onDraftThread(() -> {
                        dropped.close();
                        return null;
                    });
                    ds = null;
                } else {
                    ensureDraftCapacity(ds, to + InklingState.VERIFY_ROWS + draft.drafts() + 1, draftMaxCap(requestId));
                }
            }
            final InklingDraft.State fds = ds;
            final int tapRank = draftTapRank();
            int limit = st[0].maxChunk(args);
            int pos = from;
            Tensor logits = null;
            while (pos < to) {
                ensureHeadroom();
                int end = Math.min(to, pos + limit);
                // stop at the next position worth a snapshot: the state there is a prefix of later prompts
                if (prefixCache != null && prefixReuse) {
                    int cut = nextSnapshot(pos);
                    if (cut < prompt.length && cut < end && cut <= SNAPSHOT_MAX) {
                        end = cut;
                    }
                }
                long[] ids = new long[end - pos];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = prompt[pos + i];
                }
                boolean wantLogits = last && end == to;
                final int at = pos;
                Tensor t = ranks.forward(st, ids, wantLogits ? null : new int[0], null,
                        fds == null ? null : r -> r == tapRank ? (taps -> draft.absorb(fds, taps, at)) : null);
                if (wantLogits) {
                    logits = t;
                }
                pos = end;
                if (prefixCache != null && prefixReuse && isSnapshotPosition(pos) && pos < prompt.length && pos <= SNAPSHOT_MAX) {
                    store(st, fds, Arrays.copyOf(prompt, pos));
                }
            }
            return logits;
        }
    }

    @Override
    public Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
        int n = requestIds.length;
        InklingState[][] states = new InklingState[n][];
        for (int i = 0; i < n; i++) {
            states[i] = session(requestIds[i]);
            if (states[i][0].length != positions[i]) {
                throw new IllegalStateException("request " + requestIds[i] + " is at position " + states[i][0].length
                        + ", not " + positions[i]);
            }
        }
        synchronized (lock) {
            for (int i = 0; i < n; i++) {
                ensureStatesCapacity(states[i], states[i][0].length + 1, maxCap(requestIds[i]));
            }
            boolean fused = contexts[0].decoder != null;
            if (fused && GRAPHS && !graphsDisabled && n <= MAX_GRAPH_BATCH && smile.torch.Native.cudaGraphAvailable()) {
                int nb = n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
                if (!contexts[0].graphs.containsKey(nb)) {
                    captureAll(nb);
                    if (!graphsDisabled) {
                        logger.info("Inkling decode CUDA graph captured for batch {}", nb);
                    }
                }
                if (!graphsDisabled) {
                    return stepRanks(states, lastTokens, nb);
                }
            }
            return stepRanks(states, lastTokens, fused ? 0 : -1);
        }
    }

    /**
     * One decode step on every rank. {@code nb > 0}: replay the graph of that batch size; {@code 0}: fused kernels
     * eagerly; {@code -1}: the reference path.
     */
    private Tensor stepRanks(InklingState[][] states, int[] lastTokens, int nb) {
        int n = states.length;
        List<Tensor> out = ranks.onRanks(r -> {
            InklingState[] mine = new InklingState[n];
            for (int i = 0; i < n; i++) {
                mine[i] = states[i][r];
            }
            try (var guard = Tensor.noGradGuard()) {
                return scoped(() -> rankStep(contexts[r], mine, lastTokens, nb));
            }
        });
        return out.get(0);
    }

    /** Runs a step in its own scope; the result (if any) survives into the caller's scope. */
    private static Tensor scoped(Supplier<Tensor> step) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor t = step.get();
            if (t != null) {
                t.promoteToParent();
            }
            return t;
        } finally {
            Tensor.pop();
        }
    }

    private Tensor rankStep(RankContext c, InklingState[] states, int[] lastTokens, int nb) {
        int n = states.length;
        boolean first = c.rank == 0;           // all ranks hold the same logits: only rank 0 hands them out
        if (nb < 0) {
            Tensor[] rows = new Tensor[n];
            for (int i = 0; i < n; i++) {
                rows[i] = ranks.model(c.rank).forward(states[i], new long[]{lastTokens[i]}, null, null);
            }
            return first ? (n == 1 ? rows[0] : Ix.cat(0, rows)) : null;
        }
        Tensor logits;
        if (nb > 0) {
            Graph g = c.graphs.get(nb);
            long t0 = TIMING ? System.nanoTime() : 0;
            g.fill(c, states, lastTokens);
            long t1 = TIMING ? System.nanoTime() : 0;
            smile.torch.Native.cudaGraphReplay(g.graph);
            if (TIMING) {
                smile.torch.Aten.one("aten::sum", g.logits).doubleValue();       // wait for the replay
                long t2 = System.nanoTime();
                if (first) {
                    fillNs += t1 - t0;
                    replayNs += t2 - t1;
                    if (++timedSteps % 64 == 0) {
                        logger.info("Inkling TP step: fill {} us, graph replay {} us (rank 0, last 64 steps)",
                                fillNs / 64000, replayNs / 64000);
                        fillNs = replayNs = 0;
                    }
                }
            }
            logits = first ? smile.torch.Aten.one("aten::clone", Ix.narrow(g.logits, 0, 0, n)) : null;
        } else {
            int apps = args.numApplications();
            long[] ids = new long[n];
            int[] pos = new int[n];
            long[] table = new long[apps * n * 8];
            for (int i = 0; i < n; i++) {
                ids[i] = lastTokens[i];
                pos[i] = states[i].length;
                long[] t = states[i].table();
                for (int a = 0; a < apps; a++) {
                    System.arraycopy(t, a * 8, table, (a * n + i) * 8, 8);
                }
            }
            logits = c.decoder.forward(n, Tensor.of(ids, n).to(c.device), Tensor.of(pos, n).to(c.device),
                    Tensor.of(table, table.length).to(c.device), null);
            if (!first) {
                logits = null;
            }
        }
        for (InklingState s : states) {
            s.length++;
        }
        return logits;
    }

    /**
     * Whether the draft model is loaded and the fused decoder can verify chunks.
     *
     * @return {@code true} if speculative drafting is active.
     */
    public boolean hasDraft() {
        return draft != null && contexts[0].decoder != null && GRAPHS && !graphsDisabled && !specDisabled
                && smile.torch.Native.cudaGraphAvailable();
    }

    /**
     * Returns the number of draft tokens per block.
     *
     * @return the draft block size, or {@code 0} when no draft model is loaded.
     */
    public int draftBlock() {
        return draft == null ? 0 : draft.drafts();
    }

    @Override
    public boolean speculationMayOvershoot() {
        return true;
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts, double temperature,
                                 double topp) {
        double[] t = new double[requestIds.length];
        double[] p = new double[requestIds.length];
        Arrays.fill(t, temperature);
        Arrays.fill(p, topp);
        return speculateStep(requestIds, lastTokens, positions, numDrafts, t, p);
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions, int numDrafts, double[] temperatures,
                                 double[] topps) {
        if (!hasDraft()) {
            throw new UnsupportedOperationException("no draft model");
        }
        int n = requestIds.length;
        InklingState[][] states = new InklingState[n][];
        InklingDraft.State[] ds = new InklingDraft.State[n];
        for (int i = 0; i < n; i++) {
            states[i] = session(requestIds[i]);
            ds[i] = draftStates.get(requestIds[i]);
            if (states[i][0].length != positions[i]) {
                throw new IllegalStateException("request " + requestIds[i] + " is at position " + states[i][0].length
                        + ", not " + positions[i]);
            }
            if (ds[i] == null || ds[i].absorbed < positions[i]) {
                throw new UnsupportedOperationException("request " + requestIds[i] + " has no draft context");
            }
        }
        int rowsPer = Math.max(2, Math.min(numDrafts + 1, Math.min(draft.drafts() + 1, InklingState.VERIFY_ROWS)));
        int block = draft.drafts() + 1;
        int reqs = n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
        synchronized (lock) {
            for (int i = 0; i < n; i++) {
                if (positions[i] + rowsPer > DRAFT_MAX_CONTEXT) {
                    pending.remove(requestIds[i]);
                    InklingDraft.State dropped = draftStates.remove(requestIds[i]);
                    if (dropped != null) {
                        onDraftThread(() -> {
                            dropped.close();
                            return null;
                        });
                    }
                    throw new UnsupportedOperationException("request " + requestIds[i] + " reached draft max context " + DRAFT_MAX_CONTEXT);
                }
            }
            for (int i = 0; i < n; i++) {
                ensureStatesCapacity(states[i], states[i][0].length + rowsPer, maxCap(requestIds[i]));
                ensureDraftCapacity(ds[i], states[i][0].length + rowsPer + block, draftMaxCap(requestIds[i]));
            }
            int key = reqs * 64 + rowsPer;
            if (!contexts[0].verify.containsKey(key)) {
                captureSpec(reqs, rowsPer);
                if (specDisabled) {
                    throw new UnsupportedOperationException("speculative graphs unavailable");
                }
            }
            return speculateRound(requestIds, states, ds, lastTokens, rowsPer, reqs, key, temperatures, topps);
        }
    }

    /**
     * One verify + commit + absorb + draft round. Phase 1 (all ranks): verify the block, rank 0 samples every row and
     * decides how many drafts are accepted. Phase 2: every rank commits its accepted main KV rows, while only the single
     * draft host absorbs the committed taps and drafts the next block (in-group TP rank or external GPU).
     */
    private int[][] speculateRound(int[] requestIds, InklingState[][] states, InklingDraft.State[] ds, int[] lastTokens,
                                   int rowsPer, int reqs, int key, double[] temperatures, double[] topps) {
        int n = states.length;
        int[][] rows = new int[n][rowsPer];
        for (int i = 0; i < n; i++) {
            int[] next = pending.get(requestIds[i]);
            if (next == null) {
                // first round of a request: its drafts come from the context absorbed during prefill
                int idx = i;
                next = onDraftThread(() -> {
                    try (var guard = Tensor.noGradGuard()) {
                        return scoped0(() -> draft.draft(ds[idx], lastTokens[idx], states[idx][0].length));
                    }
                });
            }
            rows[i][0] = lastTokens[i];
            System.arraycopy(next, 0, rows[i], 1, rowsPer - 1);
        }
        int[][] out = new int[n][];
        int[] kept = new int[reqs];
        int[] anchors = new int[n];
        ranks.onRanks(r -> {
            RankContext c = contexts[r];
            InklingState[] mine = new InklingState[n];
            for (int i = 0; i < n; i++) {
                mine[i] = states[i][r];
            }
            VerifyGraph g = c.verify.get(key);
            g.fill(c, mine, rows);
            smile.torch.Native.cudaGraphReplay(g.graph);
            if (r == 0) {
                try (var guard = Tensor.noGradGuard()) {
                    AutoScope scope = new AutoScope();
                    Tensor.push(scope);
                    try {
                        for (int i = 0; i < n; i++) {
                            Tensor lg = Ix.narrow(g.logits, 0, (long) i * rowsPer, rowsPer);
                            int[] s = Sampling.sampleTokenIds(lg, temperatures[i], topps[i]);
                            int a = 0;
                            while (a < rowsPer - 1 && rows[i][a + 1] == s[a]) {
                                a++;
                            }
                            out[i] = new int[a + 1];
                            System.arraycopy(rows[i], 1, out[i], 0, a);
                            out[i][a] = s[a];
                            kept[i] = a + 1;
                            anchors[i] = s[a];
                        }
                    } finally {
                        Tensor.pop();
                    }
                }
            }
            return null;
        });
        int nd = draft.drafts();
        DraftGraph dg = draftGraphs.get(key);
        final boolean externalDraft = draftRank < 0;
        List<long[]> next = ranks.onRanks(r -> {
            RankContext c = contexts[r];
            InklingState[] mine = new InklingState[n];
            for (int i = 0; i < n; i++) {
                mine[i] = states[i][r];
            }
            VerifyGraph g = c.verify.get(key);
            try (var guard = Tensor.noGradGuard()) {
                AutoScope scope = new AutoScope();
                Tensor.push(scope);
                try {
                    smile.torch.Aten.call("aten::copy_", g.accepted, Tensor.of(kept, reqs).to(c.device), false);
                    long[] drafted = null;
                    if (!externalDraft && r == draftRank) {
                        dg.fill(c.device, ds, mine, anchors);
                        smile.torch.Native.cudaGraphReplay(dg.graph);
                        drafted = dg.out.to(Device.CPU()).longArray();
                        for (int i = 0; i < n; i++) {
                            ds[i].absorbed = mine[i].length + kept[i];
                        }
                    } else {
                        c.decoder.commit(reqs, rowsPer, g.tbl, g.accepted);
                    }
                    for (int i = 0; i < n; i++) {
                        mine[i].length += kept[i];
                    }
                    return drafted;
                } finally {
                    Tensor.pop();
                }
            }
        });
        long[] drafted;
        if (externalDraft) {
            InklingState[] refStates = new InklingState[n];
            for (int i = 0; i < n; i++) {
                refStates[i] = states[i][0];
                // mine[i].length was already incremented by kept[i]; dg.fill expects pre-commit length, so adjust below
            }
            VerifyGraph g0 = contexts[draftTapRank()].verify.get(key);
            try (var guard = Tensor.noGradGuard()) {
                AutoScope scope = new AutoScope();
                Tensor.push(scope);
                try {
                    int stride = draft.pointerStride();
                    long[] pt = new long[reqs * stride];
                    long[] av = new long[reqs];
                    int[] pv = new int[reqs];
                    for (int i = 0; i < reqs; i++) {
                        boolean real = i < n;
                        long[] row = (real ? ds[i] : padDraft).pointers();
                        System.arraycopy(row, 0, pt, i * stride, stride);
                        av[i] = real ? anchors[i] : 0;
                        pv[i] = real ? refStates[i].length - kept[i] : 0;
                    }
                    smile.torch.Aten.call("aten::copy_", dg.extAccepted, Tensor.of(kept, reqs).to(draftDevice), false);
                    smile.torch.Aten.call("aten::copy_", dg.extTaps, g0.taps.to(draftDevice), false);
                    smile.torch.Aten.call("aten::copy_", dg.ptrs, Tensor.of(pt, pt.length).to(draftDevice), false);
                    smile.torch.Aten.call("aten::copy_", dg.anchor, Tensor.of(av, reqs).to(draftDevice), false);
                    smile.torch.Aten.call("aten::copy_", dg.p0, Tensor.of(pv, reqs).to(draftDevice), false);
                    smile.torch.Native.cudaGraphReplay(dg.graph);
                    drafted = dg.out.to(Device.CPU()).longArray();
                    for (int i = 0; i < n; i++) {
                        ds[i].absorbed = refStates[i].length;
                    }
                } finally {
                    Tensor.pop();
                }
            }
        } else {
            drafted = next.get(draftRank);
        }
        for (int i = 0; i < n; i++) {
            int[] p = new int[nd];
            for (int j = 0; j < nd; j++) {
                p[j] = (int) drafted[i * nd + j];
            }
            pending.put(requestIds[i], p);
        }
        return out;
    }

    /** Runs {@code task} in its own tensor scope (the draft's temporaries are freed on return). */
    private static <T> T scoped0(Supplier<T> task) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            return task.get();
        } finally {
            Tensor.pop();
        }
    }

    @Override
    public void finish(int requestId, int[] sequenceTokens) {
        long t0 = System.nanoTime();
        try {
            finishImpl(requestId, sequenceTokens);
        } finally {
            timed("finish", t0);
        }
    }

    private void finishImpl(int requestId, int[] sequenceTokens) {
        InklingState[] st = sessions.get(requestId);
        if (st != null && prefixCache != null && prefixReuse && sequenceTokens != null && !images.containsKey(requestId)
                && st[0].length >= SNAPSHOT_MIN && st[0].length <= sequenceTokens.length) {
            // the whole conversation so far is the likely prefix of the next turn
            int[] tokens = Arrays.copyOf(sequenceTokens, st[0].length);
            synchronized (lock) {
                try {
                    store(st, draftStates.get(requestId), tokens);
                } catch (RuntimeException e) {
                    // the request itself succeeded: a missing snapshot only costs a later prefill
                    logger.warn("Inkling: could not store the prefix snapshot of {} tokens: {}", tokens.length, e.toString());
                }
            }
        }
        evict(requestId);
    }

    @Override
    public void evict(int requestId) {
        maxCapacities.remove(requestId);
        InklingPrefixCache.Entry hit = prefixMatches.remove(requestId);
        if (hit != null) {
            synchronized (prefixCache) {
                prefixCache.release(hit);
            }
        }
        ImageState is = images.remove(requestId);
        if (is != null) {
            synchronized (lock) {
                ranks.onRanks(r -> {
                    is.embeds[r].close();
                    return null;
                });
            }
        }
        pending.remove(requestId);
        InklingDraft.State ds = draftStates.remove(requestId);
        if (ds != null) {
            synchronized (lock) {
                onDraftThread(() -> {
                    ds.close();
                    return null;
                });
            }
        }
        InklingState[] s = sessions.remove(requestId);
        if (s != null) {
            synchronized (lock) {
                giveBack(s);
            }
        }
    }

    /**
     * Tokens processed so far for a request.
     *
     * @param requestId the request id from {@link #bind}.
     * @return the position of its next token.
     */
    public int positionOf(int requestId) {
        return session(requestId)[0].length;
    }

    @Override
    public void close() {
        for (int id : new ArrayList<>(sessions.keySet())) {
            evict(id);
        }
        synchronized (lock) {
            for (InklingState[] spare : spareStates) {
                ranks.onRanks(r -> {
                    spare[r].close();
                    return null;
                });
            }
            spareStates.clear();
            if (prefixCache != null) {
                synchronized (prefixCache) {
                    prefixCache.clear();
                }
            }
            ranks.onRanks(r -> {
                for (Graph g : contexts[r].graphs.values()) {
                    if (g.graph != null) {
                        smile.torch.Native.cudaGraphDestroy(g.graph);
                    }
                }
                contexts[r].graphs.clear();
                for (VerifyGraph g : contexts[r].verify.values()) {
                    if (g.graph != null) {
                        smile.torch.Native.cudaGraphDestroy(g.graph);
                    }
                }
                contexts[r].verify.clear();
                if (contexts[r].pad != null) {
                    contexts[r].pad.close();
                    contexts[r].pad = null;
                }
                return null;
            });
            onDraftThread(() -> {
                for (DraftGraph g : draftGraphs.values()) {
                    if (g.graph != null) {
                        smile.torch.Native.cudaGraphDestroy(g.graph);
                    }
                }
                draftGraphs.clear();
                if (padDraft != null) {
                    padDraft.close();
                    padDraft = null;
                }
                return null;
            });
        }
        ranks.close();
    }
}
