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
package smile.llm.model.qwen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCachePool;
import smile.util.Bytes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * History-aware MTP draft head: persistent KV is chunking-invariant, and
 * end-to-end speculation (production calling convention, first sampled token
 * at position {@code P}) stays lossless while actually using the history path.
 *
 * @author Haifeng Li
 */
public class QwenMtpHistoryTest {

    private static Tokenizer tinyTokenizer() {
        Map<Bytes, Integer> ranks = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            ranks.put(new Bytes(new byte[]{(byte) i}), i);
        }
        return new Tokenizer(ranks);
    }

    private static QwenModel tinyModel(QwenModelArgs args) {
        DeltaNetStatePool statePool = new DeltaNetStatePool(
                args.numLinearAttentionLayers(),
                args.linearNumValueHeads(),
                args.linearKeyHeadDim(),
                args.linearValueHeadDim(),
                args.linearConvDim(),
                args.linearConvKernelDim(),
                Math.max(2, args.maxBatchSize()),
                Device.CPU(),
                ScalarType.Float);
        QwenModel model = new QwenModel(args, statePool);
        model.to(Device.CPU());
        model.eval();
        model.setKvCachePool(KvCachePool.forTesting(args.kvCacheLayout(), Device.CPU()), false);
        if (model.mtp() != null) {
            model.mtp().setKvCachePool(
                    KvCachePool.forTesting(model.mtp().kvCacheLayout(), Device.CPU()), false);
        }
        return model;
    }

    private static QwenModelArgs args() {
        return new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 2, 700,
                1, 2);
    }

    private static float[] lastLogits(QwenMtp mtp, KvCachePool pool, int req, int[] tokens,
                                      Tensor hidden, int[][] chunks) {
        pool.activateStep(req);
        Tensor logits = null;
        int start = 0;
        for (int[] c : chunks) {
            int m = c[1] - c[0];
            long[] tl = new long[m];
            for (int i = 0; i < m; i++) {
                tl[i] = tokens[c[0] + i];
            }
            try (Tensor tokT = Tensor.of(tl).reshape(1, m);
                 var sl = smile.deep.tensor.Index.slice(c[0], c[1]);
                 Tensor h = hidden.get(smile.deep.tensor.Index.Colon, sl)) {
                if (logits != null) {
                    logits.close();
                }
                logits = mtp.absorb(tokT, h, c[0], true);
            }
            start = c[1];
        }
        assertEquals(tokens.length, start);
        try (Tensor flat = logits.reshape(-1); Tensor cpu = flat.to(Device.CPU())) {
            float[] out = cpu.floatArray();
            logits.close();
            return out;
        }
    }

    @Test
    public void testGivenPersistentKvWhenChunkedThenMatchesOneShot() {
        QwenModelArgs args = args();
        smile.torch.smile_torch_h.smile_manual_seed(11L);
        QwenModel model = tinyModel(args);
        QwenMtp mtp = model.mtp();
        assertNotNull(mtp);
        int len = 12;
        int[] tokens = new int[len];
        for (int i = 0; i < len; i++) {
            tokens[i] = 1 + (i * 7) % 90;
        }
        smile.torch.smile_torch_h.smile_manual_seed(12L);
        Tensor hidden = Tensor.randn(1, len, args.dim());

        KvCachePool poolA = KvCachePool.forTesting(
                new smile.llm.cache.KvCacheLayout(1, args.numKvHeads(), args.headDim(), 2, 64),
                Device.CPU());
        KvCachePool poolB = KvCachePool.forTesting(
                new smile.llm.cache.KvCacheLayout(1, args.numKvHeads(), args.headDim(), 2, 64),
                Device.CPU());
        int a = poolA.bindRequest(tokens, 64);
        int b = poolB.bindRequest(tokens, 64);

        mtp.setKvCachePool(poolA, false);
        float[] oneShot = lastLogits(mtp, poolA, a, tokens, hidden, new int[][]{{0, len}});
        mtp.setKvCachePool(poolB, false);
        float[] chunked = lastLogits(mtp, poolB, b, tokens, hidden,
                new int[][]{{0, 5}, {5, 6}, {6, 7}, {7, 12}});

        assertEquals(oneShot.length, chunked.length);
        for (int i = 0; i < oneShot.length; i++) {
            assertEquals(oneShot[i], chunked[i], 1e-3f, "logit " + i);
        }
        hidden.close();
    }

    @Test
    public void testGivenProductionConventionWhenSpeculateThenLosslessAndHistoryUsed() {
        QwenModelArgs args = args();
        long seed = 4242L;
        smile.torch.smile_torch_h.smile_manual_seed(seed);
        Qwen spec = new Qwen("tiny-hist-spec", tinyModel(args), tinyTokenizer(), args);
        smile.torch.smile_torch_h.smile_manual_seed(seed);
        Qwen plain = new Qwen("tiny-hist-plain", tinyModel(args), tinyTokenizer(), args);

        int[] prompt = new int[20];
        for (int i = 0; i < prompt.length; i++) {
            prompt[i] = 1 + (i * 3) % 60;
        }
        int rs = spec.bind(prompt, 400);
        int rp = plain.bind(prompt, 400);
        int firstS;
        int firstP;
        // Chunked prefill on the speculative side exercises the cross-chunk carry.
        try (Tensor l = spec.prefillChunk(rs, prompt, 0, 7)) {
            assertNull(l);
        }
        try (Tensor l = spec.prefillChunk(rs, prompt, 7, 8)) {
            assertNull(l);
        }
        try (Tensor l = spec.prefillChunk(rs, prompt, 8, prompt.length)) {
            firstS = smile.llm.engine.Sampling.sampleGreedyTokenId(l);
        }
        try (Tensor l = plain.prefillChunk(rp, prompt, 0, prompt.length)) {
            firstP = smile.llm.engine.Sampling.sampleGreedyTokenId(l);
        }
        assertEquals(firstP, firstS);
        assertTrue(spec.mtpHistoryValid(rs), "prefill must leave a valid MTP history");

        int posS = prompt.length;
        int tokS = firstS;
        int posP = prompt.length;
        int tokP = firstP;
        List<Integer> specTokens = new ArrayList<>();
        List<Integer> plainTokens = new ArrayList<>();
        specTokens.add(firstS);
        plainTokens.add(firstP);
        for (int round = 0; round < 40; round++) {
            int[][] out = spec.speculateStep(
                    new int[]{rs}, new int[]{tokS}, new int[]{posS}, 2, 0.0, 1.0);
            assertTrue(spec.mtpHistoryValid(rs), "history must stay valid at round " + round);
            for (int t : out[0]) {
                specTokens.add(t);
            }
            posS += out[0].length;
            tokS = out[0][out[0].length - 1];
            for (int i = 0; i < out[0].length; i++) {
                try (Tensor logits = plain.decodeStep(new int[]{rp}, new int[]{tokP}, new int[]{posP})) {
                    int t = smile.llm.engine.Sampling.sampleGreedyTokenId(logits);
                    plainTokens.add(t);
                    posP++;
                    tokP = t;
                }
            }
        }
        // specTokens[0] is the prefill token; each round's tokens follow in order.
        assertEquals(plainTokens, specTokens);
        spec.evict(rs);
        plain.evict(rp);
    }
}
