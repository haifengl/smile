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
package smile.llm.model.deepseek;

import java.util.ArrayList;
import java.util.List;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static smile.llm.model.deepseek.Dsv.o;

/**
 * The DSpark draft head stored under {@code mtp.*}: stages that read the main model's hidden states at the inputs of
 * its last layers and draft a block of tokens in one pass, a Markov head that conditions each drafted token on the
 * previous one, and a confidence head scoring every drafted position.
 *
 * <p>Per step the draft takes the token the main model just produced ({@code lastId}, at position
 * {@code startPos + 1}) and the main hidden state of the last processed position {@code startPos}; it returns
 * {@code block} tokens for the positions after {@code lastId}. Greedy only.
 */
final class DeepSeekDraft {
    /** Drafted tokens and their confidence scores for one sequence. */
    record Result(int[] tokens, float[] confidence) {
    }

    private final DeepSeekModelArgs args;
    private final int block;
    private final int dim;
    private final int hc;
    private final double eps;
    private final List<DeepSeekBlock> stages = new ArrayList<>();
    private final DeepSeekLinear mainProj;
    private final Tensor mainNorm;
    private final Tensor norm;
    /** Markov head embedding {@code [vocab, rank]} (bf16). */
    private final Tensor markovEmbed;
    /** The Markov head's output matrix transposed {@code [rank, vocab]} (bf16). */
    private final Tensor markovHeadT;
    /** Confidence projection {@code [dim + rank]} (float32). */
    private final Tensor confidence;

    /** Whether a checkpoint carries a DSpark head for these arguments. */
    static boolean available(DeepSeekModelArgs args, DeepSeekCheckpoint ck) {
        int last = args.numMtpLayers() - 1;
        return args.dsparkBlockSize() > 0 && args.numMtpLayers() > 0 && ck.has("mtp.0.main_proj.weight")
                && ck.has("mtp." + last + ".norm.weight") && ck.has("mtp." + last + ".markov_head.head.weight")
                && ck.has("mtp." + last + ".confidence_head.proj.weight");
    }

    DeepSeekDraft(DeepSeekModelArgs args, DeepSeekCheckpoint ck) {
        this.args = args;
        this.block = args.dsparkBlockSize();
        this.dim = args.dim();
        this.hc = args.hcMult();
        this.eps = args.normEps();
        for (int s = 0; s < args.numMtpLayers(); s++) {
            stages.add(new DeepSeekBlock(args, ck, args.numLayers() + s, null));
        }
        int last = args.numMtpLayers() - 1;
        this.mainProj = new DeepSeekLinear(ck, "mtp.0.main_proj", null);
        this.mainNorm = ck.dev("mtp.0.main_norm.weight");
        this.norm = ck.dev("mtp." + last + ".norm.weight");
        this.markovEmbed = ck.dev("mtp." + last + ".markov_head.embed.weight").to(ScalarType.BFloat16);
        Tensor head = ck.dev("mtp." + last + ".markov_head.head.weight").to(ScalarType.BFloat16);
        this.markovHeadT = o("aten::t", head).contiguous();
        this.confidence = DeepSeekOps.f32(ck.dev("mtp." + last + ".confidence_head.proj.weight")).reshape(-1);
        for (Tensor t : new Tensor[]{mainNorm, norm, markovEmbed, markovHeadT, confidence}) {
            t.detachFromScopes();
        }
    }

    int block() {
        return block;
    }

    /** The window rings of one sequence, one per stage. */
    DeepSeekAttention.State[] newState() {
        DeepSeekAttention.State[] s = new DeepSeekAttention.State[stages.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = stages.get(i).newState(new DeepSeekAttention.Shared());
        }
        return s;
    }

    /** {@code main_norm(main_proj(tap))} for taps {@code [1, rows, targets * dim]}: the keys' source in every stage. */
    Tensor mainX(Tensor tap) {
        return DeepSeekOps.rmsNorm(mainProj.forward(tap), mainNorm, eps);
    }

    /** Seeds every stage's window ring from the main hidden states of a whole prompt ({@code tap [1, len, targets*dim]}). */
    void seed(DeepSeekAttention.State[] states, Tensor tap) {
        Tensor mx = mainX(tap);
        for (int s = 0; s < stages.size(); s++) {
            stages.get(s).seedDraftWindow(states[s], mx);
        }
    }

    /** Writes the ring keys of the main hidden state {@code tap [1, 1, targets * dim]} of position {@code pos} in every stage. */
    void absorb(DeepSeekAttention.State[] states, Tensor tap, int pos) {
        Tensor mx = mainX(tap);
        for (int s = 0; s < stages.size(); s++) {
            stages.get(s).absorbDraftWindow(states[s], mx, pos);
        }
    }

