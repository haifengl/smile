/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.plot.swing;

/**
 * The grid in the base coordinate space.
 *
 * @author Haifeng Li
 */
class BaseGrid {
    /**
     * The axis objects.
     */
    private Axis[] axis;

    /**
     * Constructor.
     */
    public BaseGrid(Base base) {
        axis = new Axis[base.getDimension()];
        for (int i = 0; i < base.getDimension(); i++) {
            axis[i] = new Axis(base, i);
        }
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
    public BaseGrid setAxisLabel(String... axisLabels) {
        for (int i = 0; i < axisLabels.length; i++) {
            axis[i].setAxisLabel(axisLabels[i]);
        }

        return this;
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
    public BaseGrid setAxisLabel(int i, String axisLabel) {
        axis[i].setAxisLabel(axisLabel);
        return this;
    }

    /**
     * Get axis label.
     */
    public String getAxisLabel(int i) {
        return axis[i].getAxisLabel();
    }

    /**
     * Reset the grid (when the base changes).
     */
    public BaseGrid reset() {
        for (int i = 0; i < axis.length; i++) {
            axis[i].reset();
        }
        return this;
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
