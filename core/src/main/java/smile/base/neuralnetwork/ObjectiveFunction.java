package smile.base.neuralnetwork;

/**
 * Neural network objective/error function.
 */
public enum ObjectiveFunction {
    /**
     * Least mean squares error function.
     */
    LEAST_MEAN_SQUARES,

    /**
     * Cross entropy error function for output as probabilities.
     */
    CROSS_ENTROPY
}