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
    @Test
    public void testGivenGreedyTextWhenDraftedThenBlocksAreAccepted() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String dialogs = System.getenv("INKLING_DIALOGS");
        Assumptions.assumeTrue(ckpt != null && dialogs != null, "set INKLING_CKPT and INKLING_DIALOGS");
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        Device device = Device.CUDA(Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        int count = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "96"));
        InklingModel model = InklingModel.load(ckpt, args, device);
        InklingDraft draft = InklingDraft.load(ckpt, model, device);
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
        }
        System.out.printf("draft: %d blocks, mean accepted drafts %.2f, per position %s%n", blocks, accepted / (double) blocks,
                java.util.Arrays.toString(byPos));
        assertTrue(accepted / (double) blocks > 0.8, "the draft is not predicting");
    }
}
