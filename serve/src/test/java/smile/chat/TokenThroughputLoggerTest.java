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
        var meter = new TokenThroughputLogger(40, (requestId, rate, tokens, seconds) ->
                lines.add(String.format("id=%d %.1f tok/s (%d in %.2fs)",
                        requestId, rate, tokens, seconds)));
        meter.setRequestId(7);

        meter.onGeneratedTokens(2);
        Thread.sleep(50);
        meter.onGeneratedTokens(2); // closes first window
        assertEquals(1, lines.size());
        assertTrue(lines.getFirst().contains("id=7"));
        assertTrue(lines.getFirst().contains("tok/s"));

        meter.onGeneratedTokens(1);
        meter.finish(); // flushes remaining token
        assertEquals(2, lines.size());
    }

    @Test
    public void testGivenNoTokensWhenFinishThenSilent() {
        List<String> lines = new ArrayList<>();
        var meter = new TokenThroughputLogger(40, (requestId, rate, tokens, seconds) -> lines.add("x"));
        meter.finish();
        assertTrue(lines.isEmpty());
    }

    @Test
    public void testGivenAggregateWhenTokensThenFeedsSharedMeter() {
        List<String> aggregateLines = new ArrayList<>();
        var aggregate = new AggregateTokenThroughput(10_000,
                (rate, tokens, seconds, active, meanCache, meanGen) ->
                        aggregateLines.add(String.format("%d toks active=%d cache=%.0f gen=%.0f",
                                tokens, active, meanCache, meanGen)));
        var a = new TokenThroughputLogger(10_000, aggregate, (id, rate, tokens, seconds) -> {});
        var b = new TokenThroughputLogger(10_000, aggregate, (id, rate, tokens, seconds) -> {});
        a.setRequestId(1);
        b.setRequestId(2);
        a.onInputTokens(26);
        b.onInputTokens(26);
        a.onGeneratedTokens(3);
        b.onGeneratedTokens(5);
        assertEquals(8, aggregate.currentWindowTokens());
        a.finish();
        assertEquals(8, aggregate.currentWindowTokens()); // still one active
        b.finish();
        assertEquals(0, aggregate.currentWindowTokens());
        assertEquals(1, aggregateLines.size());
        assertTrue(aggregateLines.getFirst().contains("8 toks"));
        // Token-weighted: (29*3 + 31*5) / 8 = 30.25 → "cache=30"
        assertTrue(aggregateLines.getFirst().contains("cache=30"));
        assertTrue(aggregateLines.getFirst().contains("gen=4")); // (3*3 + 5*5) / 8 = 4.25
    }
}
