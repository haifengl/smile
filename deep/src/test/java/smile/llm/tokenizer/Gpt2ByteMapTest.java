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
package smile.llm.tokenizer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.qwen.Tokenizer;
import smile.util.Bytes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * GPT-2 byte map + HF vocab loading so BPE never emits {@link Integer#MAX_VALUE}.
 */
public class Gpt2ByteMapTest {

    @Test
    public void testGivenAsciiWhenMappedThenIdentity() {
        assertEquals('A', Gpt2ByteMap.byteToUnicode('A'));
        assertEquals(new Bytes("A"), Gpt2ByteMap.vocabTokenToBytes("A"));
    }

    @Test
    public void testGivenHfStyleVocabWhenEncodingThenNoMaxToken() throws Exception {
        // Simulate HF vocab: keys are GPT-2 unicode chars for each raw byte.
        Map<Bytes, Integer> ranks = new HashMap<>();
        String user = "user";
        byte[] raw = user.getBytes(StandardCharsets.UTF_8);
        StringBuilder mapped = new StringBuilder();
        for (byte b : raw) {
            mapped.append(Gpt2ByteMap.byteToUnicode(b & 0xff));
        }
        // Whole-word token plus per-byte fallbacks (as in a real BPE vocab).
        ranks.put(Gpt2ByteMap.vocabTokenToBytes(mapped.toString()), 42);
        for (byte b : raw) {
            String ch = String.valueOf(Gpt2ByteMap.byteToUnicode(b & 0xff));
            ranks.putIfAbsent(Gpt2ByteMap.vocabTokenToBytes(ch), (int) (b & 0xff));
        }
        ranks.put(new Bytes("<|endoftext|>"), 100);
        ranks.put(new Bytes("<|im_start|>"), 101);
        ranks.put(new Bytes("<|im_end|>"), 102);
        ranks.put(Gpt2ByteMap.vocabTokenToBytes("\n"), 10);
        ranks.put(Gpt2ByteMap.vocabTokenToBytes("a"), (int) 'a');
        ranks.put(Gpt2ByteMap.vocabTokenToBytes("s"), (int) 's');
        ranks.put(Gpt2ByteMap.vocabTokenToBytes("i"), (int) 'i');
        ranks.put(Gpt2ByteMap.vocabTokenToBytes("t"), (int) 't');
        ranks.put(Gpt2ByteMap.vocabTokenToBytes("n"), (int) 'n');

        Tokenizer tokenizer = new Tokenizer(ranks);
        int[] ids = tokenizer.encode("user", false, false);
        assertTrue(ids.length >= 1);
        for (int id : ids) {
            assertFalse(id == Integer.MAX_VALUE, "BPE must not emit MAX");
            assertTrue(id >= 0 && id < tokenizer.size());
        }
        assertEquals(42, ids[0]); // whole-word hit
    }

    @Test
    public void testGivenTokenizerJsonResourceWhenEncodingDialogThenIdsInRange() throws Exception {
        Path json = Path.of(getClass().getResource("/qwen/tokenizer_bpe_sample.json").toURI());
        Path dir = Files.createTempDirectory("qwen-tok-");
        Files.copy(json, dir.resolve("tokenizer.json"));
        Tokenizer tokenizer = Tokenizer.of(dir.toString());
        tokenizer.requireChatSpecialsInVocab(tokenizer.size());
        int[] ids = tokenizer.encodeDialog(new Message(Role.user, "hi"));
        for (int id : ids) {
            assertTrue(id >= 0 && id < tokenizer.size(), "id=" + id);
            assertFalse(id == Integer.MAX_VALUE);
        }
    }
}
