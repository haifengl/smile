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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in dump of per-position log-probabilities for numerical comparison against vLLM.
 *
 * <p>Environment: {@code MINIMAX_MODEL_DIR} (checkpoint), {@code MINIMAX_PROMPTS} (JSON list of
 * {@code {name, text, ids}}), {@code MINIMAX_OUT} (output JSON), optional {@code MINIMAX_DEVICE}
 * (default 0, negative = CPU), {@code MINIMAX_MAX_SEQ} (default 4096), {@code MINIMAX_MEM_FRACTION} (device memory share for weights + KV, default 0.75 on CUDA) and {@code MINIMAX_DENSE=1}
 * to force dense attention on the sparse layers.
 *
 * @author Haifeng Li
 */
public class MiniMaxReferenceDumpTest {
    private static final int TOP = 20;

    @Test
    public void dump() throws Exception {
        String dir = System.getenv("MINIMAX_MODEL_DIR");
        String promptsPath = System.getenv("MINIMAX_PROMPTS");
        String outPath = System.getenv("MINIMAX_OUT");
        assumeTrue(dir != null && promptsPath != null && outPath != null, "MINIMAX_* not set");
        String devList = System.getenv().getOrDefault("MINIMAX_DEVICES", System.getenv().getOrDefault("MINIMAX_DEVICE", "0"));
        byte[] devices = devList.equals("-1") ? null
                : Arrays.stream(devList.split(",")).map(String::trim).map(Byte::parseByte)
                        .collect(java.util.stream.Collector.of(java.io.ByteArrayOutputStream::new,
                                (b, x) -> b.write(x), (a, b) -> a, java.io.ByteArrayOutputStream::toByteArray));
        int device = devices == null ? -1 : devices[0];
        int maxSeq = Integer.parseInt(System.getenv().getOrDefault("MINIMAX_MAX_SEQ", "4096"));
        boolean dense = "1".equals(System.getenv("MINIMAX_DENSE"));
        double memFraction = Double.parseDouble(System.getenv().getOrDefault("MINIMAX_MEM_FRACTION", device >= 0 ? "0.75" : "0"));
        System.out.println("cuda available: " + (smile.torch.smile_torch_h.smile_cuda_is_available() != 0));

        JsonNode prompts = new ObjectMapper().readTree(new File(promptsPath));
        StringBuilder out = new StringBuilder("[");
        try (MiniMax m = MiniMax.build(dir, 1, maxSeq, devices, memFraction, 1)) {
            if (dense) {
                for (MiniMaxModel net : m.networks()) {
                    net.layers().forEach(l -> l.selfAttn().forceDense = true);
                }
            }
            boolean firstPrompt = true;
            for (JsonNode p : prompts) {
                int n = p.get("ids").size();
                long[] ids = new long[n];
                for (int i = 0; i < n; i++) ids[i] = p.get("ids").get(i).asLong();
                int[] ours = m.encode(p.get("text").asString());
                int[] ref = Arrays.stream(ids).mapToInt(x -> (int) x).toArray();
                int firstDiff = Arrays.mismatch(ours, ref);

                int requestId = m.bind(ref, n);
                try (var noGrad = Tensor.noGradGuard();
                     Tensor logits = m.forwardAllLogits(requestId, ref)) {
                    int vocab = (int) logits.shape()[2];
                    var cpu = smile.deep.tensor.Device.CPU();
                    float[] lp = new float[n * TOP];
                    long[] tid = new long[n * TOP];
                    float[] next = new float[Math.max(0, n - 1)];
                    final int chunk = 256;  // keep the float32 exp() temporaries small
                    for (int from = 0; from < n; from += chunk) {
                        int to = Math.min(n, from + chunk);
                        try (var scope = new smile.util.AutoScope()) {
                            Tensor.push(scope);
                            try {
                                Tensor part = logits.get(Index.Colon, Index.slice(from, to));
                                var top = part.topk(TOP, 2, true, true);
                                Tensor topVals = top._1();
                                Tensor mx = topVals.get(Index.Ellipsis, Index.slice(0, 1));
                                Tensor lse = part.sub(mx).exp().sum(2, true).log().add(mx);
                                float[] l = topVals.sub(lse).to(cpu).copy().floatArray();
                                long[] ti = top._2().to(cpu).copy().longArray();
                                System.arraycopy(l, 0, lp, from * TOP, l.length);
                                System.arraycopy(ti, 0, tid, from * TOP, ti.length);
                                int hi = Math.min(to, n - 1);
                                if (hi > from) {
                                    long[] nextIds = Arrays.copyOfRange(ids, from + 1, hi + 1);
                                    Tensor idx = Tensor.of(nextIds, 1, hi - from, 1).to(logits.device());
                                    Tensor head = part.get(Index.Colon, Index.slice(0, hi - from));
                                    Tensor lseHead = lse.get(Index.Colon, Index.slice(0, hi - from));
                                    float[] nl = head.gather(2, idx).sub(lseHead).to(cpu).copy().floatArray();
                                    System.arraycopy(nl, 0, next, from, nl.length);
                                }
                            } finally {
                                Tensor.pop();
                            }
                        }
                    }

                    if (!firstPrompt) out.append(',');
                    firstPrompt = false;
                    out.append("{\"name\":").append(new ObjectMapper().writeValueAsString(p.get("name").asString()))
                            .append(",\"n\":").append(n).append(",\"vocab\":").append(vocab)
                            .append(",\"tokenizer_first_diff\":").append(firstDiff)
                            .append(",\"top_ids\":").append(Arrays.toString(tid))
                            .append(",\"top_lp\":").append(Arrays.toString(lp))
                            .append(",\"next_lp\":").append(Arrays.toString(next)).append('}');
                } finally {
                    m.evict(requestId);
                }
            }
        }
        out.append(']');
        Files.writeString(new File(outPath).toPath(), out.toString());
    }
}
