/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.plot;

/**
 * The grid in the base coordinate space.
 *
 * @author Haifeng Li
 */
class BaseGrid {

    /**
     * The base coordinate space.
     */
    private Base base;
    /**
     * The axis objects.
     */
    private Axis[] axis;

    /**
     * Constructor.
     */
    public BaseGrid(Base base) {
        this.base = base;
        axis = new Axis[base.getDimension()];
        for (int i = 0; i < base.getDimension(); i++) {
            axis[i] = new Axis(base, i);
        }
    }

    /**
     * Constructor.
     */
    public BaseGrid(Base base, String... axisLabels) {
        if (axisLabels.length != base.getDimension()) {
            throw new IllegalArgumentException("Axis label size don't match base dimension.");
        }

        this.base = base;
        axis = new Axis[base.getDimension()];
        for (int i = 0; i < base.getDimension(); i++) {
            axis[i] = new Axis(base, i, axisLabels[i]);
        }
    }

    /**
     * Set if labels and tickmarks are visible.
     */
    public void setLabelVisible(int i, boolean v) {
        axis[i].setLabelVisible(v);
    }

    /**
     * Returns if labels and tickmarks are visible.
     */
    public boolean isLabelVisible(int i) {
        return axis[i].isLabelVisible();
    }

    /**
     * Set if the grid visible.
     */
    public void setGridVisible(int i, boolean v) {
        axis[i].setGridVisible(v);
    }

    /**
     * Returns if the grid visible.
     */
    public boolean isGridVisible(int i) {
        return axis[i].isGridVisible();
    }

    /**
     * Set if the frame visible.
     */
    public void setFrameVisible(boolean v) {
        for (int i = 0; i < axis.length; i++) {
            axis[i].setGridVisible(v);
        }
    }

    /**
     * Returns if the frame visible.
     */
    public boolean isFrameVisible() {
        return axis[0].isGridVisible();
    }

    /**
     * Returns the axis.
     */
    public Axis getAxis(int i) {
        return axis[i];
    }

    /**
     * Set axis labels.
     */
    public void setAxisLabel(String... axisLabels) {
        if (axisLabels.length != base.getDimension()) {
            throw new IllegalArgumentException("Axis label size don't match base dimension.");
        }

        for (int i = 0; i < axisLabels.length; i++) {
            axis[i].setAxisLabel(axisLabels[i]);
        }
    }

    /**
     * Get axis label.
     */
    public String[] getAxisLabel() {
        String[] array = new String[axis.length];
        for (int i = 0; i < array.length; i++) {
            array[i] = axis[i].getAxisLabel();
        }
        return array;
    }

    /**
     * Set axis labels.
     */
    public void setAxisLabel(int i, String axisLabel) {
        axis[i].setAxisLabel(axisLabel);
    }

    /**
     * Get axis label.
     */
    public String getAxisLabel(int i) {
        return axis[i].getAxisLabel();
    }

    /**
     * Set the base coordinate space.
     */
    public void setBase(Base base) {
        this.base = base;
        for (int i = 0; i < axis.length; i++) {
            axis[i].setBase(base);
        }
    }

    /**
     * Draw the grid.
     */
    public void paint(Graphics g) {
        for (int i = 0; i < axis.length; i++) {
            axis[i].paint(g);
        }
    }
}