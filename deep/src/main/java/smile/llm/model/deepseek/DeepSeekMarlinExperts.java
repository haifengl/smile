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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;
import smile.util.AutoScope;

/**
 * The routed experts of one layer on a CUDA device, run by the Marlin MoE kernel on the checkpoint's MXFP4 weights
 * converted to the NVFP4 layout ({@link DeepSeekMxfp4}). Activations are fake-quantized to fp8 before both GEMMs as
 * in the reference; the routing weight is applied to the hidden state before the down GEMM, as in the reference.
 *
 * @author Haifeng Li
 */
final class DeepSeekMarlinExperts implements AutoCloseable {
    private int dim;
    private int inter;
    private final int experts;
    private final int first;
    private final int local;
    private final Dsv.Ep ep;
    private final int topk;
    private final double limit;
    private Tensor w13q, w13s, w13g, w2q, w2s, w2g;

    /**
     * Reads, converts and repacks all experts of a layer (about 5 GB of host staging at the released sizes).
     *
     * @param ck      the checkpoint.
     * @param prefix  tensor name prefix of the layer's FFN, {@code layers.N.ffn}.
     * @param experts number of routed experts.
     * @param topk    experts per token.
     * @param limit   swiglu clamp.
     * @param device  the CUDA device.
     */
    DeepSeekMarlinExperts(DeepSeekCheckpoint ck, String prefix, int experts, int topk, double limit, Device device) {
        this(ck, prefix, experts, topk, limit, device, null);
    }

    /**
     * As above, for the calling rank's share of the experts when {@code ep} is not null.
     *
     * @param ep expert parallelism of this thread, or {@code null} to hold every expert.
     */
    DeepSeekMarlinExperts(DeepSeekCheckpoint ck, String prefix, int experts, int topk, double limit, Device device,
                          Dsv.Ep ep) {
        this.ep = ep != null && ep.world() > 1 ? ep : null;
        this.first = this.ep == null ? 0 : this.ep.rank() * experts / this.ep.world();
        this.local = this.ep == null ? experts : (this.ep.rank() + 1) * experts / this.ep.world() - first;
        this.experts = experts;
        this.topk = topk;
        this.limit = limit;
        final int chunk = 32;   // bounds the repack scratch space to a few GB
        Tensor[] out = new Tensor[6];
        for (int from = 0; from < local; from += chunk) {
            int n = Math.min(chunk, local - from);
            List<Tensor> gu = new ArrayList<>(), gus = new ArrayList<>(), dn = new ArrayList<>(), dns = new ArrayList<>();
            for (int e = first + from; e < first + from + n; e++) {
                String p = prefix + ".experts." + e;
                gu.add(Aten.one("aten::cat", new Tensor[]{u8(ck.get(p + ".w1.weight")), u8(ck.get(p + ".w3.weight"))}, 0L));
                gus.add(Aten.one("aten::cat", new Tensor[]{DeepSeekMxfp4.toNvfp4(ck.get(p + ".w1.scale")),
                        DeepSeekMxfp4.toNvfp4(ck.get(p + ".w3.scale"))}, 0L));
                dn.add(u8(ck.get(p + ".w2.weight")));
                dns.add(DeepSeekMxfp4.toNvfp4(ck.get(p + ".w2.scale")));
            }
            Tensor g13w = Aten.one("aten::stack", gu.toArray(Tensor[]::new), 0L).to(device);
            Tensor g13s = Aten.one("aten::stack", gus.toArray(Tensor[]::new), 0L).to(device);
            Tensor g2w = Aten.one("aten::stack", dn.toArray(Tensor[]::new), 0L).to(device);
            Tensor g2s = Aten.one("aten::stack", dns.toArray(Tensor[]::new), 0L).to(device);
            float[] global = new float[n];
            Arrays.fill(global, DeepSeekMxfp4.GLOBAL);
            Tensor g = Tensor.of(global, n).to(device);
            Tensor[] a13 = Native.marlinNvfp4RepackScalesStack(g13s, g);
            Tensor[] a2 = Native.marlinNvfp4RepackScalesStack(g2s, g);
            Tensor[] piece = {Native.marlinNvfp4RepackWeightStack(g13w), a13[0], a13[1],
                    Native.marlinNvfp4RepackWeightStack(g2w), a2[0], a2[1]};
            if (from == 0) {
                this.inter = (int) g2w.shape()[2] * 2;
                this.dim = (int) g2w.shape()[1];
                for (int i = 0; i < 6; i++) {
                    long[] shape = piece[i].shape().clone();
                    shape[0] = local;
                    out[i] = Aten.one("aten::new_empty", piece[i], shape);
                    out[i].detachFromScopes();
                }
            }
            for (int i = 0; i < 6; i++) {
                Dsv.assign(Aten.one("aten::slice.Tensor", out[i], 0L, (long) from, (long) from + n), piece[i]);
                piece[i].close();
            }
            for (Tensor t : new Tensor[]{g13w, g13s, g2w, g2s, g}) {
                t.close();
            }
            for (var l : List.of(gu, gus, dn, dns)) {
                l.forEach(Tensor::close);
            }
        }
        w13q = out[0];
        w13s = out[1];
        w13g = out[2];
        w2q = out[3];
        w2s = out[4];
        w2g = out[5];
        for (Tensor t : new Tensor[]{w13q, w13s, w13g, w2q, w2s, w2g}) {
            t.detachFromScopes();
        }
    }

