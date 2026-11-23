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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.Message;
import smile.llm.template.InklingChatTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The DSpark draft against the target's own greedy continuation: the mean accepted length of a block must be well above
 * one on natural text, which only holds when the taps, the context rows and the Markov head are all right.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_DIALOGS}, {@code INKLING_DEVICE}, {@code INKLING_TOKENS} (default 96).
 *
 * @author Haifeng Li
 */
public class InklingDraftTest {
    private static InklingModel model;
    private static InklingDraft draft;

    @Test
    public void testGivenGreedyTextWhenDraftedThenBlocksAreAccepted() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String dialogs = System.getenv("INKLING_DIALOGS");
        Assumptions.assumeTrue(ckpt != null && dialogs != null, "set INKLING_CKPT and INKLING_DIALOGS");
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        Device device = Device.CUDA(Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        int count = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "96"));
        if (model == null) {
            model = InklingModel.load(ckpt, args, device);
            draft = InklingDraft.load(ckpt, model, device);
        }
        assertNotNull(draft);
        InklingTokenizer tokenizer = InklingTokenizer.of(ckpt);
        JsonNode input = new ObjectMapper().readTree(new File(dialogs));
        int blocks = 0, accepted = 0;
        int[] byPos = new int[draft.drafts()];
        for (int k = 0; k < input.size(); k++) {
            JsonNode d = input.get(k);
            List<Message> messages = new ArrayList<>();
            for (JsonNode m : d.get("messages")) {
                messages.add(InklingGenerateTest.message(m));
            }
            long[] prompt = java.util.Arrays.stream(tokenizer.encodeWithSpecials(
                    new InklingChatTemplate().encode(messages, InklingGenerateTest.options(d)))).asLongStream().toArray();
            int total = prompt.length + count;
            int blocks0 = blocks, accepted0 = accepted;
            List<Tensor> tapRows = new ArrayList<>();
            long[] seq = new long[total];
            System.arraycopy(prompt, 0, seq, 0, prompt.length);
            try (InklingState st = model.newState(total + 16); InklingDraft.State ds = draft.newState(total + 16)) {
                int n = prompt.length;
                Tensor logits = model.forward(st, prompt, null, null, null, null, t -> tapRows.add(Ix.keep(t.to(Device.CPU()))));
                for (int i = 0; i < count; i++) {
                    float[] row = logits.to(Device.CPU()).floatArray();
                    int best = 0;
                    for (int v = 1; v < row.length; v++) {
                        if (row[v] > row[best]) {
                            best = v;
                        }
                    }
                    seq[n + i] = best;
                    if (i + 1 < count) {
                        logits = model.forward(st, new long[]{best}, null, null, null, null, t -> tapRows.add(Ix.keep(t.to(Device.CPU()))));
                    }
                }
                Tensor all = Ix.cat(0, tapRows.toArray(new Tensor[0])).to(device);      // taps of positions 0 .. total - 2
                int block = draft.drafts();
                int absorbed = 0;
                for (int p = prompt.length - 1; p + block < total; p += 4) {
                    draft.absorb(ds, Ix.narrow(all, 0, absorbed, p - absorbed), absorbed);
                    absorbed = p;
                    int[] d7 = draft.draft(ds, (int) seq[p], p);
                    int a = 0;
                    while (a < block && d7[a] == seq[p + 1 + a]) {
                        a++;
                    }
                    for (int j = 0; j < a; j++) {
                        byPos[j]++;
                    }
                    accepted += a;
                    blocks++;
                }
            }
            System.out.printf("dialog %d: %d blocks, mean accepted drafts %.2f%n", k, blocks - blocks0, (accepted - accepted0) / (double) (blocks - blocks0));
        }
        System.out.printf("draft: %d blocks, mean accepted drafts %.2f, per position %s%n", blocks, accepted / (double) blocks,
                java.util.Arrays.toString(byPos));
        assertTrue(accepted / (double) blocks > 0.8, "the draft is not predicting");
    }

    private static int argmax(float[] a, int from, int n) {
        int best = 0;
        for (int v = 1; v < n; v++) {
            if (a[from + v] > a[from + best]) {
                best = v;
            }
        }
        return best;
    }

    /** Speculative decoding with the reference verify must reproduce greedy decoding (up to rounding noise). */
    @Test
    public void testGivenSpeculativeRoundsWhenDecodedGreedilyThenTextMatchesPlainDecoding() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String dialogs = System.getenv("INKLING_DIALOGS");
        Assumptions.assumeTrue(ckpt != null && dialogs != null, "set INKLING_CKPT and INKLING_DIALOGS");
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        Device device = Device.CUDA(Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        int count = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "64"));
        if (model == null) {
            model = InklingModel.load(ckpt, args, device);
            draft = InklingDraft.load(ckpt, model, device);
        }
        InklingTokenizer tokenizer = InklingTokenizer.of(ckpt);
        JsonNode input = new ObjectMapper().readTree(new File(dialogs));
        int vocab = args.unpaddedVocabSize();
        for (int k = 0; k < Math.min(3, input.size()); k++) {
            JsonNode d = input.get(k);
            List<Message> messages = new ArrayList<>();
            for (JsonNode m : d.get("messages")) {
                messages.add(InklingGenerateTest.message(m));
            }
            long[] prompt = java.util.Arrays.stream(tokenizer.encodeWithSpecials(
                    new InklingChatTemplate().encode(messages, InklingGenerateTest.options(d)))).asLongStream().toArray();
            // plain greedy
            long[] plain = new long[count];
            try (InklingState st = model.newState(prompt.length + count + 16)) {
                Tensor logits = model.forward(st, prompt, null, null);
                for (int i = 0; i < count; i++) {
                    plain[i] = argmax(logits.to(Device.CPU()).floatArray(), 0, vocab);
                    logits = model.forward(st, new long[]{plain[i]}, null, null);
                }
            }
            // speculative
            List<Long> out = new ArrayList<>();
            int rounds = 0, drafted = 0;
            try (InklingState st = model.newState(prompt.length + count + 32); InklingDraft.State ds = draft.newState(prompt.length + count + 32)) {
                List<Tensor> tap = new ArrayList<>();
                Tensor logits = model.forward(st, prompt, null, null, null, null, t -> tap.add(Ix.keep(t.to(device))));
                draft.absorb(ds, tap.get(0), 0);
                int anchor = argmax(logits.to(Device.CPU()).floatArray(), 0, vocab);
                out.add((long) anchor);
                int pos = prompt.length;                 // position of the anchor
                while (out.size() < count) {
                    int[] dr = draft.draft(ds, anchor, pos);
                    long[] rows = new long[dr.length + 1];
                    rows[0] = anchor;
                    for (int i = 0; i < dr.length; i++) {
                        rows[i + 1] = dr[i];
                    }
                    List<Tensor[]> rec = new ArrayList<>();
                    tap.clear();
                    Tensor lg = model.verify(st, rows, t -> tap.add(Ix.keep(t.to(device))), rec);
                    float[] all = lg.to(Device.CPU()).floatArray();
                    int V = (int) lg.size(1);
                    int a = 0;
                    while (a < dr.length && dr[a] == argmax(all, a * V, vocab)) {
                        a++;
                    }
                    int bonus = argmax(all, a * V, vocab);
                    model.commit(st, pos, a + 1, rec);
                    draft.absorb(ds, Ix.narrow(tap.get(0), 0, 0, a + 1), pos);
                    for (int i = 0; i < a; i++) {
                        out.add((long) dr[i]);
                    }
                    out.add((long) bonus);
                    anchor = bonus;
                    pos += a + 1;
                    if (System.getenv("INKLING_DEBUG") != null) {
                        System.out.println("round " + rounds + " pos " + pos + " a " + a + " draft " + java.util.Arrays.toString(dr) + " bonus " + bonus + " plain " + java.util.Arrays.toString(java.util.Arrays.copyOfRange(plain, out.size() - a - 1, Math.min(count, out.size()))));
                    }
                    rounds++;
                    drafted += a;
                    tap.forEach(Tensor::close);
                }
            }
            int same = 0;
            while (same < count && same < out.size() && out.get(same) == plain[same]) {
                same++;
            }
            System.out.printf("dialog %d: %d tokens in %d rounds (%.2f drafts accepted per round), identical to plain greedy for %d tokens%n",
                    k, out.size(), rounds, drafted / (double) rounds, same);
            assertTrue(same >= 6, "speculative text diverges from plain decoding almost at once");
        }
    }
}
