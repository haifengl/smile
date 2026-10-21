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
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.TextPart;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in end-to-end check of image input against vLLM ({@code ref_vllm_mm.py}): the expanded prompt must equal
 * vLLM's token ids exactly and the greedy continuation's log-probabilities must agree to bf16 noise.
 * Environment: {@code MINIMAX_MM_DIR} (checkpoint with vision weights), {@code MINIMAX_MM_REF} (vLLM output),
 * {@code MINIMAX_MM_MEDIA} (directory with the images), {@code MINIMAX_DEVICES}.
 *
 * @author Haifeng Li
 */
public class MiniMaxMultimodalTest {
    private static Message user(String... items) {
        var parts = new java.util.ArrayList<smile.llm.ContentPart>();
        for (String item : items) {
            parts.add(item.startsWith("img:")
                    ? new ImageUrlPart(new File(System.getenv("MINIMAX_MM_MEDIA"), item.substring(4)).getPath())
                    : new TextPart(item));
        }
        return new Message(smile.llm.Role.user, parts);
    }

    private static double logprob(float[] row, int token) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : row) {
            max = Math.max(max, v);
        }
        double sum = 0;
        for (float v : row) {
            sum += Math.exp(v - max);
        }
        return row[token] - max - Math.log(sum);
    }

    private static float[] row(Tensor logits) {
        try (logits; Tensor cpu = logits.to(Device.CPU())) {
            return cpu.floatArray();
        }
    }

    @Test
    public void testGivenImagesWhenPromptedThenMatchesVllm() throws Exception {
        String dir = System.getenv("MINIMAX_MM_DIR");
        String ref = System.getenv("MINIMAX_MM_REF");
        String devList = System.getenv("MINIMAX_DEVICES");
        assumeTrue(dir != null && ref != null && devList != null && System.getenv("MINIMAX_MM_MEDIA") != null,
                "MINIMAX_MM_* not set");
        String[] parts = devList.split(",");
        byte[] devices = new byte[parts.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(parts[i].trim());
        }
        JsonNode expected = new ObjectMapper().readTree(new File(ref));
        Message[][] cases = {
                {user("img:img_a.png", "What is in this picture?")},
                {user("Compare", "img:img_b.png", "img:img_d.png", "these two.")},
        };
        String[] names = {"one_image", "two_images"};
        try (MiniMax m = MiniMax.build(dir, 4, 8192, devices, 0.6, 1)) {
            assertTrue(m.isMultimodal(), "vision weights not loaded");
            for (int c = 0; c < cases.length; c++) {
                JsonNode want = expected.get(names[c]);
                var mm = m.processMultimodal(cases[c], null);
                int[] ids = mm.inputIds();
                int[] wantIds = new int[want.get("prompt_token_ids").size()];
                for (int i = 0; i < wantIds.length; i++) {
                    wantIds[i] = want.get("prompt_token_ids").get(i).asInt();
                }
                assertArrayEquals(wantIds, ids, names[c] + ": expanded prompt differs from vLLM");

                int id = m.bind(ids, ids.length + 32);
                try {
                    Tensor first = m.prefillMultimodal(id, mm, 0, ids.length);
                    JsonNode tokens = want.get("token_ids");
                    JsonNode lps = want.get("logprobs");
                    double sum = 0;
                    int agree = 0;
                    int n = tokens.size();
                    float[] logits = row(first);
                    for (int i = 0; i < n; i++) {
                        int token = tokens.get(i).asInt();
                        int arg = 0;
                        for (int v = 1; v < logits.length; v++) {
                            if (logits[v] > logits[arg]) {
                                arg = v;
                            }
                        }
                        double ours = logprob(logits, token);
                        double theirs = lps.get(i).get(String.valueOf(token)).asDouble();
                        sum += Math.abs(ours - theirs);
                        if (arg == token) {
                            agree++;
                        }
                        if (i + 1 < n) {
                            logits = row(m.decodeStep(new int[]{id}, new int[]{token}, new int[]{ids.length + i}));
                        }
                    }
                    System.out.println("MM " + names[c] + ": prompt " + ids.length + " tokens, mean |dlogprob| "
                            + sum / n + ", top-1 agreement " + agree + "/" + n);
                    assertTrue(sum / n < 0.1, names[c] + " mean |dlogprob| " + sum / n);
                    assertTrue(agree >= n - 1, names[c] + " top-1 agreement " + agree + "/" + n);
                } finally {
                    m.evict(id);
                    mm.close();
                }
            }
        }
    }
}
