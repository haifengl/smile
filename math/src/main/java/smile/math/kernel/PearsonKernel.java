/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.math.kernel;

/**
 * The Pearson Mercer Kernel.

 * @author Diego Catalano
 */
public class PearsonKernel implements MercerKernel<double[]> {
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