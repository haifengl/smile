/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

/**
 * A kernel matrix of dataset is the array of k(x<sub>i</sub>, x<sub>j</sub>).
 * Various cache strategies can be used to reduce the evaluations of kernel
 * functions during training a kernel machine such as SVM.
 *
 * @author Haifeng Li
 */
public interface KernelMatrix {

    /**
     * Returns the element k(x<sub>i</sub>, x<sub>j</sub>) of kernel matrix for
     * a given dataset.
     */
    public double k(int i, int j);
}
