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
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
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

    /**
     * A real scroll pane holding a column of fixed-height blocks, the way the
     * transcript holds output areas and the question box. The blocks are sized
     * explicitly so the layout produces exact offsets to assert against.
     * <p>A scroll pane that was never realized in a window does not size its view
     * on a plain {@code doLayout()}, so the fixture lays the view out itself and
     * reports the size to the viewport, the way a realized pane would. Without
     * that the blocks would all sit at offset zero and the assertions would be
     * meaningless.
     */
    private static final class Column {
        final JPanel view = new JPanel();
        final JScrollPane scroll;
        final AutoScroller scroller;

        Column(int viewportHeight) {
            view.setLayout(new BoxLayout(view, BoxLayout.Y_AXIS));
            scroll = new JScrollPane(view);
            scroll.setSize(300, viewportHeight);
            scroller = new AutoScroller(scroll);
            layout();
        }

        /** Appends a block of the given height and lays the pane out. */
        JPanel block(int height) {
            JPanel block = new JPanel();
            Dimension size = new Dimension(200, height);
            block.setPreferredSize(size);
            block.setMinimumSize(size);
            block.setMaximumSize(size);
            view.add(block);
            layout();
            return block;
        }

        /** Sizes the view to its preferred height and lays it out, as a realized pane would. */
        void layout() {
            view.revalidate();
            // The viewport must know its extent before the view can be sized to it.
            scroll.doLayout();
            int width = scroll.getViewport().getExtentSize().width;
            view.setSize(new Dimension(Math.max(1, width), view.getPreferredSize().height));
            view.doLayout();
            scroll.getViewport().setViewSize(view.getSize());
            // A pane that was never realized reports the new view size through the
            // scrollbar on a later pass. Setting the model here makes the extent and
            // maximum exact now, so the assertions do not depend on dispatch timing.
            JScrollBar bar = scroll.getVerticalScrollBar();
            int extent = scroll.getViewport().getExtentSize().height;
            bar.setValues(bar.getValue(), extent, 0, Math.max(extent, view.getHeight()));
        }

        JScrollBar bar() {
            return scroll.getVerticalScrollBar();
        }

        int extent() {
            return scroll.getViewport().getExtentSize().height;
        }

        /** The block's vertical offset in the scrolled view. */
        int topOf(Component block) {
            return SwingUtilities.convertRectangle(block.getParent(), block.getBounds(), view).y;
        }

        int heightOf(Component block) {
            return block.getHeight();
        }

        void flush() {
            try {
                for (int i = 0; i < 6; i++) {
                    SwingUtilities.invokeAndWait(() -> { });
                }
                // The deferred passes run on the EDT; relay out afterwards so the
                // scrollbar sees the final view height.
                SwingUtilities.invokeAndWait(this::layout);
            } catch (Exception ex) {
                throw new AssertionError("EDT flush failed", ex);
            }
        }
    }

    @Test
    void reveal_showsAWholeBoxThatFitsTheViewport() {
        // Given a long transcript with the box near its end and the view at the bottom
        Column c = new Column(VIEWPORT_HEIGHT);
        for (int i = 0; i < 10; i++) {
            c.block(100);
        }
        var box = c.block(60);
        c.block(100);
        c.flush();
        assertTrue(c.bar().getMaximum() > c.extent(), "the content must overflow the viewport");

        // When the box is revealed
        c.scroller.reveal(box);
        c.flush();

        // Then the whole box is on screen, not clipped to a slice
        int top = c.topOf(box);
        int value = c.bar().getValue();
        assertTrue(top >= value, "the box top must not sit above the viewport");
        assertTrue(top + c.heightOf(box) <= value + c.extent(),
                "the whole box must be inside the viewport");
    }

    @Test
    void reveal_keepsTheHeaderOfABoxTallerThanTheViewportOnScreen() {
        // Given the view at the bottom of a long transcript
        Column c = new Column(VIEWPORT_HEIGHT);
        for (int i = 0; i < 10; i++) {
            c.block(100);
        }
        // And a box taller than the viewport, so no scroll position can show all of it
        var box = c.block(400);
        c.block(100);
        c.flush();
        assertTrue(c.heightOf(box) > c.extent());

        // When the box is revealed
        c.scroller.reveal(box);
        c.flush();

        // Then the view stops at the box's top, keeping its header visible
        int top = c.topOf(box);
        assertEquals(top, c.bar().getValue(),
                "a box taller than the viewport must be top-aligned, not bottom-aligned");
    }

    @Test
    void reveal_followsTheBoxDownEvenWhenTheUserHadScrolledUp() {
        // Given the user scrolled to the very top of a long transcript
        Column c = new Column(VIEWPORT_HEIGHT);
        for (int i = 0; i < 10; i++) {
            c.block(100);
        }
        var box = c.block(60);
        c.block(100);
        // Settle at the bottom first, then scroll up the way the user reading history would
        c.flush();
        c.bar().setValue(c.bar().getValue() / 2);
        c.flush();
        assertFalse(c.scroller.isFollowing());

        // When the agent asks a question, the box must come into view anyway
        c.scroller.reveal(box);
        c.flush();

        // Then the box is on screen
        int top = c.topOf(box);
        int value = c.bar().getValue();
        assertTrue(top >= value && top + c.heightOf(box) <= value + c.extent(),
                "a revealed box must be visible even after the user scrolled away");
    }

    @Test
    void reveal_stopsFollowingUntilTheQuestionIsAnswered() {
        // Given a revealed box
        Column c = new Column(VIEWPORT_HEIGHT);
        for (int i = 0; i < 10; i++) {
            c.block(100);
        }
        var box = c.block(60);
        c.scroller.reveal(box);
        c.flush();
        assertFalse(c.scroller.isFollowing(), "the view must hold still while the user answers");

        // When content grows before the user answers (the custom-input area opening)
        c.block(40);
        c.flush();

        // Then the view stays on the box rather than jumping to the new bottom
        int top = c.topOf(box);
        int value = c.bar().getValue();
        assertTrue(top >= value && top + c.heightOf(box) <= value + c.extent(),
                "growth while a question is open must not pull the box out of view");

        // When the question is answered, the pane follows the turn's output again
        c.scroller.resumeFollowingAfter(box);
        c.flush();
        assertTrue(c.scroller.isFollowing());
        assertEquals(c.bar().getMaximum() - c.bar().getVisibleAmount() + c.bar().getMinimum(),
                c.bar().getValue(), "answering must resume following to the bottom");
    }

    @Test
    void answeringAnEarlierQuestion_doesNotPullTheViewOffTheNextBox() {
        // Given a revealed question the agent is waiting on
        Column c = new Column(VIEWPORT_HEIGHT);
        for (int i = 0; i < 10; i++) {
            c.block(100);
        }
        var first = c.block(60);
        c.scroller.reveal(first);
        c.flush();

        // And the agent asks a second question, which replaces the first as the target
        var second = c.block(60);
        c.block(100);
        c.scroller.reveal(second);
        c.flush();

        // When the first answer arrives, after the second box is already on screen
        c.scroller.resumeFollowingAfter(first);
        c.flush();

        // Then the view stays on the second box instead of jumping to the bottom
        assertFalse(c.scroller.isFollowing(),
                "a stale answer must not re-arm following while another question is open");
        int top = c.topOf(second);
        int value = c.bar().getValue();
        assertTrue(top >= value && top + c.heightOf(second) <= value + c.extent(),
                "the box the user is still reading must stay in view");

        // And answering the second one does resume following
        c.scroller.resumeFollowingAfter(second);
        c.flush();
        assertTrue(c.scroller.isFollowing());
    }
}
