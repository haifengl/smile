/*******************************************************************************
 * Copyright (c) 2015 Diego Catalano
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

package smile.math.kernel;

import java.lang.Math;
import java.io.Serializable;

/**
 * The Pearson Mercer Kernel.

 * @author Diego Catalano
 */
public class PearsonKernel implements MercerKernel<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    
    private double omega;
    private double sigma;
    private double constant;
    
    /**
     * Get the omega parameter.
     * @return Omega parameter.
     */
    public double getOmega() {
        return omega;
    }

    /**
     * Set the omega parameter.
     * @param omega Omega parameter.
     */
    public void setOmega(double omega) {
        this.omega = omega;
        this.constant = 2 * Math.sqrt(Math.pow(2, (1 / omega)) - 1) / sigma;
    }

    /**
     * Get the sigma parameter.
     * @return Sigma parameter.
     */
    public double getSigma() {
        return sigma;
    }

    /**
     * Set the sigma parameter.
     * @param sigma Sigma parameter.
     */
    public void setSigma(double sigma) {
        this.sigma = sigma;
        this.constant = 2 * Math.sqrt(Math.pow(2, (1 / omega)) - 1) / sigma;
    }
    
    /**
     * Constructor.
     */
    public PearsonKernel() {
        this(1,1);
    }

    /**
     * Constructor.
     * @param omega Omega value.
     * @param sigma Sigma value.
     */
    public PearsonKernel(double omega, double sigma) {
        this.omega = omega;
        this.sigma = sigma;
    }

    @Override
    public String toString() {
        return "Pearson Kernel";
    }

    @Override
    public double k(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        
        //Inner product
        double xx = 0;
        double yy = 0;
        double xy = 0;
        for (int i = 0; i < x.length; i++){
            xx += x[i] * x[i];
            yy += y[i] * y[i];
            xy += x[i] * y[i];
        }
        
        double m = constant * Math.sqrt(-2.0 * xy + xx + yy);
        return 1.0 / Math.pow(1.0 + m * m, omega);

    }
}