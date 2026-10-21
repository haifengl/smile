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

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in: teacher-forces the greedy continuation of a reference run (vLLM) through the model and writes, per step, this
 * model's log-probability of the reference token, its own argmax and its top-1/top-2 margin, for comparison with the
 * reference's log-probabilities. Environment: {@code QWEN_DIR}, {@code QWEN_REF} (JSON with {@code prompt_ids} and
 * {@code steps[].token}), {@code QWEN_OUT}, {@code QWEN_MEM_FRACTION} (default 0.93).
 *
 * @author Haifeng Li
 */
public class QwenFp8LogprobDumpTest {
    @Test
    public void dump() throws Exception {
        String dir = System.getenv("QWEN_DIR");
        String ref = System.getenv("QWEN_REF");
        String out = System.getenv("QWEN_OUT");
        assumeTrue(dir != null && ref != null && out != null, "QWEN_* not set");
        double fraction = Double.parseDouble(System.getenv().getOrDefault("QWEN_MEM_FRACTION", "0.93"));
        JsonNode cases = new ObjectMapper().readTree(new File(ref));
        StringBuilder json = new StringBuilder("[");
        try (Qwen qwen = Qwen.build(dir, 4, 2048, (byte) 0, fraction, null)) {
            boolean firstCase = true;
            for (JsonNode c : cases) {
                int[] prompt = new int[c.get("prompt_ids").size()];
                for (int i = 0; i < prompt.length; i++) {
                    prompt[i] = c.get("prompt_ids").get(i).asInt();
                }
                JsonNode steps = c.get("steps");
                int n = steps.size();
                int id = qwen.bind(prompt, prompt.length + n + 8);
                StringBuilder row = new StringBuilder();
                try {
                    float[] logits = host(qwen.prefill(id, prompt, 0));
                    for (int i = 0; i < n; i++) {
                        int token = steps.get(i).get("token").asInt();
                        double lse = logsumexp(logits);
                        int a1 = 0;
                        for (int v = 1; v < logits.length; v++) {
                            if (logits[v] > logits[a1]) {
                                a1 = v;
                            }
                        }
                        double second = Double.NEGATIVE_INFINITY;
                        for (int v = 0; v < logits.length; v++) {
                            if (v != a1) {
                                second = Math.max(second, logits[v]);
                            }
                        }
                        if (i > 0) {
                            row.append(',');
                        }
                        row.append(String.format("[%d,%.6f,%d,%.6f]", token, logits[token] - lse, a1,
                                logits[a1] - second));
                        if (i + 1 < n) {
                            logits = host(qwen.decodeStep(new int[]{id}, new int[]{token},
                                    new int[]{prompt.length + i}));
                        }
                    }
                } finally {
                    qwen.evict(id);
                }
                if (!firstCase) {
                    json.append(',');
                }
                firstCase = false;
                json.append("{\"q\":").append(new ObjectMapper().writeValueAsString(c.get("q").asString()))
                        .append(",\"steps\":[").append(row).append("]}");
            }
        }
        json.append(']');
        Files.writeString(new File(out).toPath(), json.toString());
    }

    private static float[] host(Tensor logits) {
        try (logits; Tensor cpu = logits.to(Device.CPU()).to(smile.deep.tensor.ScalarType.Float)) {
            return cpu.floatArray();
        }
    }

    private static double logsumexp(float[] x) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : x) {
            max = Math.max(max, v);
        }
        double s = 0;
        for (float v : x) {
            s += Math.exp(v - max);
        }
        return max + Math.log(s);
    }
}
