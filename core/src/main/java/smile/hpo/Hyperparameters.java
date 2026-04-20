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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import smile.math.MathEx;

/**
 * Hyperparameter configuration. A hyperparameter is a parameter whose value
 * is set before the learning process begins. By contrast, the values of other
 * parameters are derived via training.
 * <p>
 * Hyperparameters can be classified as model hyperparameters, that cannot
 * be inferred while fitting the machine to the training set because they
 * refer to the model selection task, or algorithm hyperparameters, that
 * in principle have no influence on the performance of the model but
 * affect the speed and quality of the learning process. For example,
 * the topology and size of a neural network are model hyperparameters,
 * while learning rate and mini-batch size are algorithm hyperparameters.
 * <p>
 * The below example shows how to tune the hyperparameters of random forest
 * using grid search.
 * <pre>
 * {@code
 *    import smile.io.*;
 *    import smile.data.formula.Formula;
 *    import smile.validation.*;
 *    import smile.classification.RandomForest;
 *
 *    var hp = new Hyperparameters()
 *        .add("smile.random.forest.trees", 100) // a fixed value
 *        .add("smile.random.forest.mtry", new int[] {2, 3, 4}) // discrete choices
 *        .add("smile.random.forest.max.nodes", 100, 500, 50); // range [100, 500] step 50
 *
 *    var train = Read.arff("data/weka/segment-challenge.arff");
 *    var test = Read.arff("data/weka/segment-test.arff");
 *    var formula = Formula.lhs("class");
 *    var testy = formula.y(test).toIntArray();
 *
 *    hp.grid().forEach(prop -> {
 *        var model = RandomForest.fit(formula, train, prop);
 *        var pred = model.predict(test);
 *        System.out.println(prop);
 *        System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
 *        System.out.println(ConfusionMatrix.of(testy, pred));
 *    });
 *
 *    // Random search — remember to limit the infinite stream:
 *    hp.random().limit(50).forEach(prop -> { ... });
 * }
 * </pre>
 * @author Haifeng Li
 */
public class Hyperparameters {
    /** The set of parameters, in insertion order. */
    private final LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();

    /**
     * Intermediate key-value pair used while expanding the Cartesian product
     * in {@link #grid()}.
     */
    private record KeyValue(String key, String value) {}

    /**
     * An integer range {@code [start, end]} with a positive step size.
     * {@link #toArray()} generates values {@code start, start+step, ...}
     * up to and not exceeding {@code end}.
     *
     * @param start the start of the range (inclusive).
     * @param end   the end of the range (the last generated value will be
     *              {@code <= end}).
     * @param step  a positive step size.
     */
    private record IntRange(int start, int end, int step) {
        public IntRange {
            if (start >= end) {
                throw new IllegalArgumentException(
                        String.format("start (%d) must be less than end (%d)", start, end));
            }
            if (step <= 0) {
                throw new IllegalArgumentException(
                        String.format("step must be positive, but got %d", step));
            }
        }

        public IntRange(int start, int end) {
            this(start, end, Math.max(1, (end - start) / 10));
        }

        int[] toArray() {
            int n = (end - start) / step + 1;
            int[] a = new int[n];
            for (int i = 0; i < n; i++) {
                a[i] = start + i * step;
            }
            return a;
        }
    }

    /**
     * A double range {@code [start, end]} with a positive step size.
     * {@link #toArray()} generates values {@code start, start+step, ...}
     * clamped so that no value exceeds {@code end}.
     *
     * @param start the start of the range (inclusive).
     * @param end   the end of the range (the last generated value will be
     *              {@code <= end}).
     * @param step  a positive step size.
     */
    private record DoubleRange(double start, double end, double step) {
        public DoubleRange {
            if (start >= end) {
                throw new IllegalArgumentException(
                        String.format("start (%f) must be less than end (%f)", start, end));
            }
            if (step <= 0.0) {
                throw new IllegalArgumentException(
                        String.format("step must be positive, but got %f", step));
            }
        }

        public DoubleRange(double start, double end) {
            this(start, end, (end - start) / 10);
        }

