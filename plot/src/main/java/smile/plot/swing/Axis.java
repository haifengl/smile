/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.plot.swing;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import smile.math.MathEx;
import smile.sort.QuickSort;

/**
 * This class describes an axis of a coordinate system.
 *
 * @author Haifeng Li
 */
public class Axis {
    /**
     * The base coordinate space.
     */
    private Base base;
    /**
     * The index of coordinate associated with this axis.
     */
    private int index;
    /**
     * The tick mark label.
     */
    private String[] ticks;
    /**
     * The tick mark location.
     */
    private double[] location;
    /**
     * Visibility of ticks.
     */
    private boolean isTickVisible = true;
    /**
     * Visibility of the grid.
     */
    private boolean isGridVisible = true;
    /**
     * Visibility of the frame.
     */
    private boolean isFrameVisible = true;
    /**
     * The number of slices in linear scale.
     */
    private int slices = 10;
    /**
     * The coordinates of origin point.
     */
    private double[] origin;
    /**
     * The axis label.
     */
    private BaseLabel axisLabel;
    /**
     * The grid lines.
     */
    private Line[][] gridLines;
    /**
     * The grid labels.
     */
    private GridLabel[] gridLabels;
    /**
     * The rotation degree of grid labels.
     */
    private double rotation = 0.0;

    /**
     * Constructor.
     */
    public Axis(Base base, int index) {
        this.base = base;
        this.index = index;

        String label = "";
        switch (index) {
            case 0:
                label = "X";
                break;
            case 1:
                label = "Y";
                break;
            case 2:
                label = "Z";
                break;
        }
        setLabel(label);

        init();
    }

    /**
     * Sets the axis to its default initial value.
     */
    private void init() {
        initOrigin();
        setTicks();
    }

    /**
     * Initialize the origin point.
     */
    private void initOrigin() {
        origin = base.getCoordinateSpace()[0];
    }

