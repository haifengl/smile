/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.math;

import java.io.Serializable;

/**
 * Complex number. The object is immutable so once you create and initialize
 * a Complex object, you cannot modify it.
 *
 * @param re real part
 * @param im imaginary part
 *
 * @author Haifeng Li
 */
public record Complex(double re, double im) implements Serializable {
    /**
     * Returns this + b.
     */
    public Complex add(Complex b) {
        Complex a = this;
        double real = a.re + b.re;
        double imag = a.im + b.im;
        return new Complex(real, imag);
    }

    /**
     * Returns this - b.
     */
    public Complex sub(Complex b) {
        Complex a = this;
        double real = a.re - b.re;
        double imag = a.im - b.im;
        return new Complex(real, imag);
    }

    /**
     * Returns this * b.
     */
    public Complex mul(Complex b) {
        Complex a = this;
        double real = a.re * b.re - a.im * b.im;
        double imag = a.re * b.im + a.im * b.re;
        return new Complex(real, imag);
    }

    /**
     * Scalar multiplication.
     * @return this * b.
     */
    public Complex scale(double b) {
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
