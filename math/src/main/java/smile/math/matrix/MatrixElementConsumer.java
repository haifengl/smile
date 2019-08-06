package smile.math.matrix;

/**
 * Functional interface for lambda iteration through a sparse matrix.
 */
public interface MatrixElementConsumer {
    void accept(int row, int column, double value);
}
