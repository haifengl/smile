package smile.plot;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;

/**
 * From FreeHEP VectorGraphics project (https://github.com/freehep/freehep-vectorgraphics).
 *
 * A class which aids in creating swing components in a "headless" environment.
 * Useful for using swing components to export graphics to a file, without requiring
 * a connection to a display (i.e. with -Djava.awt.headless=true).
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