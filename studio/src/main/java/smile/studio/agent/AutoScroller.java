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

import java.awt.Component;
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
 * <p>A question widget is a special case, handled by {@link ScrollReveal}: the
 * pane moves to show the box instead of the bottom, and holds still while the
 * user answers. {@link #reveal(Component)} starts that; {@link #follow()} and
 * {@link #resumeFollowingAfter(Component)} end it.
 *
 * @author Haifeng Li
 */
final class AutoScroller {
    /** How many deferred passes a reveal makes while the widget waits for layout. */
    private static final int REVEAL_ATTEMPTS = 3;
    /** The scroll pane to keep at the bottom. */
    private final JScrollPane scroll;
    /** Where the pane must sit to show a pending question box, or null when none is pending. */
    private final ScrollReveal reveal;
    /** Whether new content should pull the view to the bottom. */
    private boolean follow = true;
    /** Guards the deferred scroll so a fast stream queues one scroll, not one per token. */
    private boolean pending;
    /** The last scrollbar value seen, to tell a moved view from content growth. */
    private int lastValue;
    /** Set while this class moves the bar, so its own scroll is not read as a user scroll. */
    private boolean scrolling;
    /** Deferred passes left for the reveal, so a bottom-align queued by the same layout cannot undo it. */
    private int revealPasses;

    /**
     * Constructor.
     * @param scroll the scroll pane to keep at the bottom.
     */
    AutoScroller(JScrollPane scroll) {
        this.scroll = Objects.requireNonNull(scroll, "scroll");
        this.reveal = new ScrollReveal(scroll, this::onRevealBoundsChanged);
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
        if (reveal.target() != null) {
            // A question is on screen: growth must not pull the view off the box
            // before the user answers, whether or not they scrolled.
            return;
        }
        follow = isAtBottom();
    }

    /**
     * Re-arms following after it was stopped by a user scroll. Called when a new
     * turn starts or a transcript is replaced, so its output comes into view.
     * Also ends any pending reveal: the question it belonged to is answered.
     */
    void follow() {
        reveal.stop();
        follow = true;
    }

    /**
     * Brings a component into view, keeping its top edge inside the viewport.
     * <p>Used for the question box, which sits above a fresh, empty output area;
     * following to the bottom would push the box's header above the fold. See
     * {@link ScrollReveal} for the geometry.
     * <p>The scroll is deferred and repeated for a few passes. Adding the box
     * grows the panel, and the layout that follows reports the growth through the
     * scrollbar model, which queues a scroll to the bottom; running after that
     * scroll, not before it, is what makes the reveal stick.
     *
     * @param target the component to show.
     */
    void reveal(Component target) {
        if (target == null) {
            return;
        }
        // The box is what the agent is waiting on, so show it even if the user had
        // scrolled up to read. Stop following meanwhile so the box's own layout
        // (the custom-input area opening, say) cannot pull the view off it.
        follow = false;
        revealPasses = REVEAL_ATTEMPTS;
        reveal.start(target);
        scheduleScroll();
    }

    /**
     * Returns whether the pane currently follows the bottom.
     * @return true when new content pulls the view down.
     */
    boolean isFollowing() {
        return follow;
    }

    /**
     * Resumes following after the revealed question was answered. While the box
     * was on screen the pane held still; once the answer is in, the turn's
     * output is what the user wants to see again.
     * <p>A no-op when {@code answered} is not the box currently revealed. An
     * agent can ask several questions in a row, and the earlier answer is
     * completed around the time the next box is added: resuming then would yank
     * the view to the bottom of a box the user is still reading.
     *
     * @param answered the box whose answer arrived.
     */
    void resumeFollowingAfter(Component answered) {
        if (answered == null || reveal.target() != answered) {
            return;
        }
        follow();
        scheduleScroll();
    }

    /**
     * Pins the pane to the bottom on a later EDT pass, so the pending layout has
     * already grown the scrollbar's maximum. Repeated calls before the pass runs
     * are coalesced into one scroll. A no-op once the user has scrolled away.
     */
    void scrollToBottom() {
        if (!follow) {
            return;
        }
        scheduleScroll();
    }

    /**
     * Queues one deferred scroll pass. A pass shows the pending reveal target, or
     * otherwise pins the view to the bottom. Repeated calls are coalesced.
     */
    private void scheduleScroll() {
        if (pending) {
            return;
        }
        pending = true;
        SwingUtilities.invokeLater(() -> {
            pending = false;
            if (reveal.target() != null) {
                scrollToReveal();
                // A bottom-align queued by the layout that followed the reveal may
                // still be behind this pass; repeat so the reveal outlasts it. The
                // target stays watched either way, so a later layout that moves the
                // box reveals it again instead of leaving it half-hidden.
                if (revealPasses > 0) {
                    revealPasses--;
                    scheduleScroll();
                }
            } else if (follow) {
                toBottom();
            }
        });
    }

    /** Restarts the reveal after the target's bounds settle. */
    private void onRevealBoundsChanged() {
        if (reveal.target() != null) {
            revealPasses = REVEAL_ATTEMPTS;
            scheduleScroll();
        }
    }

    /** Moves the bar to the revealed component's position. Must run on the EDT. */
    private void scrollToReveal() {
        int next = reveal.position();
        if (next >= 0) {
            setValue(next);
        }
    }

    /** Scrolls to the bottom now. Must run on the EDT. */
    private void toBottom() {
        JScrollBar bar = scroll.getVerticalScrollBar();
        int bottom = bar.getMaximum() - bar.getVisibleAmount() + bar.getMinimum();
        if (bar.getValue() != bottom) {
            setValue(bottom);
        } else {
            lastValue = bar.getValue();
        }
    }

    /** Moves the bar without letting the model listener read it as a user scroll. */
    private void setValue(int value) {
        JScrollBar bar = scroll.getVerticalScrollBar();
        scrolling = true;
        try {
            bar.setValue(value);
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
