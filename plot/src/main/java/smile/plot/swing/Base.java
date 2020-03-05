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

import java.util.Arrays;

/**
 * The coordinate base of PlotCanvas. This support both 2D and 3D
 * device-independent logical space where graphics primitives are specified.
 * <p>
 * The user need supply the lower and upper bounds for each axis. These bounds
 * usually are extended (a little) internally for better view purpose.
 *
 * @author Haifeng Li
 */
public class Base {

    private static final String BOUND_SIZE_DON_T_MATCH_THE_DIMENSION = "Bound size don't match the dimension.";
    /**
     * The dimensionality of base. Should be 2 or 3.
     */
    int dimension;
    /**
     * Coordinates of base.
     */
    double[][] baseCoords;
    /**
     * Rounded/extended lower bound of each axis.
     */
    double[] lowerBound;
    /**
     * Rounded/extended upper bound of each axis.
     */
    double[] upperBound;
    /**
     * Precision unit of each axis.
     */
    private double[] precisionUnit;
    /**
     * Precision unit digits of each axis.
     */
    private int[] precisionDigits;
    /**
     * Original lower bound of each axis.
     */
    private double[] originalLowerBound;
    /**
     * Original upper bound of each axis.
     */
    private double[] originalUpperBound;
    /**
     * True to round/extend bound of each axis to nearest precision units.
     */
    private boolean[] extendBound;

    /**
     * Constructor.
     */
    public Base(double[] lowerBound, double[] upperBound) {
        this(lowerBound, upperBound, true);
    }

    /**
     * Constructor.
     */
    public Base(double[] lowerBound, double[] upperBound, boolean extendBound) {
        if (lowerBound.length != upperBound.length) {
            throw new IllegalArgumentException("Lower bound and upper bound size don't match.");
        }

        if (lowerBound.length != 2 && lowerBound.length != 3) {
            throw new IllegalArgumentException("Invalid bound dimension: " + lowerBound.length);
        }

        dimension = lowerBound.length;

        for (int i = 0; i < dimension; i++) {
            if (lowerBound[i] > upperBound[i]) {
                throw new IllegalArgumentException("Lower bound is larger than upper bound.");
            }
            
            if (lowerBound[i] == upperBound[i]) {
                lowerBound[i] -= 1.0;
                upperBound[i] += 1.0;
            }
        }

        this.originalLowerBound = lowerBound;
        this.originalUpperBound = upperBound;
        this.extendBound = new boolean[dimension];
        Arrays.fill(this.extendBound, extendBound);

        precisionDigits = new int[dimension];
        precisionUnit = new double[dimension];
        this.lowerBound = new double[dimension];
        this.upperBound = new double[dimension];

        reset();
    }

    /**
     * Reset base coordinates. Round/extend lower and upper bounds if necessary.
     */
    public void reset() {
        for (int i = 0; i < dimension; i++) {
            lowerBound[i] = originalLowerBound[i];
            upperBound[i] = originalUpperBound[i];
            setPrecisionUnit(i);
        }

        // setup rounded base.
        for (int i = 0; i < dimension; i++) {
            if (extendBound[i]) {
                extendBound(i);
            }
        }

        initBaseCoord();
    }

    /**
     * Reset coord.
     */
    void initBaseCoord() {
        baseCoords = new double[dimension + 1][];
        for (int i = 0; i < baseCoords.length; i++) {
            baseCoords[i] = lowerBound.clone();
            if (i > 0) {
                baseCoords[i][i - 1] = upperBound[i - 1];
            }
        }
    }

    /**
     * Returns the dimensionality of coordinates.
     */
    public int getDimension() {
        return dimension;
    }

    /**
     * Set the precision unit for axis i.
     */
    void setPrecisionUnit(int i) {
        if (upperBound[i] > lowerBound[i]) {
            double digits = Math.log10(Math.abs(upperBound[i] - lowerBound[i]));
            double residual = digits - Math.floor(digits);
            if (residual < 0.2) {
                // If the range is less than 15 units, we reduce one level.
                digits -= 1.0;
            }

            precisionDigits[i] = (int) Math.floor(digits);
            precisionUnit[i] = Math.pow(10, precisionDigits[i]);

            if (residual >= 0.2 && residual <= 0.7) {
                // In case of too few grids, we use a half of precision unit.
                precisionUnit[i] /= 2;
                precisionDigits[i] -= 1;
            }
        } else {
            precisionUnit[i] = 0.1;
        }
    }

