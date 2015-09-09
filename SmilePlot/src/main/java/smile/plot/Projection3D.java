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
package smile.plot;

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
    private double factor = 1.7;

    /**
     * Constructor.
     */
    public Projection3D(PlotCanvas canvas) {
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
