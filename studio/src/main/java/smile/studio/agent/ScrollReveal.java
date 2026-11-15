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
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Objects;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * Computes where a scroll pane must sit to show a given component, keeping the
 * component's top edge inside the viewport.
 * <p>Built for the question box. A question is placed in the transcript above a
 * fresh, empty output area, so pinning the pane to the bottom would push the
 * box's header above the fold and leave only a slice visible. A box taller than
 * the viewport has no position that shows all of it; its top edge -- the titled
 * header that says what is being asked -- is the part worth keeping.
 * <p>This class only decides the position. The owner applies it, so the
 * scrollbar bookkeeping in {@link AutoScroller} stays in one place.
 *
 * @author Haifeng Li
 */
final class ScrollReveal {
    /** The scroll pane whose view holds the target. */
    private final JScrollPane scroll;
    /** Called when the target's bounds settle, so the owner can recompute. */
    private final Runnable onBoundsChanged;
    /** The component to show, or null when no reveal is pending. */
    private Component target;

    /**
     * Constructor.
     *
     * @param scroll the scroll pane to search for a position in.
     * @param onBoundsChanged invoked on the EDT when the target moves or resizes.
     */
    ScrollReveal(JScrollPane scroll, Runnable onBoundsChanged) {
        this.scroll = Objects.requireNonNull(scroll, "scroll");
        this.onBoundsChanged = Objects.requireNonNull(onBoundsChanged, "onBoundsChanged");
    }

    /**
     * Returns the component to show, or null when no reveal is pending.
     * @return the reveal target.
     */
    Component target() {
        return target;
    }

    /**
     * Starts revealing a component, replacing any previous target.
     * <p>The target is watched for bounds changes: an added widget has no size
     * or offset until the panel lays it out, and that layout is the first moment
     * a position can be computed.
     *
     * @param component the component to show.
     */
    void start(Component component) {
        if (component == null) {
            stop();
            return;
        }
        if (component == target) {
            return;
        }
        stop();
        target = component;
        component.addComponentListener(boundsWatcher);
    }

    /** Stops revealing the current target, if any. */
    void stop() {
        if (target != null) {
            target.removeComponentListener(boundsWatcher);
            target = null;
        }
    }

    /**
     * Returns the scrollbar value that shows the target while keeping its top
     * edge on screen, or -1 when the pane should not move.
     * <p>-1 means one of: no target, a target that is not (or no longer) in the
     * scrolled view, a pane that has not been laid out yet, an unlaid-out
     * target, or a target already fully in view.
     *
     * @return the value to scroll to, or -1 to stay put.
     */
    int position() {
        JViewport viewport = scroll.getViewport();
        if (viewport == null || target == null) {
            return -1;
        }
        Component view = viewport.getView();
        if (view == null || target.getParent() == null
                || !SwingUtilities.isDescendingFrom(target, view)) {
            // The widget was replaced or belongs to another pane; nothing to show.
            return -1;
        }
        int extent = viewport.getExtentSize().height;
        if (extent <= 0) {
            // The pane has not been laid out; a later pass will have a viewport.
            return -1;
        }
        Rectangle rect = SwingUtilities.convertRectangle(
                target.getParent(), target.getBounds(), view);
        if (rect.height <= 0) {
            // Added but not laid out; its offset is still wrong. The bounds
            // listener restarts the reveal once it has real bounds.
            return -1;
        }
        JScrollBar bar = scroll.getVerticalScrollBar();
        int value = bar.getValue();
        int top = rect.y;
        int bottom = rect.y + rect.height;
        if (top >= value && bottom <= value + extent) {
            // Already fully in view; moving would only drag it around.
            return -1;
        }
        // Bottom-align a box that fits the viewport, top-align one that does not.
        // The minimum of the two keeps the header on screen either way.
        int preferred = Math.min(top, bottom - extent);
        int max = bar.getMaximum() - bar.getVisibleAmount() + bar.getMinimum();
        int next = Math.max(bar.getMinimum(), Math.min(preferred, max));
        return next == value ? -1 : next;
    }

    /** Recomputes the position after the target's bounds settle. */
    private final ComponentAdapter boundsWatcher = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
            onBoundsChanged.run();
        }

        @Override
        public void componentMoved(ComponentEvent event) {
            onBoundsChanged.run();
        }
    };
}
