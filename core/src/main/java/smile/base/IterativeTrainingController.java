/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.base;

import java.util.concurrent.*;

/**
 * A controller for iterative training algorithms.
 * @param <T> the type of training status objects.
 */
public class IterativeTrainingController<T> implements AutoCloseable {
    /** Flag if early stopping the training. */
    private boolean interrupted;
    /** Training progress publisher. */
    private final SubmissionPublisher<T> publisher;

    /**
     * Constructor.
     */
    public IterativeTrainingController() {
        this(Executors.newFixedThreadPool(1), 2048);
    }

    /**
     * Constructor.
     * @param executor the executor to use for async delivery, supporting
     *                 creation of at least one independent thread.
     * @param maxBufferCapacity the maximum capacity for each subscriber's buffer.
     */
    public IterativeTrainingController(Executor executor, int maxBufferCapacity) {
        interrupted = false;
        publisher = new SubmissionPublisher<>(executor, maxBufferCapacity);
    }

    @Override
    public void close() {
        publisher.close();
    }

    /**
     * Checks if keep training going.
     * @return true if keep training going; false to early stop.
     */
    public final boolean isInterrupted() {
        return interrupted;
    }

    /**
     * Early stops the training.
     */
    public void stop() {
        interrupted = true;
    }

    /**
     * Adds the given Subscriber for training progress.
     * @param subscriber the subscriber.
     */
    public void subscribe(Flow.Subscriber<T> subscriber) {
        publisher.subscribe(subscriber);
    }

    /**
     * Publishes the training status to each current subscriber asynchronously.
     * @param status the training progress information.
     */
    public void submit(T status) {
        publisher.submit(status);
    }
}