    /**
     * Set the slices of axis.
     */
    private void setTicks() {
        // slicing initialisation
        double[] gridLocation;
        double[] tickLocation;
        String[] marks = null;

        if (ticks == null || location == null) {
            double min = base.getPrecisionUnit()[index] * Math.ceil(base.getLowerBounds()[index] / base.getPrecisionUnit()[index]);
            double max = base.getPrecisionUnit()[index] * Math.floor(base.getUpperBounds()[index] / base.getPrecisionUnit()[index]);
            slices = (int) Math.ceil(MathEx.round((max - min) / base.getPrecisionUnit()[index], 1));

            if (slices <= 0) slices = 1;
            if (slices < 3) slices *= 2;

            gridLocation = new double[slices + 3];
            tickLocation = new double[slices + 3];

            double pitch = (max - min) / slices;
            for (int i = 1; i <= slices + 1; i++) {
                // lines and labels slicing are the same
                gridLocation[i] = min + (i - 1) * pitch;
                tickLocation[i] = min + (i - 1) * pitch;
            }

            gridLocation[0] = base.getLowerBounds()[index];
            tickLocation[0] = base.getLowerBounds()[index];
            gridLocation[slices + 2] = base.getUpperBounds()[index];
            tickLocation[slices + 2] = base.getUpperBounds()[index];

        } else {
            gridLocation = new double[ticks.length + 2];
            tickLocation = new double[ticks.length];
            marks = new String[ticks.length];

            gridLocation[0] = base.getLowerBounds()[index];
            gridLocation[gridLocation.length - 1] = base.getUpperBounds()[index];

            for (int i = 0; i < ticks.length; i++) {
                gridLocation[i+1] = location[i];
                tickLocation[i] = location[i];
                marks[i] = ticks[i];
            }

            Arrays.sort(gridLocation);
            QuickSort.sort(tickLocation, marks);
        }

        // Initialize grid line labels.
        int dim = base.getDimension();
        double[] offset = new double[dim];
        for (int j = 0; j < dim; j++) {
            if (j != index) {
                offset[j] = -(base.getUpperBounds()[j] - base.getLowerBounds()[j]) / 100;
            }
        }

        gridLabels = new GridLabel[tickLocation.length];
        for (int i = 0; i < gridLabels.length; i++) {
            double[] coord = new double[base.getDimension()];
            System.arraycopy(base.getCoordinateSpace()[index + 1], 0, coord, 0, base.getDimension());
            coord[index] = tickLocation[i];

            if (dim == 3) {
                if (index == 0) {
                    coord[2] = base.getUpperBounds()[2] - 2 * offset[2];
                } else if (index == 1) {
                    coord[0] = base.getUpperBounds()[0] - 2 * offset[0];
                } else if (index == 2) {
                    coord[1] = base.getUpperBounds()[1] - 2 * offset[1];
                }
            }

            String label;
            if (marks != null) {
                label = marks[i % marks.length];
            } else {
                int decimal = base.getPrecisionDigits()[index];
                if (ticks == null) {
                    if ((i == 0 && tickLocation[0] != tickLocation[1]) || (i == gridLabels.length - 1 && tickLocation[gridLabels.length - 1] != tickLocation[gridLabels.length - 2]))
                    decimal -= 1;
                }

                String format = "%.0f";
                if (decimal < 0) {
                    format = String.format("%%.%df", -decimal);
                }
                label = String.format(format, tickLocation[i]);
            }

            for (int j = 0; j < dim; j++) {
                coord[j] += offset[j];
            }

            if (base.getDimension() == 2) {
                if (index == 0 && rotation == 0.0) {
                    gridLabels[i] = new GridLabel(label, coord, 0.5, 1.0, rotation);
                } else {
                    gridLabels[i] = new GridLabel(label, coord, 1.0, 0.5, rotation);
                }
            } else {
                if (index == 0) {
                    gridLabels[i] = new GridLabel(label, coord, 0.5, -0.5, rotation);
                } else if (index == 1) {
                    gridLabels[i] = new GridLabel(label, coord, 0.5, 1.0, rotation);
                } else if (index == 2) {
                    gridLabels[i] = new GridLabel(label, coord, 0.0, 0.5, rotation);
                }
            }
        }

        // Initialize grid lines.
        gridLines = new Line[base.getDimension() - 1][gridLocation.length];
        for (int i = 0, i2 = 0; i < base.getDimension() - 1; i++) {
            if (i2 == index) {
                i2++;
            }

            for (int j = 0; j < gridLines[i].length; j++) {
                double[] originBase = new double[base.getDimension()];
                double[] endBase = new double[base.getDimension()];

                System.arraycopy(origin, 0, originBase, 0, base.getDimension());
                System.arraycopy(origin, 0, endBase, 0, base.getDimension());

                endBase[i2] = base.getCoordinateSpace()[i2 + 1][i2];
                originBase[index] = gridLocation[j];
                endBase[index] = gridLocation[j];

                double[][] points = {originBase, endBase};
                if (j > 0 && j < gridLines[i].length - 1) {
                    gridLines[i][j] = new Line(points, Line.Style.DOT, ' ', Color.LIGHT_GRAY);
                } else {
                    gridLines[i][j] = new Line(points, Line.Style.SOLID, ' ', Color.BLACK);
                }
            }
            i2++;
        }
    }

    /**
     * Set the base coordinate space.
     */
    public void reset() {
        init();
    }
    
    /**
     * Sets the rotation degree of tick strings.
     * @param rotation rotation degree.
     */
    public Axis setRotation(double rotation) {
        this.rotation = rotation;
        return this;
    }

    /**
     * Add a label to the axis at given location.
     */
    public void setTicks(String[] ticks, double[] location) {
        if (ticks.length != location.length) {
            throw new IllegalArgumentException("Tick and location size don't match.");
        }

        this.ticks = ticks;
        this.location = location;

        setTicks();
    }

    /**
     * Returns the number of slices in linear scale.
     */
    public int slices() {
        return slices;
    }

    /**
     * Set the visibility of the grid lines and their labels.
     */
    public Axis setGridVisible(boolean v) {
        isGridVisible = v;
        return this;
    }

    /**
     * Returns the visibility of the grid lines and their labels.
     */
    public boolean isGridVisible() {
        return isGridVisible;
    }

