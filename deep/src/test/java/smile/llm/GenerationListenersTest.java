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
package smile.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SubmissionPublisher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GenerationListeners}.
 */
public class GenerationListenersTest {

    @Test
    public void testGivenComposeWhenEventsThenFansOut() {
        List<String> events = new ArrayList<>();
        GenerationListener a = new GenerationListener() {
            @Override public void onInputTokens(int i, int count) { events.add("in:" + i + ":" + count); }
            @Override public void onCachedInputTokens(int i, int count) { events.add("cache:" + i + ":" + count); }
            @Override public void onGeneratedTokens(int i, int count) { events.add("gen:" + i + ":" + count); }
        };
        GenerationListener b = new GenerationListener() {
            @Override public void onThinkingTokens(int i, int count) { events.add("think:" + i + ":" + count); }
            @Override public void onText(int i, String chunk) { events.add("text:" + i + ":" + chunk); }
        };
        GenerationListener composed = GenerationListeners.compose(a, null, b);
        assertNotNull(composed);
        composed.onInputTokens(1, 10);
        composed.onCachedInputTokens(1, 4);
        composed.onGeneratedTokens(0, 3);
        composed.onThinkingTokens(0, 2);
        composed.onText(0, "hi");
        assertEquals(List.of(
                "in:1:10",
                "cache:1:4",
                "gen:0:3",
                "think:0:2",
                "text:0:hi"), events);
    }

    @Test
    public void testGivenToPublisherWhenOnTextThenSubmits() throws Exception {
        try (var publisher = new SubmissionPublisher<String>()) {
            List<String> received = new ArrayList<>();
            publisher.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
                    s.request(Long.MAX_VALUE);
                }
                @Override public void onNext(String item) { received.add(item); }
                @Override public void onError(Throwable t) {}
                @Override public void onComplete() {}
            });
            GenerationListeners.toPublisher(publisher).onText(0, "chunk");
            Thread.sleep(50);
            assertEquals(List.of("chunk"), received);
        }
    }
}
