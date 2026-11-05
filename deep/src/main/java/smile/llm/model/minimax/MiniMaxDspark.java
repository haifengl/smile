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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.attention.TorchNativeAttentionKernel;
import smile.llm.model.qwen.PartialRotaryEncoding;
import smile.llm.quant.LinearOp;
import smile.llm.quant.MxFp8;
import smile.llm.quant.MxFp8Layer;
import smile.torch.Native;
import smile.util.AutoScope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The MiniMax-M3.1 DSpark block drafter.
 *
 * <p>Per committed token the target's hidden states at {@code target_layer_ids} are fused
 * ({@code fc}, {@code hidden_norm}) and turned into K/V of every draft layer ({@link #absorb}); a round
 * ({@link #draft}) then runs the block {@code [anchor, mask, ..., mask]} through the draft layers in one parallel
 * pass, non-causal inside the block and attending to the cached context, and samples the block left to right with
 * a low-rank Markov transition bias on top of the target's own output head.
 *
 * <p>This class runs on one device and owns no tensor parallelism: the target's embedding and output head (shared
 * with the draft, they are not part of the checkpoint) come through {@link TargetHeads}. The context K/V is a ring
 * per request, so only the last {@code sliding_window} positions are kept. Eager reference implementation.
 *
 * @author Haifeng Li
 */
public class MiniMaxDspark implements AutoCloseable {
    private static final String PREFIX = "language_model.model.dspark.";
    private static final TorchNativeAttentionKernel KERNEL = new TorchNativeAttentionKernel();

    /** The target's token embedding and output head. */
    public interface TargetHeads {
        /**
         * Looks up token embeddings.
         *
         * @param tokenIds int64 ids {@code [n]} on the draft's device.
         * @return embeddings {@code [n, dim]} in the compute dtype (caller owns).
         */
        Tensor embed(Tensor tokenIds);

        /**
         * Applies the output head.
         *
         * @param hidden final-normed hidden states {@code [n, dim]} in the compute dtype.
         * @return float32 logits {@code [n, vocab]} (caller owns).
         */
        Tensor logits(Tensor hidden);
    }

    /**
     * One drafted block.
     *
     * @param tokens     the {@code blockSize} drafted token ids.
     * @param confidence per-token acceptance confidence in {@code [0, 1]}, or {@code null} without a confidence head.
     * @param hidden     the draft's final hidden states {@code [blockSize, dim]} (for tests), or {@code null}; the
     *                   caller owns it.
     */
    public record Draft(int[] tokens, float[] confidence, Tensor hidden) { }

    /** Gemma-style RMSNorm {@code x * rsqrt(mean(x^2) + eps) * (1 + w)} with a plain weight tensor. */
    private static final class Norm implements AutoCloseable {
        final Tensor weight;
        final double eps;

        Norm(Tensor weight, double eps) {
            this.weight = weight;
            this.eps = eps;
        }

        Tensor forward(Tensor x) {
            Tensor fused = Native.gemmaRmsNorm(x, weight, eps);
            if (fused != null) {
                return fused;
            }
            try (Tensor f = x.to(ScalarType.Float);
                 Tensor mean = f.pow(2).mean(-1, true);
                 Tensor denom = mean.add(eps).rsqrt_();
                 Tensor normalized = f.mul(denom);
                 Tensor scale = weight.to(ScalarType.Float).add(1.0);
                 Tensor scaled = normalized.mul(scale)) {
                return scaled.to(x.dtype());
            }
        }

        @Override
        public void close() {
            weight.close();
        }
    }

    /** A dense bf16 projection {@code x @ W^T}. */
    private static final class DenseOp implements LinearOp, AutoCloseable {
        final Tensor weight;

        DenseOp(Tensor weight) {
            this.weight = weight;
        }

        @Override
        public Tensor forward(Tensor input) {
            try (Tensor t = weight.transpose(0, 1)) {
                return input.matmul(t);
            }
        }

        @Override
        public void close() {
            weight.close();
        }
    }

    private static final class Layer {
        Norm inputNorm, postNorm, qNorm, kNorm;
        LinearOp q, k, v, o;
        MiniMaxMlp mlp;
    }

    /** One request's context K/V: per layer {@code [ring, numKvHeads * headDim]}. */
    static final class Cache {
        final Tensor[] k;
        final Tensor[] v;
        final int ring;

        Cache(int layers, int ring, int width, Device device, ScalarType dtype) {
            this.ring = ring;
            this.k = new Tensor[layers];
            this.v = new Tensor[layers];
            var options = new Tensor.Options().device(device).dtype(dtype).requireGradients(false);
            for (int i = 0; i < layers; i++) {
                k[i] = Tensor.zeros(options, ring, width);
                v[i] = Tensor.zeros(options, ring, width);
                // Owned by the request, not by whichever AutoScope is open on the creating thread (a rank thread's
                // prefill scope would free it under the request).
                k[i].detachFromScopes();
                v[i].detachFromScopes();
            }
        }

        void close() {
            for (int i = 0; i < k.length; i++) {
                k[i].close();
                v[i].close();
            }
        }
    }

    private final MiniMaxDsparkArgs args;
    private final Device device;
    private final ScalarType dtype;
    private final TargetHeads heads;
    private final Layer[] layers;
    private final Map<Integer, Cache> caches = new HashMap<>();
    private final List<AutoCloseable> owned = new ArrayList<>();
    private final int numRep;
    private PartialRotaryEncoding.CosSin rope;
    private Tensor fc;
    private Norm hiddenNorm, finalNorm;
    private Tensor markovW1, markovW2;
    private Tensor confHidden, confMarkov, confBias;

    private MiniMaxDspark(MiniMaxDsparkArgs args, Device device, ScalarType dtype, TargetHeads heads, int maxPositions,
                          MiniMaxWeightMode mode) {
        this.args = args;
        this.device = device;
        this.dtype = dtype;
        this.heads = heads;
        this.numRep = args.numHeads() / args.numKvHeads();
        this.layers = new Layer[args.numLayers()];
        for (int i = 0; i < layers.length; i++) {
            Layer l = new Layer();
            l.q = mode.newLinear(args.dim(), args.numHeads() * args.headDim());
            l.k = mode.newLinear(args.dim(), args.numKvHeads() * args.headDim());
            l.v = mode.newLinear(args.dim(), args.numKvHeads() * args.headDim());
            l.o = mode.newLinear(args.numHeads() * args.headDim(), args.dim());
            l.mlp = new MiniMaxMlp(args.dim(), args.ffnSize(), args.swigluAlpha(), args.swigluLimit(), mode);
            layers[i] = l;
        }
        PartialRotaryEncoding.CosSin table = PartialRotaryEncoding.computeCosSin(
                args.rotaryDim(), maxPositions, args.ropeTheta());
        Tensor cos = table.cos().to(device);
        Tensor sin = table.sin().to(device);
        cos.detachFromScopes();
        sin.detachFromScopes();
        this.rope = new PartialRotaryEncoding.CosSin(cos, sin);
    }

    /**
     * Loads the draft from a {@code dspark/} checkpoint directory.
     *
     * @param dir          directory holding {@code config.json}, {@code model.safetensors.index.json} and the shards.
     * @param device       device to place the draft on.
     * @param mode         weight mode of the draft projections (the draft ships MXFP8 weights like the target).
     * @param dtype        compute dtype (bf16).
     * @param heads        the target's embedding and output head.
     * @param maxPositions size of the RoPE tables (the target's maximum sequence length).
     * @return the draft.
     * @throws IOException if a file is unreadable or a tensor is missing or unexpected.
     */
    public static MiniMaxDspark load(String dir, Device device, MiniMaxWeightMode mode, ScalarType dtype,
                                     TargetHeads heads, int maxPositions) throws IOException {
        MiniMaxDsparkArgs args = MiniMaxDsparkArgs.fromHuggingFace(new File(dir, "config.json").getPath());
        MiniMaxDspark draft = new MiniMaxDspark(args, device, dtype, heads, maxPositions, mode);
        try {
            draft.loadWeights(new File(dir), mode);
        } catch (IOException | RuntimeException e) {
            draft.close();
            throw e;
        }
        return draft;
    }

    private void loadWeights(File dir, MiniMaxWeightMode mode) throws IOException {
        JsonNode index = new ObjectMapper().readTree(new File(dir, "model.safetensors.index.json"));
        Map<String, List<String>> shards = new java.util.TreeMap<>();
        index.get("weight_map").properties().forEach(e ->
                shards.computeIfAbsent(e.getValue().asString(), k -> new ArrayList<>()).add(e.getKey()));
        Set<String> loaded = new HashSet<>();
        int expected = 0;
        for (var shard : shards.entrySet()) {
            SafeTensors st = SafeTensors.read(Path.of(dir.getPath(), shard.getKey()).toString(), Device.CPU(),
                    shard.getValue());
            try (var noGrad = Tensor.noGradGuard()) {
                for (String hfName : shard.getValue()) {
                    expected++;
                    if (hfName.endsWith("_scale_inv")) {
                        continue;  // consumed with its weight
                    }
                    Tensor src = st.tensors().get(hfName);
                    if (src == null) {
                        throw new IOException("Tensor '" + hfName + "' missing from " + shard.getKey());
                    }
                    if (!hfName.startsWith(PREFIX)) {
                        throw new IOException("unexpected DSpark tensor " + hfName);
                    }
                    String name = hfName.substring(PREFIX.length());
                    Tensor scale = st.tensors().get(hfName + "_scale_inv");
                    assign(name, src, scale, mode);
                    loaded.add(hfName);
                    if (scale != null) {
                        loaded.add(hfName + "_scale_inv");
                    }
                }
            } finally {
                for (Tensor t : st.tensors().values()) {
                    t.close();
                }
            }
        }
        if (loaded.size() != expected) {
            throw new IOException("DSpark checkpoint: loaded " + loaded.size() + " of " + expected + " tensors");
        }
        if (fc == null || hiddenNorm == null || finalNorm == null || markovW1 == null || markovW2 == null
                || (args.confidenceHead() && confBias == null)) {
            throw new IOException("DSpark checkpoint is missing top-level tensors");
        }
    }

    private Tensor onDevice(Tensor t) {
        Tensor d = t.to(device);
        Tensor c = d.contiguous();
        if (d != t && c != d) {
            d.close();
        }
        if (c == t) {
            c = t.copy();   // t is closed with the file's tensors
        }
        c.detachFromScopes();
        return c;
    }

    private Norm norm(Tensor src) {
        Norm n = new Norm(onDevice(src), args.normEps());
        owned.add(n);
        return n;
    }

    private LinearOp project(LinearOp slot, Tensor weight, Tensor scale, MiniMaxWeightMode mode) {
        if (scale == null) {
            throw new IllegalStateException("projection without scales");
        }
        if (mode.quantized()) {
            Tensor fp8 = weight.to(device);
            Tensor s = scale.to(device);
            try {
                ((MxFp8Layer) slot).setWeight(fp8, s);
            } finally {
                if (fp8 != weight) fp8.close();
                if (s != scale) s.close();
            }
            owned.add((MxFp8Layer) slot);
            return slot;
        }
        Tensor fp8 = weight.to(device);
        Tensor s = scale.to(device);
        Tensor dense = MxFp8.dequantize(fp8, s, dtype);
        if (fp8 != weight) fp8.close();
        if (s != scale) s.close();
        dense.detachFromScopes();
        DenseOp op = new DenseOp(dense);
        owned.add(op);
        return op;
    }

    private void assign(String name, Tensor src, Tensor scale, MiniMaxWeightMode mode) throws IOException {
        switch (name) {
            case "fc.weight" -> fc = onDevice(src);
            case "hidden_norm.weight" -> hiddenNorm = norm(src);
            case "final_norm.weight" -> finalNorm = norm(src);
            case "markov_head.markov_w1.weight" -> markovW1 = onDevice(src);
            case "markov_head.markov_w2.weight" -> markovW2 = onDevice(src);
            case "confidence_head.proj.weight" -> {
                try (Tensor w = onDevice(src);
                     Tensor f = w.to(ScalarType.Float);
                     var h = Index.slice(0, args.dim());
                     var m = Index.slice(args.dim(), (int) f.shape()[1]);
                     Tensor wh = f.get(Index.Colon, h);
                     Tensor wm = f.get(Index.Colon, m)) {
                    confHidden = wh.contiguous().copy();
                    confMarkov = wm.contiguous().copy();
                    confHidden.detachFromScopes();
                    confMarkov.detachFromScopes();
                }
            }
            case "confidence_head.proj.bias" -> {
                try (Tensor b = onDevice(src)) {
                    confBias = b.to(ScalarType.Float).copy();
                    confBias.detachFromScopes();
                }
            }
            default -> assignLayer(name, src, scale, mode);
        }
    }

    private static final java.util.regex.Pattern LAYER = java.util.regex.Pattern.compile(
            "^layers\\.(\\d+)\\.decoder_layer\\.(.+)$");

    private void assignLayer(String name, Tensor src, Tensor scale, MiniMaxWeightMode mode) throws IOException {
        var m = LAYER.matcher(name);
        if (!m.matches() || Integer.parseInt(m.group(1)) >= layers.length) {
            throw new IOException("unexpected DSpark tensor " + name);
        }
        Layer l = layers[Integer.parseInt(m.group(1))];
        switch (m.group(2)) {
            case "input_layernorm.weight" -> l.inputNorm = norm(src);
            case "post_attention_layernorm.weight" -> l.postNorm = norm(src);
            case "self_attn.q_norm.weight" -> l.qNorm = norm(src);
            case "self_attn.k_norm.weight" -> l.kNorm = norm(src);
            case "self_attn.q_proj.weight" -> l.q = project(l.q, src, scale, mode);
            case "self_attn.k_proj.weight" -> l.k = project(l.k, src, scale, mode);
            case "self_attn.v_proj.weight" -> l.v = project(l.v, src, scale, mode);
            case "self_attn.o_proj.weight" -> l.o = project(l.o, src, scale, mode);
            case "mlp.gate_proj.weight" -> l.mlp.w1 = project(l.mlp.w1, src, scale, mode);
            case "mlp.up_proj.weight" -> l.mlp.w3 = project(l.mlp.w3, src, scale, mode);
            case "mlp.down_proj.weight" -> l.mlp.w2 = project(l.mlp.w2, src, scale, mode);
            default -> throw new IOException("unexpected DSpark tensor " + name);
        }
    }

    /**
     * Returns the hyperparameters.
     * @return the draft args.
     */
    public MiniMaxDsparkArgs args() {
        return args;
    }

    /**
     * Allocates the context cache of a request.
     *
     * @param requestId the request.
     * @param capacity  most positions the request will reach.
     */
    public void bind(int requestId, int capacity) {
        int ring = Math.min(args.slidingWindow() + 2 * args.blockSize(), capacity + args.blockSize());
        Cache old = caches.put(requestId, new Cache(layers.length, ring, args.numKvHeads() * args.headDim(),
                device, dtype));
        if (old != null) {
            old.close();
        }
    }

    /**
     * Frees the context cache of a request.
     * @param requestId the request.
     */
    public void release(int requestId) {
        Cache c = caches.remove(requestId);
        if (c != null) {
            c.close();
        }
    }

    /**
     * Removes a request's context from this draft without freeing it, so a later request with the same prefix can adopt
     * it. The context of positions {@code [0, n)} depends only on the tokens before {@code n}, hence is reusable.
     *
     * @param requestId the request.
     * @return its context, or {@code null} when it has none.
     */
    Cache detach(int requestId) {
        return caches.remove(requestId);
    }

    /**
     * Gives the context a detached request kept for the first {@code positions} positions to a new request.
     *
     * @param requestId the new request.
     * @param capacity  most positions it will reach.
     * @param source    a context returned by {@link #detach}; stays valid and owned by the caller.
     * @param positions positions to copy; must fit both rings without wrapping.
     * @return {@code true} if the context was installed.
     */
    boolean adopt(int requestId, int capacity, Cache source, int positions) {
        bind(requestId, capacity);
        Cache target = caches.get(requestId);
        if (positions > Math.min(target.ring, source.ring)) {
            release(requestId);
            return false;
        }
        try (var noGrad = Tensor.noGradGuard();
             var rows = Index.slice(0, positions)) {
            for (int i = 0; i < layers.length; i++) {
                try (Tensor dk = target.k[i].get(rows, Index.Colon);
                     Tensor sk = source.k[i].get(rows, Index.Colon);
                     Tensor dv = target.v[i].get(rows, Index.Colon);
                     Tensor sv = source.v[i].get(rows, Index.Colon)) {
                    Native.copy_(dk, sk);
                    Native.copy_(dv, sv);
                }
            }
        }
        return true;
    }

    /**
     * Frees a detached context.
     * @param context the context.
     */
    void discard(Cache context) {
        context.close();
    }

    /**
     * Returns the number of positions a detached context can hold without wrapping.
     * @param context the context.
     * @return its ring size.
     */
    int ringOf(Cache context) {
        return context.ring;
    }

    private Cache cache(int requestId) {
        Cache c = caches.get(requestId);
        if (c == null) {
            throw new IllegalStateException("request " + requestId + " is not bound");
        }
        return c;
    }

    private Tensor slots(int from, int count, int ring) {
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = (from + i) % ring;
        }
        return Tensor.of(ids).to(device);
    }

    /** Per-head Gemma norm of q and k, then partial RoPE; q and k are {@code [1, S, H, D]}. */
    private Tensor[] normRope(Tensor q, Tensor k, Norm qn, Norm kn, Tensor cos, Tensor sin) {
        Tensor[] fused = Native.qkNormRope(q, k, qn.weight, kn.weight, cos, sin, args.rotaryDim(), args.normEps());
        if (fused != null) {
            return fused;
        }
        long[] qs = q.shape();
        long[] ks = k.shape();
        Tensor qn4 = qn.forward(q.reshape(-1, args.headDim())).view(qs);
        Tensor kn4 = kn.forward(k.reshape(-1, args.headDim())).view(ks);
        long batch = qs[0];
        Tensor c = batch > 1 ? cos.reshape(batch, qs[1], -1) : cos;
        Tensor sn = batch > 1 ? sin.reshape(batch, qs[1], -1) : sin;
        var r = PartialRotaryEncoding.apply(qn4, kn4, c, sn, args.rotaryDim());
        return new Tensor[]{r._1(), r._2()};
    }

    /**
     * Adds the target hidden states of consecutive committed tokens to the context.
     *
     * @param requestId the request.
     * @param aux       taps {@code [n, numTapLayers * dim]} in the compute dtype on the draft's device (as returned
     *                  by {@code MiniMax.takeAux}); caller keeps ownership.
     * @param startPos  absolute position of the first row.
     */
    public void absorb(int requestId, Tensor aux, int startPos) {
        Cache c = cache(requestId);
        int n = (int) aux.shape()[0];
        if (aux.shape()[1] != args.contextWidth()) {
            throw new IllegalArgumentException("aux width " + aux.shape()[1] + " != " + args.contextWidth());
        }
        int skip = Math.max(0, n - c.ring);  // rows older than the ring are never read
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try (var noGrad = Tensor.noGradGuard();
             var rows = Index.slice(skip, n);
             var window = Index.slice(startPos + skip, startPos + n)) {
            int count = n - skip;
            Tensor tail = skip == 0 ? aux : aux.get(rows, Index.Colon);
            Tensor fcT = fc.transpose(0, 1);
            Tensor fused = tail.reshape(1, count, -1).matmul(fcT);
            Tensor x = hiddenNorm.forward(fused);
            Tensor cos = rope.cos().get(window);
            Tensor sin = rope.sin().get(window);
            Tensor idx = slots(startPos + skip, count, c.ring);
            long width = (long) args.numKvHeads() * args.headDim();
            for (int i = 0; i < layers.length; i++) {
                Layer l = layers[i];
                Tensor kRaw = l.k.forward(x).reshape(1, count, args.numKvHeads(), args.headDim());
                Tensor vRaw = l.v.forward(x).reshape(count, width);
                Tensor kRope = normRope(kRaw, kRaw, l.kNorm, l.kNorm, cos, sin)[1];
                c.k[i].put_(kRope.reshape(count, width).to(dtype), idx);
                c.v[i].put_(vRaw.to(dtype), idx);
            }
        } finally {
            Tensor.pop();
            scope.close();
        }
    }

    /**
     * Drafts one block.
     *
     * @param requestId  the request (its context must hold every committed position below {@code anchorPos}).
     * @param anchorToken the last sampled token, not yet in the context.
     * @param anchorPos  its absolute position.
     * @param wantHidden whether to return the draft's final hidden states (tests).
     * @return the drafted tokens.
     */
    public Draft draft(int requestId, int anchorToken, int anchorPos, boolean wantHidden) {
        return draftBatch(new int[]{requestId}, new int[]{anchorToken}, new int[]{anchorPos}, wantHidden)[0];
    }

    /**
     * Drafts one block for each of several requests in one pass: the projections, the MLPs, the output head and the
     * sequential Markov steps run once over all rows (the Markov weights are read once per step instead of once per
     * request); only the attention over each request's own context is per request.
     *
     * @param requestIds   the requests (each context must hold every committed position below its anchor).
     * @param anchorTokens each request's last sampled token, not yet in its context.
     * @param anchorPos    their absolute positions.
     * @return one block per request.
     */
    public Draft[] draftBatch(int[] requestIds, int[] anchorTokens, int[] anchorPos) {
        return draftBatch(requestIds, anchorTokens, anchorPos, false);
    }

    private Draft[] draftBatch(int[] requestIds, int[] anchorTokens, int[] anchorPos, boolean wantHidden) {
        int b = requestIds.length;
        int s = args.blockSize();
        int nkv = args.numKvHeads();
        int hd = args.headDim();
        int heads = args.numHeads();
        long width = (long) nkv * hd;
        Cache[] c = new Cache[b];
        int[] keys = new int[b];
        int[] lo = new int[b];
        for (int j = 0; j < b; j++) {
            c[j] = cache(requestIds[j]);
            lo[j] = Math.max(0, anchorPos[j] - (args.slidingWindow() - 1));
            keys[j] = anchorPos[j] + s - 1 - lo[j] + 1;
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try (var noGrad = Tensor.noGradGuard()) {
            // The block's inputs: the anchor's embedding at row 0, the shared mask-token embedding elsewhere.
            long[] ids = new long[b + 1];
            for (int j = 0; j < b; j++) {
                ids[j] = anchorTokens[j];
            }
            ids[b] = args.noiseTokenId();
            long[] pick = new long[b * s];
            long[] ropeRows = new long[b * s];
            for (int j = 0; j < b; j++) {
                for (int t = 0; t < s; t++) {
                    pick[j * s + t] = t == 0 ? j : b;
                    ropeRows[j * s + t] = anchorPos[j] + t;
                }
            }
            Tensor embedded = this.heads.embed(Tensor.of(ids).to(device));
            Tensor h = embedded.get(Tensor.of(pick).to(device)).reshape(b, s, args.dim());
            Tensor ropeIdx = Tensor.of(ropeRows).to(device);
            Tensor cos = rope.cos().get(ropeIdx);   // [b * s, R]: one row per token
            Tensor sin = rope.sin().get(ropeIdx);
            Tensor[] writeIdx = new Tensor[b];
            Tensor[] readIdx = new Tensor[b];
            for (int j = 0; j < b; j++) {
                writeIdx[j] = slots(anchorPos[j], s, c[j].ring);
                readIdx[j] = slots(lo[j], keys[j], c[j].ring);
            }
            for (int i = 0; i < layers.length; i++) {
                Layer l = layers[i];
                Tensor a = l.inputNorm.forward(h);
                Tensor qRaw = l.q.forward(a).reshape(b, s, heads, hd);
                Tensor kRaw = l.k.forward(a).reshape(b, s, nkv, hd);
                Tensor vRaw = l.v.forward(a).reshape(b, s, width);
                Tensor[] qk = normRope(qRaw, kRaw, l.qNorm, l.kNorm, cos, sin);
                Tensor kRows = qk[1].reshape(b, s, width).to(dtype);
                Tensor vRows = vRaw.to(dtype);
                java.util.List<Tensor> merged = new java.util.ArrayList<>(b);
                for (int j = 0; j < b; j++) {
                    c[j].k[i].put_(b == 1 ? kRows.reshape(s, width) : kRows.get(j), writeIdx[j]);
                    c[j].v[i].put_(b == 1 ? vRows.reshape(s, width) : vRows.get(j), writeIdx[j]);
                    Tensor kAll = c[j].k[i].get(readIdx[j]).reshape(keys[j], nkv, hd).permute(1, 0, 2).unsqueeze(0).contiguous();
                    Tensor vAll = c[j].v[i].get(readIdx[j]).reshape(keys[j], nkv, hd).permute(1, 0, 2).unsqueeze(0).contiguous();
                    // The numRep query heads of a KV group attend as extra query rows of that group's keys.
                    Tensor qj = b == 1 ? qk[0] : qk[0].get(j);
                    Tensor q = qj.reshape(s, nkv, numRep, hd).permute(1, 2, 0, 3).reshape(1, nkv, (long) numRep * s, hd);
                    Tensor att = KERNEL.forward(q, kAll, vAll, null, null);
                    merged.add(att.reshape(nkv, numRep, s, hd).permute(2, 0, 1, 3).reshape(s, (long) heads * hd));
                }
                Tensor mergedAll = (b == 1 ? merged.get(0) : Tensor.vstack(merged.toArray(new Tensor[0])))
                        .reshape(b, s, (long) heads * hd);
                h = h.add(l.o.forward(mergedAll));
                h = h.add(l.mlp.forward(l.postNorm.forward(h)));
            }
            Tensor fin = finalNorm.forward(h).reshape((long) b * s, args.dim());
            Tensor base = this.heads.logits(fin);   // [b * s, vocab]

            // Sequential Markov sampling, all requests together; everything stays on the device until the end.
            long[] sel = new long[s * b];
            for (int i = 0; i < s; i++) {
                for (int j = 0; j < b; j++) {
                    sel[i * b + j] = (long) j * s + i;
                }
            }
            Tensor selAll = Tensor.of(sel).to(device);
            var longs = new Tensor.Options().device(device).dtype(ScalarType.Int64).requireGradients(false);
            var floats = new Tensor.Options().device(device).dtype(ScalarType.Float).requireGradients(false);
            Tensor outTokens = Tensor.zeros(longs, s, b);
            Tensor outConf = args.confidenceHead() ? Tensor.zeros(floats, s, b) : null;
            long[] anchors = new long[b];
            for (int j = 0; j < b; j++) {
                anchors[j] = anchorTokens[j];
            }
            Tensor prev = Tensor.of(anchors).to(device);
            try (Tensor w2t = markovW2.transpose(0, 1);
                 Tensor ch = confHidden == null ? null : confHidden.transpose(0, 1);
                 Tensor cm = confMarkov == null ? null : confMarkov.transpose(0, 1)) {
                for (int i = 0; i < s; i++) {
                    Tensor idx;
                    try (var r = Index.slice(i * b, (i + 1) * b)) {
                        idx = selAll.get(r);
                    }
                    Tensor e = markovW1.get(prev);                              // [b, rank]
                    Tensor bias = e.matmul(w2t).to(ScalarType.Float);            // [b, vocab]
                    Tensor next = base.get(idx).add(bias).argmax(-1, false);     // [b]
                    outTokens.put_(next, Index.of(i));
                    if (outConf != null) {
                        Tensor z = fin.get(idx).to(ScalarType.Float).matmul(ch)
                                .add(e.to(ScalarType.Float).matmul(cm)).add(confBias);   // [b, 1]
                        outConf.put_(z.reshape(b), Index.of(i));
                    }
                    prev = next;
                }
            }
            long[] tokens;
            float[] logits = null;
            try (Tensor host = outTokens.to(Device.CPU()).copy()) {
                tokens = host.longArray();
            }
            if (outConf != null) {
                try (Tensor host = outConf.to(Device.CPU()).copy()) {
                    logits = host.floatArray();
                }
            }
            Draft[] result = new Draft[b];
            for (int j = 0; j < b; j++) {
                int[] out = new int[s];
                float[] conf = logits == null ? null : new float[s];
                for (int i = 0; i < s; i++) {
                    out[i] = (int) tokens[i * b + j];
                    if (conf != null) {
                        conf[i] = (float) (1.0 / (1.0 + Math.exp(-logits[i * b + j])));
                    }
                }
                Tensor hidden = null;
                if (wantHidden && b == 1) {
                    hidden = fin.to(ScalarType.Float).copy();
                    hidden.detachFromScopes();
                }
                result[j] = new Draft(out, conf, hidden);
            }
            return result;
        } finally {
            Tensor.pop();
            scope.close();
        }
    }

    @Override
    public void close() {
        for (Cache c : caches.values()) {
            c.close();
        }
        caches.clear();
        for (AutoCloseable c : owned) {
            try {
                c.close();
            } catch (Exception e) {
                // best effort
            }
        }
        owned.clear();
        for (Tensor t : new Tensor[]{fc, markovW1, markovW2, confHidden, confMarkov, confBias}) {
            if (t != null) {
                t.close();
            }
        }
        if (rope != null) {
            rope.close();
        }
    }
}
