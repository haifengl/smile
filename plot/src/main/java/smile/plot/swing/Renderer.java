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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;

/**
 * Renderer provides methods to draw graphical primitives in logical/mathematical
 * coordinates. The mathematical coordinates are translated into Java2D
 * coordinates based on suitable projection method. Both 2D and 3D shapes are
 * supported.
 *
 * @author Haifeng Li
 */
public class Renderer {

    /**
     * Projection used to map logical/mathematical coordinates to Java2D
     * coordinates.
     */
    private final Projection projection;
    /**
     * Java2D graphics object to render shapes.
     */
    private Graphics2D g2d;
    /**
     * Original clip shape associated with g2d.
     */
    private java.awt.Shape clip;

    /**
     * Constructor.
     * @param projection the projection.
     */
    public Renderer(Projection projection) {
        this.projection = projection;
    }

    /**
     * Returns the projection object.
     * @return the projection object.
     */
    public Projection projection() {
        return projection;
    }

    /**
     * Returns the Java2D graphics object.
     * @return the Java2D graphics object.
     */
    public Graphics2D getGraphics() {
        return g2d;
    }

    /**
     * Sets the Java2D graphics object.
     * @param g2d Java2D graphics.
     */
    public void setGraphics(Graphics2D g2d) {
        this.g2d = g2d;
        // antialiasing methods
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /**
     * Returns the lower bounds of coordinate space.
     * @return the lower bounds of coordinate space.
     */
    public double[] getLowerBound() {
        return projection.figure.base.lowerBound;
    }

    /**
     * Returns the upper bounds of coordinate space.
     * @return the upper bounds of coordinate space.
     */
    public double[] getUpperBound() {
        return projection.figure.base.upperBound;
    }

    /**
     * Gets the current font.
     * @return the font.
     */
    public Font getFont() {
        return g2d.getFont();
    }

    /**
     * Sets the font.
     * @param font the font.
     * @return this object.
     */
    public Renderer setFont(Font font) {
        g2d.setFont(font);
        return this;
    }

    /**
     * Gets the current color.
     * @return the color.
     */
    public Color getColor() {
        return g2d.getColor();
    }

    /**
     * Sets the color.
     * @param color the color.
     * @return this object.
     */
    public Renderer setColor(Color color) {
        g2d.setColor(color);
        return this;
    }

    /**
     * Gets the current paint object.
     * @return the paint.
     */
    public Paint getPaint() {
        return g2d.getPaint();
    }

    /**
     * Sets the paint object.
     * @param paint the paint.
     * @return this object.
     */
    public Renderer setPaint(Paint paint) {
        g2d.setPaint(paint);
        return this;
    }

    /**
     * Gets the current stroke.
     * @return the stroke.
     */
    public Stroke getStroke() {
        return g2d.getStroke();
    }

    /**
     * Sets the stroke.
     * @param stroke the stroke.
     * @return this object.
     */
    public Renderer setStroke(Stroke stroke) {
        g2d.setStroke(stroke);
        return this;
    }

    /**
     * Restricts the draw area to the valid base coordinate space.
     */
    public void clip() {
        int x = (int) (projection.width() * projection.figure.margin);
        int y = (int) (projection.height() * projection.figure.margin);
        int w = (int) (projection.width() * (1 - 2 * projection.figure.margin));
        int h = (int) (projection.height() * (1 - 2 * projection.figure.margin));
        clip = g2d.getClip();
        g2d.clipRect(x, y, w, h);
    }

    /**
     * Clears the restriction of the draw area.
     */
    public void clearClip() {
        g2d.setClip(clip);
        clip = null;
    }

    /**
     * Draws a string. Reference point is the center of string. The coordinates
     * are logical coordinates.
     * @param text the text.
     * @param coord the text coordinates.
     */
    public void drawText(String text, double[] coord) {
        drawText(text, coord, 0.5, 0.5, 0.0);
    }

    /**
     * Draws a string with given rotation angle. Reference point is the center
     * of string. The coordinates are logical coordinates. The angle of rotation
     * is in radians.
     * @param text the text.
     * @param coord the text coordinates.
     * @param rotation the rotation angel of text.
     */
    public void drawText(String text, double[] coord, double rotation) {
        drawText(text, coord, 0.5, 0.5, rotation);
    }

    /**
     * Draws a string with given reference point. (0.5, 0.5) is center, (0, 0) is
     * lower left, (0, 1) is upper left, etc. The coordinates are logical coordinates.
     * @param text the text.
     * @param coord the text coordinates.
     * @param horizontalReference the horizontal reference position of coordinates respected to dimension of text.
     * @param verticalReference the vertical reference position of coordinates respected to dimension of text.
     */
    public void drawText(String text, double[] coord, double horizontalReference, double verticalReference) {
        drawText(text, coord, horizontalReference, verticalReference, 0.0);
    }

    /**
     * Draws a string with given reference point and rotation angle. (0.5, 0.5)
     * is center, (0, 0) is lower left, (0, 1) is upper left, etc. The angle of
     * rotation is in radians. The coordinates are logical coordinates.
     * @param text the text.
     * @param coord the text coordinates.
     * @param horizontalReference the horizontal reference position of coordinates respected to dimension of text.
     * @param verticalReference the vertical reference position of coordinates respected to dimension of text.
     * @param rotation the rotation angel of text.
     */
    public void drawText(String text, double[] coord, double horizontalReference, double verticalReference, double rotation) {
        int[] sc = projection.screenProjection(coord);
        int x = sc[0];
        int y = sc[1];

        AffineTransform transform = g2d.getTransform();

        // Corner offset adjustment : Text Offset is used Here
        FontRenderContext frc = g2d.getFontRenderContext();
        Font font = g2d.getFont();
        double w = font.getStringBounds(text, frc).getWidth();
        double h = font.getSize2D();

        if (rotation != 0) {
            g2d.rotate(rotation, x, y);
        }

        x -= (int) (w * horizontalReference);
        y += (int) (h * verticalReference);

        g2d.drawString(text, x, y);
        g2d.setTransform(transform);
    }

    /**
     * Draw a string with given rotation angle. Reference point is the center
     * of string. The logical coordinates are proportional to the base coordinates.
     * @param text the text.
     * @param coord the text coordinates in base ratio.
     */
    public void drawTextBaseRatio(String text, double[] coord) {
        drawTextBaseRatio(text, coord, 0.5, 0.5, 0.0);
    }

    /**
     * Draw a string with given rotation angle. Reference point is the center
     * of string. The angle of rotation is in radians. The logical coordinates
     * are proportional to the base coordinates.
     * @param text the text.
     * @param coord the text coordinates in base ratio.
     * @param rotation the rotation angel of text.
     */
    public void drawTextBaseRatio(String text, double[] coord, double rotation) {
        drawTextBaseRatio(text, coord, 0.5, 0.5, 0.0);
    }

    /**
     * Draw a string with given reference point. (0.5, 0.5) is center, (0, 0) is
     * lower left, (0, 1) is upper left, etc. The logical coordinates are
     * proportional to the base coordinates.
     * @param text the text.
     * @param coord the text coordinates in base ratio.
     * @param horizontalReference the horizontal reference position of coordinates respected to dimension of text.
     * @param verticalReference the vertical reference position of coordinates respected to dimension of text.
     */
    public void drawTextBaseRatio(String text, double[] coord, double horizontalReference, double verticalReference) {
        drawTextBaseRatio(text, coord, horizontalReference, verticalReference, 0.0);
    }

    /**
     * Draw a string with given reference point and rotation angle. (0.5, 0.5)
     * is center, (0, 0) is lower left, (1, 0) is upper left, etc.
     * The angle of rotation is in radians. The logical  are proportional to
     * the base coordinates.
     * @param text the text.
     * @param coord the text coordinates in base ratio.
     * @param horizontalReference the horizontal reference position of coordinates respected to dimension of text.
     * @param verticalReference the vertical reference position of coordinates respected to dimension of text.
     * @param rotation the rotation angel of text.
     */
    public void drawTextBaseRatio(String text, double[] coord, double horizontalReference, double verticalReference, double rotation) {
        int[] sc = projection.screenProjectionBaseRatio(coord);
        int x = sc[0];
        int y = sc[1];

        AffineTransform transform = g2d.getTransform();

        // Corner offset adjustment : Text Offset is used Here
        FontRenderContext frc = g2d.getFontRenderContext();
        Font font = g2d.getFont();
        double w = font.getStringBounds(text, frc).getWidth();
        double h = font.getSize2D();

        if (rotation != 0) {
            g2d.rotate(rotation, x, y);
        }

        x -= (int) (w * horizontalReference);
        y += (int) (h * verticalReference);

        g2d.drawString(text, x, y);
        g2d.setTransform(transform);
    }

    /**
     * Draws poly line.
     * @param coord the line point coordinates.
     */
    private void drawLine(int[]... coord) {
        int[] x = new int[coord.length];
        for (int i = 0; i < coord.length; i++) {
            x[i] = coord[i][0];
        }

        int[] y = new int[coord.length];
        for (int i = 0; i < coord.length; i++) {
            y[i] = coord[i][1];
        }

        g2d.drawPolyline(x, y, coord.length);
    }

    /**
     * Draws poly line. The coordinates are in logical coordinates.
     * @param coord the line point coordinates.
     */
    public void drawLine(double[]... coord) {
        int[][] sc = new int[coord.length][];
        for (int i = 0; i < sc.length; i++) {
            sc[i] = projection.screenProjection(coord[i]);
        }

        drawLine(sc);
    }

    /**
     * Draws poly line. The logical coordinates are proportional to the base
     * coordinates.
     * @param coord the line point coordinates in base ratio.
     */
    public void drawLineBaseRatio(double[]... coord) {
        int[][] sc = new int[coord.length][];
        for (int i = 0; i < sc.length; i++) {
            sc[i] = projection.screenProjectionBaseRatio(coord[i]);
        }

        drawLine(sc);
    }

    /**
     * Draws a point. The coordinates are in logical coordinates.
     * @param coord the point coordinates.
     */
    public void drawPoint(double... coord) {
        drawPoint('.', coord);
    }

    /**
     * Draws a point with given pattern. The coordinates are in logical coordinates.
     * @param mark the pattern of point:
     * <ul>
     * <li> . : dot
     * <li> + : cross
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large sollid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * </ul>
     * @param coord the point coordinates.
     */
    public void drawPoint(char mark, double... coord) {
        int size = 2;
        int midSize = 3;
        int bigSize = 4;

        int[] sc = projection.screenProjection(coord);

        int x = sc[0];
        int y = sc[1];

        switch (mark) {
            case '+':
                g2d.drawLine(x - size, y, x + size, y);
                g2d.drawLine(x, y - size, x, y + size);
                break;

            case '-':
                g2d.drawLine(x - size, y, x + size, y);
                break;

            case '|':
                g2d.drawLine(x, y - size, x, y + size);
                break;

            case 'x':
                g2d.drawLine(x - size, y - size, x + size, y + size);
                g2d.drawLine(x + size, y - size, x - size, y + size);
                break;

            case '*':
                g2d.drawLine(x - bigSize, y, x + bigSize, y);
                g2d.drawLine(x, y - bigSize, x, y + bigSize);
                g2d.drawLine(x - midSize, y - midSize, x + midSize, y + midSize);
                g2d.drawLine(x + midSize, y - midSize, x - midSize, y + midSize);
                break;

            case 'o':
                g2d.drawOval(x - size, y - size, 2 * size, 2 * size);
                break;

            case 'O':
                g2d.drawOval(x - bigSize, y - bigSize, 2 * bigSize, 2 * bigSize);
                break;

            case '@':
                g2d.fillOval(x - size, y - size, 2 * size, 2 * size);
                break;

            case '#':
                g2d.fillOval(x - bigSize, y - bigSize, 2 * bigSize, 2 * bigSize);
                break;

            case 's':
                g2d.drawRect(x - size, y - size, 2 * size, 2 * size);
                break;

            case 'S':
                g2d.drawRect(x - bigSize, y - bigSize, 2 * bigSize, 2 * bigSize);
                break;

            case 'q':
                g2d.fillRect(x - size, y - size, 2 * size, 2 * size);
                break;

            case 'Q':
                g2d.fillRect(x - bigSize, y - bigSize, 2 * bigSize, 2 * bigSize);
                break;

            default:
                g2d.drawRect(x, y, 1, 1);
                break;
        }
    }

    /**
     * Draws polygon. The coordinates are in logical coordinates.
     * @param coord the polygon point coordinates.
     */
    public void drawPolygon(double[]... coord) {
        int[][] c = new int[coord.length][2];
        for (int i = 0; i < coord.length; i++) {
            c[i] = projection.screenProjection(coord[i]);
        }

        int[] x = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            x[i] = c[i][0];
        }
        int[] y = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            y[i] = c[i][1];
        }
        g2d.drawPolygon(x, y, c.length);
    }

    /**
     * Fills polygon. The coordinates are in logical coordinates.
     * @param coord the polygon point coordinates.
     */
    public void fillPolygon(double[]... coord) {
        int[][] c = new int[coord.length][2];
        for (int i = 0; i < coord.length; i++) {
            c[i] = projection.screenProjection(coord[i]);
        }

        int[] x = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            x[i] = c[i][0];
        }
        int[] y = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            y[i] = c[i][1];
        }

        g2d.fillPolygon(x, y, c.length);
    }

    /**
     * Fills polygon. The coordinates are in logical coordinates. This also supports
     * basic alpha compositing rules for combining source and destination
     * colors to achieve blending and transparency effects with graphics and images.
     *
     * @param alpha the constant alpha to be multiplied with the alpha of the
     *     source. alpha must be a floating point number in the inclusive range
     *     [0.0, 1.0].
     * @param coord the polygon point coordinates.
     */
    public void fillPolygon(float alpha, double[]... coord) {
        int[][] c = new int[coord.length][2];
        for (int i = 0; i < coord.length; i++) {
            c[i] = projection.screenProjection(coord[i]);
        }

        int[] x = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            x[i] = c[i][0];
        }
        int[] y = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            y[i] = c[i][1];
        }

        Composite cs = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.fillPolygon(x, y, c.length);

        g2d.setComposite(cs);
    }

    /**
     * Draws the outline of the specified rectangle.
     * @param topLeft the top left coordinates.
     * @param rightBottom the right bottom coordinates.
     */
    public void drawRect(double[] topLeft, double[] rightBottom) {
        if (!(projection instanceof Projection2D)) {
            throw new UnsupportedOperationException("Only 2D graphics supports drawing rectangles.");
        }

        int[] sc = projection.screenProjection(topLeft);
        int[] sc2 = projection.screenProjection(rightBottom);

        g2d.drawRect(sc[0], sc[1], sc2[0] - sc[0], sc2[1] - sc[1]);
    }

    /**
     * Draws the outline of the specified rectangle. The logical coordinates
     * are proportional to the base coordinates.
     * @param topLeft the top left coordinates in base ratio.
     * @param rightBottom the right bottom coordinates in base ratio.
     */
    public void drawRectBaseRatio(double[] topLeft, double[] rightBottom) {
        if (!(projection instanceof Projection2D)) {
            throw new UnsupportedOperationException("Only 2D graphics supports drawing rectangles.");
        }

        int[] sc = projection.screenProjectionBaseRatio(topLeft);
        int[] sc2 = projection.screenProjectionBaseRatio(rightBottom);

        g2d.drawRect(sc[0], sc[1], sc2[0] - sc[0], sc2[1] - sc[1]);
    }

    /**
     * Fills the specified rectangle.
     * @param topLeft the top left coordinates.
     * @param rightBottom the right bottom coordinates.
     */
    public void fillRect(double[] topLeft, double[] rightBottom) {
        if (!(projection instanceof Projection2D)) {
            throw new UnsupportedOperationException("Only 2D graphics supports drawing rectangles.");
        }

        int[] sc = projection.screenProjection(topLeft);
        int[] sc2 = projection.screenProjection(rightBottom);

        g2d.fillRect(sc[0], sc[1], sc2[0] - sc[0], sc2[1] - sc[1]);
    }

    /**
     * Fills the specified rectangle. The logical coordinates are proportional
     * to the base coordinates.
     * @param topLeft the top left coordinates in base ratio.
     * @param rightBottom the right bottom coordinates in base ratio.
     */
    public void fillRectBaseRatio(double[] topLeft, double[] rightBottom) {
        if (!(projection instanceof Projection2D)) {
            throw new UnsupportedOperationException("Only 2D graphics supports drawing rectangles.");
        }

        int[] sc = projection.screenProjectionBaseRatio(topLeft);
        int[] sc2 = projection.screenProjectionBaseRatio(rightBottom);

        g2d.fillRect(sc[0], sc[1], sc2[0] - sc[0], sc2[1] - sc[1]);
    }

    /**
     * Rotates the 3D view based on the changes on mouse position.
     * @param x changes of mouse position on the x-axis.
     * @param y changes on mouse position on the y-axis.
     */
    public void rotate(double x, double y) {
        if (!(projection instanceof Projection3D)) {
            throw new UnsupportedOperationException("Only 3D graphics supports rotation.");
        }

        ((Projection3D) projection).rotate(x, y);
    }
}
