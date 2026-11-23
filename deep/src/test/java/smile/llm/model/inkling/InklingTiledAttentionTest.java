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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The key-blocked global attention against the one-shot score matrix: next-token distributions after a prefill that
 * crosses several blocks must agree to rounding (total variation far below the noise of a different chunking).
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_DEVICE}, {@code INKLING_PROMPT} (tokens, default 3000).
 *
 * @author Haifeng Li
 */
public class InklingTiledAttentionTest {
    private static float[] logits(InklingModel model, InklingModelArgs args, long[] ids, int tile) {
        InklingAttention.tile = tile;
        try (InklingState st = model.newState(ids.length + 8)) {
            Tensor last = null;
            for (int from = 0; from < ids.length; from += st.maxChunk(args)) {
                int to = Math.min(ids.length, from + st.maxChunk(args));
                last = model.forward(st, java.util.Arrays.copyOfRange(ids, from, to), to == ids.length ? null : new int[0], null);
            }
            return last.to(Device.CPU()).floatArray();
        }
    }

    @Test
    public void testGivenLongPromptWhenTiledThenLogitsMatchOneShot() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        Assumptions.assumeTrue(ckpt != null, "set INKLING_CKPT");
        int n = Integer.parseInt(System.getenv().getOrDefault("INKLING_PROMPT", "3000"));
        Device dev = Device.CUDA(Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 8192);
        InklingModel model = InklingModel.load(ckpt, args, dev);
        InklingTokenizer tok = InklingTokenizer.of(ckpt);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; sb.length() < n * 5; i++) {
            sb.append("Paragraph ").append(i).append(": the committee reviewed item ").append(i * 7 % 13).append(" and noted that the schedule for week ")
                    .append(i % 52).append(" was unchanged. ");
        }
        long[] ids = java.util.Arrays.stream(tok.encodeWithSpecials(sb.toString())).asLongStream().limit(n).toArray();
        float[] one = logits(model, args, ids, 1 << 30);
        float[] tiled = logits(model, args, ids, 512);
        float[] again = logits(model, args, ids, 1 << 30);
        double tvTiled = tv(one, tiled), tvBase = tv(one, again);
        System.out.printf("prompt %d tokens: total variation tiled vs one-shot %.5f, one-shot vs itself %.5f%n", ids.length, tvTiled, tvBase);
        int[] top = new int[5];
        float[] copy = one.clone();
        StringBuilder top5 = new StringBuilder("top-5 (kv fp8=" + InklingState.KV_FP8 + "):");
        for (int k = 0; k < 5; k++) {
            int bi = 0;
            for (int i = 1; i < copy.length; i++) {
                if (copy[i] > copy[bi]) {
                    bi = i;
                }
            }
            top5.append(String.format(" %d(%.2f)", bi, copy[bi]));
            copy[bi] = -1e30f;
        }
        System.out.println(top5);
        String dump = System.getenv("INKLING_DUMP");
        if (dump != null) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(one.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (float f : one) {
                bb.putFloat(f);
            }
            java.nio.file.Files.write(java.nio.file.Path.of(dump), bb.array());
        }
        assertTrue(tvTiled < 0.02, "tiled attention drifts: " + tvTiled);
    }

    private static double tv(float[] a, float[] b) {
        double ma = Double.NEGATIVE_INFINITY, mb = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            ma = Math.max(ma, a[i]);
            mb = Math.max(mb, b[i]);
        }
        double za = 0, zb = 0;
        for (int i = 0; i < a.length; i++) {
            za += Math.exp(a[i] - ma);
            zb += Math.exp(b[i] - mb);
        }
        double d = 0;
        for (int i = 0; i < a.length; i++) {
            d += Math.abs(Math.exp(a[i] - ma) / za - Math.exp(b[i] - mb) / zb);
        }
        return d / 2;
    }
}
