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
 * Thin plate RBF. &phi;(r) = r<sup>2</sup> log(r / r<sub>0</sub>)
 * with the limiting value &phi;(0)=0 assumed, where r<sub>0</sub> is a scale factor.
 * This function has some physical justification in the energy minimization
 * problem associated with warping a thin elastic plate. However, there is no
 * indication that it is generally better than multiquadric or inverse
 * multiquadric function.
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
public class ThinPlateRadialBasis implements RadialBasisFunction, Serializable {
    private static final long serialVersionUID = 1L;

    private double r0;

    /**
     * Constructor.
     */
    public ThinPlateRadialBasis() {
        this(1.0);
    }

    /**
     * Constructor.
     */
    public ThinPlateRadialBasis(double scale) {
        r0 = scale;
    }

    @Override
    public double f(double r) {
        return r <= 0.0 ? 0.0 : r * r * Math.log(r / r0);
    }

    @Override
    public String toString() {
        return String.format("Thin Plate Radial Basis (r0 = %.4f)", r0);
    }
}
