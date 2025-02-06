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

import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * A controller for iterative training algorithms.
 */
public class IterativeTrainingController {
    /** Flag if early stopping the training. */
    private boolean interrupted;
    /** Training progress publisher. */
    private final SubmissionPublisher<Map<String, Object>> publisher;

    /**
     * Constructor.
     */
    public IterativeTrainingController() {
        interrupted = false;
        publisher = new SubmissionPublisher<>();
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
    public void subscribe(Flow.Subscriber<Map<String, Object>> subscriber) {
        publisher.subscribe(subscriber);
    }

    /**
     * Publishes the training status to each current subscriber asynchronously.
     * @param status the training progress information.
     */
    public void submit(Map<String, Object> status) {
        publisher.submit(status);
    }
}