    /**
     * Rounds the bounds for axis i.
     */
    public void extendBound(int i) {
        if (i < 0 || i >= dimension) {
            throw new IllegalArgumentException("Invalid bound index: " + i);
        }

        extendBound[i] = true;
        lowerBound[i] = precisionUnit[i] * (Math.floor(originalLowerBound[i] / precisionUnit[i]));
        upperBound[i] = precisionUnit[i] * (Math.ceil(originalUpperBound[i] / precisionUnit[i]));
    }

    /**
     * Sets the axis bounds without applying the extending heuristic.
     * @param lowerBound the lower bound.
     * @param upperBound the upper bound
     */
    public void setBound(double[] lowerBound, double[] upperBound) {
        System.arraycopy(lowerBound, 0, this.originalLowerBound, 0, this.originalLowerBound.length);
        System.arraycopy(upperBound, 0, this.originalUpperBound, 0, this.originalUpperBound.length);
        System.arraycopy(lowerBound, 0, this.lowerBound, 0, this.lowerBound.length);
        System.arraycopy(upperBound, 0, this.upperBound, 0, this.upperBound.length);

        for (int i = 0; i < dimension; i++) {
            setPrecisionUnit(i);
        }

        initBaseCoord();
    }

    /**
     * Returns the coordinates.
     */
    public double[][] getCoordinateSpace() {
        return baseCoords;
    }

    /**
     * Returns the lower bounds.
     */
    public double[] getLowerBounds() {
        return lowerBound;
    }

    /**
     * Returns the upper bounds.
     */
    public double[] getUpperBounds() {
        return upperBound;
    }

    /**
     * Returns the precision units of axes.
     */
    public double[] getPrecisionUnit() {
        return precisionUnit;
    }

    /**
     * Returns the precision unit digits of axes.
     */
    public int[] getPrecisionDigits() {
        return precisionDigits;
    }

    /**
     * Extend lower bounds.
     */
    public void extendLowerBound(double[] bound) {
        if (bound.length != dimension) {
            throw new IllegalArgumentException(BOUND_SIZE_DON_T_MATCH_THE_DIMENSION);
        }

        boolean extend = false;
        for (int i = 0; i < bound.length; i++) {
            if (bound[i] < originalLowerBound[i]) {
                originalLowerBound[i] = bound[i];
                extend = true;
            }
        }

        if (extend) {
            reset();
        }
    }

    /**
     * Extend upper bounds.
     */
    public void extendUpperBound(double[] bound) {
        if (bound.length != dimension) {
            throw new IllegalArgumentException(BOUND_SIZE_DON_T_MATCH_THE_DIMENSION);
        }

        boolean extend = false;
        for (int i = 0; i < bound.length; i++) {
            if (bound[i] > originalUpperBound[i]) {
                originalUpperBound[i] = bound[i];
                extend = true;
            }
        }

        if (extend) {
            reset();
        }
    }

    /**
     * Extend lower and upper bounds.
     */
    public void extendBound(double[] lowerBound, double[] upperBound) {
        if (lowerBound.length != dimension || upperBound.length != dimension) {
            throw new IllegalArgumentException(BOUND_SIZE_DON_T_MATCH_THE_DIMENSION);
        }

        boolean extend = false;
        for (int i = 0; i < lowerBound.length; i++) {
            if (lowerBound[i] < this.originalLowerBound[i]) {
                this.originalLowerBound[i] = lowerBound[i];
                extend = true;
            }
        }

        for (int i = 0; i < upperBound.length; i++) {
            if (upperBound[i] > this.originalUpperBound[i]) {
                this.originalUpperBound[i] = upperBound[i];
                extend = true;
            }
        }

        if (extend) {
            reset();
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(String.format("Base[%d]{", dimension));
        for (int i = 0; i < baseCoords.length; i++) {
            s.append("[");
            for (int j = 0; j < baseCoords[i].length; j++) {
                s.append(baseCoords[i][j]).append(',');
            }
            s.setCharAt(s.length() - 1, ']');
        }
        s.append('}');
        return s.toString();
    }
}
