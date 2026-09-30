/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.hpo;

import java.util.*;
import smile.math.MathEx;

/**
 * Maps hyperparameter configurations to and from a normalized continuous
 * hypercube {@code [0, 1]^d} for surrogate model fitting.
 *
 * @author Haifeng Li
 */
class ParameterSpace {
    /** Dimension descriptor in the normalized space. */
    interface Dimension {
        String name();
        double toUnit(String value);
        String format(double u);
    }

    private static class DoubleRangeDim implements Dimension {
        final String name;
        final double start;
        final double end;
        final double step;

        DoubleRangeDim(String name, double start, double end, double step) {
            this.name = name;
            this.start = start;
            this.end = end;
            this.step = step;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public double toUnit(String value) {
            double v = Double.parseDouble(value);
            return Math.clamp((v - start) / (end - start), 0.0, 1.0);
        }

        @Override
        public String format(double u) {
            double v = start + Math.clamp(u, 0.0, 1.0) * (end - start);
            if (step > 0.0) {
                long k = Math.round((v - start) / step);
                v = Math.min(end, start + k * step);
            }
            return String.valueOf(v);
        }
    }

    private static class IntRangeDim implements Dimension {
        final String name;
        final int start;
        final int end;
        final int step;

        IntRangeDim(String name, int start, int end, int step) {
            this.name = name;
            this.start = start;
            this.end = end;
            this.step = step;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public double toUnit(String value) {
            int v = Integer.parseInt(value);
            return Math.clamp((double) (v - start) / (end - start), 0.0, 1.0);
        }

        @Override
        public String format(double u) {
            double v = start + Math.clamp(u, 0.0, 1.0) * (end - start);
            long k = Math.round((v - start) / (double) step);
            int val = (int) Math.min(end, start + k * step);
            return String.valueOf(val);
        }
    }

    private static class DiscreteChoiceDim implements Dimension {
        final String name;
        final String[] choices;

        DiscreteChoiceDim(String name, String[] choices) {
            this.name = name;
            this.choices = choices;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public double toUnit(String value) {
            if (choices.length <= 1) return 0.0;
            for (int i = 0; i < choices.length; i++) {
                if (choices[i].equals(value)) {
                    return (double) i / (choices.length - 1);
                }
            }
            return 0.0;
        }

        @Override
        public String format(double u) {
            if (choices.length <= 1) return choices[0];
            int idx = (int) Math.round(Math.clamp(u, 0.0, 1.0) * (choices.length - 1));
            idx = Math.clamp(idx, 0, choices.length - 1);
            return choices[idx];
        }
    }

    private final List<Dimension> dimensions = new ArrayList<>();
    private final Map<String, String> fixedParameters = new LinkedHashMap<>();

    ParameterSpace(Hyperparameters hp) {
        Map<String, Object> params = hp.parameters();
        params.forEach((name, value) -> {
            switch (value) {
                case int[] arr -> {
                    if (arr.length == 1) {
                        fixedParameters.put(name, String.valueOf(arr[0]));
                    } else {
                        String[] s = new String[arr.length];
                        for (int i = 0; i < arr.length; i++) s[i] = String.valueOf(arr[i]);
                        dimensions.add(new DiscreteChoiceDim(name, s));
                    }
                }
                case double[] arr -> {
                    if (arr.length == 1) {
                        fixedParameters.put(name, String.valueOf(arr[0]));
                    } else {
                        String[] s = new String[arr.length];
                        for (int i = 0; i < arr.length; i++) s[i] = String.valueOf(arr[i]);
                        dimensions.add(new DiscreteChoiceDim(name, s));
                    }
                }
                case String[] arr -> {
                    if (arr.length == 1) {
                        fixedParameters.put(name, arr[0]);
                    } else {
                        dimensions.add(new DiscreteChoiceDim(name, arr.clone()));
                    }
                }
                case Hyperparameters.IntRange range ->
                    dimensions.add(new IntRangeDim(name, range.start(), range.end(), range.step()));
                case Hyperparameters.DoubleRange range ->
                    dimensions.add(new DoubleRangeDim(name, range.start(), range.end(), range.step()));
                case null, default ->
                    throw new IllegalStateException("Unknown parameter specification: " + value);
            }
        });
    }

    int dimension() {
        return dimensions.size();
    }

    double[] toVector(Properties props) {
        int d = dimensions.size();
        double[] u = new double[d];
        for (int i = 0; i < d; i++) {
            Dimension dim = dimensions.get(i);
            String val = props.getProperty(dim.name());
            u[i] = val != null ? dim.toUnit(val) : 0.5;
        }
        return u;
    }

    Properties toProperties(double[] u) {
        Properties props = new Properties();
        fixedParameters.forEach(props::setProperty);
        int d = dimensions.size();
        for (int i = 0; i < d; i++) {
            Dimension dim = dimensions.get(i);
            props.setProperty(dim.name(), dim.format(u[i]));
        }
        return props;
    }

    double[] sampleRandom() {
        int d = dimensions.size();
        double[] u = new double[d];
        for (int i = 0; i < d; i++) {
            u[i] = MathEx.random();
        }
        return u;
    }

    double[] perturb(double[] point, double std) {
        int d = dimensions.size();
        double[] u = new double[d];
        for (int i = 0; i < d; i++) {
            u[i] = Math.clamp(point[i] + MathEx.randn() * std, 0.0, 1.0);
        }
        return u;
    }
}
