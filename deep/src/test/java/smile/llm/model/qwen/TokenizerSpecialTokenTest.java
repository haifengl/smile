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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import smile.util.Bytes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures Qwen chat specials reuse HF vocab ids instead of appending past
 * {@code vocab_size} (which triggers CUDA embedding gather OOB).
 */
public class TokenizerSpecialTokenTest {

    /** Copies a classpath resource to a real file (works inside JARs). */
    private static Path copyResource(String classpath) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                TokenizerSpecialTokenTest.class.getResourceAsStream(classpath), classpath)) {
            Path tmp = Files.createTempFile("qwen-tok-", ".json");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            return tmp;
        }
    }

    @Test
    public void testGivenSpecialsAlreadyInVocabWhenResolvingThenIdsReuseVocabEntries() {
        // Given: HF-style vocab where chat specials already have assigned ids
        Map<Bytes, Integer> ranks = new HashMap<>();
        ranks.put(utf8("a"), 0);
        ranks.put(utf8("<|endoftext|>"), 100);
        ranks.put(utf8("<|im_start|>"), 101);
        ranks.put(utf8("<|im_end|>"), 102);

        // Limit specials so defaultSpecialTokens() does not inflate size()
        Tokenizer tokenizer = new Tokenizer(ranks,
                "<|endoftext|>", "<|endoftext|>",
                "<|endoftext|>", "<|im_start|>", "<|im_end|>");

        // Then: specials keep vocab ids (not ranks.size()+i past the embedding table)
        assertEquals(101, tokenizer.specialToken("<|im_start|>").intValue());
        assertEquals(102, tokenizer.specialToken("<|im_end|>").intValue());
        assertEquals(100, tokenizer.pad());
        assertTrue(tokenizer.specialToken("<|im_start|>") < tokenizer.size());
        assertTrue(tokenizer.specialToken("<|im_end|>") < tokenizer.size());
        assertEquals(103, tokenizer.size());
    }

    @Test
    public void testGivenMissingSpecialWhenConstructingThenAppendsAfterMaxId() {
        Map<Bytes, Integer> ranks = new HashMap<>();
        ranks.put(utf8("x"), 0);
        ranks.put(utf8("<|endoftext|>"), 5);

        Tokenizer tokenizer = new Tokenizer(ranks,
                "<|endoftext|>", "<|endoftext|>",
                "<|endoftext|>", "<|im_start|>", "<|im_end|>");

        assertEquals(5, tokenizer.specialToken("<|endoftext|>").intValue());
        assertEquals(6, tokenizer.specialToken("<|im_start|>").intValue());
        assertEquals(7, tokenizer.specialToken("<|im_end|>").intValue());
        assertEquals(8, tokenizer.size());
    }

    @Test
    public void testGivenTokenizerJsonWithAddedTokensWhenLoadingThenSpecialsResolve() throws Exception {
        Path json = copyResource("/qwen/tokenizer_added_tokens.json");
        var ranks = Tokenizer.loadTokenizerJson(json);
        Tokenizer tokenizer = new Tokenizer(ranks,
                "<|endoftext|>", "<|endoftext|>",
                "<|endoftext|>", "<|im_start|>", "<|im_end|>");
        assertEquals(10, tokenizer.specialToken("<|endoftext|>").intValue());
        assertEquals(11, tokenizer.specialToken("<|im_start|>").intValue());
        assertEquals(12, tokenizer.specialToken("<|im_end|>").intValue());
        // max added id is 12 → decoder length 13 when specials reuse vocab ids
        assertEquals(13, tokenizer.size());
        tokenizer.requireChatSpecialsInVocab(13);
    }

    @Test
    public void testGivenSpecialOutOfVocabWhenRequireChatSpecialsThenThrows() {
        Map<Bytes, Integer> ranks = new HashMap<>();
        ranks.put(utf8("x"), 0);
        // endoftext present; im_* missing → assigned past maxId
        Tokenizer tokenizer = new Tokenizer(ranks,
                "<|endoftext|>", "<|endoftext|>",
                "<|endoftext|>", "<|im_start|>", "<|im_end|>");
        assertThrows(IllegalStateException.class,
                () -> tokenizer.requireChatSpecialsInVocab(1));
    }

    private static Bytes utf8(String s) {
        return new Bytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
