/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.plot;

import java.awt.Color;
import javax.swing.JComponent;

/**
 * This is the abstract base class of plots.
 *
 * @author Haifeng Li
 */
public abstract class Plot extends Shape {
    /**
     * The id of plot.
     */
    private String id;

    /**
     * Constructor.
     */
    public Plot() {
    }

    /**
     * Constructor.
     */
    public Plot(String id) {
        this.id = id;
    }

    /**
     * Constructor.
     */
    public Plot(Color color) {
        super(color);
    }

    /**
     * Constructor.
     */
    public Plot(String id, Color color) {
        super(color);
        this.id = id;
    }

    /**
     * Set the id of plot.
     */
    public void setID(String id) {
        this.id = id;
    }

    /**
     * Returns the id of plot.
     */
    public String getID() {
        return id;
    }

    /**
     * Returns a optional tool tip for the object at given coordinates.
     * @param coord the logical coordinates of current mouse position.
     * @return a string if an object with label close to the given coordinates.
     * Otherwise null.
     */
    public String getToolTip(double[] coord) {
        return null;
    }
    
    /**
     * Returns an optional list of components in tool bar to control the plot.
     * @return an optional list of toolbar components, may be null.
     */
    public JComponent[] getToolBar() {
        return null;
    }
}