    /**
     * Writes the ring keys of many main hidden states at once: row {@code r} of {@code tap [1, rows, targets * dim]} is
     * the state at position {@code pos[r]} of the sequence whose draft rings are {@code rings[r]}.
     */
    void absorbRows(DeepSeekAttention.State[][] rings, Tensor tap, long[] pos) {
        Tensor mx = mainX(tap);
        Tensor posT = Dsv.dev(Tensor.of(pos, pos.length));
        for (int s = 0; s < stages.size(); s++) {
            Tensor[] windows = new Tensor[pos.length];
            for (int r = 0; r < windows.length; r++) {
                windows[r] = rings[r][s].window();
            }
            Tensor kv = stages.get(s).draftMainKv(mx, pos).contiguous();
            smile.torch.Native.dsWindowWrite(kv, smile.torch.Native.dsPtrTable(windows), posT, args.windowSize());
        }
    }

    /**
     * The device-side inputs of one draft pass over {@code n} sequence slots. Everything the pass needs is a tensor of
     * fixed shape, so the same pass runs eagerly or inside a captured CUDA graph (which refills these before each replay).
     */
    static final class Inputs {
        final int n;
        /** The token each sequence just produced, int64 {@code [n]}. */
        final Tensor lastIds;
        /** The last processed position of each sequence, int64 {@code [n]}. */
        final Tensor startPos;
        /** Their main hidden states {@code [1, n, targets * dim]}. */
        final Tensor taps;
        /** Per stage: each sequence's ring pointer {@code [n]} and each row's {@code [n * block]}. */
        final Tensor[] perSeq;
        final Tensor[] rowPtrs;
        // constants of the shape
        final Tensor noise;
        final Tensor steps;
        final Tensor ring;
        final Tensor own;
        final Tensor slots;
        final Tensor pre0;

        Inputs(DeepSeekDraft draft, int n, int tapWidth, boolean persistent) {
            this.n = n;
            int b = draft.block;
            int w = draft.args.windowSize();
            this.lastIds = Dsv.dev(Tensor.of(new long[n], n));
            this.startPos = Dsv.dev(Tensor.of(new long[n], n));
            this.taps = Dsv.zeros(ScalarType.BFloat16, 1, n, tapWidth);
            this.perSeq = new Tensor[draft.stages.size()];
            this.rowPtrs = new Tensor[draft.stages.size()];
            for (int s = 0; s < perSeq.length; s++) {
                perSeq[s] = Dsv.dev(Tensor.of(new long[n], n));
                rowPtrs[s] = Dsv.dev(Tensor.of(new long[n * b], (long) n * b));
            }
            long[] nz = new long[n * (b - 1)];
            java.util.Arrays.fill(nz, draft.args.dsparkNoiseToken());
            this.noise = Dsv.dev(Tensor.of(nz, n, b - 1));
            long[] st = new long[b];
            for (int j = 0; j < b; j++) {
                st[j] = j + 1;
            }
            this.steps = Dsv.dev(Tensor.of(st, 1, b));
            int[] r = new int[w];
            for (int c = 0; c < w; c++) {
                r[c] = c;
            }
            this.ring = Dsv.dev(Tensor.of(r, 1, w));
            int[] ow = new int[n * b * b];
            long[] sl = new long[n * b];
            for (int row = 0; row < n * b; row++) {
                sl[row] = w + row % b;
                for (int c = 0; c < b; c++) {
                    ow[row * b + c] = w + c;
                }
            }
            this.own = Dsv.dev(Tensor.of(ow, (long) n * b, b));
            this.slots = Dsv.dev(Tensor.of(sl, (long) n * b));
            float[] one = new float[n * b * draft.hc];
            for (int i = 0; i < n * b; i++) {
                one[i * draft.hc] = 1f;
            }
            this.pre0 = Dsv.dev(Tensor.of(one, 1, (long) n * b, draft.hc));
            if (persistent) {
                for (Tensor t : all()) {
                    t.detachFromScopes();
                }
            }
        }

        private java.util.List<Tensor> all() {
            java.util.List<Tensor> l = new ArrayList<>(java.util.List.of(lastIds, startPos, taps, noise, steps, ring, own, slots, pre0));
            l.addAll(java.util.List.of(perSeq));
            l.addAll(java.util.List.of(rowPtrs));
            return l;
        }
    }

