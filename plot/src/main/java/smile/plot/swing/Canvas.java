/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.Optional;

/**
 * The Swing component to draw the figure.
 */
public class Canvas extends JComponent implements Printable, ComponentListener, MouseListener,
        MouseMotionListener, MouseWheelListener, ActionListener {
    /**
     * The plot figure.
     */
    private final Figure figure;
    /**
     * The right-click popup menu.
     */
    private final JPopupMenu popup;
    /**
     * If the mouse double-clicked.
     */
    private boolean mouseDoubleClicked = false;
    /**
     * The x coordinate (in Java2D coordinate space) of mouse click.
     */
    private int mouseClickX = -1;
    /**
     * The y coordinate (in Java2D coordinate space) of mouse click.
     */
    private int mouseClickY = -1;
    /**
     * The x coordinate (in Java2D coordinate space) of mouse dragging.
     */
    private int mouseDraggingX = -1;
    /**
     * The y coordinate (in Java2D coordinate space) of mouse dragging.
     */
    private int mouseDraggingY = -1;
    /**
     * The coordinate base when the user start dragging the mouse.
     */
    private Base backupBase;

    /**
     * Constructor.
     * @param figure The plot figure.
     * @param popup The right-click popup menu.
     */
    public Canvas(Figure figure, JPopupMenu popup) {
        this.figure = figure;
        this.popup = popup;
        setBackground(Color.white);
        setDoubleBuffered(true);
        addComponentListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
    }

    @Override
    public void paintComponent(java.awt.Graphics g) {
        figure.paint((Graphics2D) g, getWidth(), getHeight());

        if (mouseDraggingX >= 0 && mouseDraggingY >= 0) {
            g.setColor(Color.BLACK);
            g.drawRect(
                    Math.min(mouseClickX, mouseDraggingX),
                    Math.min(mouseClickY, mouseDraggingY),
                    Math.abs(mouseClickX - mouseDraggingX),
                    Math.abs(mouseClickY - mouseDraggingY)
            );
        }
    }

    @Override
    public int print(java.awt.Graphics g, PageFormat pf, int page) {
        if (page > 0) {
            // We have only one page, and 'page' is zero-based
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) g;

        // User (0,0) is typically outside the printable area, so we must
        // translate by the X and Y values in the PageFormat to avoid clipping
        g2d.translate(pf.getImageableX(), pf.getImageableY());

        // Scale plots to paper size.
        double scaleX = pf.getImageableWidth() / getWidth();
        double scaleY = pf.getImageableHeight() / getHeight();
        g2d.scale(scaleX, scaleY);

        // Now we perform our rendering
        figure.paint(g2d, getWidth(), getHeight());

        // tell the caller that this page is part of the printed document
        return PAGE_EXISTS;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            popup.show(e.getComponent(), e.getX(), e.getY());
            e.consume();
        } else {
            mouseClickX = e.getX();
            mouseClickY = e.getY();
            e.consume();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (figure.base.dimension == 2) {
            mouseDraggingX = e.getX();
            mouseDraggingY = e.getY();
        } else if (figure.base.dimension == 3) {
            figure.graphics.rotate(e.getX() - mouseClickX, e.getY() - mouseClickY);
            mouseClickX = e.getX();
            mouseClickY = e.getY();
        }

        repaint();
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        Base base = figure.base;
        Graphics graphics = figure.graphics;

        if (e.isPopupTrigger()) {
            popup.show(e.getComponent(), e.getX(), e.getY());
            e.consume();
        } else {
            if (mouseDraggingX != -1 && mouseDraggingY != -1) {
                mouseDraggingX = -1;
                mouseDraggingY = -1;

                if (base.dimension == 2) {
                    if (Math.abs(e.getX() - mouseClickX) > 20 && Math.abs(e.getY() - mouseClickY) > 20) {
                        double[] sc1 = ((Projection2D) (graphics.projection)).inverseProjection(mouseClickX, mouseClickY);
                        double[] sc2 = ((Projection2D) (graphics.projection)).inverseProjection(e.getX(), e.getY());

                        if (Math.min(sc1[0], sc2[0]) < base.upperBound[0]
                                && Math.max(sc1[0], sc2[0]) > figure.base.lowerBound[0]
                                && Math.min(sc1[1], sc2[1]) < base.upperBound[1]
                                && Math.max(sc1[1], sc2[1]) > base.lowerBound[1]) {

                            base.lowerBound[0] = Math.max(base.lowerBound[0], Math.min(sc1[0], sc2[0]));
                            base.upperBound[0] = Math.min(base.upperBound[0], Math.max(sc1[0], sc2[0]));
                            base.lowerBound[1] = Math.max(base.lowerBound[1], Math.min(sc1[1], sc2[1]));
                            base.upperBound[1] = Math.min(base.upperBound[1], Math.max(sc1[1], sc2[1]));

                            for (int i = 0; i < base.dimension; i++) {
                                base.setPrecisionUnit(i);
                            }
                            base.initBaseCoord();
                            graphics.projection.reset();
                            figure.resetAxis();
                        }
                    }
                }

                repaint();
                e.consume();
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            mouseDoubleClicked = true;
            backupBase = figure.base;
        } else {
            mouseDoubleClicked = false;
        }

        mouseClickX = e.getX();
        mouseClickY = e.getY();

        e.consume();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        Base base = figure.base;
        Graphics graphics = figure.graphics;

        if (base.dimension == 2) {
            if (mouseDoubleClicked) {
                double x = mouseClickX - e.getX();
                if (Math.abs(x) > 20) {
                    int s = figure.axis[0].slices();
                    x = x > 0 ? 1.0 / s : -1.0 / s;
                    x *= (backupBase.upperBound[0] - backupBase.lowerBound[0]);
                    base.lowerBound[0] = backupBase.lowerBound[0] + x;
                    base.upperBound[0] = backupBase.upperBound[0] + x;
                    mouseClickX = e.getX();
                }

                double y = mouseClickY - e.getY();
                if (Math.abs(y) > 20) {
                    int s = figure.axis[1].slices();
                    y = y > 0 ? -1.0 / s : 1.0 / s;
                    y *= (backupBase.upperBound[1] - backupBase.lowerBound[1]);
                    base.lowerBound[1] = backupBase.lowerBound[1] + y;
                    base.upperBound[1] = backupBase.upperBound[1] + y;
                    mouseClickY = e.getY();
                }

                base.initBaseCoord();
                graphics.projection.reset();
                figure.resetAxis();
                repaint();
            } else {
                StringBuilder tooltip = new StringBuilder();
                double[] sc = ((Projection2D) (graphics.projection)).inverseProjection(e.getX(), e.getY());

                //String firstid = null;
                for (Shape shape : figure.shapes) {
                    if (shape instanceof Plot plot) {
                        Optional<String> s = plot.tooltip(sc);
                        if (s.isPresent()) {
                            if (!tooltip.isEmpty()) {
                                tooltip.append("<br>");
                            }
                            tooltip.append(s);
                        }
                    }
                }

                if (!tooltip.isEmpty()) {
                    setToolTipText(String.format("<html>%s</html>", tooltip));
                }
            }
        }

        e.consume();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.getWheelRotation() == 0) {
            return;
        }

        Base base = figure.base;
        Graphics graphics = figure.graphics;

        for (int i = 0; i < base.dimension; i++) {
            int s = figure.axis[i].slices();
            double r = e.getWheelRotation() > 0 ? 1.0 / s : -1.0 / s;
            if (r > -0.5) {
                double d = (base.upperBound[i] - base.lowerBound[i]) * r;
                base.lowerBound[i] -= d;
                base.upperBound[i] += d;
            }
        }

        for (int i = 0; i < base.dimension; i++) {
            base.setPrecisionUnit(i);
        }

        base.initBaseCoord();
        graphics.projection.reset();
        figure.resetAxis();

        repaint();
        e.consume();
    }

    @Override
    public void componentResized(ComponentEvent e) {
        Base base = figure.base;
        Graphics graphics = figure.graphics;

        if (graphics != null) {
            base.initBaseCoord();
            graphics.projection.reset();
            figure.resetAxis();
        }

        repaint();
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }
}
