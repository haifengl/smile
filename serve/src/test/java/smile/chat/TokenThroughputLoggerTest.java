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
package smile.chat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TokenThroughputLogger}.
 */
public class TokenThroughputLoggerTest {

    @Test
    public void testGivenTokensSpanningIntervalWhenReportedThenEmitsWindowRate() throws Exception {
        List<String> lines = new ArrayList<>();
        var meter = new TokenThroughputLogger(40, (rate, tokens, seconds) ->
                lines.add(String.format("%.1f tok/s (%d in %.2fs)", rate, tokens, seconds)));

        meter.onGeneratedTokens(0, 2);
        Thread.sleep(50);
        meter.onGeneratedTokens(0, 2); // closes first window
        assertEquals(1, lines.size());
        assertTrue(lines.getFirst().contains("tok/s"));

        meter.onGeneratedTokens(0, 1);
        meter.finish(); // flushes remaining token
        assertEquals(2, lines.size());
    }

    @Test
    public void testGivenNoTokensWhenFinishThenSilent() {
        List<String> lines = new ArrayList<>();
        var meter = new TokenThroughputLogger(40, (rate, tokens, seconds) -> lines.add("x"));
        meter.finish();
        assertTrue(lines.isEmpty());
    }
}
