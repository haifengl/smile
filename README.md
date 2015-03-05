SmileMiner
==========

SmileMiner (Statistical Machine Intelligence and Learning Engine) is a set of pure Java libraries of various state-of-art machine learning algorithms. SmileMiner is self contained and requires only Java standard library.

SmileMiner is well documented and you can browse the <a href="http://haifengl.github.io/smile/doc/index.html">javadoc</a> for more information. A basic tutorial is available on the project <a href="http://github.com/haifengl/smile/wiki/Tutorial:-A-Gentle-Introduction-of-SmileMiner">wiki</a>.

To see SmileMiner in action, please download the <a href="http://haifengl.github.io/smile/smile-demo.jar">demo</a> jar file and then run <tt>java -jar smile-demo.jar</tt>.

* Classification:
Support Vector Machines, Decision Trees, AdaBoost, Gradient Boosting, Random Forest, Logistic Regression, Neural Networks, RBF Networks, Maximum Entropy Classifier, KNN, Naïve Bayesian, Fisher/Linear/Quadratic/Regularized Discriminant Analysis.

* Regression:
Support Vector Regression, Gaussian Process, Regression Trees, Gradient Boosting, Random Forest, RBF Networks, OLS, LASSO, Ridge Regression.

* Feature Selection:
Genetic Algorithm based Feature Selection, Ensemble Learning based Feature Selection, Signal Noise ratio, Sum Squares ratio.

* Clustering:
BIRCH, CLARANS, DBScan, DENCLUE, Deterministic Annealing, K-Means, X-Means, G-Means, Neural Gas, Growing Neural Gas, Hierarchical Clustering, Sequential Information Bottleneck, Self-Organizing Maps, Spectral Clustering, Minimum Entropy Clustering.

* Association Rule & Frequent Itemset Mining:
FP-growth mining algorithm

* Manifold learning:
IsoMap, LLE, Laplacian Eigenmap, PCA, Kernel PCA, Probabilistic PCA, GHA, Random Projection

* Multi-Dimensional Scaling:
Classical MDS, Isotonic MDS, Sammon Mapping

* Nearest Neighbor Search:
BK-Tree, Cover Tree, KD-Tree, LSH

* Sequence Learning:
Hidden Markov Model.

SmilePlot
=========

SmileMiner also has a Swing-based data visualization library SmilePlot, which provides scatter plot, line plot, staircase plot, bar plot, box plot, histogram, 3D histogram, dendrogram, heatmap, hexmap, QQ plot, contour plot, surface, and wireframe. The class PlotCanvas provides builtin functions such as zoom in/out, export, print, customization, etc.

SmilePlot requires SwingX library for JXTable. But if your environment cannot use SwingX, it is easy to remove this dependency by using JTable.

Demo Gallery
============
<table class="center">
  <tr>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-kpca.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-kpca.png" alt="Kernel PCA" width="100%"></a>
        <figcaption>Kernel PCA</figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-isomap.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-isomap.png" alt="IsoMap" width="100%"></a>
        <figcaption>IsoMap</figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-mds.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-mds.png" alt="MDS" width="100%"></a>
        <figcaption>Multi-Dimensional Scaling</figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-som.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-som.png" alt="SOM" width="100%"></a>
        <figcaption>SOM</figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-ann.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-ann.png" alt="Neural Network" width="100%"></a>
        <figcaption>Neural Network</figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-svm.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-svm.png" alt="SVM" width="100%"></a>
        <figcaption>SVM</figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-agglomerative-clustering.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-agglomerative-clustering.png" alt="Agglomerative Clustering" width="100%"></a>
        <figcaption>Agglomerative Clustering</figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-xmeans.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-xmeans.png" alt="X-Means" width="100%"></a>
        <figcaption>X-Means</figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-dbscan.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-dbscan.png" alt="DBScan" width="100%"></a>
        <figcaption>DBScan</figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-neural-gas.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-neural-gas.png" alt="Neural Gas" width="100%"></a>
        <figcaption>Neural Gas</figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-wavelet.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-wavelet.png" alt="Wavelet" width="100%"></a>
        <figcaption>Wavelet</figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="http://haifengl.github.io/smile/gallery/smile-demo-mixture.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-mixture.png" alt="Mixture" width="100%"></a>
        <figcaption>Exponential Family Mixture</figcaption>
      </figure>
    </td>
  </tr>
</table>
