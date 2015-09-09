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
import java.awt.Font;

/**
 * This is specialized label for axis labels. Coordinates used here are are
 * proportional to the base coordinates.
 *
 * @author Haifeng Li
 */
class BaseLabel extends Label {

    /**
     * Constructor.
     */
    public BaseLabel(String text, double horizontalReference, double verticalReference, double... coord) {
        super(text, horizontalReference, verticalReference, 0.0, coord);
    }

    /**
     * Constructor.
     */
    public BaseLabel(String text, double horizontalReference, double verticalReference, double rotation, double... coord) {
        super(text, horizontalReference, verticalReference, rotation, coord);
    }

    @Override
    public void paint(Graphics g) {
        Font f = g.getFont();
        if (font != null) {
            g.setFont(font);
        }

        Color c = g.getColor();
        g.setColor(getColor());

        g.drawTextBaseRatio(text, horizontalReference, verticalReference, rotation, coord);

        g.setColor(c);
        if (font != null) {
            g.setFont(f);
        }
    }
}