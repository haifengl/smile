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
package smile.llm.model.deepseek;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link DeepSeekEngramTokenMap}. Skipped unless a checkpoint directory with {@code tokenizer.json} is given by
 * {@code DEEPSEEK_DIR} (default {@code /raid/haili/dsv4/flash_raw}).
 */
public class DeepSeekEngramTokenMapTest {
    @Test
    public void testGivenReleasedTokenizerThenCompressedVocabIs99092() throws Exception {
        Path json = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"), "tokenizer.json");
        Assumptions.assumeTrue(Files.exists(json), "no tokenizer.json at " + json);
        var map = DeepSeekEngramTokenMap.load(json, 129280);
        // The engram hash multipliers derive from this number, and checkpoint config.json states it.
        assertEquals(99092, map.size());
        // Spot values; the whole table was also compared with an independent stdlib Python implementation.
        assertEquals(0, map.map(0));
        assertEquals(1, map.map(1));
        assertEquals(2, map.map(2));
        assertEquals(15844, map.map(19923));
        assertEquals(1751, map.map(2058));
        assertEquals(98615, map.map(128803));
        assertEquals(99091, map.map(129279));
    }

    @Test
    public void testGivenCaseAccentAndSpaceVariantsThenSameClass() {
        assertEquals(DeepSeekEngramTokenMap.normalize("The"), DeepSeekEngramTokenMap.normalize(" the"));
        assertEquals("cafe", DeepSeekEngramTokenMap.normalize("Café"));
        assertEquals(" ", DeepSeekEngramTokenMap.normalize(" "), "a lone space survives the strip");
        assertEquals(" ", DeepSeekEngramTokenMap.normalize("\n\t "));
        assertEquals("ab", DeepSeekEngramTokenMap.normalize("  AB \n"));
        assertEquals("क", DeepSeekEngramTokenMap.normalize("कि"), "spacing marks (Mc) are removed too");
    }
}
