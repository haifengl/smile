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
    private final List<DeepSeekBlock> blocks = new ArrayList<>();
    private final DeepSeekEngram.Hash engramHash;

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
        this.head = DeepSeekOps.f32(ck.dev("head.weight"));
        this.engramHash = args.engramLayers().length == 0 ? null
                : new DeepSeekEngram.Hash(args, DeepSeekEngramTokenMap.load(checkpoint.resolve("tokenizer.json"), args.vocabSize()));
        DeepSeekAttention.Shared shared = new DeepSeekAttention.Shared();
        for (int i = 0; i < args.numLayers(); i++) {
            blocks.add(new DeepSeekBlock(args, ck, i, shared, args.engramSlot(i) >= 0 ? engramRows.apply(i) : null));
        }
    }

    /**
     * All mutable per-sequence state of the model (each layer's caches and the engram token history). The model runs
     * one sequence at a time; {@link #use} switches which one, so a serving layer can interleave requests.
     */
    public static final class Session implements AutoCloseable {
        private final DeepSeekAttention.State[] layers;
        private final long[] history;

        private Session(DeepSeekAttention.State[] layers, long[] history) {
            this.layers = layers;
            this.history = history;
        }

        @Override
        public void close() {
            for (DeepSeekAttention.State s : layers) {
                s.close();
            }
        }
    }

    /** Allocates the state of an empty sequence (call outside any tensor scope). */
    public Session newSession() {
        DeepSeekAttention.State[] s = new DeepSeekAttention.State[blocks.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = blocks.get(i).newState();
        }
        return new Session(s, engramHash == null ? null : engramHash.newHistory());
    }

    /** Makes {@code session} the sequence the next {@link #forward} calls continue. */
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
        for (DeepSeekBlock block : blocks) {
            if (block.hasEngram()) {
                h = block.applyEngram(h, hashes);
                if (trace != null) {
                    trace.put("L" + block.layer + ".engram_out", h);
                }
            }
            Tensor[] r = block.forward(h, startPos, pre, trace);
            h = r[0];
            pre = r[1];
            if (trace != null) {
                trace.put("L" + block.layer + ".out", h);
            }
        }
        Tensor collapsed = DeepSeekBlock.hcPre(h, pre);
        Tensor normed = DeepSeekOps.rmsNorm(collapsed, normWeight, args.normEps());
        Tensor last = o("aten::select.int", normed, 1L, normed.size(1) - 1);
        return o("aten::linear", DeepSeekOps.f32(last), head);
    }

    @Override
    public void close() throws IOException {
        ck.close();
    }
}
