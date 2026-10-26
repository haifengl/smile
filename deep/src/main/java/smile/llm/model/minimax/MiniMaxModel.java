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
package smile.llm.model.minimax;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smile.deep.layer.EmbeddingLayer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCachePool;
import smile.llm.model.qwen.PartialRotaryEncoding;
import smile.llm.model.qwen.QwenRMSNorm;
import smile.util.AutoScope;

import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_list_as_module;
import static smile.torch.smile_torch_h.smile_module_list_create;
import static smile.torch.smile_torch_h.smile_module_list_free;
import static smile.torch.smile_torch_h.smile_module_list_push_back;

/**
 * MiniMax-M3 text model: embeddings, decoder blocks, final norm and LM head.
 *
 * <p>Construct on CPU, load weights, call {@link #to(Device)} to place parameters and the RoPE
 * tables, then install the KV pool with {@link #setKvCachePool}. This first version is the
 * eager reference path (dense attention, host-routed MoE): no CUDA graphs, no tensor parallelism.
 *
 * @author Haifeng Li
 */
public class MiniMaxModel extends LayerBlock {
    final MiniMaxModelArgs params;
    final MiniMaxShard shard;
    final EmbeddingLayer tokEmbeddings;
    final List<MiniMaxBlock> layers = new ArrayList<>();
    final QwenRMSNorm norm;
    final LinearLayer lmHead;
    /** Non-null when the embedding and the output head are split by vocabulary across ranks. */
    private final smile.llm.parallel.TensorParallelGroup group;
    PartialRotaryEncoding.CosSin rope;
    KvCachePool kvCachePool;
    MiniMaxIndexCache indexCache;

    /**
     * Constructs the module graph on CPU.
     *
     * @param args hyperparameters.
     */
    public MiniMaxModel(MiniMaxModelArgs args) {
        this(args, MiniMaxShard.single(), null);
    }

    /**
     * Expert-parallel constructor: one model replica per rank, each owning a slice of the experts.
     *
     * @param args  hyperparameters.
     * @param shard this rank's expert slice (its expert weights are allocated on {@code shard.device()}).
     * @param group all-reduce group, or {@code null} for a single rank.
     */
    public MiniMaxModel(MiniMaxModelArgs args, MiniMaxShard shard, smile.llm.parallel.TensorParallelGroup group) {
        super();
        this.params = args;
        this.shard = shard;
        this.group = shard.tp() ? group : null;
        this.tokEmbeddings = new EmbeddingLayer(shard.part(args.vocabSize()), args.dim());
        MemorySegment list = smile_module_list_create();
        for (int i = 0; i < args.numLayers(); i++) {
            MiniMaxBlock block = new MiniMaxBlock(i, args, shard, group);
            layers.add(block);
            smile_module_list_push_back(list, block.module);
        }
        this.norm = new QwenRMSNorm(args.dim(), args.normEps());
        this.lmHead = new LinearLayer(args.dim(), shard.part(args.vocabSize()), false);
        this.rope = PartialRotaryEncoding.computeCosSin(
                args.rotaryDim(), args.maxSeqLen() * 2, args.ropeTheta());

        MemorySegment listModule = smile_module_list_as_module(list);
        add("layers", listModule);
        smile_module_free(listModule);
        smile_module_list_free(list);
        add("embed_tokens", tokEmbeddings);
        add("norm", norm);
        add("lm_head", lmHead);
    }

    /**
     * Returns the hyperparameters.
     * @return model args.
     */
    public MiniMaxModelArgs params() {
        return params;
    }

    /**
     * Returns this rank's expert slice.
     * @return the shard.
     */
    public MiniMaxShard shard() {
        return shard;
    }

    /**
     * Returns the FP8-resident (Marlin) linear layers by their SMILE weight names, e.g.
     * {@code layers.3.self_attn.q_proj.weight}; empty unless the model was built in an MXFP8 weight mode.
     * Routed experts are loaded through {@link MiniMaxMoE} instead.
     *
     * @return name to layer map.
     */
    public java.util.Map<String, smile.llm.quant.MxFp8Layer> quantLinears() {
        var out = new java.util.LinkedHashMap<String, smile.llm.quant.MxFp8Layer>();
        for (MiniMaxBlock layer : layers) {
            layer.collect(out);
        }
        return out;
    }

