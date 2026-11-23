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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;
import smile.llm.ChatOptions;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.engine.Sampling;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Needle in a haystack through the executor API: a code word is hidden in the middle of a long prompt of filler
 * paragraphs and asked for at the end; reports prefill time, decode speed and the answer.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_DEVICE}, {@code INKLING_PROMPT} (tokens, default 32000),
 * {@code INKLING_TOKENS} (generated, default 200).
 *
 * @author Haifeng Li
 */
public class InklingLongPromptTest {
    @Test
    public void testGivenNeedleInLongPromptWhenAskedThenFound() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        Assumptions.assumeTrue(ckpt != null, "set INKLING_CKPT");
        int target = Integer.parseInt(System.getenv().getOrDefault("INKLING_PROMPT", "32000"));
        int gen = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "200"));
        byte device = Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0"));
        try (Inkling model = Inkling.build(ckpt, 0, (int) (target * 1.15) + gen + 4096, device)) {
            java.util.Random r = new java.util.Random(7);
            String[] subjects = {"The harbor authority", "A local baker", "The village council", "An old fisherman", "The museum curator", "A travelling merchant"};
            String[] verbs = {"recorded", "announced", "postponed", "repaired", "counted", "inspected"};
            String[] objects = {"the northern lighthouse", "forty crates of salt", "the annual market schedule", "a broken bell", "the river ferry", "seventeen barrels"};
            StringBuilder sb = new StringBuilder();
            StringBuilder tail = new StringBuilder();
            int needleAt = target * 4 / 10 * 4;      // characters (about 4 per token)
            boolean placed = false;
            for (int i = 0; sb.length() < target * 4; i++) {
                sb.append(subjects[r.nextInt(6)]).append(' ').append(verbs[r.nextInt(6)]).append(' ').append(objects[r.nextInt(6)])
                        .append(" on day ").append(r.nextInt(365)).append(" of year ").append(1900 + r.nextInt(120)).append(". ");
                if (!placed && sb.length() > needleAt) {
                    sb.append("IMPORTANT: the secret vault code word is TANGERINE-4821. ");
                    placed = true;
                }
            }
            sb.append("\n\nQuestion: what is the secret vault code word mentioned in the text above? Answer with the code word only.");
            ChatOptions options = new ChatOptions(null, smile.llm.ToolChoice.AUTO, true).withReasoningEffort("none");
            int[] prompt = model.encodeChat(new Message[]{new Message(Role.user, sb.toString())}, options);
            System.out.printf("prompt: %d tokens%n", prompt.length);
            int id = model.bind(prompt, prompt.length + gen + 16);
            long t0 = System.nanoTime();
            int pos = 0, chunk = 2048;
            Tensor logits = null;
            while (pos < prompt.length) {
                int end = Math.min(prompt.length, pos + chunk);
                Tensor t = model.prefillChunk(id, prompt, pos, end);
                if (t != null) {
                    logits = t;
                }
                pos = end;
                if (pos % 16384 < chunk) {
                    System.out.printf("  prefilled %d tokens, %.0f s%n", pos, (System.nanoTime() - t0) / 1e9);
                }
            }
            double prefill = (System.nanoTime() - t0) / 1e9;
            System.out.printf("prefill: %.1f s = %.0f tok/s%n", prefill, prompt.length / prefill);
            List<Integer> out = new ArrayList<>();
            int next;
            Tensor first = logits;
            try (first) {
                next = Sampling.sampleTokenIds(first, 0.0, 1.0)[0];
            }
            long t1 = System.nanoTime();
            for (int i = 0; i < gen; i++) {
                out.add(next);
                if (next == 200006) {
                    break;
                }
                try (Tensor lg = model.decodeStep(new int[]{id}, new int[]{next}, new int[]{prompt.length + i})) {
                    next = Sampling.sampleTokenIds(lg, 0.0, 1.0)[0];
                }
            }
            double dec = (System.nanoTime() - t1) / 1e9;
            String text = model.decode(out.stream().mapToInt(Integer::intValue).toArray());
            System.out.printf("decode: %d tokens in %.2f s = %.1f ms/token%nanswer: %s%n", out.size(), dec, dec * 1000 / out.size(), text.replace('\n', ' '));
            model.evict(id);
            assertTrue(text.contains("TANGERINE-4821"), "the code word was not found");
        }
    }
}
