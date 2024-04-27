/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.plot.swing;

/**
 * Project 3D logical coordinates to Java2D coordinates. Also provide support
 * of rotation of plot, i.e. change the view point.
 *
 * @author Haifeng Li
 */
class Projection3D extends Projection {

    /**
     * Rotation view angle around Y axis.
     */
    private double theta = Math.PI / 4;
    /**
     * Elevation view angle around Z axis.
     */
    private double phi = Math.PI / 8;
    /**
     * sin(&theta;).
     */
    private double sinTheta = Math.sin(theta);
    /**
     * cos(&theta;).
     */
    private double cosTheta = Math.cos(theta);
    /**
     * sin(&phi;).
     */
    private double sinPhi = Math.sin(phi);
    /**
     * cos($phi;).
     */
    private double cosPhi = Math.cos(phi);
    /**
     * Zoom factor.
     */
    private final double factor = 1.7;

    /**
     * Constructor.
     */
    public Projection3D(Canvas canvas) {
        super(canvas);
    }

    /**
     * Pre-computes sin and cos of rotation angles.
     */
    private void precompute() {
        sinTheta = Math.sin(theta);
        cosTheta = Math.cos(theta);
        sinPhi = Math.sin(phi);
        cosPhi = Math.cos(phi);
    }
    
    /**
     * Returns the camera coordinates.
     * @param xyz the world coordinates.
     * @return the camera coordinates.
     */
    public double[] project(double[] xyz) {
        double[] coord = new double[3];

        coord[0] = cosTheta * xyz[1] - sinTheta * xyz[0];
        coord[1] = cosPhi * xyz[2] - sinPhi * cosTheta * xyz[0] - sinPhi * sinTheta * xyz[1];
        coord[2] = cosPhi * sinTheta * xyz[1] + sinPhi * xyz[2] + cosPhi * cosTheta * xyz[0];

        return coord;
    }
    
    /**
     * Returns z-axis value in the camera coordinates.
     * @param xyz the world coordinates.
     * @return z-axis value in the camera coordinates.
     */
    public double z(double[] xyz) {
        return cosPhi * sinTheta * xyz[1] + sinPhi * xyz[2] + cosPhi * cosTheta * xyz[0];
    }
    
    @Override
    double[] baseCoordsScreenProjectionRatio(double[] xyz) {
        double[] sc = new double[2];
        sc[0] = 0.5
                + (cosTheta * ((xyz[1] - (canvas.base.upperBound[1] + canvas.base.lowerBound[1]) / 2) / (canvas.base.upperBound[1] - canvas.base.lowerBound[1]))
                -  sinTheta * ((xyz[0] - (canvas.base.upperBound[0] + canvas.base.lowerBound[0]) / 2) / (canvas.base.upperBound[0] - canvas.base.lowerBound[0])))
                / factor;
        sc[1] = 0.5
                + (cosPhi * ((xyz[2] - (canvas.base.upperBound[2] + canvas.base.lowerBound[2]) / 2) / (canvas.base.upperBound[2] - canvas.base.lowerBound[2]))
                -  sinPhi * cosTheta * ((xyz[0] - (canvas.base.upperBound[0] + canvas.base.lowerBound[0]) / 2) / (canvas.base.upperBound[0] - canvas.base.lowerBound[0]))
                -  sinPhi * sinTheta * ((xyz[1] - (canvas.base.upperBound[1] + canvas.base.lowerBound[1]) / 2) / (canvas.base.upperBound[1] - canvas.base.lowerBound[1])))
                / factor;
        return sc;
    }

    /**
     * Sets the default view angle.
     */
    public void setDefaultView() {
        setView(Math.PI / 4, Math.PI / 8);
    }
    
    /**
     * Sets the view angle.
     */
    public void setView(double theta, double phi) {
        this.theta = theta;
        this.phi = phi;
        precompute();
        reset();
    }

    /**
     * Rotates the plot, i.e. change the view angle.
     * @param t the change add to &theta;
     * @param p the change add to &phi;
     */
    public void rotate(double t, double p) {
        theta = theta - t / 100;
        phi = phi + p / 100;
        precompute();
        reset();
    }
}
