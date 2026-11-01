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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.engine.GenerationRequest;
import smile.llm.engine.InferenceEngine;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in end-to-end test of DSpark speculative decoding through {@link InferenceEngine} (no HTTP): output validity and
 * agreement with plain decoding, {@code max_tokens} inside a block, stop tokens inside accepted drafts, prefix reuse,
 * concurrent and sampled requests, tiny and long (chunk-prefilled) prompts.
 *
 * <p>Environment: {@code MINIMAX_MODEL_DIR} (Flash checkpoint with {@code dspark/}), {@code MINIMAX_DEVICES};
 * pass {@code SMILE_MINIMAX_VISION=0} and {@code MINIMAX_MEM_FRACTION=0.92} on 8x40 GB cards (the draft needs ~3 GB on rank 0).
 * Greedy speculative output may differ from plain output after a bf16 near tie, so agreement is only required for the first
 * {@value #AGREE} tokens of the short prompts (the earliest difference seen there was after 11; at ~3k context the
 * model's run-to-run noise is larger and only validity is checked).
 *
 * @author Haifeng Li
 */
public class MiniMaxDsparkEngineTest {
    private static final int AGREE = 6;

    private static int firstDiff(int[] a, int[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : n;
    }

    private static ChatCompletion run(InferenceEngine engine, int[] prompt, int maxGen, double temperature, double topp,
                                      boolean speculative) throws Exception {
        return engine.submit(GenerationRequest.ofTokens(prompt, maxGen, temperature, topp, false, 0, null, null,
                speculative, 7)).future().get(300, TimeUnit.SECONDS);
    }

    @Test
    public void speculate() throws Exception {
        String dir = System.getenv("MINIMAX_MODEL_DIR");
        String devList = System.getenv("MINIMAX_DEVICES");
        assumeTrue(dir != null && devList != null, "MINIMAX_* not set");
        String[] ds = devList.split(",");
        byte[] devices = new byte[ds.length];
        for (int i = 0; i < ds.length; i++) {
            devices[i] = Byte.parseByte(ds[i].trim());
        }
        double mem = Double.parseDouble(System.getenv().getOrDefault("MINIMAX_MEM_FRACTION", "0.92"));
        try (MiniMax m = MiniMax.build(dir, 4, 4096, devices, mem, 1)) {
            assumeTrue(m.isSpeculativeEnabled(), "no DSpark draft loaded");
            m.setPrefixReuseEnabled(true);
            InferenceEngine engine = new InferenceEngine(m, 4, 4, 2048, 120_000, 0);
            engine.setMaxSpeculativeConcurrency(4);   // the concurrent scenarios verify their windows in one batched graph
            String[] questions = {
                    "What is the capital of France? Answer in one sentence.",
                    "Write a Python function that checks whether a string is a palindrome.",
                    "Explain how a hash map handles collisions in two short paragraphs."};
            List<int[]> prompts = new ArrayList<>();
            for (String q : questions) {
                prompts.add(m.encodeChat(new Message(Role.user, q)));
            }
            int[] stops = m.stopTokens();

            // A. agreement with plain decoding, validity of the tokens
            List<ChatCompletion> plain = new ArrayList<>();
            for (int[] p : prompts) {
                ChatCompletion base = run(engine, p, 48, 0.0, 1.0, false);
                long rounds = m.speculativeRounds();
                ChatCompletion spec = run(engine, p, 48, 0.0, 1.0, true);
                assertTrue(m.speculativeRounds() > rounds, "the request did not speculate (prefix reuse lost the draft context?)");
                int d = firstDiff(base.completionTokens(), spec.completionTokens());
                System.out.println("A: prompt " + p.length + " tokens, plain " + base.completionTokens().length + " vs spec "
                        + spec.completionTokens().length + ", first diff " + d);
                assertTrue(d < 0 || d >= AGREE, "speculative output differs from plain at token " + d);
                for (int t : spec.completionTokens()) {
                    assertTrue(t >= 0 && t < m.params().vocabSize());
                }
                plain.add(base);
            }
            System.out.println("   " + m.speculativeStats());

            // B. max_tokens falling inside a block
            for (int maxGen : new int[]{1, 2, 3, 5, 8, 13}) {
                ChatCompletion base = run(engine, prompts.get(1), maxGen, 0.0, 1.0, false);
                long rounds = m.speculativeRounds();
                ChatCompletion spec = run(engine, prompts.get(1), maxGen, 0.0, 1.0, true);
                if (maxGen >= 3) {
                    assertTrue(m.speculativeRounds() > rounds, "max_tokens=" + maxGen + " did not speculate");
                }
                assertEquals(base.completionTokens().length, spec.completionTokens().length, "length for max_tokens=" + maxGen);
                assertEquals(maxGen, spec.completionTokens().length);
                assertEquals(FinishReason.length, spec.reason());
                int d = firstDiff(base.completionTokens(), spec.completionTokens());
                assertTrue(d < 0 || d >= AGREE || maxGen <= d, "max_tokens=" + maxGen + " differs at " + d);
            }
            System.out.println("B: max_tokens 1,2,3,5,8,13 ok");

            // C. a short answer that ends at a stop token inside an accepted block
            ChatCompletion stopBase = run(engine, prompts.get(0), 200, 0.0, 1.0, false);
            long stopRounds = m.speculativeRounds();
            ChatCompletion stopSpec = run(engine, prompts.get(0), 200, 0.0, 1.0, true);
            assertTrue(m.speculativeRounds() > stopRounds, "the stop-token request did not speculate");
            assertEquals(FinishReason.stop, stopSpec.reason());
            int[] tokens = stopSpec.completionTokens();
            for (int i = 0; i < tokens.length - 1; i++) {
                for (int s : stops) {
                    assertNotEquals(s, tokens[i], "no stop token before the end (position " + i + ")");
                }
            }
            System.out.println("C: stop at " + tokens.length + " tokens (plain " + stopBase.completionTokens().length
                    + "), first diff " + firstDiff(stopBase.completionTokens(), tokens));

            // D. prefix reuse: the second identical request hits the radix cache, so its draft has no taps
            String before = m.speculativeStats();
            long beforeRounds = m.speculativeRounds();
            ChatCompletion again = run(engine, prompts.get(1), 48, 0.0, 1.0, true);
            assertEquals(48, again.completionTokens().length);
            assertTrue(m.speculativeRounds() > beforeRounds, "a prefix-cached request must still speculate");
            System.out.println("D: repeat of a cached prompt ok; before: " + before + "\n   after:  " + m.speculativeStats());

            // E. concurrent speculative requests
            long concurrentRounds = m.speculativeRounds();
            List<CompletableFuture<ChatCompletion>> futures = new ArrayList<>();
            for (int[] p : prompts) {
                futures.add(engine.submit(GenerationRequest.ofTokens(p, 40, 0.0, 1.0, false, 0, null, null, true, 7)).future());
            }
            for (int i = 0; i < futures.size(); i++) {
                ChatCompletion c = futures.get(i).get(300, TimeUnit.SECONDS);
                int d = firstDiff(plain.get(i).completionTokens(), c.completionTokens());
                System.out.println("E: concurrent " + i + ": " + c.completionTokens().length + " tokens, first diff vs plain " + d);
                assertTrue(d < 0 || d >= AGREE || plain.get(i).completionTokens().length <= d || c.completionTokens().length <= d);
            }

            assertTrue(m.speculativeRounds() > concurrentRounds, "concurrent requests did not speculate");

            // F. sampled requests
            long sampledRounds = m.speculativeRounds();
            futures.clear();
            for (int[] p : prompts) {
                futures.add(engine.submit(GenerationRequest.ofTokens(p, 40, 0.8, 0.9, false, 0, null, null, true, 7)).future());
            }
            for (CompletableFuture<ChatCompletion> f : futures) {
                ChatCompletion c = f.get(300, TimeUnit.SECONDS);
                assertTrue(c.completionTokens().length >= 1 && c.completionTokens().length <= 40);
                for (int t : c.completionTokens()) {
                    assertTrue(t >= 0 && t < m.params().vocabSize());
                }
            }
            assertTrue(m.speculativeRounds() > sampledRounds, "sampled requests did not speculate");
            System.out.println("F: sampled ok; " + m.speculativeStats());

            // G. a three-token prompt, shorter than a draft block
            long tinyRounds = m.speculativeRounds();
            ChatCompletion tiny = run(engine, new int[]{758, 4729, 15801}, 24, 0.0, 1.0, true);
            assertTrue(m.speculativeRounds() > tinyRounds, "the tiny prompt did not speculate");
            assertTrue(tiny.completionTokens().length >= 1);
            System.out.println("G: tiny prompt ok, " + tiny.completionTokens().length + " tokens");

            // H. a long prompt that is prefilled in several chunks
            StringBuilder sb = new StringBuilder();
            for (int i = 0; sb.length() < 12000; i++) {
                sb.append("Item ").append(i).append(": the quick brown fox jumps over the lazy dog near the river bank. ");
            }
            int[] longPrompt = m.encodeChat(new Message(Role.user, sb + "\nSummarize the list above in one sentence."));
            assertTrue(longPrompt.length > 2048 && longPrompt.length < 3900, "prompt tokens " + longPrompt.length);
            ChatCompletion longBase = run(engine, longPrompt, 48, 0.0, 1.0, false);
            long longRounds = m.speculativeRounds();
            ChatCompletion longSpec = run(engine, longPrompt, 48, 0.0, 1.0, true);
            assertTrue(m.speculativeRounds() > longRounds, "the long prompt did not speculate");
            int d = firstDiff(longBase.completionTokens(), longSpec.completionTokens());
            System.out.println("H: long prompt " + longPrompt.length + " tokens, first diff " + d + "; " + m.speculativeStats());
            // No agreement requirement here: with a ~3k context the model's own noise between a 1-row and an 8-row forward
            // (MoE routing flips; top-20 logits move by 1-3 between execution shapes) can flip an early greedy token.
            assertTrue(longSpec.completionTokens().length >= 1);

            // I. a multi-turn follow-up: its prompt starts with the earlier prompt (a prefix hit) plus new text
            ChatCompletion first = run(engine, prompts.get(2), 64, 0.0, 1.0, true);
            int[] followUp = m.encodeChat(new Message(Role.user, questions[2]), new Message(Role.assistant, first.content()),
                    new Message(Role.user, "Now say it again in one sentence."));
            long turnRounds = m.speculativeRounds();
            ChatCompletion second = run(engine, followUp, 48, 0.0, 1.0, true);
            assertTrue(m.speculativeRounds() > turnRounds, "the follow-up turn did not speculate");
            System.out.println("I: follow-up " + followUp.length + " tokens -> " + second.completionTokens().length + " tokens; "
                    + m.speculativeStats());
            engine.close();
        }
    }
}