    /**
     * Returns the decoder blocks.
     * @return blocks in layer order.
     */
    public List<MiniMaxBlock> layers() {
        return layers;
    }

    /**
     * Returns the installed KV pool.
     * @return KV pool, or {@code null} before {@link #setKvCachePool}.
     */
    public KvCachePool kvCachePool() {
        return kvCachePool;
    }

    @Override
    public MiniMaxModel to(Device device) {
        super.to(device);
        moveRope(device);
        return this;
    }

    @Override
    public MiniMaxModel to(Device device, ScalarType dtype) {
        super.to(device, dtype);
        moveRope(device);
        return this;
    }

    private void moveRope(Device device) {
        Tensor cos = rope.cos().to(device);
        Tensor sin = rope.sin().to(device);
        if (cos != rope.cos() || sin != rope.sin()) {
            cos.detachFromScopes();
            sin.detachFromScopes();
            rope.close();
            rope = new PartialRotaryEncoding.CosSin(cos, sin);
        }
    }

    /** Merges each layer's attention projections into one GEMM where the weights allow it; call after loading. */
    public void fuseProjections() {
        for (var layer : layers) {
            layer.selfAttn.fuseProjections();
        }
    }

    /**
     * Installs the KV pool in every attention layer.
     *
     * @param pool          new KV pool (must not be {@code null}).
     * @param closePrevious {@code true} to close the previous pool when replaced.
     */
    public void setKvCachePool(KvCachePool pool, boolean closePrevious) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null");
        }
        var previous = this.kvCachePool;
        closeGraphs();
        this.kvCachePool = pool;
        if (indexCache != null) {
            indexCache.close();
            indexCache = null;
        }
        int sparseLayers = params.numSparseLayers();
        if (sparseLayers > 0) {
            indexCache = new MiniMaxIndexCache(sparseLayers, pool.numSlots(), params.numIndexKeyHeads(),
                    params.indexDim(), pool.device(), pool.computeDtype());
        }
        int ordinal = 0;
        for (var layer : layers) {
            layer.selfAttn.setCachePool(pool);
            if (params.isSparse(layer.layerId())) {
                layer.selfAttn.setIndexCache(indexCache, ordinal++);
            }
        }
        if (closePrevious && previous != null && previous != pool) {
            previous.close();
        }
    }

    /**
     * Prefill / multi-token window forward at a uniform cache position.
     *
     * @param tokens         token ids {@code [B, S]}.
     * @param startPos       cache start position.
     * @param allTokenLogits score every position when {@code true}, otherwise only the last.
     * @return float32 logits.
     */
    public Tensor forward(Tensor tokens, int startPos, boolean allTokenLogits) {
        return forward(tokens, startPos, allTokenLogits, null, null);
    }

    /**
     * Prefill window whose embeddings are partly replaced, for multimodal prompts: rows of the token embeddings
     * at {@code rows} are overwritten by {@code replacement} (vision embeddings) before the first layer.
     *
     * @param tokens         token ids {@code [1, S]}.
     * @param startPos       cache start position.
     * @param allTokenLogits score every position when {@code true}, otherwise only the last.
     * @param rows           window positions to overwrite, or {@code null}.
     * @param replacement    {@code [rows.length, hidden]} embeddings in the compute dtype, on this device.
     * @return float32 logits.
     */
    public Tensor forward(Tensor tokens, int startPos, boolean allTokenLogits, long[] rows, Tensor replacement) {
        int batch = (int) tokens.shape()[0];
        int seqlen = (int) tokens.shape()[1];
        int[] positions = new int[batch];
        Arrays.fill(positions, startPos);
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        MiniMaxStep step = MiniMaxStep.begin(positions);
        try (var window = Index.slice(startPos, startPos + seqlen)) {
            Tensor cos = rope.cos().get(window);
            Tensor sin = rope.sin().get(window);
            Tensor h = embed(tokens);
            if (rows != null && rows.length > 0) {
                long[] shape = h.shape();
                Tensor flat = h.reshape(-1, shape[shape.length - 1]);
                Tensor index = Tensor.of(rows).to(h.device());
                flat.put_(replacement.to(flat.dtype()), index);
                h = flat.reshape(shape);
            }
            Tensor mask = seqlen > 1 ? causalMask(seqlen, startPos, h) : null;
            Tensor logits = run(h, positions, cos, sin, mask, allTokenLogits);
            logits.promoteToParent();
            return logits;
        } finally {
            step.close();
            Tensor.pop();
        }
    }

    /**
     * Single-token decode with per-row absolute positions.
     *
     * @param tokens    token ids {@code [B, 1]}.
     * @param positions write position per batch row (also the RoPE position).
     * @return float32 logits {@code [B, 1, V]}.
     */
    public Tensor forward(Tensor tokens, int[] positions) {
        if (positions == null || positions.length != (int) tokens.shape()[0]) {
            throw new IllegalArgumentException("positions length must equal batch size");
        }
        if (tokens.shape()[1] != 1) {
            throw new IllegalArgumentException("ragged forward requires seqLen == 1");
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        MiniMaxStep step = MiniMaxStep.begin(positions);
        try {
            Tensor cos = PartialRotaryEncoding.gather(rope.cos(), positions);
            Tensor sin = PartialRotaryEncoding.gather(rope.sin(), positions);
            Tensor h = embed(tokens);
            Tensor logits = run(h, positions, cos, sin, null, false);
            logits.promoteToParent();
            return logits;
        } finally {
            step.close();
            Tensor.pop();
        }
    }


    // ------------------------------------------------------------------ CUDA-graph decode

    private static final org.slf4j.Logger graphLog = org.slf4j.LoggerFactory.getLogger(MiniMaxModel.class);
    /** Whole-step decode graphs (power-of-two batches, bucketed window); {@code SMILE_MINIMAX_CUDA_GRAPH=0} disables them. */
    private static final boolean GRAPHS_REQUESTED = !"0".equals(System.getenv("SMILE_MINIMAX_CUDA_GRAPH"));
    private static final int GRAPH_MAX_BATCH = intEnv("SMILE_MINIMAX_CUDA_GRAPH_MAX_BATCH", 32);
    private static final int GRAPH_MAX_ENTRIES = intEnv("SMILE_MINIMAX_CUDA_GRAPH_MAX_ENTRIES", 48);

    private static int intEnv(String name, int fallback) {
        try {
            return Integer.parseInt(System.getenv().getOrDefault(name, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** One captured decode step for a fixed (batch, window). */
    private static final class GraphEntry implements AutoCloseable {
        final int batch;
        final int length;
        /** Static input: tokens[B] | positions[B] | write slots[B] | read slots[B * length], all int64. */
        final Tensor meta;
        final Tensor keyPos;
        final Tensor logits;
        MemorySegment graph;
        boolean captured;

        GraphEntry(Device device, int batch, int length, int vocab) {
            this.batch = batch;
            this.length = length;
            var ints = new Tensor.Options().device(device).dtype(ScalarType.Int64).requireGradients(false);
            var floats = new Tensor.Options().device(device).dtype(ScalarType.Float).requireGradients(false);
            meta = Tensor.zeros(ints, 3L * batch + (long) batch * length);
            long[] grid = new long[length];
            for (int i = 0; i < length; i++) {
                grid[i] = i;
            }
            keyPos = Tensor.of(grid, 1, length).to(device);
            logits = Tensor.zeros(floats, batch, 1, vocab);
            for (Tensor t : new Tensor[]{meta, keyPos, logits}) {
                t.detachFromScopes();
            }
        }

        @Override
        public void close() {
            if (graph != null) {
                smile.torch.Native.cudaGraphDestroy(graph);
                graph = null;
            }
            meta.close();
            keyPos.close();
            logits.close();
        }
    }

    private final java.util.Map<Long, GraphEntry> graphs = new java.util.HashMap<>();
    private boolean graphsDisabled;

    /** Window bucket: at most ~12.5% above the needed length, a multiple of 256. */
    static int graphWindow(int needed) {
        int n = Math.max(needed, 256);
        int step = Math.max(256, Integer.highestOneBit(n) / 8);
        return (n + step - 1) / step * step;
    }

    private boolean graphEligible(int batch) {
        return GRAPHS_REQUESTED && !graphsDisabled && kvCachePool != null && smile.torch.Native.cudaGraphAvailable()
                && kvCachePool.device().isCUDA() && params.weightMode() == MiniMaxWeightMode.MARLIN
                && batch >= 1 && batch <= GRAPH_MAX_BATCH
                && smile.torch.Native.fusedOpsAvailable();
    }

    /** Frees every captured graph (the KV pool or the weights changed under them). */
    public void closeGraphs() {
        for (GraphEntry e : graphs.values()) {
            e.close();
        }
        graphs.clear();
    }

    /**
     * Single-token decode through a captured CUDA graph.
     *
     * <p>The first step of a (batch, window) shape runs eagerly twice (library handles and kernel attributes must
     * exist before capture), then captures the whole forward pass and replays it; later steps only refill the
     * static metadata buffer and replay. Nothing is built on the host while capturing: slots, positions and the
     * attention mask come from the static buffers (see {@link MiniMaxStep.External}).
     *
     * @param realTokens    last token of each row.
     * @param realPositions KV write position of each row.
     * @return float32 logits {@code [B, V]} (a fresh tensor), or {@code null} when graphs are off or not
     *         applicable and the caller must run the eager path.
     */
    public Tensor decodeGraph(long[] realTokens, int[] realPositions) {
        int real = realPositions.length;
        if (!graphEligible(real)) {
            return null;
        }
        // Pad to the next power of two with copies of row 0: a padded row reads and writes exactly what row 0 does
        // (same token, position and slots), so it needs no scratch KV slot and the real rows are unaffected.
        int batch = Integer.highestOneBit(real) == real ? real : Integer.highestOneBit(real) << 1;
        if (batch > GRAPH_MAX_BATCH) {
            return null;
        }
        long[] tokens = java.util.Arrays.copyOf(realTokens, batch);
        int[] positions = java.util.Arrays.copyOf(realPositions, batch);
        for (int i = real; i < batch; i++) {
            tokens[i] = realTokens[0];
            positions[i] = realPositions[0];
        }
        int maxPos = 0;
        for (int p : positions) {
            maxPos = Math.max(maxPos, p);
        }
        int length = graphWindow(maxPos + 1);
        long key = ((long) batch << 32) | length;
        GraphEntry entry = graphs.get(key);
        if (entry == null && graphs.size() >= GRAPH_MAX_ENTRIES) {
            return null;
        }
        Device device = kvCachePool.device();
        long[] host = new long[3 * batch + batch * length];
        long[] write = kvCachePool.slotIndices(realPositions, 1);
        long[] read = kvCachePool.slotIndices(length);
        write = java.util.Arrays.copyOf(write, batch);
        read = java.util.Arrays.copyOf(read, batch * length);
        for (int i = real; i < batch; i++) {
            write[i] = write[0];
            System.arraycopy(read, 0, read, i * length, length);
        }
        for (int i = 0; i < batch; i++) {
            host[i] = tokens[i];
            host[batch + i] = positions[i];
            host[2 * batch + i] = write[i];
        }
        System.arraycopy(read, 0, host, 3 * batch, read.length);
        try {
            boolean fresh = entry == null;
            if (fresh) {
                entry = new GraphEntry(device, batch, length, params.vocabSize());
                graphs.put(key, entry);
            }
            try (Tensor src = Tensor.of(host)) {
                smile.torch.Native.copy_(entry.meta, src);
            }
            if (fresh) {
                for (int i = 0; i < 2; i++) {
                    smile.torch.Native.copy_(entry.logits, graphForward(entry, positions));
                }
                entry.graph = smile.torch.Native.cudaGraphCreate();
                int deviceIndex = Byte.toUnsignedInt(device.index());
                long t0 = System.nanoTime();
                smile.torch.Native.cudaGraphCaptureBegin(entry.graph, deviceIndex);
                try {
                    smile.torch.Native.copy_(entry.logits, graphForward(entry, positions));
                } finally {
                    smile.torch.Native.cudaGraphCaptureEnd(entry.graph);
                }
                entry.captured = true;
                graphLog.info("captured MiniMax decode graph batch={} window={} in {} ms ({} graphs)", batch, length,
                        (System.nanoTime() - t0) / 1_000_000, graphs.size());
            }
            smile.torch.Native.cudaGraphReplay(entry.graph);
            Tensor all = entry.logits.reshape(batch, -1);
            if (real == batch) {
                return all.copy();
            }
            try (var rows = Index.slice(0, real)) {
                return all.get(rows).copy();
            }
        } catch (RuntimeException e) {
            graphLog.warn("MiniMax decode CUDA graph failed, falling back to eager decode: {}", e.toString());
            if (System.getenv("SMILE_MINIMAX_GRAPH_DEBUG") != null) {
                e.printStackTrace();
            }
            graphsDisabled = true;
            closeGraphs();
            return null;
        }
    }

    /** The decode forward pass reading all per-step data from the entry's static buffers. */
    private Tensor graphForward(GraphEntry e, int[] positions) {
        int b = e.batch;
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        MiniMaxStep step = null;
        try {
            Tensor tokens = e.meta.get(Index.slice(0, b)).reshape(b, 1);
            Tensor pos = e.meta.get(Index.slice(b, 2 * b));
            Tensor write = e.meta.get(Index.slice(2 * b, 3 * b));
            Tensor read = e.meta.get(Index.slice(3 * b, 3 * b + b * e.length));
            step = MiniMaxStep.beginExternal(positions,
                    new MiniMaxStep.External(pos, write, read, e.keyPos, e.length));
            Tensor cos = rope.cos().get(pos).unsqueeze(1);
            Tensor sin = rope.sin().get(pos).unsqueeze(1);
            Tensor h = embed(tokens);
            Tensor logits = run(h, positions, cos, sin, null, false);
            logits.promoteToParent();
            return logits;
        } finally {
            if (step != null) {
                step.close();
            }
            Tensor.pop();
        }
    }

    @Override
    public Tensor forward(Tensor tokens) {
        return forward(tokens, 0, false);
    }

    /**
     * Looks up the token embeddings. With a vocabulary-split embedding every rank looks up the tokens in its own
     * slice (others contribute zeros) and the slices are summed across ranks.
     */
    private Tensor embed(Tensor tokens) {
        if (group == null) {
            return tokEmbeddings.forward(tokens);
        }
        int local = shard.part(params.vocabSize());
        // Token ids are far below 2^24, so float arithmetic is exact and keeps host tensors out of CUDA-graph capture.
        Tensor index = tokens.to(ScalarType.Float).sub((double) shard.partStart(params.vocabSize()));
        Tensor valid = index.ge(0).and(index.lt(local));
        Tensor rows = tokEmbeddings.forward(index.clamp(0, local - 1).to(ScalarType.Int64));
        Tensor out = rows.mul(valid.to(rows.dtype()).unsqueeze(-1));
        group.allReduceSumInPlace(shard.rank(), out);
        return out;
    }

    /** Output head: the full-vocabulary logits (a vocabulary-split head is gathered across ranks). */
    private Tensor project(Tensor hidden) {
        Tensor local = lmHead.forward(hidden);
        if (group == null) {
            return local;
        }
        long[] shape = local.shape();
        int vocab = params.vocabSize();
        shape[shape.length - 1] = vocab;
        Tensor full = Tensor.zeros(new Tensor.Options().device(local.device()).dtype(local.dtype()).requireGradients(false), shape);
        try (var cols = Index.slice(shard.partStart(vocab), shard.partStart(vocab) + shard.part(vocab));
             Tensor slot = full.get(Index.Ellipsis, cols)) {
            smile.torch.Native.copy_(slot, local);
        }
        group.allReduceSumInPlace(shard.rank(), full);
        return full;
    }

    private Tensor run(Tensor h, int[] positions, Tensor cos, Tensor sin, Tensor mask,
                       boolean allTokenLogits) {
        for (MiniMaxBlock layer : layers) {
            h = layer.forward(h, positions, cos, sin, mask);
        }
        Tensor normalized = norm.forward(h);
        int seqlen = (int) normalized.shape()[1];
        Tensor logits;
        if (!allTokenLogits && seqlen > 1) {
            try (var last = Index.of(-1)) {
                Tensor lastRow = normalized.get(Index.Colon, last).unsqueeze(1);
                logits = project(lastRow);
            }
        } else {
            logits = project(normalized);
        }
        return logits.to(ScalarType.Float);
    }

    private static Tensor causalMask(int seqlen, int startPos, Tensor like) {
        var opts = new Tensor.Options().device(like.device()).dtype(ScalarType.Float).requireGradients(false);
        Tensor mask = Tensor.zeros(opts, seqlen, seqlen).fill_(Float.NEGATIVE_INFINITY);
        mask.triu_(1);
        if (startPos > 0) {
            Tensor zeros = Tensor.zeros(opts, seqlen, startPos);
            mask = Tensor.hstack(zeros, mask);
        }
        return mask.to(like.dtype());
    }
}
