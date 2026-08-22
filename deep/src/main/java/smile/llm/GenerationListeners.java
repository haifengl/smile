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
import java.util.Objects;
import java.util.concurrent.SubmissionPublisher;

/**
 * Helpers for composing and adapting {@link GenerationListener}s.
 *
 * @author Haifeng Li
 */
public final class GenerationListeners {
    private GenerationListeners() {}

    /**
     * Forwards {@link GenerationListener#onText} to {@code publisher}.
     * Does not close the publisher.
     *
     * @param publisher destination for streamed text; must not be {@code null}.
     * @return a listener that only implements text forwarding.
     */
    public static GenerationListener toPublisher(SubmissionPublisher<String> publisher) {
        Objects.requireNonNull(publisher, "publisher");
        return new GenerationListener() {
            @Override
            public void onText(String chunk) {
                publisher.submit(chunk);
            }
        };
    }

    /**
     * Returns a single listener that fans out to every non-null argument.
     *
     * @param listeners listeners to compose; {@code null} entries are ignored.
     * @return a composite listener, or {@code null} when nothing to compose.
     */
    public static GenerationListener compose(GenerationListener... listeners) {
        if (listeners == null || listeners.length == 0) {
            return null;
        }
        List<GenerationListener> active = new ArrayList<>(listeners.length);
        for (GenerationListener listener : listeners) {
            if (listener != null) {
                active.add(listener);
            }
        }
        if (active.isEmpty()) {
            return null;
        }
        if (active.size() == 1) {
            return active.getFirst();
        }
        GenerationListener[] copy = active.toArray(GenerationListener[]::new);
        return new GenerationListener() {
            @Override
            public void onInputTokens(int count) {
                for (GenerationListener listener : copy) {
                    listener.onInputTokens(count);
                }
            }

            @Override
            public void onCachedInputTokens(int count) {
                for (GenerationListener listener : copy) {
                    listener.onCachedInputTokens(count);
                }
            }

            @Override
            public void onGeneratedTokens(int count) {
                for (GenerationListener listener : copy) {
                    listener.onGeneratedTokens(count);
                }
            }

            @Override
            public void onThinkingTokens(int count) {
                for (GenerationListener listener : copy) {
                    listener.onThinkingTokens(count);
                }
            }

            @Override
            public void onText(String chunk) {
                for (GenerationListener listener : copy) {
                    listener.onText(chunk);
                }
            }
        };
    }
}
