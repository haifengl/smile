/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.plot.swing;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;

/**
 * Aids in creating swing components in a "headless" environment.
 * Useful for using swing components to export graphics to a file,
 * without requiring a connection to a display (with -Djava.awt.headless=true).
 *
 * From FreeHEP VectorGraphics project (https://github.com/freehep/freehep-vectorgraphics).
 *
 * @author Tony Johnson
 * @author Mark Donszelmann
 */
@SuppressWarnings("serial")
public class Headless extends JInternalFrame {

    public Headless(JComponent component) {
        setContentPane(component);
    }

    // Note, this must override the (deprecated) method show, not setVisible
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
