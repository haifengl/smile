package smile.feature.extraction;

import smile.data.DataFrame;
import smile.manifold.KPCA;
import smile.math.kernel.MercerKernel;

import java.io.Serial;

/**
 * Kernel PCA transform. Kernel PCA is an extension of
 * principal component analysis (PCA) using techniques of kernel methods.
 * Using a kernel, the originally linear operations of PCA are done in a
 * reproducing kernel Hilbert space with a non-linear mapping.
 * <p>
 * In practice, a large data set leads to a large Kernel/Gram matrix K, and
 * storing K may become a problem. One way to deal with this is to perform
 * clustering on your large dataset, and populate the kernel with the means
 * of those clusters. Since even this method may yield a relatively large K,
 * it is common to compute only the top P eigenvalues and eigenvectors of K.
 * <p>
 * Kernel PCA with an isotropic kernel function is closely related to metric MDS.
 * Carrying out metric MDS on the kernel matrix K produces an equivalent configuration
 * of points as the distance (2(1 - K(x<sub>i</sub>, x<sub>j</sub>)))<sup>1/2</sup>
 * computed in feature space.
 * <p>
 * Kernel PCA also has close connections with Isomap, LLE, and Laplacian eigenmaps.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Bernhard Scholkopf, Alexander Smola, and Klaus-Robert Muller. Nonlinear Component Analysis as a Kernel Eigenvalue Problem. Neural Computation, 1998.</li>
 * </ol>
 *
 * @see smile.feature.extraction.PCA
 * @see smile.math.kernel.MercerKernel
 * @see smile.manifold.KPCA
] *
 * @author Haifeng Li
 */
public class KernelPCA extends Projection {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Kernel PCA.
     */
    public final KPCA<double[]> kpca;

    /**
     * Constructor.
     * @param kpca kernel PCA object.
     * @param columns the columns to fit kernel PCA. If empty, all columns
     *                will be used.
     */
    public KernelPCA(KPCA<double[]> kpca, String... columns) {
        super(kpca.projection(), "KPCA", columns);
        this.kpca = kpca;
    }

    /**
     * Fits kernel principal component analysis.
     * @param data training data.
     * @param kernel Mercer kernel.
     * @param options kernel PCA hyperparameters.
     * @param columns the columns to fit kernel PCA. If empty, all columns
     *                will be used.
     * @return the model.
     */
    public static KernelPCA fit(DataFrame data, MercerKernel<double[]> kernel, KPCA.Options options, String... columns) {
        double[][] x = data.toArray(columns);
        KPCA<double[]> kpca = KPCA.fit(x, kernel, options);
        return new KernelPCA(kpca, columns);
    }

    @Override
    public double[] apply(double[] x) {
        return kpca.apply(x);
    }
}
