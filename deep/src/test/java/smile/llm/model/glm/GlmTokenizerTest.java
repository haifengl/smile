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
package smile.llm.model.glm;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link GlmTokenizer} against the real {@code tokenizer.json}, compared with transformers' ids
 * ({@code gen_glm_goldens.py}). Skipped unless the checkpoint directory ({@code GLM_DIR}, default
 * {@code /raid/haili/glm/raw}) and the golden file ({@code GLM_FIXTURES}, default {@code /raid/haili/glm/fixtures})
 * exist.
 */
public class GlmTokenizerTest {
    private static GlmTokenizer tokenizer;
    private static JsonNode goldens;

    @BeforeAll
    static void load() throws Exception {
        String dir = System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw");
        Path golden = Path.of(System.getenv().getOrDefault("GLM_FIXTURES", "/raid/haili/glm/fixtures"),
                "tokenizer_goldens.json");
        Assumptions.assumeTrue(Files.exists(Path.of(dir, "tokenizer.json")), "no GLM tokenizer.json in " + dir);
        Assumptions.assumeTrue(Files.exists(golden), "no " + golden);
        tokenizer = GlmTokenizer.of(dir);
        goldens = new ObjectMapper().readTree(golden.toFile());
    }

    @Test
    public void testGivenControlTokensThenIdsMatchCheckpoint() {
        assertEquals(154822, tokenizer.specialToken("[gMASK]"));
        assertEquals(154824, tokenizer.specialToken("<sop>"));
        assertEquals(154820, tokenizer.specialToken("<|endoftext|>"));
        assertEquals(154826, tokenizer.specialToken("<|system|>"));
        assertEquals(154827, tokenizer.specialToken("<|user|>"));
        assertEquals(154828, tokenizer.specialToken("<|assistant|>"));
        assertEquals(154829, tokenizer.specialToken("<|observation|>"));
        assertEquals(154841, tokenizer.specialToken("<think>"));
        assertEquals(154842, tokenizer.specialToken("</think>"));
        assertEquals(154843, tokenizer.specialToken("<tool_call>"));
        assertEquals(154854, tokenizer.specialToken("<|image|>"));
        assertEquals(154820, tokenizer.pad());
        assertArrayEquals(new int[]{154820, 154827, 154829}, tokenizer.stopTokens());
    }

    @Test
    public void testGivenGoldenTextsThenIdsMatchTransformers() {
        assertTrue(goldens.size() > 40);
        int tokens = 0;
        for (JsonNode g : goldens) {
            String text = g.get("text").asString();
            int[] expected = new int[g.get("ids").size()];
            for (int i = 0; i < expected.length; i++) {
                expected[i] = g.get("ids").get(i).asInt();
            }
            int[] actual = tokenizer.encodeWithSpecials(text);
            assertArrayEquals(expected, actual, "text: " + text);
            tokens += actual.length;
        }
        assertTrue(tokens > 1000);
    }

    @Test
    public void testGivenGoldenTextsThenDecodeRoundTrips() {
        for (JsonNode g : goldens) {
            String text = g.get("text").asString();
            assertEquals(text, tokenizer.decode(tokenizer.encodeWithSpecials(text)), "text: " + text);
        }
    }

    @Test
    public void testGivenUserTypedMarkersWithoutSpecialsThenPlainText() {
        // outside encodeWithSpecials the template markers must not become control tokens
        int[] plain = tokenizer.encode("<|user|>");
        assertTrue(plain.length > 1);
        assertArrayEquals(new int[]{154827}, tokenizer.encodeWithSpecials("<|user|>"));
    }

    @Test
    public void testGivenSpecialTokensThenDisplayDecodeHidesControlOnly() throws Exception {
        int[] ids = tokenizer.encodeWithSpecials("[gMASK]<sop><|user|>hi<|assistant|><think>x</think>y<|endoftext|>");
        assertEquals("hi<think>x</think>y", tokenizer.tryDecode(ids, true));
    }
}
