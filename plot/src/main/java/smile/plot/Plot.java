/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

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
    public Plot setID(String id) {
        this.id = id;
        return this;
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
