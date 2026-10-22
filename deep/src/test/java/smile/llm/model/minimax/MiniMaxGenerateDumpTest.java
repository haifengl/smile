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
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import smile.llm.ChatCompletion;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in greedy generation dump, used to check that expert-parallel decode (KV cache, mixed
 * batch positions, per-rank threads) reproduces the single-rank tokens.
 *
 * <p>Environment: {@code MINIMAX_MODEL_DIR}, {@code MINIMAX_PROMPTS}, {@code MINIMAX_OUT},
 * {@code MINIMAX_DEVICES}, optional {@code MINIMAX_GEN} (tokens per prompt, default 24), {@code MINIMAX_MEM_FRACTION}
 * (default 0.6), and {@code MINIMAX_CLUSTER_PORT} + {@code MINIMAX_CLUSTER_FOLLOWERS} to lead a multi-node model.
 *
 * @author Haifeng Li
 */
public class MiniMaxGenerateDumpTest {
    @Test
    public void generate() throws Exception {
        String dir = System.getenv("MINIMAX_MODEL_DIR");
        String promptsPath = System.getenv("MINIMAX_PROMPTS");
        String outPath = System.getenv("MINIMAX_OUT");
        String devList = System.getenv("MINIMAX_DEVICES");
        assumeTrue(dir != null && promptsPath != null && outPath != null && devList != null, "MINIMAX_* not set");
        int gen = Integer.parseInt(System.getenv().getOrDefault("MINIMAX_GEN", "24"));
        byte[] devices = new byte[devList.split(",").length];
        for (int i = 0; i < devices.length; i++) devices[i] = Byte.parseByte(devList.split(",")[i].trim());

        var prompts = new ObjectMapper().readTree(new File(promptsPath));
        StringBuilder out = new StringBuilder("[");
        // MINIMAX_CLUSTER_PORT / MINIMAX_CLUSTER_FOLLOWERS: run as the leader of a multi-node model (see MiniMaxFollower)
        String clusterPort = System.getenv("MINIMAX_CLUSTER_PORT");
        double memFraction = Double.parseDouble(System.getenv().getOrDefault("MINIMAX_MEM_FRACTION", "0.6"));
        try (MiniMax m = clusterPort == null
                ? MiniMax.build(dir, 4, 8192, devices, memFraction, 1)
                : MiniMax.buildLeader(dir, 4, 8192, devices, memFraction, 1, null, Integer.parseInt(clusterPort),
                        Integer.parseInt(System.getenv().getOrDefault("MINIMAX_CLUSTER_FOLLOWERS", "1")), 3_600_000L)) {
            boolean first = true;
            for (var p : prompts) {
                int[] ids = new int[p.get("ids").size()];
                for (int i = 0; i < ids.length; i++) ids[i] = p.get("ids").get(i).asInt();
                ChatCompletion c = m.generate(ids, gen, 0.0, 1.0, false, 0, null);
                if (!first) out.append(',');
                first = false;
                out.append("{\"name\":\"").append(p.get("name").asString()).append("\",\"completion\":")
                        .append(Arrays.toString(c.completionTokens())).append('}');
            }
            // Teacher-forced decode: prefill all but the last 8 tokens of each prompt, then decode the real
            // tokens one by one and record the log-probability of the true next token for comparison with vLLM.
            for (var p : prompts) {
                int[] ids = new int[p.get("ids").size()];
                for (int i = 0; i < ids.length; i++) ids[i] = p.get("ids").get(i).asInt();
                int n = ids.length;
                int hold = 8;
                int id = m.bind(ids, n);
                StringBuilder lps = new StringBuilder();
                try {
                    m.prefill(id, Arrays.copyOf(ids, n - hold), 0).close();
                    for (int t = n - hold; t < n - 1; t++) {
                        try (var logits = m.decodeStep(new int[]{id}, new int[]{ids[t]}, new int[]{t})) {
                            float[] row = logits.to(smile.deep.tensor.Device.CPU()).copy().floatArray();
                            double max = Double.NEGATIVE_INFINITY;
                            for (float v : row) max = Math.max(max, v);
                            double sum = 0;
                            for (float v : row) sum += Math.exp(v - max);
                            double lp = row[ids[t + 1]] - max - Math.log(sum);
                            int arg = 0;
                            for (int i = 1; i < row.length; i++) if (row[i] > row[arg]) arg = i;
                            if (lps.length() > 0) lps.append(',');
                            lps.append("[").append(t + 1).append(',').append(lp).append(',').append(arg).append(']');
                        }
                    }
                } finally {
                    m.evict(id);
                }
                out.append(",{\"name\":\"teacher_").append(p.get("name").asString()).append("\",\"decode\":[")
                        .append(lps).append("]}");
            }
            // Two requests decoded together at different positions (ragged batch).
            int[] a = new int[40];
            int[] b = new int[17];
            for (int i = 0; i < a.length; i++) a[i] = 100 + i;
            for (int i = 0; i < b.length; i++) b[i] = 500 + 3 * i;
            int ia = m.bind(a, 64);
            int ib = m.bind(b, 64);
            try (var la = m.prefill(ia, a, 0); var lb = m.prefill(ib, b, 0);
                 var step = m.decodeStep(new int[]{ia, ib}, new int[]{7, 9}, new int[]{a.length, b.length})) {
                int[] argmax = smile.llm.engine.Sampling.sampleGreedyTokenIds(step);
                out.append(",{\"name\":\"ragged\",\"completion\":").append(Arrays.toString(argmax)).append('}');
            } finally {
                m.evict(ia);
                m.evict(ib);
            }
        }
        out.append(']');
        Files.writeString(new File(outPath).toPath(), out.toString());
    }
}
