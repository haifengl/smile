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
 * This is specialized line for axis lines. Coordinates used here are are
 * proportional to the base coordinates.
 * @author Haifeng Li
 */
class BaseLine extends Line {

    /**
     * Constructor.
     * @param start the start point of the line.
     * @param end   the end point of the line.
     */
    public BaseLine(double[] start, double[] end) {
        super(start, end);
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());
        g.drawLineBaseRatio(points);
        g.setColor(c);
    }
}