        double[] toArray() {
            int n = (int) Math.floor((end - start) / step) + 1;
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                // Multiplication avoids floating-point accumulation errors.
                a[i] = Math.min(start + i * step, end);
            }
            return a;
        }
    }

    /** Constructor. */
    public Hyperparameters() {
    }

    /**
     * Validates that a parameter name is non-null and non-blank.
     *
     * @param name the parameter name to validate.
     * @throws IllegalArgumentException if the name is null or blank.
     */
    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name must not be null or blank");
        }
    }

    /**
     * Adds a parameter with a fixed integer value.
     *
     * @param name  the parameter name.
     * @param value a fixed value of the parameter.
     * @return this object.
     */
    public Hyperparameters add(String name, int value) {
        return add(name, new int[]{value});
    }

    /**
     * Adds a parameter with a fixed double value.
     *
     * @param name  the parameter name.
     * @param value a fixed value of the parameter.
     * @return this object.
     */
    public Hyperparameters add(String name, double value) {
        return add(name, new double[]{value});
    }

    /**
     * Adds a parameter with a fixed string value.
     *
     * @param name  the parameter name.
     * @param value a fixed value of the parameter.
     * @return this object.
     */
    public Hyperparameters add(String name, String value) {
        return add(name, new String[]{value});
    }

    /**
     * Adds a parameter with a discrete set of integer choices.
     *
     * @param name   the parameter name.
     * @param values a non-empty array of candidate values.
     * @return this object.
     * @throws IllegalArgumentException if {@code values} is empty.
     */
    public Hyperparameters add(String name, int[] values) {
        requireName(name);
        if (values.length == 0) {
            throw new IllegalArgumentException("values array must not be empty");
        }
        parameters.put(name, values);
        return this;
    }

    /**
     * Adds a parameter with a discrete set of double choices.
     *
     * @param name   the parameter name.
     * @param values a non-empty array of candidate values.
     * @return this object.
     * @throws IllegalArgumentException if {@code values} is empty.
     */
    public Hyperparameters add(String name, double[] values) {
        requireName(name);
        if (values.length == 0) {
            throw new IllegalArgumentException("values array must not be empty");
        }
        parameters.put(name, values);
        return this;
    }

    /**
     * Adds a parameter with a discrete set of string choices.
     *
     * @param name   the parameter name.
     * @param values a non-empty array of candidate values.
     * @return this object.
     * @throws IllegalArgumentException if {@code values} is empty.
     */
    public Hyperparameters add(String name, String[] values) {
        requireName(name);
        if (values.length == 0) {
            throw new IllegalArgumentException("values array must not be empty");
        }
        parameters.put(name, values);
        return this;
    }

    /**
     * Adds an integer parameter with an auto-stepped range.
     * The step defaults to {@code max(1, (end-start)/10)}.
     *
     * @param name  the parameter name.
     * @param start the start of value range (inclusive).
     * @param end   the end of value range (inclusive upper bound for steps).
     * @return this object.
     */
    public Hyperparameters add(String name, int start, int end) {
        requireName(name);
        parameters.put(name, new IntRange(start, end));
        return this;
    }

    /**
     * Adds an integer parameter with an explicit step range.
     *
     * @param name  the parameter name.
     * @param start the start of value range (inclusive).
     * @param end   the end of value range (inclusive upper bound for steps).
     * @param step  the step size (must be positive).
     * @return this object.
     */
    public Hyperparameters add(String name, int start, int end, int step) {
        requireName(name);
        parameters.put(name, new IntRange(start, end, step));
        return this;
    }

    /**
     * Adds a double parameter with an auto-stepped range.
     * The step defaults to {@code (end-start)/10}.
     *
     * @param name  the parameter name.
     * @param start the start of value range (inclusive).
     * @param end   the end of value range (inclusive upper bound for steps).
     * @return this object.
     */
    public Hyperparameters add(String name, double start, double end) {
        requireName(name);
        parameters.put(name, new DoubleRange(start, end));
        return this;
    }

    /**
     * Adds a double parameter with an explicit step range.
     *
     * @param name  the parameter name.
     * @param start the start of value range (inclusive).
     * @param end   the end of value range (inclusive upper bound for steps).
     * @param step  the step size (must be positive).
     * @return this object.
     */
    public Hyperparameters add(String name, double start, double end, double step) {
        requireName(name);
        parameters.put(name, new DoubleRange(start, end, step));
        return this;
    }

    /**
     * Removes a previously registered parameter. Has no effect if the
     * parameter was not registered.
     *
     * @param name the parameter name.
     * @return this object.
     */
    public Hyperparameters remove(String name) {
        parameters.remove(name);
        return this;
    }

    /**
     * Removes all registered parameters.
     *
     * @return this object.
     */
    public Hyperparameters clear() {
        parameters.clear();
        return this;
    }

    /**
     * Returns the number of registered parameters.
     *
     * @return the number of registered parameters.
     */
    public int size() {
        return parameters.size();
    }

    /**
     * Generates an <strong>infinite</strong> stream of randomly sampled
     * hyperparameter configurations. The caller must apply {@link Stream#limit}
     * before collecting, e.g.:
     * <pre>{@code hp.random().limit(100).forEach(this::evaluate);}</pre>
     * <p>
     * For {@link IntRange} and {@link DoubleRange} parameters the step field
     * is ignored; values are sampled uniformly from {@code [start, end)}.
     *
     * @return an infinite stream of hyperparameter configurations.
     * @throws IllegalStateException if no parameters have been registered.
     */
    public Stream<Properties> random() {
        if (parameters.isEmpty()) {
            throw new IllegalStateException("No hyperparameters have been registered");
        }
        return Stream.generate(() -> {
            Properties params = new Properties();
            parameters.forEach((name, values) -> {
                switch (values) {
                    case int[] a -> {
                        int v = a.length == 1 ? a[0] : a[MathEx.randomInt(a.length)];
                        params.setProperty(name, String.valueOf(v));
                    }
                    case double[] a -> {
                        double v = a.length == 1 ? a[0] : a[MathEx.randomInt(a.length)];
                        params.setProperty(name, String.valueOf(v));
                    }
                    case String[] a -> {
                        String v = a.length == 1 ? a[0] : a[MathEx.randomInt(a.length)];
                        params.setProperty(name, v);
                    }
                    case IntRange range ->
                        params.setProperty(name, String.valueOf(MathEx.randomInt(range.start(), range.end())));
                    case DoubleRange range ->
                        params.setProperty(name, String.valueOf(MathEx.random(range.start(), range.end())));
                    case null, default ->
                        throw new IllegalStateException("Unknown parameter type: " + values);
                }
            });
            return params;
        });
    }

    /**
     * Generates a finite stream of {@code n} randomly sampled hyperparameter
     * configurations. This is a convenience alternative to
     * {@code random().limit(n)}.
     *
     * @param n the number of configurations to sample (must be positive).
     * @return a stream of {@code n} randomly sampled configurations.
     * @throws IllegalArgumentException if {@code n <= 0}.
     * @throws IllegalStateException    if no parameters have been registered.
     */
    public Stream<Properties> random(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException(
                    String.format("n must be positive, but got %d", n));
        }
        return random().limit(n);
    }

    /**
     * Generates a finite stream of all hyperparameter configurations for
     * grid (exhaustive) search. The total number of configurations is the
     * Cartesian product of all registered parameter value lists — be cautious
     * of combinatorial explosion.
     * <p>
     * Parameters are enumerated in insertion order.
     *
     * @return a finite stream of all grid-search configurations.
     * @throws IllegalStateException if no parameters have been registered.
     */
    public Stream<Properties> grid() {
        if (parameters.isEmpty()) {
            throw new IllegalStateException("No hyperparameters have been registered");
        }

        ArrayList<Map.Entry<String, Object>> lists = new ArrayList<>(parameters.entrySet());

        // Seed combinations with the first parameter's values.
        ArrayList<ArrayList<KeyValue>> combinations = new ArrayList<>();
        for (var pair : values(lists.getFirst())) {
            ArrayList<KeyValue> newList = new ArrayList<>();
            newList.add(pair);
            combinations.add(newList);
        }

        // Cross-product with each subsequent parameter.
        for (int i = 1; i < lists.size(); i++) {
            ArrayList<KeyValue> nextList = values(lists.get(i));
            ArrayList<ArrayList<KeyValue>> newCombinations = new ArrayList<>();
            for (var first : combinations) {
                for (var second : nextList) {
                    ArrayList<KeyValue> newList = new ArrayList<>(first);
                    newList.add(second);
                    newCombinations.add(newList);
                }
            }
            combinations = newCombinations;
        }

        return combinations.stream().map(list -> {
            Properties params = new Properties();
            list.forEach(p -> params.setProperty(p.key(), p.value()));
            return params;
        });
    }

    /** Returns the list of {@link KeyValue} pairs for a single parameter entry. */
    private ArrayList<KeyValue> values(Map.Entry<String, Object> parameter) {
        ArrayList<KeyValue> list = new ArrayList<>();
        String name = parameter.getKey();
        Object values = parameter.getValue();
        switch (values) {
            case int[] array -> {
                for (int value : array) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case double[] array -> {
                for (double value : array) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case String[] array -> {
                for (String value : array) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case IntRange range -> {
                for (int value : range.toArray()) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case DoubleRange range -> {
                for (double value : range.toArray()) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case null, default ->
                throw new IllegalStateException("Unknown parameter type: " + values);
        }
        return list;
    }
}
