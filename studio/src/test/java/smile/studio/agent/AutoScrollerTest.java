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

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the sticky-bottom behavior of streamed output.
 * <p>The fixture drives the scrollbar's model directly. That is the same event
 * stream a real layout produces when an output area grows -- the maximum
 * changes while the value stays put -- without the layout passes and clamping
 * that make a real scroll pane nondeterministic in a unit test.
 *
 * @author Haifeng Li
 */
@Timeout(10)
class AutoScrollerTest {
    /** The viewport height, i.e. the visible amount the scrollbar reports. */
    private static final int VIEWPORT_HEIGHT = 120;

    /** A scroll pane whose vertical scrollbar the test drives. */
    private static final class Fixture {
        final JScrollPane scroll = new JScrollPane(new JPanel());
        final AutoScroller scroller;

        Fixture() {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setModel(new javax.swing.DefaultBoundedRangeModel(0, VIEWPORT_HEIGHT, 0, VIEWPORT_HEIGHT));
            scroller = new AutoScroller(scroll);
        }

        /** Grows the content as a streamed line would, then runs the deferred scroll. */
        void grow(int pixels) {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setMaximum(bar.getMaximum() + pixels);
            flush();
        }

        /** Moves the viewport to a value, as the user dragging the bar would, then drains the EDT. */
        void scrollTo(int value) {
            scroll.getVerticalScrollBar().setValue(value);
            flush();
        }

        /** Drains the EDT so the deferred scroll and any echo of it both run. */
        void flush() {
            try {
                for (int i = 0; i < 4; i++) {
                    SwingUtilities.invokeAndWait(() -> { });
                }
            } catch (Exception ex) {
                throw new AssertionError("EDT flush failed", ex);
            }
        }

        JScrollBar bar() {
            return scroll.getVerticalScrollBar();
        }

        /** Returns the largest value the bar can take, i.e. the bottom of the content. */
        int bottom() {
            return bar().getMaximum() - bar().getVisibleAmount() + bar().getMinimum();
        }

        boolean atBottom() {
            return bar().getValue() >= bottom();
        }
    }

    @Test
    void followsStreamedContentToTheBottom() {
        // Given a viewport already showing all of a short transcript
        Fixture f = new Fixture();
        assertTrue(f.scroller.isFollowing());

        // When the content grows past the viewport
        f.grow(600);

        // Then the view is pinned to the newest line
        assertTrue(f.bar().getMaximum() > f.bar().getVisibleAmount(),
                "the fixture must overflow the viewport for this test to mean anything");
        assertTrue(f.scroller.isFollowing(), "streaming content must keep following");
        assertTrue(f.atBottom(), "the view must stay at the bottom as output streams in");
    }

    @Test
    void userScrollUpStopsFollowing_soReadingEarlierOutputIsNotInterrupted() {
        // Given output streaming at the bottom
        Fixture f = new Fixture();
        f.grow(600);
        assertTrue(f.atBottom());

        // When the user scrolls up to re-read
        f.scrollTo(f.bottom() / 2);

        // Then following stops, and new output does not yank the view down
        assertFalse(f.scroller.isFollowing(), "scrolling up must stop the auto-follow");
        int parked = f.bar().getValue();
        f.grow(200);
        assertEquals(parked, f.bar().getValue(),
                "growing content must not pull a scrolled-up view to the bottom");
    }

    @Test
    void scrollingBackToTheBottomResumesFollowing() {
        // Given the user scrolled up and stopped the follow
        Fixture f = new Fixture();
        f.grow(600);
        f.scrollTo(f.bottom() / 2);
        assertFalse(f.scroller.isFollowing());

        // When the user scrolls back to the bottom
        f.scrollTo(f.bottom());

        // Then following resumes
        assertTrue(f.scroller.isFollowing(), "reaching the bottom must re-arm the follow");
        f.grow(200);
        assertTrue(f.atBottom(), "output must follow again once the user returns to the bottom");
    }

    @Test
    void followReArmsAfterAUserScroll() {
        // Given the user scrolled to the top and stopped the follow
        Fixture f = new Fixture();
        f.grow(600);
        f.scrollTo(0);
        assertFalse(f.scroller.isFollowing());

        // When a new turn starts (the panel explicitly re-arms following)
        f.scroller.follow();

        // Then new output is followed again
        assertTrue(f.scroller.isFollowing());
        f.grow(200);
        assertTrue(f.atBottom());
    }

    @Test
    void realScrollPane_followsTextAppendedByLayout() throws Exception {
        // Guards the assumption the helper rests on: a real scroll pane reports the
        // growth of its content through the scrollbar model, with max and extent
        // moving while the value stays put. The model-driven fixture above would
        // keep passing even if layout stopped reporting growth.
        var area = new javax.swing.JTextArea(4, 20);
        var scroll = new JScrollPane(area);
        var scroller = new AutoScroller(scroll);

        int[] before = new int[2];
        int[] after = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            scroll.setSize(200, 100);
            scroll.doLayout();
            JScrollBar bar = scroll.getVerticalScrollBar();
            before[0] = bar.getMaximum();
            before[1] = bar.getValue();
            for (int i = 0; i < 80; i++) {
                area.append("streamed line " + i + "\n");
            }
            scroll.doLayout();
            after[0] = bar.getMaximum();
            after[1] = bar.getValue();
        });

        assertTrue(after[0] > before[0],
                "appending text must grow the scrollbar maximum");
        assertEquals(before[1], after[1], "content growth alone must not move the value");

        // Drain the EDT so the growth-triggered scroll runs.
        for (int i = 0; i < 4; i++) {
            SwingUtilities.invokeAndWait(() -> { });
        }
        SwingUtilities.invokeAndWait(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            assertEquals(bar.getMaximum() - bar.getVisibleAmount(), bar.getValue(),
                    "the helper must pin a real scroll pane to the appended text");
        });
    }
}
