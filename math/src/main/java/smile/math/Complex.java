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

import java.io.Serializable;

/**
 * Complex number. The object is immutable so once you create and initialize
 * a Complex object, you cannot modify it.
 *
 * @author Haifeng Li
 */
public class Complex implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The real part.
     */
    private final double re;
    /**
     * The imaginary part.
     */
    private final double im;

    /**
     * Constructor.
     * @param real real part
     * @param imag imaginary part
     */
    public Complex(double real, double imag) {
        re = real;
        im = imag;
    }

    @Override
    public String toString() {
        if (im == 0) {
            return String.format("%.4f", re);
        }

        if (re == 0) {
            return String.format("%.4fi", im);
        }

        if (im < 0) {
            return String.format("%.4f - %.4fi", re, -im);
        }

        return String.format("%.4f + %.4fi", re, im);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Complex) {
            Complex c = (Complex) o;
            if (re == c.re && im == c.im) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + (int) (Double.doubleToLongBits(re) ^ (Double.doubleToLongBits(re) >>> 32));
        hash = 47 * hash + (int) (Double.doubleToLongBits(im) ^ (Double.doubleToLongBits(im) >>> 32));
        return hash;
    }

    /**
     * Returns abs/modulus/magnitude.
     */
    public double abs() {
        return Math.hypot(re, im);
    }

    /**
     * Returns angle/phase/argument between -pi and pi.
     */
    public double phase() {
        return Math.atan2(im, re);
    }

    /**
     * Returns this + b.
     */
    public Complex plus(Complex b) {
        Complex a = this;
        double real = a.re + b.re;
        double imag = a.im + b.im;
        return new Complex(real, imag);
    }

    /**
     * Returns this - b.
     */
    public Complex minus(Complex b) {
        Complex a = this;
        double real = a.re - b.re;
        double imag = a.im - b.im;
        return new Complex(real, imag);
    }

    /**
     * Returns this * b.
     */
    public Complex times(Complex b) {
        Complex a = this;
        double real = a.re * b.re - a.im * b.im;
        double imag = a.re * b.im + a.im * b.re;
        return new Complex(real, imag);
    }

    /**
     * Scalar multiplication.* Returns this * b.
     */
    public Complex times(double b) {
        return new Complex(b * re, b * im);
    }

    /**
     * Returns a / b.
     */
    public Complex div(Complex b) {
        double cdivr, cdivi;
        double r, d;
        if (Math.abs(b.re) > Math.abs(b.im)) {
            r = b.im / b.re;
            d = b.re + r * b.im;
            cdivr = (re + r * im) / d;
            cdivi = (im - r * re) / d;
        } else {
            r = b.re / b.im;
            d = b.im + r * b.re;
            cdivr = (r * re + im) / d;
            cdivi = (r * im - re) / d;
        }

        return new Complex(cdivr, cdivi);
    }

    /**
     * Returns the conjugate.
     */
    public Complex conjugate() {
        return new Complex(re, -im);
    }

    /**
     * Returns the reciprocal.
     */
    public Complex reciprocal() {
        double scale = re * re + im * im;
        return new Complex(re / scale, -im / scale);
    }

    /**
     * Returns the real part.
     */
    public double re() {
        return re;
    }

    /**
     * Returns the imaginary part.
     */
    public double im() {
        return im;
    }

    /**
     * Returns the complex exponential.
     */
    public Complex exp() {
        return new Complex(Math.exp(re) * Math.cos(im), Math.exp(re) * Math.sin(im));
    }

    /**
     * Returns the complex sine.
     */
    public Complex sin() {
        return new Complex(Math.sin(re) * Math.cosh(im), Math.cos(re) * Math.sinh(im));
    }

    /**
     * Returns the complex cosine.
     */
    public Complex cos() {
        return new Complex(Math.cos(re) * Math.cosh(im), -Math.sin(re) * Math.sinh(im));
    }

    /**
     * Returns the complex tangent.
     */
    public Complex tan() {
        return sin().div(cos());
    }
}
