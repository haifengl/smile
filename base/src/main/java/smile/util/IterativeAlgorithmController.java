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
package smile.util;

import java.util.concurrent.*;

/**
 * A controller for iterative algorithms.
 * @param <T> the type of algorithm progress status objects.
 *
 * @author Karl Li
 */
public class IterativeAlgorithmController<T> implements AutoCloseable {
    /** Flag if early stopping the algorithm. */
    private boolean interrupted;
    /** Algorithm progress publisher. */
    private final SubmissionPublisher<T> publisher;

    /**
     * Constructor.
     */
    public IterativeAlgorithmController() {
        this(Executors.newFixedThreadPool(1), 2048);
    }

    /**
     * Constructor.
     * @param executor the executor to use for async delivery, supporting
     *                 creation of at least one independent thread.
     * @param maxBufferCapacity the maximum capacity for each subscriber's buffer.
     */
    public IterativeAlgorithmController(Executor executor, int maxBufferCapacity) {
        interrupted = false;
        publisher = new SubmissionPublisher<>(executor, maxBufferCapacity);
    }

    @Override
    public void close() {
        publisher.close();
    }

    /**
     * Checks if keep algorithm going.
     * @return true if keep algorithm going; false to early stop.
     */
    public final boolean isInterrupted() {
        return interrupted;
    }

    /**
     * Early stops the algorithm.
     */
    public void stop() {
        interrupted = true;
    }

    /**
     * Adds the given subscriber for algorithm progress.
     * @param subscriber the subscriber.
     */
    public void subscribe(Flow.Subscriber<T> subscriber) {
        publisher.subscribe(subscriber);
    }

    /**
     * Publishes the algorithm status to each current subscriber asynchronously.
     * @param status the algorithm progress information.
     */
    public void submit(T status) {
        publisher.submit(status);
    }
}
