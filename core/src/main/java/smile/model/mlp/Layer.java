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
package smile.model.mlp;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import smile.math.MathEx;
import smile.tensor.DenseMatrix;
import smile.tensor.Vector;
import smile.util.Regex;
import static smile.linalg.Transpose.*;
import static smile.tensor.ScalarType.*;

/**
 * A layer in the neural network.
 *
 * @author Haifeng Li
 */
public abstract class Layer implements AutoCloseable, Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
    /**
     * The number of neurons in this layer
     */
    protected final int n;
    /**
     * The number of input variables.
     */
    protected final int p;
    /**
     * The dropout rate. Dropout randomly sets input units to 0 with this rate
     * at each step during training time, which helps prevent overfitting.
     */
    protected final double dropout;
    /**
     * The affine transformation matrix.
     */
    protected DenseMatrix weight;
    /**
     * The bias.
     */
    protected Vector bias;
    /**
     * The output vector.
     */
    protected transient ThreadLocal<Vector> output;
    /**
     * The output gradient.
     */
    protected transient ThreadLocal<Vector> outputGradient;
    /**
     * The weight gradient.
     */
    protected transient ThreadLocal<DenseMatrix> weightGradient;
    /**
     * The bias gradient.
     */
    protected transient ThreadLocal<Vector> biasGradient;
    /**
     * The first moment of weight gradient.
     */
    protected transient ThreadLocal<DenseMatrix> weightGradientMoment1;
    /**
     * The second moment of weight gradient.
     */
    protected transient ThreadLocal<DenseMatrix> weightGradientMoment2;
    /**
     * The first moment of bias gradient.
     */
    protected transient ThreadLocal<Vector> biasGradientMoment1;
    /**
     * The second moment of bias gradient.
     */
    protected transient ThreadLocal<Vector> biasGradientMoment2;
    /**
     * The weight update.
     */
    protected transient ThreadLocal<DenseMatrix> weightUpdate;
    /**
     * The bias update.
     */
    protected transient ThreadLocal<Vector> biasUpdate;
    /**
     * The dropout mask.
     */
    protected transient ThreadLocal<byte[]> mask;

    /**
     * Constructor for input layer.
     *
     * @param n the number of neurons.
     * @param dropout the dropout rate.
     */
    Layer(int n, double dropout) {
        if (dropout < 0.0 || dropout >= 1.0) {
            throw new IllegalArgumentException("Invalid dropout rate: " + dropout);
        }

        this.n = n;
        this.p = n;
        this.dropout = dropout;

        output = ThreadLocal.withInitial(() -> Vector.zeros(Float32, n));

        if (dropout > 0.0) {
            mask = ThreadLocal.withInitial(() -> new byte[n]);
        }
    }

    /**
     * Constructor. Randomly initialized weights and zero bias.
     *
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public Layer(int n, int p) {
        this(n, p, 0.0);
    }

    /**
     * Constructor. Randomly initialized weights and zero bias.
     *
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     * @param dropout the dropout rate.
     */
    public Layer(int n, int p, double dropout) {
        this(DenseMatrix.rand(Float32, n, p, -Math.sqrt(6.0 / (n+p)), Math.sqrt(6.0 / (n+p))), Vector.zeros(Float32, n), dropout);
    }

    /**
     * Constructor.
     * @param weight the weight matrix.
     * @param bias the bias vector.
     */
    public Layer(DenseMatrix weight, Vector bias) {
        this(weight, bias, 0.0);
    }

    /**
     * Constructor.
     * @param weight the weight matrix.
     * @param bias the bias vector.
     * @param dropout the dropout rate.
     */
    public Layer(DenseMatrix weight, Vector bias, double dropout) {
        if (dropout < 0.0 || dropout >= 1.0) {
            throw new IllegalArgumentException("Invalid dropout rate: " + dropout);
        }

        this.n = weight.nrow();
        this.p = weight.ncol();
        this.weight = weight;
        this.bias = bias;
        this.dropout = dropout;

        init();
    }

    @Override
    public void close() {
        if (output != null) output.remove();
        if (outputGradient != null) outputGradient.remove();
        if (weightGradient != null) weightGradient.remove();
        if (biasGradient != null) biasGradient.remove();
        if (weightGradientMoment1 != null) weightGradientMoment1.remove();
        if (weightGradientMoment2 != null) weightGradientMoment2.remove();
        if (biasGradientMoment1 != null) biasGradientMoment1.remove();
        if (biasGradientMoment2 != null) biasGradientMoment2.remove();
        if (weightUpdate != null) weightUpdate.remove();
        if (biasUpdate != null) biasUpdate.remove();
        if (mask != null) mask.remove();
    }

    /**
     * Initializes the workspace when deserializing the object.
     * @param in the input stream.
     * @throws IOException when fails to read the stream.
     * @throws ClassNotFoundException when fails to load the class.
     */
    @Serial
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        init();
    }

    /**
     * Initializes the workspace.
     */
    private void init() {
        output = ThreadLocal.withInitial(() -> weight.vector(n));
        outputGradient = ThreadLocal.withInitial(() -> weight.vector(n));
        weightGradient = ThreadLocal.withInitial(() -> weight.zeros(n, p));
        biasGradient = ThreadLocal.withInitial(() -> weight.vector(n));
        weightGradientMoment1 = ThreadLocal.withInitial(() -> weight.zeros(n, p));
        weightGradientMoment2 = ThreadLocal.withInitial(() -> weight.zeros(n, p));
        biasGradientMoment1 = ThreadLocal.withInitial(() -> weight.vector(n));
        biasGradientMoment2 = ThreadLocal.withInitial(() -> weight.vector(n));
        weightUpdate = ThreadLocal.withInitial(() -> weight.zeros(n, p));
        biasUpdate = ThreadLocal.withInitial(() -> weight.vector(n));

        if (dropout > 0.0) {
            mask = ThreadLocal.withInitial(() -> new byte[n]);
        }
    }

    /**
     * Returns the dimension of output vector.
     * @return the dimension of output vector.
     */
    public int getOutputSize() {
        return n;
    }

    /**
     * Returns the dimension of input vector (not including bias value).
     * @return the dimension of input vector.
     */
    public int getInputSize() {
        return p;
    }

    /**
     * Returns the weight matrix.
     * @return the weight matrix.
     */
    public DenseMatrix weight() {
        return weight;
    }

    /**
     * Returns the bias vector.
     * @return the bias vector.
     */
    public Vector bias() {
        return bias;
    }

    /**
     * Returns the output vector.
     * @return the output vector.
     */
    public Vector output() {
        return output.get();
    }

    /**
     * Returns the output gradient vector.
     * @return the output gradient vector.
     */
    public Vector gradient() {
        return outputGradient.get();
    }

    /**
     * Propagates the signals from a lower layer to this layer.
     * @param x the lower layer signals.
     */
    public void propagate(Vector x) {
        Vector output = this.output.get();
        Vector.copy(bias,  0, output, 0, n);
        weight.mv(NO_TRANSPOSE, 1.0, x, 1.0, output);
        transform(output);
    }

    /**
     * Propagates the output signals through the implicit dropout layer.
     * Dropout randomly sets output units to 0. It should only be applied
     * during training.
     */
    public void propagateDropout() {
        if (dropout > 0.0) {
            Vector output = this.output.get();
            byte[] mask = this.mask.get();
            double scale = 1.0 / (1.0 - dropout);
            for (int i = 0; i < n; i++) {
                byte retain = (byte) (MathEx.random() < dropout ? 0 : 1);
                mask[i] = retain;
                output.mul(i, retain * scale);
            }
        }
    }

    /**
     * The activation or output function.
     * @param x the input and output values.
     */
    public abstract void transform(Vector x);

    /**
     * Propagates the errors back to a lower layer.
     * @param lowerLayerGradient the gradient vector of lower layer.
     */
    public abstract void backpropagate(Vector lowerLayerGradient);

    /**
     * Propagates the errors back through the (implicit) dropout layer.
     */
    public void backpopagateDropout() {
        if (dropout > 0.0) {
            Vector gradient = this.outputGradient.get();
            byte[] mask = this.mask.get();
            double scale = 1.0 / (1.0 - dropout);
            for (int i = 0; i < n; i++) {
                gradient.mul(i, mask[i] * scale);
            }
        }
    }

    /**
     * Computes the parameter gradient and update the weights.
     *
     * @param x the input vector.
     * @param learningRate the learning rate.
     * @param momentum the momentum factor.
     * @param decay weight decay factor.
     */
    public void computeGradientUpdate(Vector x, double learningRate, double momentum, double decay) {
        Vector outputGradient = this.outputGradient.get();

        if (momentum > 0.0 && momentum < 1.0) {
            DenseMatrix weightUpdate = this.weightUpdate.get();
            Vector biasUpdate = this.biasUpdate.get();

            weightUpdate.scale(momentum);
            weightUpdate.ger(learningRate, outputGradient, x);
            weight.add(weightUpdate);

            biasUpdate.add(momentum, biasUpdate, learningRate, outputGradient);
            bias.add(biasUpdate);
        } else {
            weight.ger(learningRate, outputGradient, x);
            bias.axpy(learningRate, outputGradient);
        }

        if (decay > 0.9 && decay < 1.0) {
            weight.scale(decay);
        }
    }

    /**
     * Computes the parameter gradient for a sample of (mini-)batch.
     *
     * @param x the input vector.
     */
    public void computeGradient(Vector x) {
        Vector outputGradient = this.outputGradient.get();
        DenseMatrix weightGradient = this.weightGradient.get();
        Vector biasGradient = this.biasGradient.get();

        weightGradient.ger(1.0, outputGradient, x);
        biasGradient.add(outputGradient);
    }

    /**
     * Adjust network weights by back-propagation algorithm.
     *
     * @param m the size of mini-batch.
     * @param learningRate the learning rate.
     * @param momentum the momentum factor.
     * @param decay weight decay factor.
     * @param rho RMSProp discounting factor for the history/coming gradient.
     * @param epsilon a small constant for numerical stability.
     */
    public void update(int m, double learningRate, double momentum, double decay, double rho, double epsilon) {
        DenseMatrix weightGradient = this.weightGradient.get();
        Vector biasGradient = this.biasGradient.get();

        // Instead of computing the average gradient explicitly,
        // we scale down the learning rate by the number of samples.
        double eta = learningRate / m;

        if (rho > 0.0 && rho < 1.0) {
            // As gradient will be averaged and smoothed in RMSProp,
            // we need to use the original learning rate.
            eta = learningRate;
            weightGradient.scale(1.0 / m);
            biasGradient.scale(1.0 / m);

            DenseMatrix rmsWeightGradient = this.weightGradientMoment2.get();
            Vector rmsBiasGradient = this.biasGradientMoment2.get();

            double rho1 = 1.0 - rho;
            for (int j = 0; j < p; j++) {
                for (int i = 0; i < n; i++) {
                    rmsWeightGradient.set(i, j, rho * rmsWeightGradient.get(i, j) + rho1 * MathEx.pow2(weightGradient.get(i, j)));
                }
            }
            for (int i = 0; i < n; i++) {
                rmsBiasGradient.set(i, rho * rmsBiasGradient.get(i) + rho1 * MathEx.pow2(biasGradient.get(i)));
            }

            for (int j = 0; j < p; j++) {
                for (int i = 0; i < n; i++) {
                    weightGradient.div(i, j, Math.sqrt(epsilon + rmsWeightGradient.get(i, j)));
                }
            }
            for (int i = 0; i < n; i++) {
                biasGradient.div(i, Math.sqrt(epsilon + rmsBiasGradient.get(i)));
            }
        }

        if (momentum > 0.0 && momentum < 1.0) {
            DenseMatrix weightUpdate = this.weightUpdate.get();
            Vector biasUpdate = this.biasUpdate.get();

            weightUpdate.add(momentum, weightUpdate, eta, weightGradient);
            biasUpdate.add(momentum, biasUpdate, eta, biasGradient);
            weight.add(weightUpdate);
            bias.add(biasUpdate);
        } else {
            weight.axpy(eta, weightGradient);
            bias.axpy(eta, biasGradient);
        }

        // Weight decay as the weights are multiplied
        // by a factor slightly less than 1. This prevents the weights
        // from growing too large, and can be seen as gradient descent
        // on a quadratic regularization term.
        if (decay > 0.9 && decay < 1.0) {
            weight.scale(decay);
        }

        weightGradient.fill(0.0);
        biasGradient.fill(0.0);
    }

    /**
     * Returns a hidden layer.
     * @param activation the activation function.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @param param the optional activation function parameter.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder builder(String activation, int neurons, double dropout, double param) {
        switch(activation.toLowerCase(Locale.ROOT)) {
            case "relu":
                return rectifier(neurons, dropout);
            case "sigmoid":
                return sigmoid(neurons, dropout);
            case "tanh":
                return tanh(neurons, dropout);
            case "linear":
                return linear(neurons, dropout);
            case "leaky":
                if (Double.isNaN(param))
                    return leaky(neurons, dropout);
                else
                    return leaky(neurons, dropout, param);
            default:
                throw new IllegalArgumentException("Unsupported activation function: " + activation);
        }
    }

    /**
     * Returns an input layer.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static LayerBuilder input(int neurons) {
        return input(neurons, 0.0);
    }

    /**
     * Returns an input layer.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @return the layer builder.
     */
    public static LayerBuilder input(int neurons, double dropout) {
        return new LayerBuilder(neurons, dropout) {
            @Override
            public InputLayer build(int p) {
                return new InputLayer(neurons, dropout);
            }
        };
    }

    /**
     * Returns a hidden layer with linear activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder linear(int neurons) {
        return linear(neurons, 0.0);
    }

    /**
     * Returns a hidden layer with linear activation function.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder linear(int neurons, double dropout) {
        return new HiddenLayerBuilder(neurons, dropout, ActivationFunction.linear());
    }

    /**
     * Returns a hidden layer with rectified linear activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder rectifier(int neurons) {
        return rectifier(neurons, 0.0);
    }

    /**
     * Returns a hidden layer with rectified linear activation function.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder rectifier(int neurons, double dropout) {
        return new HiddenLayerBuilder(neurons, dropout, ActivationFunction.rectifier());
    }

    /**
     * Returns a hidden layer with leaky rectified linear activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder leaky(int neurons) {
        return rectifier(neurons, 0.0);
    }

    /**
     * Returns a hidden layer with leaky rectified linear activation function.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder leaky(int neurons, double dropout) {
        return new HiddenLayerBuilder(neurons, dropout, ActivationFunction.leaky());
    }

    /**
     * Returns a hidden layer with leaky rectified linear activation function.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @param a the parameter of leaky ReLU.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder leaky(int neurons, double dropout, double a) {
        return new HiddenLayerBuilder(neurons, dropout, ActivationFunction.leaky(a));
    }

    /**
     * Returns a hidden layer with sigmoid activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder sigmoid(int neurons) {
        return sigmoid(neurons, 0.0);
    }

    /**
     * Returns a hidden layer with sigmoid activation function.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder sigmoid(int neurons, double dropout) {
        return new HiddenLayerBuilder(neurons, dropout, ActivationFunction.sigmoid());
    }

    /**
     * Returns a hidden layer with hyperbolic tangent activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder tanh(int neurons) {
        return tanh(neurons, 0.0);
    }

    /**
     * Returns a hidden layer with hyperbolic tangent activation function.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder tanh(int neurons, double dropout) {
        return new HiddenLayerBuilder(neurons, dropout, ActivationFunction.tanh());
    }

    /**
     * Returns an output layer with mean squared error cost function.
     * @param neurons the number of neurons.
     * @param output the output function.
     * @return the layer builder.
     */
    public static OutputLayerBuilder mse(int neurons, OutputFunction output) {
        return new OutputLayerBuilder(neurons, output, Cost.MEAN_SQUARED_ERROR);
    }

    /**
     * Returns an output layer with (log-)likelihood cost function.
     * @param neurons the number of neurons.
     * @param output the output function.
     * @return the layer builder.
     */
    public static OutputLayerBuilder mle(int neurons, OutputFunction output) {
        return new OutputLayerBuilder(neurons, output, Cost.LIKELIHOOD);
    }

    /**
     * Returns the layer builders given a string representation such as
     * "Input(10, 0.2)|ReLU(50, 0.5)|Sigmoid(30, 0.5)|...".
     *
     * @param k the number of classes. {@code k < 2} for regression.
     * @param p the number of input variables (not including bias value).
     * @param spec the hidden layer specification.
     * @return the layer builders.
     */
    public static LayerBuilder[] of(int k, int p, String spec) {
        Pattern regex = Pattern.compile(String.format("(\\w+)\\((%s)(,\\s*(%s))?(,\\s*(%s))?\\)", Regex.INTEGER_REGEX, Regex.DOUBLE_REGEX, Regex.DOUBLE_REGEX));
        String[] layers = spec.split("\\|");
        ArrayList<LayerBuilder> builders = new ArrayList<>();
        for (int i = 0; i < layers.length; i++) {
            Matcher m = regex.matcher(layers[i]);
            if (m.matches()) {
                String activation = m.group(1);
                int neurons = Integer.parseInt(m.group(2));
                double dropout = 0.0;
                if (m.group(3) != null) {
                    dropout = Double.parseDouble(m.group(4));
                }

                double param = Double.NaN; // activation function parameter
                if (m.group(5) != null) {
                    param = Double.parseDouble(m.group(6));
                }

                if (i == 0) {
                    if (activation.equalsIgnoreCase("input")) {
                        builders.add(Layer.input(neurons, dropout));
                    } else {
                        builders.add(Layer.input(p));
                        builders.add(Layer.builder(activation, neurons, dropout, param));
                    }
                } else {
                    builders.add(Layer.builder(activation, neurons, dropout, param));
                }
            } else {
                throw new IllegalArgumentException("Invalid layer: " + layers[i]);
            }
        }

        if (k < 2) {
            builders.add(Layer.mse(1, OutputFunction.LINEAR));
        } else if (k == 2) {
            builders.add(Layer.mle(1, OutputFunction.SIGMOID));
        } else {
            builders.add(Layer.mle(k, OutputFunction.SOFTMAX));
        }

        return builders.toArray(new LayerBuilder[0]);
    }
}