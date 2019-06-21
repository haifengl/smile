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
    public final double re;
    /**
     * The imaginary part.
     */
    public final double im;

    /**
     * Constructor.
     * @param real real part
     * @param imag imaginary part
     */
    public Complex(double real, double imag) {
        re = real;
        im = imag;
    }

    /** Returns a Complex instance representing the specified value. */
    public static Complex of(double real) {
        return new Complex(real, 0.0);
    }

    /** Returns a Complex instance representing the specified value. */
    public static Complex of(double real, double imag) {
        return new Complex(real, imag);
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
            return re == c.re && im == c.im;
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

    /** Packed array of complex numbers for better memory efficiency. */
    public static class Array {
        public final int length;
        private double[] data;

        /** Constructor.
         *
         * @param length the length of array.
         */
        public Array(int length) {
            if (length < 0) {
                throw new IllegalArgumentException("Negative array length: " + length);
            }
            this.length = length;
            data = new double[length * 2];
        }

        /** Returns the i-th element. */
        public Complex get(int i) {
            int idx = i << 1;
            return new Complex(data[idx], data[idx+1]);
        }

        /** Returns the i-th element. For Scala convenience. */
        public Complex apply(int i) {
            return get(i);
        }

        /** Sets the i-th element. */
        public void set(int i, Complex c) {
            int idx = i << 1;
            data[idx] = c.re;
            data[idx+1] = c.im;
        }

        /** Sets the i-th element with a real value. */
        public void set(int i, double re) {
            int idx = i << 1;
            data[idx] = re;
        }

        /** Sets the i-th element. For Scala convenience. */
        public void update(int i, Complex c) {
            set(i, c);
        }

        /** Sets the i-th element with a real value. For Scala convenience. */
        public void update(int i, double re) {
            set(i, re);
        }

        public static Array of(Complex... x) {
            Array a = new Array(x.length);
            for (int i = 0; i < x.length; i++) {
                a.set(i, x[i]);
            }
            return a;
        }
    }
}
