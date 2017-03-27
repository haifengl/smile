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
 * Bilinear interpolation in a two-dimensional regular grid. Bilinear interpolation is an extension
 * of linear interpolation for interpolating functions of two variables on a
 * regular grid. The key idea is to perform linear interpolation first in one
 * direction, and then again in the other direction.
 *
 * @author Haifeng Li
 */
public class BilinearInterpolation implements Interpolation2D {

    private double[][] y;
    private LinearInterpolation x1terp, x2terp;

    /**
     * Constructor.
     */
    public BilinearInterpolation(double[] x1, double[] x2, double[][] y) {
        this.y = y;
        x1terp = new LinearInterpolation(x1, x1);
        x2terp = new LinearInterpolation(x2, x2);
    }

    @Override
    public double interpolate(double x1, double x2) {
        int i = x1terp.search(x1);
        int j = x2terp.search(x2);

        double t = (x1-x1terp.xx[i])/(x1terp.xx[i+1]-x1terp.xx[i]);
        double u = (x2-x2terp.xx[j])/(x2terp.xx[j+1]-x2terp.xx[j]);

        double yy = (1.-t)*(1.-u)*y[i][j] + t*(1.-u)*y[i+1][j] + (1.-t)*u*y[i][j+1] + t*u*y[i+1][j+1];

        return yy;
    }
};