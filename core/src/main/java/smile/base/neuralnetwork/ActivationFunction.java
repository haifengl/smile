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

package smile.base.neuralnetwork;

/**
 * The activation function in artificial neural networks. The activation
 * function of a node defines the output of that node given an input or
 * set of inputs.
 *
 * @author Haifeng Li
 */
public enum ActivationFunction {
    /**
     * The rectifier activation function max(0, x). It is introduced
     * with strong biological motivations and mathematical justifications.
     * The rectifier is the most popular activation function for deep
     * neural networks. A unit employing the rectifier is called a
     * rectified linear unit (ReLU).
     */
    RECTIFIER,

    /**
     * Logistic sigmoid activation function: sigma(v)=1/(1+exp(-v)).
     * For multi-class classification, each unit in output layer
     * corresponds to a class. For binary classification and cross
     * entropy error function, there is only one output unit whose
     * value can be regarded as posteriori probability.
     */
    LOGISTIC_SIGMOID,

    /**
     * Hyperbolic tangent activation function. The tanh function is a
     * rescaling of the logistic sigmoid, such that its outputs range
     * from -1 to 1.
     */
    TANH,

    /**
     * Linear activation function.
     */
    LINEAR,

    /**
     * Softmax activation for multi-class cross entropy objection function.
     * The values of units in output layer can be regarded as posteriori
     * probabilities of each class.
     */
    SOFTMAX
}