    private static Tensor u8(Tensor w) {
        return w.dtype() == ScalarType.UInt8 ? w : Aten.one("aten::view.dtype", w, ScalarType.UInt8);
    }

    private int blockSize(int tokens) {
        int block = 8;
        for (int candidate : new int[]{8, 16, 32, 48, 64}) {
            block = candidate;
            if ((double) tokens * topk / experts / candidate < 0.9) {
                break;
            }
        }
        return block;
    }

    /**
     * Routed-expert output.
     *
     * @param flat    bfloat16 tokens {@code [T, dim]} on the device.
     * @param indices chosen experts {@code [T, topk]} int64.
     * @param weights combine weights {@code [T, topk]} float32.
     * @return float32 {@code [T, dim]}.
     */
    Tensor forward(Tensor flat, Tensor indices, Tensor weights) {
        int tokens = (int) flat.size(0);
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor x = DeepSeekOps.fakeQuantFp8(flat, 32).contiguous();
            Tensor idx = indices.contiguous();
            Tensor w = weights.to(ScalarType.Float).contiguous();
            int block = blockSize(tokens);
            Tensor align = Native.moeAlignBlockSize(idx, first, local, block);
            Tensor gateUp = Native.marlinMoeNvfp4Gemm(x, w13q, w13s, w13g, align, w, block, topk, false);
            Tensor gate = DeepSeekOps.f32(Aten.one("aten::slice.Tensor", gateUp, 1L, 0L, (long) inter));
            Tensor up = DeepSeekOps.f32(Aten.one("aten::slice.Tensor", gateUp, 1L, (long) inter, (long) 2 * inter));
            if (limit > 0) {
                up = Aten.one("aten::clamp", up, -limit, limit);
                gate = Aten.one("aten::clamp", gate, null, limit);
            }
            // as in the reference, the routing weight scales the hidden state before it is rounded to bf16 and to fp8
            Tensor h = Aten.one("aten::mul.Tensor", Aten.one("aten::mul.Tensor", Aten.one("aten::silu", gate), up),
                    w.reshape(-1, 1)).to(ScalarType.BFloat16);
            h = DeepSeekOps.fakeQuantFp8(h, 32).contiguous();
            Tensor down = Native.marlinMoeNvfp4Gemm(h, w2q, w2s, w2g, align, w, block, 1, false);
            Tensor out = Aten.one("aten::sum.dim_IntList", DeepSeekOps.f32(down.reshape(tokens, topk, dim)), new long[]{1}, false);
            if (ep != null) {
                out = out.contiguous();
                ep.group().allReduceSumInPlace(ep.rank(), out);
            }
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }

    @Override
    public void close() {
        for (Tensor t : new Tensor[]{w13q, w13s, w13g, w2q, w2s, w2g}) {
            if (t != null) {
                t.close();
            }
        }
        w13q = null;
    }
}
