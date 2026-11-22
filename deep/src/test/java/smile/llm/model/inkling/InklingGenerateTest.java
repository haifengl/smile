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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.*;
import smile.llm.template.InklingChatTemplate;
import smile.llm.tool.InklingResponseParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End to end with the reference model: dialogs are rendered by {@link InklingChatTemplate}, tokenized by
 * {@link InklingTokenizer}, prefilled (one chunk and in small chunks) and decoded greedily; the continuation is compared
 * with the Hugging Face model's, and the reply is split by {@link InklingResponseParser}.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_ORACLE} (out json with {@code gen_ids}), {@code INKLING_DIALOGS}
 * (the dialogs json), {@code INKLING_DEVICE}, {@code INKLING_TOKENS} (default 48).
 *
 * @author Haifeng Li
 */
public class InklingGenerateTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    static Message message(JsonNode m) {
        List<ToolCall> calls = null;
        if (m.has("tool_calls")) {
            calls = new ArrayList<>();
            for (JsonNode c : m.get("tool_calls")) {
                calls.add(ToolCall.function(c.get("id").asString(), new FunctionCall(
                        c.get("function").get("name").asString(), c.get("function").get("arguments").toString())));
            }
        }
        String content = m.get("content").asString();
        List<ContentPart> parts = content.isEmpty() && calls != null ? List.of() : List.of(new TextPart(content));
        return new Message(Role.valueOf(m.get("role").asString()), parts, calls,
                m.has("tool_call_id") ? m.get("tool_call_id").asString() : null, null);
    }

    @SuppressWarnings("unchecked")
    static ChatOptions options(JsonNode d) {
        ToolDefinition[] tools = null;
        if (d.has("tools")) {
            tools = new ToolDefinition[d.get("tools").size()];
            for (int i = 0; i < tools.length; i++) {
                JsonNode fn = d.get("tools").get(i).get("function");
                tools[i] = ToolDefinition.function(new FunctionDefinition(fn.get("name").asString(),
                        fn.get("description").asString(), MAPPER.convertValue(fn.get("parameters"), Map.class)));
            }
        }
        ChatOptions options = new ChatOptions(tools, ToolChoice.AUTO, true);
        return d.has("reasoning_effort") ? options.withReasoningEffort(d.get("reasoning_effort").asString()) : options;
    }

    /** Greedy continuation: prefill in chunks of {@code chunk} tokens (0 = one chunk), then single-token steps. */
    private static long[] generate(InklingModel model, long[] prompt, int chunk, int count, float[] firstLogits) {
        try (InklingState st = model.newState(prompt.length + count + 8)) {
            int pos = 0;
            Tensor logits = null;
            while (pos < prompt.length) {
                int n = chunk <= 0 ? prompt.length : Math.min(chunk, prompt.length - pos);
                long[] part = java.util.Arrays.copyOfRange(prompt, pos, pos + n);
                logits = model.forward(st, part, null, null);
                pos += n;
            }
            long[] out = new long[count];
            for (int i = 0; i < count; i++) {
                float[] row = logits.to(Device.CPU()).floatArray();
                if (i == 0 && firstLogits != null) {
                    System.arraycopy(row, 0, firstLogits, 0, row.length);
                }
                int best = 0;
                for (int v = 1; v < row.length; v++) {
                    if (row[v] > row[best]) {
                        best = v;
                    }
                }
                out[i] = best;
                if (best == 200006) {
                    return java.util.Arrays.copyOf(out, i + 1);
                }
                logits = model.forward(st, new long[]{best}, null, null);
            }
            return out;
        }
    }

    /** Largest change of the log-probability among the {@code k} most likely tokens of {@code a}. */
    private static double topDiff(float[] a, float[] b, int k) {
        double[] la = logSoftmax(a);
        double[] lb = logSoftmax(b);
        Integer[] order = new Integer[a.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (x, y) -> Double.compare(la[y], la[x]));
        double max = 0;
        for (int i = 0; i < k; i++) {
            max = Math.max(max, Math.abs(la[order[i]] - lb[order[i]]));
        }
        return max;
    }

    private static double[] logSoftmax(float[] x) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : x) {
            max = Math.max(max, v);
        }
        double sum = 0;
        for (float v : x) {
            sum += Math.exp(v - max);
        }
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = x[i] - max - Math.log(sum);
        }
        return out;
    }

    @Test
    public void testGivenDialogsWhenGeneratedGreedilyThenContinuationMatchesTheOracle() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String oracle = System.getenv("INKLING_ORACLE");
        String dialogs = System.getenv("INKLING_DIALOGS");
        Assumptions.assumeTrue(ckpt != null && oracle != null && dialogs != null && new File(oracle).exists(),
                "set INKLING_CKPT, INKLING_ORACLE and INKLING_DIALOGS");
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        String layers = System.getenv("INKLING_LAYERS");
        if (layers != null) {
            args = args.withLayers(Integer.parseInt(layers), Integer.parseInt(System.getenv().getOrDefault("INKLING_PASSES", "1")));
        }
        Device device = Device.CUDA(Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        int count = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "48"));
        InklingModel model = InklingModel.load(ckpt, args, device);
        InklingTokenizer tokenizer = InklingTokenizer.of(ckpt);
        InklingResponseParser parser = new InklingResponseParser();
        JsonNode input = MAPPER.readTree(new File(dialogs));
        JsonNode results = MAPPER.readTree(new File(oracle)).get("results");
        for (int k = 0; k < input.size(); k++) {
            JsonNode d = input.get(k);
            List<Message> messages = new ArrayList<>();
            for (JsonNode m : d.get("messages")) {
                messages.add(message(m));
            }
            long[] prompt = java.util.Arrays.stream(tokenizer.encodeWithSpecials(
                    new InklingChatTemplate().encode(messages, options(d)))).asLongStream().toArray();
            JsonNode ref = results.get(k);
            long[] oracleGen = new long[ref.get("gen_ids").size()];
            for (int i = 0; i < oracleGen.length; i++) {
                oracleGen[i] = ref.get("gen_ids").get(i).asLong();
            }
            float[] first = new float[args.unpaddedVocabSize()];
            float[] firstChunked = new float[args.unpaddedVocabSize()];
            long[] gen = generate(model, prompt, 0, count, first);
            long[] chunked = generate(model, prompt, 7, 1, firstChunked);
            float[] firstChunked2 = new float[args.unpaddedVocabSize()];
            generate(model, prompt, 11, 1, firstChunked2);
            int same = 0;
            while (same < Math.min(gen.length, oracleGen.length) && gen[same] == oracleGen[same]) {
                same++;
            }
            double chunkDiff = topDiff(first, firstChunked, 3);
            double chunkDiff2 = topDiff(first, firstChunked2, 3);
            double chunkVsChunk = topDiff(firstChunked, firstChunked2, 3);
            double tail = topDiff(first, firstChunked, 20);
            String text = tokenizer.decode(java.util.Arrays.stream(gen).mapToInt(x -> (int) x).toArray());
            var parsed = parser.split(text);
            System.out.printf("dialog %-12s prompt %3d  generated %2d  agrees with the oracle for %2d tokens;"
                            + " first-token top-3 logprob diff vs one-chunk prefill: chunks of 7 %.3f, of 11 %.3f (7 vs 11: %.3f; top-20 of 7: %.3f)%n",
                    d.get("name").asString(), prompt.length, gen.length, same, chunkDiff, chunkDiff2, chunkVsChunk, tail);
            System.out.println("    reasoning: " + parsed.reasoning().replace('\n', ' ')
                    + (parsed.content().isEmpty() ? "" : "\n    content: " + parsed.content().replace('\n', ' ')));
            assertTrue(same >= Math.min(8, oracleGen.length), "continuation diverges from the oracle at token " + same);
            // The first-token logprobs move by bf16 noise (up to ~1 nat in the tail); the rigorous chunking check is
            // the layer-by-layer test below.
            assertTrue(chunkDiff < 2.0 && chunkDiff2 < 2.0, "chunked prefill changes the top logprobs by " + chunkDiff);
        }
    }

    /**
     * Chunked prefill must give the same layer outputs as one pass: the convolution and attention caches carry the
     * history. Compared per layer application against the noise a different GEMM shape alone causes (a chunk of the
     * same length through the same path).
     */
    @Test
    public void testGivenChunkedPrefillWhenComparedLayerByLayerThenOnlyRoundingNoiseDiffers() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String oracle = System.getenv("INKLING_ORACLE");
        Assumptions.assumeTrue(ckpt != null && oracle != null && new File(oracle).exists(), "set INKLING_CKPT and INKLING_ORACLE");
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        Device device = Device.CUDA(Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        InklingModel model = InklingModel.load(ckpt, args, device);
        JsonNode r = MAPPER.readTree(new File(oracle)).get("results").get(0);
        List<Long> all = new ArrayList<>();
        r.get("prompt_ids").forEach(n -> all.add(n.asLong()));
        r.get("gen_ids").forEach(n -> all.add(n.asLong()));
        long[] tokens = all.stream().mapToLong(Long::longValue).toArray();
        float[][] whole = trace(model, tokens, 0);
        float[][] chunked = trace(model, tokens, 7);
        float[][] chunked2 = trace(model, tokens, 11);
        double worst = 0;
        for (int a = 0; a < whole.length; a++) {
            double d7 = relRms(whole[a], chunked[a]);
            double d11 = relRms(whole[a], chunked2[a]);
            worst = Math.max(worst, Math.max(d7, d11));
            if (a % 6 == 5 || a == whole.length - 1) {
                System.out.printf("  app %2d: rel rms vs one pass: chunks of 7 %.4f, of 11 %.4f; 7 vs 11 %.4f%n", a, d7, d11,
                        relRms(chunked[a], chunked2[a]));
            }
        }
        assertTrue(worst < 0.1, "chunked prefill drifts from the one-pass result: rel rms " + worst);
    }

    private static float[][] trace(InklingModel model, long[] tokens, int chunk) {
        int apps = model.args().numApplications();
        int dim = model.args().dim();
        float[][] out = new float[apps][tokens.length * dim];
        try (InklingState st = model.newState(tokens.length + 8)) {
            int pos = 0;
            while (pos < tokens.length) {
                int n = chunk <= 0 ? tokens.length : Math.min(chunk, tokens.length - pos);
                int[] app = {0};
                int from = pos;
                model.forward(st, java.util.Arrays.copyOfRange(tokens, pos, pos + n), null, t -> {
                    float[] rows = t.to(smile.deep.tensor.ScalarType.Float).floatArray();
                    System.arraycopy(rows, 0, out[app[0]++], from * dim, rows.length);
                });
                pos += n;
            }
        }
        return out;
    }

    private static double relRms(float[] a, float[] b) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            num += d * d;
            den += (double) b[i] * b[i];
        }
        return Math.sqrt(num / den);
    }
}
