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
package smile.llm.model.inkling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.util.AutoScope;

import static smile.llm.model.inkling.Ix.*;

/**
 * Inkling-2-Mini text model, reference implementation: one sequence at a time, torch ops only, routed experts
 * dequantized per use from the checkpoint's NVFP4/MXFP8 tensors. It follows the Hugging Face model's dtype flow so that
 * it can be compared with it layer by layer.
 *
 * <p>The network has no residual stream in the usual sense. Each sublayer reads its input from the AttnRes states
 * (completed block states plus the running partial state); the layers {@code [coreStart, coreEnd)} run
 * {@code numPasses} times, every pass after the first restarting from the state before the loop and receiving a
 * "message" read from the previous pass.
 *
 * @author Haifeng Li
 */
public final class InklingModel implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(InklingModel.class);

    private final InklingModelArgs args;
    private final Device device;
    private final Tensor embed;
    private final Tensor embedNorm;
    private final Tensor unembed;
    private final Tensor finalNorm;
    private final Tensor outputQuery;
    private final Tensor messageQueries;    // [loop layers, 4, D]
    private final InklingLayer[] layers;

    private InklingModel(InklingModelArgs args, Device device, Tensor embed, Tensor embedNorm, Tensor unembed,
                         Tensor finalNorm, Tensor outputQuery, Tensor messageQueries, InklingLayer[] layers) {
        this.args = args;
        this.device = device;
        this.embed = embed;
        this.embedNorm = embedNorm;
        this.unembed = unembed;
        this.finalNorm = finalNorm;
        this.outputQuery = outputQuery;
        this.messageQueries = messageQueries;
        this.layers = layers;
    }

    /**
     * Loads the model.
     *
     * @param dir    checkpoint directory.
     * @param args   hyperparameters (possibly a slice, see {@link InklingModelArgs#withLayers}).
     * @param device the device.
     * @return the model.
     * @throws IOException if the checkpoint cannot be read.
     */
    public static InklingModel load(String dir, InklingModelArgs args, Device device) throws IOException {
        InklingCheckpoint ck = new InklingCheckpoint(dir);
        Set<String> top = new HashSet<>(Set.of("embed.weight", "embed_norm.weight", "norm.weight", "unembed.weight",
                "attn_res.output_query"));
        boolean passes = args.numPasses() > 1 && args.coreEnd() > args.coreStart();
        if (passes) {
            top.add("middle_cycle_cross_pass_queries");
            top.add("middle_cycle_cross_pass_kv_queries");
        }
        var t = ck.read(device, top);
        Tensor embed = keep(t.get("embed.weight"));
        Tensor embedNorm = keep(f32(t.get("embed_norm.weight")));
        Tensor unembed = keep(contiguous(narrow(t.get("unembed.weight"), 0, 0, args.unpaddedVocabSize())));
        Tensor finalNorm = keep(f32(t.get("norm.weight")));
        Tensor outputQuery = keep(f32(t.get("attn_res.output_query")));
        Tensor messages = null;
        if (passes) {
            // [layer, (query, mlp)] and [layer, (key, value)] -> [layer, (query, key, value, mlp)]
            Tensor q = reshape(f32(t.get("middle_cycle_cross_pass_queries")), -1, 2, args.dim());
            Tensor kv = reshape(f32(t.get("middle_cycle_cross_pass_kv_queries")), -1, 2, args.dim());
            messages = keep(cat(1, narrow(q, 1, 0, 1), kv, narrow(q, 1, 1, 1)));
        }
        InklingLayer[] layers = new InklingLayer[args.numLayers()];
        for (int i = 0; i < layers.length; i++) {
            var lt = ck.read(device, InklingLayer.names(args, i));
            layers[i] = new InklingLayer(args, i, lt, device);
            for (Tensor x : lt.values()) {
                if (!isKept(x)) {
                    x.close();
                }
            }
            if (i % 6 == 5) {
                logger.info("loaded {} of {} layers", i + 1, layers.length);
            }
        }
        return new InklingModel(args, device, embed, embedNorm, unembed, finalNorm, outputQuery, messages, layers);
    }

    private static boolean isKept(Tensor t) {
        return true;       // the layer keeps what it needs; the rest is released with the thread's scope
    }

    /** Hyperparameters. */
    public InklingModelArgs args() {
        return args;
    }

    /**
     * Creates the state of a new sequence.
     *
     * @param capacity maximum tokens.
     * @return the state.
     */
    InklingState newState(int capacity) {
        return new InklingState(args, capacity, device);
    }

    /**
     * Processes tokens and returns the logits of selected rows.
     *
     * @param st     the sequence state, extended by the tokens.
     * @param tokens the tokens at positions {@code st.length ..}.
     * @param rows   the rows (indices into {@code tokens}) whose logits are wanted; {@code null} for the last one only,
     *               empty for none (the result is then {@code null}).
     * @param trace  receives the output of every layer application ({@code [T, D]} bf16 on the host), or {@code null}.
     * @return float32 {@code [rows, unpadded vocab]} (soft-capped).
     */
    Tensor forward(InklingState st, long[] tokens, int[] rows, Consumer<Tensor> trace) {
        int start = st.length;
        if (start + tokens.length > st.capacity) {
            throw new IllegalArgumentException("sequence exceeds the state capacity " + st.capacity);
        }
        if (tokens.length > st.maxChunk(args)) {
            throw new IllegalArgumentException("a chunk of " + tokens.length + " tokens exceeds the limit " + st.maxChunk(args));
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor ids = Tensor.of(tokens, tokens.length).to(device);
            Tensor h = bf16(rms(o("aten::index_select", embed, 0L, ids), embedNorm, args.normEps()));
            List<Tensor> blocks = new ArrayList<>();
            Tensor preH = null;
            List<Tensor> preBlocks = null;
            int pre = 0;
            int core = args.coreEnd() - args.coreStart();
            List<Tensor> prevBlocks = null;
            Tensor prevH = null;
            for (int app = 0; app < args.numApplications(); app++) {
                int layerIndex = args.layerOfApplication(app);
                int pass = args.passOfApplication(app);
                boolean inLoop = args.isLooped(layerIndex);
                boolean first = inLoop && layerIndex == args.coreStart();
                if (first && pass == 0) {
                    preH = h;
                    preBlocks = new ArrayList<>(blocks);
                    pre = blocks.size();
                } else if (first) {
                    // a later pass restarts from the state before the loop and reads the previous pass's states
                    prevBlocks = new ArrayList<>(blocks.subList(pre + 1, blocks.size()));
                    prevH = h;
                    h = preH;
                    blocks = new ArrayList<>(preBlocks);
                }
                InklingLayer layer = layers[layerIndex];
                AutoScope step = new AutoScope();
                Tensor.push(step);
                Tensor out;
                try {
                    Tensor messages = null;
                    if (inLoop && pass > 0) {
                        messages = InklingLayer.attnRes(prevBlocks, prevH,
                                o("aten::select.int", messageQueries, 0L, (long) (layerIndex - args.coreStart())));
                    }
                    out = layer.forward(h, blocks, messages, st, app, start);
                    out.promoteToParent();
                } finally {
                    Tensor.pop();
                }
                if (trace != null) {
                    Tensor host = out.to(Device.CPU());
                    host.promoteToParent();         // survives the forward's scope
                    trace.accept(host);
                }
                if (layer.blockBoundary) {
                    blocks.add(h);
                }
                h = out;
            }
            if (rows != null && rows.length == 0) {
                st.length = start + tokens.length;          // a prefill chunk: no logits wanted
                return null;
            }
            Tensor last = InklingLayer.attnRes(blocks, h, outputQuery);
            Tensor x = bf16(rms(o("aten::select.int", last, 1L, 0L), finalNorm, args.normEps()));
            if (rows != null) {
                long[] idx = new long[rows.length];
                for (int i = 0; i < idx.length; i++) {
                    idx[i] = rows[i];
                }
                x = o("aten::index_select", x, 0L, Tensor.of(idx, idx.length).to(device));
            } else {
                x = narrow(x, 0, tokens.length - 1, 1);
            }
            double cap = args.finalLogitSoftcap();
            Tensor logits = mul(tanh(mul(f32(linear(x, unembed)), 1.0 / cap)), cap);
            logits.promoteToParent();
            st.length = start + tokens.length;
            return logits;
        } finally {
            Tensor.pop();
        }
    }

    @Override
    public void close() {
        // weights are kept out of scopes; the process owns them
    }
}
