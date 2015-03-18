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
package smile.swing;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;

/**
 * An Icon wrapper that paints the contained icon with a specified transparency.
 * <P>
 * <B>Note:</B> This class is not suitable for wrapping an
 * <CODE>ImageIcon</CODE> that holds an animated image.
 */
public class AlphaIcon implements Icon {

    private Icon icon;
    private float alpha;

    /**
     * Creates an
     * <CODE>AlphaIcon</CODE> with the specified icon and opacity. The opacity
     * <CODE>alpha</CODE> should be in the range 0.0F (fully transparent) to
     * 1.0F (fully opaque).
     *
     * @param icon the Icon to wrap
     * @param alpha the opacity
     */
    public AlphaIcon(Icon icon, float alpha) {
        this.icon = icon;
        this.alpha = alpha;
    }

    /**
     * Gets this
     * <CODE>AlphaIcon</CODE>'s opacity
     *
     * @return the opacity, in the range 0.0 to 1.0
     */
    public float getAlpha() {
        return alpha;
    }

    /**
     * Gets the icon wrapped by this
     * <CODE>AlphaIcon</CODE>
     *
     * @return the wrapped icon
     */
    public Icon getIcon() {
        return icon;
    }

    /**
     * Paints the wrapped icon with this
     * <CODE>AlphaIcon</CODE>'s transparency.
     *
     * @param c The component to which the icon is painted
     * @param g the graphics context
     * @param x the X coordinate of the icon's top-left corner
     * @param y the Y coordinate of the icon's top-left corner
     */
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcAtop.derive(alpha));
        icon.paintIcon(c, g2, x, y);
        g2.dispose();
    }

    /**
     * Gets the width of the bounding rectangle of this
     * <CODE>AlphaIcon</CODE>. Returns the width of the wrapped icon.
     *
     * @return the width in pixels
     */
    @Override
    public int getIconWidth() {
        return icon.getIconWidth();
    }

    /**
     * Gets the height of the bounding rectangle of this
     * <CODE>AlphaIcon</CODE>. * Returns the height of the wrapped icon.
     *
     * @return the height in pixels
     */
    @Override
    public int getIconHeight() {
        return icon.getIconHeight();
    }
}
