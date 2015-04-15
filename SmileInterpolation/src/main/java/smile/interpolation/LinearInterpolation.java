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
package smile.interpolation;

/**
 * Piecewise linear interpolation. Linear interpolation, sometimes known as
 * lerp, is quick and easy, but it is not very precise. Another disadvantage
 * is that the interpolant is not differentiable at the control points x.
 * 
 * @author Haifeng Li
 */
public class LinearInterpolation extends AbstractInterpolation {

    /**
     * Constructor.
     */
    public LinearInterpolation(double[] x, double[] y) {
        super(x, y);
    }

    @Override
    double rawinterp(int j, double x) {
        if (xx[j] == xx[j + 1]) {
            return yy[j];
        } else {
            return yy[j] + ((x - xx[j]) / (xx[j + 1] - xx[j])) * (yy[j + 1] - yy[j]);
        }
    }
}

