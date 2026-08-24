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
package smile.llm.engine;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import smile.llm.ChatCompletion;

/**
 * Handle for an in-flight or queued {@link GenerationRequest}.
 *
 * @author Haifeng Li
 */
public final class GenerationHandle {
    private final long requestId;
    private final CompletableFuture<ChatCompletion> future;
    private final AtomicBoolean aborted = new AtomicBoolean(false);

    GenerationHandle(long requestId, CompletableFuture<ChatCompletion> future) {
        this.requestId = requestId;
        this.future = future;
    }

    /**
     * Creates a handle for a future owned outside {@link InferenceEngine}
     * (e.g. serve fallback when no engine is present).
     */
    public static GenerationHandle of(long requestId, CompletableFuture<ChatCompletion> future) {
        return new GenerationHandle(requestId, Objects.requireNonNull(future, "future"));
    }

    /** Engine-assigned request id. */
    public long requestId() {
        return requestId;
    }

    /** Completion of the generation (exceptionally if aborted or failed). */
    public CompletableFuture<ChatCompletion> future() {
        return future;
    }

    /**
     * Requests cancellation. Queued jobs are dropped (Instant Eviction from the
     * wait queue). Running jobs stop cooperatively between decode steps when
     * the model sees {@link #isAborted()} (at most one forward after abort).
     */
    public void abort() {
        if (aborted.compareAndSet(false, true)) {
            future.cancel(true);
        }
    }

    /** {@code true} after {@link #abort()}. */
    public boolean isAborted() {
        return aborted.get();
    }
}
