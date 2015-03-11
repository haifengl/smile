/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.interpolation;

/**
 * In numerical analysis, interpolation is a method of constructing new data
 * points within the range of a discrete set of known data points.
 * In engineering and science one often has a number of data points, as
 * obtained by sampling or experimentation, and tries to construct a function
 * which closely fits those data points. This is called curve fitting or
 * regression analysis. Interpolation is a specific case of curve fitting,
 * in which the function must go exactly through the data points.
 *
 * @author Haifeng Li
 */
public interface Interpolation {

    /**
     * Given a value x, return an interploated value.
     */
    public double interpolate(double x);
}