    /**
     * One draft pass, all on the device (capturable): the drafted tokens {@code [n, block]} (int64) and their confidences
     * {@code [n, block]} (float32).
     */
    Tensor[] core(DeepSeekModel model, Inputs in) {
        int n = in.n;
        long total = (long) n * block;
        Tensor mainX = mainX(in.taps);                                                   // [1, n, dim]
        Tensor ids = Dsv.cat(1, in.lastIds.unsqueeze(1), in.noise).reshape(1, total);
        Tensor h = model.embedTensor(ids);                                               // [1, total, dim]
        h = o("aten::repeat", h.unsqueeze(2), new long[]{1, 1, hc, 1});
        Tensor pre = in.pre0;
        // positions of the block rows and the ring slots each may read (the last `window` positions, -1 before the start)
        Tensor qpos = o("aten::add.Tensor", in.startPos.unsqueeze(1), in.steps).reshape(total);
        int w = args.windowSize();
        Tensor valid = o("aten::lt.Tensor", in.ring, o("aten::clamp_max", o("aten::add.Scalar", in.startPos, 1L), (long) w).unsqueeze(1));
        Tensor base = o("aten::where.ScalarOther", valid, in.ring, -1.0).to(ScalarType.Int32);   // [n, window]
        Tensor idx = Dsv.cat(1, o("aten::repeat_interleave.self_int", base, (long) block, 0L), in.own).reshape(1, total, w + block);
        for (int s = 0; s < stages.size(); s++) {
            final int stage = s;
            final Tensor hin = h, pin = pre;
            Tensor[] r = Dsv.layerScoped(() -> stages.get(stage).forwardDraft(hin, pin, mainX, in.startPos, qpos, idx,
                    in.slots, in.perSeq[stage], in.rowPtrs[stage], n, block));
            h = r[0];
            pre = r[1];
        }
        Tensor x = DeepSeekBlock.hcPre(h, pre);                                          // [1, total, dim]
        Tensor logits = model.logits(DeepSeekOps.rmsNorm(x, norm, eps).reshape(total, dim)).reshape(n, block, -1);
        Tensor cur = in.lastIds;
        Tensor[] picked = new Tensor[block];
        Tensor[] embeds = new Tensor[block];
        for (int i = 0; i < block; i++) {
            Tensor me = o("aten::embedding", markovEmbed, cur);                          // [n, rank]
            Tensor bias = o("aten::mm.dtype", me, markovHeadT, ScalarType.Float);        // [n, vocab]
            Tensor li = o("aten::add.Tensor", o("aten::select.int", logits, 1L, (long) i), bias);
            cur = o("aten::argmax", li, -1L, false);                                     // [n]
            picked[i] = cur;
            embeds[i] = me;
        }
        Tensor tokens = o("aten::stack", picked, 1L);                                    // [n, block]
        Tensor feats = Dsv.cat(-1, DeepSeekOps.f32(x).reshape(n, block, dim), DeepSeekOps.f32(o("aten::stack", embeds, 1L)));
        Tensor conf = o("aten::mv", feats.reshape(total, -1), confidence).reshape(n, block);
        return new Tensor[]{tokens, conf};
    }

    /** Fills the host-side parts of {@code in} for the first {@code lastIds.length} slots; the rest stay padding. */
    void fill(Inputs in, int[] lastIds, int[] startPos, Tensor[] taps, DeepSeekAttention.State[][] states,
              DeepSeekAttention.State[] pad) {
        int n = lastIds.length;
        long[] ids = new long[in.n];
        long[] pos = new long[in.n];
        java.util.Arrays.fill(pos, 1L);
        for (int i = 0; i < n; i++) {
            ids[i] = lastIds[i];
            pos[i] = startPos[i];
        }
        Aten.call("aten::copy_", in.lastIds, Dsv.dev(Tensor.of(ids, in.n)), false);
        Aten.call("aten::copy_", in.startPos, Dsv.dev(Tensor.of(pos, in.n)), false);
        for (int i = 0; i < n; i++) {
            Aten.call("aten::copy_", Dsv.narrow(in.taps, 1, i, 1), taps[i], false);
        }
        for (int s = 0; s < stages.size(); s++) {
            Tensor[] windows = new Tensor[in.n];
            Tensor[] perRow = new Tensor[in.n * block];
            for (int i = 0; i < in.n; i++) {
                windows[i] = (i < n ? states[i] : pad)[s].window();
                for (int j = 0; j < block; j++) {
                    perRow[i * block + j] = windows[i];
                }
            }
            Aten.call("aten::copy_", in.perSeq[s], Native.dsPtrTable(windows), false);
            Aten.call("aten::copy_", in.rowPtrs[s], Native.dsPtrTable(perRow), false);
        }
    }

    /**
     * Drafts a block for several sequences, eagerly.
     *
     * @param model    the owner (embedding, head).
     * @param lastIds  the token each sequence just produced.
     * @param startPos each sequence's last processed position (its main hidden state is {@code taps[i]}).
     * @param taps     {@code [1, 1, targets * dim]} per sequence.
     * @param states   each sequence's draft rings.
     * @return the drafted tokens ({@code block} per sequence, for the positions after {@code lastIds}) and confidences.
     */
    Result[] draft(DeepSeekModel model, int[] lastIds, int[] startPos, Tensor[] taps, DeepSeekAttention.State[][] states) {
        int n = lastIds.length;
        Inputs in = new Inputs(this, n, (int) taps[0].size(-1), false);
        fill(in, lastIds, startPos, taps, states, null);
        Tensor[] out = core(model, in);
        return read(out[0], out[1], n);
    }

    /** Reads the first {@code n} sequences of the pass's outputs. */
    Result[] read(Tensor tokens, Tensor conf, int n) {
        long[] tk = Dsv.host(tokens).longArray();
        float[] cf = Dsv.host(conf).floatArray();
        Result[] out = new Result[n];
        for (int i = 0; i < n; i++) {
            int[] t = new int[block];
            float[] c = new float[block];
            for (int j = 0; j < block; j++) {
                t[j] = (int) tk[i * block + j];
                c[j] = cf[i * block + j];
            }
            out[i] = new Result(t, c);
        }
        return out;
    }
}
