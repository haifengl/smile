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
package smile.math;

/**
 * An abstract implementation that uses finite differences to calculate the
 * partial derivatives instead of providing them analytically.
 *
 * @author Haifeng Li
 */
public abstract class AbstractDifferentiableMultivariateFunction implements DifferentiableMultivariateFunction {

    private static final double EPS = 1.0E-8;

    @Override
    public double f(double[] x, double[] gradient) {
        double f = f(x);

        double[] xh = x.clone();
        for (int j = 0; j < x.length; j++) {
            double temp = x[j];
            double h = EPS * Math.abs(temp);
            if (h == 0.0) {
                h = EPS;
            }
            xh[j] = temp + h; // trick to reduce finite-precision error.
            h = xh[j] - temp;
            double fh = f(xh);
            xh[j] = temp;
            gradient[j] = (fh - f) / h;
        }

        return f;
    }
}
