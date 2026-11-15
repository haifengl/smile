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

import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the position computed to show a question box.
 * <p>Only the geometry lives here; {@link AutoScroller} applies the value and is
 * covered by its own tests. The fixture lays the view out by hand because a
 * scroll pane that was never realized in a window does not size its view on a
 * plain {@code doLayout()}.
 *
 * @author Haifeng Li
 */
@Timeout(10)
class ScrollRevealTest {
    /** The visible height the fixture's viewport reports. */
    private static final int VIEWPORT_HEIGHT = 120;

    /** A scroll pane holding a column of fixed-height blocks. */
    private static final class Column {
        final JPanel view = new JPanel();
        final JScrollPane scroll = new JScrollPane(view);
        final AtomicInteger boundsEvents = new AtomicInteger();
        final ScrollReveal reveal;

        Column() {
            view.setLayout(new BoxLayout(view, BoxLayout.Y_AXIS));
            scroll.setSize(300, VIEWPORT_HEIGHT);
            reveal = new ScrollReveal(scroll, boundsEvents::incrementAndGet);
            layout();
        }

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
            int extent = scroll.getViewport().getExtentSize().height;
            int max = Math.max(extent, view.getHeight());
            scroll.getVerticalScrollBar().setModel(
                    new DefaultBoundedRangeModel(0, extent, 0, max));
        }

        int extent() {
            return scroll.getViewport().getExtentSize().height;
        }

        int bottom() {
            var bar = scroll.getVerticalScrollBar();
            return bar.getMaximum() - bar.getVisibleAmount() + bar.getMinimum();
        }

        int value() {
            return scroll.getVerticalScrollBar().getValue();
        }

        void scrollTo(int value) {
            scroll.getVerticalScrollBar().setValue(value);
        }
    }

    @Test
    void positionShowsAWholeBoxThatFitsTheViewport() {
        // Given the view at the bottom of a transcript with the box near its end
        Column c = new Column();
        for (int i = 0; i < 10; i++) {
            c.block(100);
        }
        var box = c.block(60);
        c.block(100);
        c.scrollTo(c.bottom());

        // When a position is computed for the box
        c.reveal.start(box);
        int position = c.reveal.position();

        // Then it moves the view up just enough to show the whole box
        assertTrue(position >= 0, "the box must need a scroll");
        assertTrue(position < c.bottom(), "the view must leave the bottom to show the box");
        int top = box.getY();
        assertTrue(position <= top, "the box top must be at or below the viewport top");
        assertTrue(top + box.getHeight() <= position + c.extent(),
                "the whole box must fit inside the viewport");
    }

    @Test
    void positionKeepsTheHeaderOfABoxTallerThanTheViewportOnScreen() {
        // Given a box taller than the viewport, so no position can show all of it
        Column c = new Column();
        for (int i = 0; i < 10; i++) {
            c.block(100);
        }
        var box = c.block(400);
        c.block(100);
        c.scrollTo(c.bottom());
        assertTrue(box.getHeight() > c.extent());

        // When a position is computed for it
        c.reveal.start(box);
        int position = c.reveal.position();

        // Then the view stops at the box's top, keeping its header visible
        assertEquals(box.getY(), position,
                "a box taller than the viewport must be top-aligned, not bottom-aligned");
    }

    @Test
    void positionIsNegativeWhenTheBoxIsAlreadyFullyInView() {
        // Given a short transcript that does not overflow, with the box visible
        Column c = new Column();
        c.block(40);
        var box = c.block(40);
        c.block(40);

        // When a position is computed
        c.reveal.start(box);

        // Then the pane is left alone
        assertEquals(-1, c.reveal.position(), "an already-visible box must not move the view");
    }

    @Test
    void positionIsNegativeWithoutATargetOrWithAStaleOne() {
        // Given no reveal in progress
        Column c = new Column();
        c.block(400);
        assertEquals(-1, c.reveal.position(), "there is nothing to show");

        // And a target that was removed from the pane
        var box = c.block(40);
        c.reveal.start(box);
        c.view.remove(box);

        // Then the position is refused rather than computed from stale bounds
        assertEquals(-1, c.reveal.position());
    }

    @Test
    void startReplacesThePreviousTargetAndReportsBoundsChanges() throws Exception {
        // Given a reveal of one box
        Column c = new Column();
        c.block(400);
        var first = c.block(40);
        c.reveal.start(first);
        assertSame(first, c.reveal.target());

        // When a second box is revealed
        var second = c.block(40);
        c.reveal.start(second);

        // Then the second replaces the first
        assertSame(second, c.reveal.target());

        // And resizing the watched box reports a bounds change, so the owner can recompute
        int before = c.boundsEvents.get();
        SwingUtilities.invokeAndWait(() -> {
            second.setSize(200, 200);
            second.dispatchEvent(new java.awt.event.ComponentEvent(
                    second, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        });
        assertTrue(c.boundsEvents.get() > before,
                "a resized target must ask the owner to recompute its position");
    }

    @Test
    void stopNoLongerReportsBoundsChanges() throws Exception {
        // Given a reveal that was stopped, as it is once the question is answered
        Column c = new Column();
        c.block(400);
        var box = c.block(40);
        c.reveal.start(box);
        c.reveal.stop();
        assertNull(c.reveal.target());

        // When the box changes bounds
        int before = c.boundsEvents.get();
        SwingUtilities.invokeAndWait(() -> box.dispatchEvent(new java.awt.event.ComponentEvent(
                box, java.awt.event.ComponentEvent.COMPONENT_MOVED)));

        // Then nothing is recomputed
        assertEquals(before, c.boundsEvents.get());
    }
}
