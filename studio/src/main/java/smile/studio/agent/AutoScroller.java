/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.agent;

import java.util.Objects;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Keeps a scroll pane pinned to the bottom while output streams in, so the
 * latest tokens stay visible as they arrive.
 * <p>A conversation still has to be readable while it streams: once the user
 * scrolls up to re-read an earlier answer, dragging the view back down on the
 * next token makes the pane unusable. The scroller therefore follows only while
 * the view is at the bottom -- the user scrolling up stops it, and scrolling
 * back down (or reaching the bottom again) re-arms it.
 * <p>The scrollbar's model reports every change with one listener, and the
 * events carry different meaning. Comparing the value against the one last seen
 * separates them:
 * <ul>
 *   <li>A <em>value</em> change means the view moved: the user dragged the bar,
 *       the wheel scrolled it, or a previous scroll landed at the bottom. That
 *       is the signal to re-evaluate the follow state.</li>
 *   <li>The value unchanged while the maximum or extent grew means content
 *       streamed in or the pane was resized. That is the signal to follow. Read
 *       as a scroll instead, the first line that overflows the viewport would
 *       stop the follow it is supposed to start.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
final class AutoScroller {
    /** The scroll pane to keep at the bottom. */
    private final JScrollPane scroll;
    /** Whether new content should pull the view to the bottom. */
    private boolean follow = true;
    /** Guards the deferred scroll so a fast stream queues one scroll, not one per token. */
    private boolean pending;
    /** The last scrollbar value seen, to tell a moved view from content growth. */
    private int lastValue;
    /** Set while this class moves the bar, so its own scroll is not read as a user scroll. */
    private boolean scrolling;

    /**
     * Constructor.
     * @param scroll the scroll pane to keep at the bottom.
     */
    AutoScroller(JScrollPane scroll) {
        this.scroll = Objects.requireNonNull(scroll, "scroll");
        JScrollBar bar = scroll.getVerticalScrollBar();
        this.lastValue = bar.getValue();
        bar.getModel().addChangeListener(event -> onModelChanged());
    }

    /** Re-evaluates the follow state after the scrollbar's model changed. */
    private void onModelChanged() {
        if (scrolling) {
            return;
        }
        int value = scroll.getVerticalScrollBar().getValue();
        if (value == lastValue) {
            // The value did not move: content grew or the pane was resized.
            scrollToBottom();
            return;
        }
        lastValue = value;
        follow = isAtBottom();
    }

    /**
     * Re-arms following after it was stopped by a user scroll. Called when a new
     * turn starts or a transcript is replaced, so its output comes into view.
     */
    void follow() {
        follow = true;
    }

    /**
     * Returns whether the pane currently follows the bottom.
     * @return true when new content pulls the view down.
     */
    boolean isFollowing() {
        return follow;
    }

    /**
     * Pins the pane to the bottom on a later EDT pass, so the pending layout has
     * already grown the scrollbar's maximum. Repeated calls before the pass runs
     * are coalesced into one scroll. A no-op once the user has scrolled away.
     */
    void scrollToBottom() {
        if (!follow || pending) {
            return;
        }
        pending = true;
        SwingUtilities.invokeLater(() -> {
            pending = false;
            if (follow) {
                toBottom();
            }
        });
    }

    /** Scrolls to the bottom now. Must run on the EDT. */
    private void toBottom() {
        JScrollBar bar = scroll.getVerticalScrollBar();
        if (isAtBottom()) {
            lastValue = bar.getValue();
            return;
        }
        int bottom = bar.getMaximum() - bar.getVisibleAmount() + bar.getMinimum();
        scrolling = true;
        try {
            bar.setValue(bottom);
            lastValue = bar.getValue();
        } finally {
            scrolling = false;
        }
    }

    /** Returns true when the view shows the end of the content. */
    private boolean isAtBottom() {
        JScrollBar bar = scroll.getVerticalScrollBar();
        int bottom = bar.getMaximum() - bar.getVisibleAmount() + bar.getMinimum();
        return bar.getValue() >= bottom;
    }
}
