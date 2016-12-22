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

package smile.math.rbf;

import java.io.Serializable;
import smile.math.Math;

/**
 * Gaussian RBF. &phi;(r) = e<sup>-0.5 * r<sup>2</sup> / r<sup>2</sup><sub>0</sub></sup>
 * where r<sub>0</sub> is a scale factor. The interpolation accuracy using
 * Gaussian basis functions can be very sensitive to r<sub>0</sub>, and they
 * are often avoided for this reason. However, for smooth functions and with
 * an optimal r<sub>0</sub>, very high accuracy can be achieved. The Gaussian
 * also will extrapolate any function to zero far from the data, and it gets
 * to zero quickly.
 * <p>
 * In general, r<sub>0</sub> should be larger than the typical separation of
 * points but smaller than the "outer scale" or feature size of the function
 * to interplate. There can be several orders of magnitude difference between
 * the interpolation accuracy with a good choice for r<sub>0</sub>, versus a
 * poor choice, so it is definitely worth some experimentation. One way to
 * experiment is to construct an RBF interpolator omitting one data point
 * at a time and measuring the interpolation error at the omitted point.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Nabil Benoudjit and Michel Verleysen. On the kernel widths in radial-basis function networks. Neural Process, 2003.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class GaussianRadialBasis implements RadialBasisFunction, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The scale factor.
     */
    private double r0;

    /**
     * Constructor. The default bandwidth is 1.0.
     */
    public GaussianRadialBasis() {
        this(1.0);
    }

    /**
     * Constructor.
     *
     * @param scale the scale (bandwidth/sigma) parameter.
     */
    public GaussianRadialBasis(double scale) {
        r0 = scale;
    }

    @Override
    public double f(double r) {
        r /= r0;
        return Math.exp(-0.5 * r * r);
    }

    @Override
    public String toString() {
        return String.format("Gaussian Radial Basis (r0 = %.4f)", r0);
    }
}
