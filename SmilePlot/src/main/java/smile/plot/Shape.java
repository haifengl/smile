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

/**
 * Abstract rendering object in a PlotCanvas.
 *
 * @author Haifeng Li
 */
public abstract class Shape {

    /**
     * The color of the shape. By default, it is black.
     */
    private Color color = Color.BLACK;

    /**
     * Constructor.
     */
    public Shape() {
    }

    /**
     * Constructor.
     */
    public Shape(Color color) {
        this.color = color;
    }

    /**
     * Set the color of component.
     */
    public Shape setColor(Color color) {
        this.color = color;
        return this;
    }

    /**
     * Returns the color of component.
     */
    public Color getColor() {
        return color;
    }
    
    /**
     * Draw the component with given graphics object.
     * @param painter
     */
    public abstract void paint(Graphics painter);
}
