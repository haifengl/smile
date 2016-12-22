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

/**
 * Inverse multiquadric RBF. &phi;(r) = (r<sup>2</sup> + r<sup>2</sup><sub>0</sub>)<sup>-1/2</sup>
 * where r<sub>0</sub> is a scale factor. Although it sounds odd, the inverse
 * multiquadric gives results that are comparable to the multiquadric,
 * sometimes better. The reason is what really matters is smoothness, and
 * certain properties of the function's Fourier transform that are not very
 * different between the multiquadric and its reciprocal. The fact that one
 * increases monotonically and the other decreases turns out to be almost
 * irrelevant. Besides, inverse multiquadric will extrapolate any function to
 * zero far from the data.
 * <p>
 * In general, r<sub>0</sub> should be larger than the typical separation of
 * points but smaller than the "outer scale" or feature size of the function
 * to interplate. There can be several orders of magnitude difference between
 * the interpolation accuracy with a good choice for r<sub>0</sub>, versus a
 * poor choice, so it is definitely worth some experimentation. One way to
 * experiment is to construct an RBF interpolator omitting one data point
 * at a time and measuring the interpolation error at the omitted point.
 *
 * @author Haifeng Li
 */
public class InverseMultiquadricRadialBasis implements RadialBasisFunction, Serializable {
    private static final long serialVersionUID = 1L;

    private double r02;

    public InverseMultiquadricRadialBasis() {
        this(1.0);
    }

	public InverseMultiquadricRadialBasis(double scale) {
        r02 = scale * scale;
    }

    @Override
    public double f(double r) {
        return 1 / Math.sqrt(r*r + r02);
    }

    @Override
    public String toString() {
        return String.format("Inverse Multi-quadric Radial Basis (r0^2 = %.4f)", r02);
    }
}