    /**
     * Set the visibility of the frame grid lines and their labels.
     */
    public Axis setFrameVisible(boolean v) {
        isFrameVisible = v;
        return this;
    }

    /**
     * Returns the visibility of the frame grid lines and their labels.
     */
    public boolean isFrameVisible() {
        return isFrameVisible;
    }

    /**
     * Set the visibility of the axis label.
     */
    public Axis setTickVisible(boolean v) {
        isTickVisible = v;
        return this;
    }

    /**
     * Returns the visibility of the axis label.
     */
    public boolean isTickVisible() {
        return isTickVisible;
    }

    /**
     * Sets the label.
     */
    public void setLabel(String label) {
        if (label == null) {
            axisLabel = null;
            return;
        }

        double[] position = new double[base.getDimension()];
        if (base.getDimension() == 2) {
            position[index] = 0.5;
            if (index == 0) {
                position[1] = -0.1;
                axisLabel = new BaseLabel(label, position,0.5, 1.0, 0.0);
            } else {
                position[0] = -0.15;
                axisLabel = new BaseLabel(label, position,0.5, 0.5, -Math.PI / 2);
            }
        } else {
            if (index == 0) {
                position[2] = 1.0;
                position[index] = 0.5;
                axisLabel = new BaseLabel(label, position, 0.5, -2.0, 0.0);
            } else if (index == 1) {
                position[0] = 1.0;
                position[index] = 0.5;
                axisLabel = new BaseLabel(label, position, 0.5, 3.0, 0.0);
            } else if (index == 2) {
                position[1] = 1.0;
                position[index] = 1.0;
                axisLabel = new BaseLabel(label, position, -0.5, -1.0, 0.0);
            }
        }
    }

    /**
     * Returns the label of the axis.
     */
    public String getLabel() {
        if (axisLabel == null) {
            return null;
        } else {
            return axisLabel.text;
        }
    }

    /**
     * Draw the axis.
     */
    public void paint(Graphics g) {
        if (gridLines != null) {
            if (isGridVisible) {
                for (int i = 0; i < gridLines.length; i++) {
                    for (int j = 1; j < gridLines[i].length - 1; j++) {
                        gridLines[i][j].paint(g);
                    }
                }
            }

            if (isFrameVisible) {
                for (int i = 0; i < gridLines.length; i++) {
                    gridLines[i][0].paint(g);
                    gridLines[i][gridLines[i].length - 1].paint(g);
                }
            }
        }

        if (isTickVisible) {
            if (gridLabels != null) {
                int[] xy = g.projection.screenProjection(gridLabels[1].coordinates);
                int prevx = xy[0];
                int prevy = xy[1];
                for (int i = 0; i < gridLabels.length; i++) {
                    if (!gridLabels[i].text.isEmpty()) {
                        double[] coord = gridLabels[i].coordinates;
                        xy = g.projection.screenProjection(coord);
                        int x = xy[0];
                        int y = xy[1];
                        
                        if (base.getDimension() == 2 && index == 0 && rotation != 0.0) {
                            if ((prevx == x && prevy == y)
                              || Math.abs(x - prevx) > gridLabels[i].font.getSize()) {
                                gridLabels[i].paint(g);
                                prevx = x;
                                prevy = y;
                            }
                        } else if (base.getDimension() == 2 && index == 1) {
                            if ((prevx == x && prevy == y && i == 0)
                              || Math.abs(prevy - y) > gridLabels[i].font.getSize()) {
                                gridLabels[i].paint(g);
                                prevx = x;
                                prevy = y;
                            }
                        } else {
                            if ((prevx == x && prevy == y)
                              || Math.abs(x - prevx) > g.getGraphics().getFontMetrics(gridLabels[i].font).stringWidth(gridLabels[i].text)
                              || Math.abs(prevy - y) > gridLabels[i].font.getSize()) {
                                gridLabels[i].paint(g);
                                prevx = x;
                                prevy = y;
                            }
                        }
                    }
                }
            }
        }

        if (axisLabel != null) {
            axisLabel.paint(g);
        }
    }
}
