/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.interpolation;

/**
 * Interpolation of 2-dimensional data.
 *
 * @author Haifeng Li
 */
public interface Interpolation2D {

    /**
     * Interpolate the data at a given 2-dimensional point.
     */
    public double interpolate(double x1, double x2);
}
