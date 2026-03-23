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
package smile.plot.swing;

import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;

/**
 * Aids in creating swing components in a "headless" environment.
 * Useful for using swing components to export graphics to a file,
 * without requiring a connection to a display (with -Djava.awt.headless=true).
 * <p>
 * From (<a href="https://github.com/freehep/freehep-vectorgraphics">FreeHEP VectorGraphics</a> project.
 *
 * @author Tony Johnson
 * @author Mark Donszelmann
 */
public class Headless extends JInternalFrame {
    /**
     * Constructor.
     * @param component the swing component.
     * @param width the width of component.
     * @param height the height of component.
     */
    public Headless(JComponent component, int width, int height) {
        component.setPreferredSize(new Dimension(width, height));
        setContentPane(component);
    }

    // Note, this must override the (deprecated) method show, not setVisible
    @Override
    public void show() {
        super.show();
        // Although the above calculates the size of the components, it does not lay them out.
        // For some reason frame.validate simply delegates to Container.validate(), which does nothing
        // if there is no peer defined.
        addNotify();
        synchronized(super.getTreeLock()) {
            super.validateTree();
        }
    }
}
