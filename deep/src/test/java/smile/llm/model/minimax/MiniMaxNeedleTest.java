/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.minimax;

import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in ({@code MINIMAX_MODEL_DIR}, {@code MINIMAX_DEVICES}): greedy answers to needle-in-a-haystack questions about a long
 * list of facts, which only the block-sparse attention can retrieve from far away (checks the selection and attention kernels
 * on a real model end to end). {@code MM_NEEDLE_FACTS} (default 150) sets the number of facts (about 25 tokens each).
 */
public class MiniMaxNeedleTest {
    @Test
    public void testPrintsAnswersToRetrievalQuestions() throws Exception {
        String dir = System.getenv("MINIMAX_MODEL_DIR");
        String devList = System.getenv("MINIMAX_DEVICES");
        assumeTrue(dir != null && devList != null, "MINIMAX_MODEL_DIR / MINIMAX_DEVICES not set");
        String[] ds = devList.split(",");
        byte[] devices = new byte[ds.length];
        for (int i = 0; i < ds.length; i++) {
            devices[i] = Byte.parseByte(ds[i].trim());
        }
        int facts = Integer.parseInt(System.getenv().getOrDefault("MM_NEEDLE_FACTS", "150"));
        MiniMaxTokenizer tok = MiniMaxTokenizer.of(dir);
        StringBuilder doc = new StringBuilder("Here is a list of station records.\n");
        for (int i = 1; i <= facts; i++) {
            doc.append("Record ").append(i).append(": the access code of station ").append(i).append(" is ")
                    .append((i * 7919) % 9973).append(" and its keeper is named Keeper").append(i * 3).append(".\n");
        }
        int[] questions = {facts / 7, facts / 2, facts - 3, 5};
        try (MiniMax m = MiniMax.build(dir, 2, 16384, devices, 0.85, 16, null, null, false)) {
            int right = 0;
            for (int q : questions) {
                String prompt = doc + "\nQuestion: what is the access code of station " + q + "?\nAnswer: the access code of station " + q + " is";
                int[] ids = tok.encode(prompt, true, false);
                ChatCompletion c = m.generate(ids, 8, 0.0, 1.0, false, 0, null);
                String text = tok.tryDecode(c.completionTokens(), true);
                String want = String.valueOf((q * 7919) % 9973);
                boolean ok = text.contains(want);
                right += ok ? 1 : 0;
                System.out.println("NEEDLE prompt " + ids.length + " tokens, station " + q + ": expected " + want + ", got '"
                        + text.replace('\n', ' ') + "' " + (ok ? "OK" : "WRONG"));
            }
            System.out.println("NEEDLE correct " + right + "/" + questions.length);
        }
    }
}
