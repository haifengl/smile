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

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static smile.llm.model.deepseek.Dsv.o;

/**
 * Sampling for speculative decoding with a deterministic draft. For a drafted token {@code d} at a row whose target
 * distribution is {@code p} (temperature and nucleus applied), {@code d} is accepted with probability {@code p(d)};
 * if it is rejected the replacement is drawn from {@code p} without {@code d}, renormalized. That reproduces
 * sampling from {@code p}. Rows without a drafted token (the bonus row after the last draft, or an ordinary decode
 * step) are drawn from {@code p} directly. The nucleus is taken from the {@value #CANDIDATES} most probable tokens,
 * which holds all the mass that matters at ordinary temperatures.
 */
final class DeepSeekSampler {
    static final int CANDIDATES = 2048;

    private DeepSeekSampler() {
    }

    /**
     * @param logits {@code [m, vocab]} float32.
     * @param draft  the drafted token of each row, or {@code -1} for rows without one.
     * @param temp   temperature per row ({@code 0}: greedy).
     * @param topp   nucleus mass per row.
     * @return {@code {accepted, token}} per row: whether the drafted token is accepted (false without a draft) and the
     *         token to emit if it is not (for a row without a draft, the sampled token).
     */
    static long[][] verify(Tensor logits, int[] draft, double[] temp, double[] topp) {
        int m = draft.length;
        float[] t = new float[m];
        float[] p = new float[m];
        long[] d = new long[m];
        for (int i = 0; i < m; i++) {
            t[i] = (float) Math.max(temp[i], 1e-5);
            p[i] = (float) topp[i];
            d[i] = draft[i];
        }
        Tensor probs = o("aten::softmax.int", o("aten::div.Tensor", logits, Dsv.dev(Tensor.of(t, m, 1))), -1L,
                ScalarType.Float);
        Tensor[] top = smile.torch.Aten.call("aten::topk", probs, (long) Math.min(CANDIDATES, logits.size(1)), -1L, true, true);
        Tensor vals = top[0];
        Tensor idx = top[1];
        Tensor cum = o("aten::cumsum", vals, -1L);
        Tensor keep = o("aten::lt.Tensor", o("aten::sub.Tensor", cum, vals), Dsv.dev(Tensor.of(p, m, 1)));
        Tensor vf = o("aten::mul.Tensor", vals, keep.to(ScalarType.Float));
        vf = o("aten::div.Tensor", vf, o("aten::clamp_min", o("aten::sum.dim_IntList", vf, new long[]{-1}, true), 1e-20));
        Tensor dt = Dsv.dev(Tensor.of(d, m, 1));
        Tensor match = o("aten::eq.Tensor", idx, dt);                                      // [m, c]
        Tensor pd = o("aten::sum.dim_IntList", o("aten::mul.Tensor", vf, match.to(ScalarType.Float)), new long[]{-1});
        Tensor u = Dsv.dev(o("aten::rand", new long[]{m}));
        Tensor accepted = o("aten::lt.Tensor", u, pd);
        // the replacement: p without the drafted token (a row with no draft removes nothing)
        Tensor residual = o("aten::mul.Tensor", vf, o("aten::logical_not", match).to(ScalarType.Float));
        residual = o("aten::add.Scalar", residual, 1e-30);
        Tensor pick = o("aten::multinomial", residual, 1L, false);                        // [m, 1]
        Tensor token = o("aten::gather", idx, 1L, pick).reshape(m);
        long[] acc = Dsv.host(accepted.to(ScalarType.Int64)).longArray();
        long[] tok = Dsv.host(token).longArray();
        return new long[][]{acc, tok};
    }
}
