/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import smile.demo.classification.FLDDemo;
import smile.demo.classification.LDADemo;
import smile.demo.classification.LogisticRegressionDemo;
import smile.demo.classification.NeuralNetworkDemo;
import smile.demo.classification.QDADemo;
import smile.demo.classification.RBFNetworkDemo;
import smile.demo.classification.RDADemo;
import smile.demo.classification.SVMDemo;
import smile.demo.clustering.CLARANSDemo;
import smile.demo.clustering.DBScanDemo;
import smile.demo.clustering.DENCLUEDemo;
import smile.demo.clustering.DeterministicAnnealingDemo;
import smile.demo.clustering.GMeansDemo;
import smile.demo.clustering.HierarchicalClusteringDemo;
import smile.demo.clustering.KMeansDemo;
import smile.demo.clustering.MECDemo;
import smile.demo.clustering.NeuralGasDemo;
import smile.demo.clustering.SIBDemo;
import smile.demo.clustering.SOMDemo;
import smile.demo.clustering.SpectralClusteringDemo;
import smile.demo.clustering.XMeansDemo;
import smile.demo.manifold.TSNEDemo;
import smile.demo.plot.BarPlotDemo;
import smile.demo.plot.BoxPlotDemo;
import smile.demo.plot.ContourDemo;
import smile.demo.plot.GridDemo;
import smile.demo.plot.HeatmapDemo;
import smile.demo.plot.HexmapDemo;
import smile.demo.plot.HistogramDemo;
import smile.demo.plot.LinePlotDemo;
import smile.demo.plot.QQPlotDemo;
import smile.demo.plot.ScatterPlotDemo;
import smile.demo.plot.StaircasePlotDemo;
import smile.demo.plot.SurfaceDemo;
import smile.demo.plot.SparseMatrixPlotDemo;
import smile.demo.interpolation.Interpolation1Demo;
import smile.demo.interpolation.Interpolation2Demo;
import smile.demo.interpolation.LaplaceInterpolationDemo;
import smile.demo.interpolation.ScatterDataInterpolationDemo;
import smile.demo.manifold.IsoMapDemo;
import smile.demo.manifold.LLEDemo;
import smile.demo.manifold.LaplacianEigenmapDemo;
import smile.demo.mds.IsotonicMDSDemo;
import smile.demo.vq.BIRCHDemo;
import smile.demo.vq.GrowingNeuralGasDemo;
import smile.demo.vq.NeuralMapDemo;
import smile.demo.mds.MDSDemo;
import smile.demo.mds.SammonMappingDemo;
import smile.demo.neighbor.ApproximateStringSearchDemo;
import smile.demo.neighbor.KNNDemo;
import smile.demo.neighbor.NearestNeighborDemo;
import smile.demo.neighbor.RNNSearchDemo;
import smile.demo.plot.Histogram3Demo;
import smile.demo.projection.GHADemo;
import smile.demo.projection.KPCADemo;
import smile.demo.projection.PCADemo;
import smile.demo.projection.PPCADemo;
import smile.demo.projection.RandomProjectionDemo;
import smile.demo.stat.distribution.BernoulliDistributionDemo;
import smile.demo.stat.distribution.BetaDistributionDemo;
import smile.demo.stat.distribution.BinomialDistributionDemo;
import smile.demo.stat.distribution.ChiSquareDistributionDemo;
import smile.demo.stat.distribution.EmpiricalDistributionDemo;
import smile.demo.stat.distribution.ExponentialDistributionDemo;
import smile.demo.stat.distribution.ExponentialFamilyMixtureDemo;
import smile.demo.stat.distribution.FDistributionDemo;
import smile.demo.stat.distribution.GammaDistributionDemo;
import smile.demo.stat.distribution.GaussianDistributionDemo;
import smile.demo.stat.distribution.GaussianMixtureDemo;
import smile.demo.stat.distribution.GeometricDistributionDemo;
import smile.demo.stat.distribution.HyperGeometricDistributionDemo;
import smile.demo.stat.distribution.LogNormalDistributionDemo;
import smile.demo.stat.distribution.LogisticDistributionDemo;
import smile.demo.stat.distribution.MultivariateGaussianDistributionDemo;
import smile.demo.stat.distribution.MultivariateGaussianMixtureDemo;
import smile.demo.stat.distribution.NegativeBinomialDistributionDemo;
import smile.demo.stat.distribution.PoissonDistributionDemo;
import smile.demo.stat.distribution.ShiftedGeometricDistributionDemo;
import smile.demo.stat.distribution.TDistributionDemo;
import smile.demo.stat.distribution.WeibullDistributionDemo;
import smile.demo.wavelet.BestLocalizedWaveletDemo;
import smile.demo.wavelet.CoifletWaveletDemo;
import smile.demo.wavelet.D4WaveletDemo;
import smile.demo.wavelet.DaubechiesWaveletDemo;
import smile.demo.wavelet.HaarWaveletDemo;
import smile.demo.wavelet.SymletWaveletDemo;

@SuppressWarnings("serial")
public class SmileDemo extends JPanel implements TreeSelectionListener {
    private JTree tree;
    private JTextArea logPane;
    private JSplitPane workspace;

    public SmileDemo() {
        super(new GridLayout(1,0));

        //Create the nodes.
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("Smile");
        createNodes(top);

        //Create a tree that allows one selection at a time.
        tree = new JTree(top);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        //Listen for when the selection changes.
        tree.addTreeSelectionListener(this);

        //Create the scroll pane and add the tree to it.
        JScrollPane treeView = new JScrollPane(tree);

        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setBackground(Color.white);

        //Create the log pane.
        logPane = new JTextArea();
        logPane.setEditable(false);
        JScrollPane logView = new JScrollPane(logPane);
        redirectSystemStreams();

        workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        workspace.setTopComponent(placeholder);
        workspace.setBottomComponent(logView);
        workspace.setDividerLocation(700);

        //Add the scroll panes to a split pane.
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(treeView);
        splitPane.setRightComponent(workspace);

        Dimension minimumSize = new Dimension(100, 50);
        placeholder.setMinimumSize(minimumSize);
        logView.setMinimumSize(minimumSize);
        workspace.setMinimumSize(minimumSize);
        treeView.setMinimumSize(minimumSize);
        splitPane.setDividerLocation(300);
        splitPane.setPreferredSize(new Dimension(1000, 800));

        //Add the split pane to this panel.
        add(splitPane);
    }

    /** Required by TreeSelectionListener interface. */
    @Override
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

        if (node != null && node.isLeaf()) {
            int pos = workspace.getDividerLocation();
            workspace.setTopComponent((JPanel) node.getUserObject());
            workspace.setDividerLocation(pos);
        }
    }

    private void createNodes(DefaultMutableTreeNode top) {
        DefaultMutableTreeNode category = null;
        DefaultMutableTreeNode category2 = null;
        DefaultMutableTreeNode algorithm = null;

        category = new DefaultMutableTreeNode("Feature Extraction");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new PCADemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new KPCADemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new PPCADemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new GHADemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new smile.demo.projection.LDADemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new RandomProjectionDemo());
        category.add(algorithm);

        category = new DefaultMutableTreeNode("Multi-Dimensional Scaling");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new MDSDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new SammonMappingDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new IsotonicMDSDemo());
        category.add(algorithm);

        category = new DefaultMutableTreeNode("Manifold Learning");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new IsoMapDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new LLEDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new LaplacianEigenmapDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new TSNEDemo());
        category.add(algorithm);

        category = new DefaultMutableTreeNode("Classification");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new smile.demo.classification.KNNDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new LDADemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new FLDDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new QDADemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new RDADemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new LogisticRegressionDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new NeuralNetworkDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new RBFNetworkDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new SVMDemo());
        category.add(algorithm);

        category = new DefaultMutableTreeNode("Clustering");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new HierarchicalClusteringDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new KMeansDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new XMeansDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new GMeansDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new DeterministicAnnealingDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new SOMDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new NeuralGasDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new CLARANSDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new DBScanDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new DENCLUEDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new SpectralClusteringDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new MECDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new SIBDemo());
        category.add(algorithm);

        category = new DefaultMutableTreeNode("Vector Quantization");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new GrowingNeuralGasDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new NeuralMapDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new BIRCHDemo());
        category.add(algorithm);


        category = new DefaultMutableTreeNode("Interpolation");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new Interpolation1Demo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new Interpolation2Demo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new ScatterDataInterpolationDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new LaplaceInterpolationDemo());
        category.add(algorithm);

        category = new DefaultMutableTreeNode("Nearest Neighbor Search");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new NearestNeighborDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new KNNDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new RNNSearchDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new ApproximateStringSearchDemo());
        category.add(algorithm);

        category = new DefaultMutableTreeNode("Distributions");
        top.add(category);

        category2 = new DefaultMutableTreeNode("Discrete");
        category.add(category2);

        algorithm = new DefaultMutableTreeNode(new BernoulliDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new BinomialDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new GeometricDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new ShiftedGeometricDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new HyperGeometricDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new EmpiricalDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new NegativeBinomialDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new PoissonDistributionDemo());
        category2.add(algorithm);

        category2 = new DefaultMutableTreeNode("Continuous");
        category.add(category2);

        algorithm = new DefaultMutableTreeNode(new BetaDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new ChiSquareDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new ExponentialDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new FDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new GammaDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new GaussianDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new LogNormalDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new LogisticDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new TDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new WeibullDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new GaussianMixtureDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new ExponentialFamilyMixtureDemo());
        category2.add(algorithm);

        category2 = new DefaultMutableTreeNode("Multivariate");
        category.add(category2);

        algorithm = new DefaultMutableTreeNode(new MultivariateGaussianDistributionDemo());
        category2.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new MultivariateGaussianMixtureDemo());
        category2.add(algorithm);

        category = new DefaultMutableTreeNode("Wavelet");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new HaarWaveletDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new D4WaveletDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new DaubechiesWaveletDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new CoifletWaveletDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new SymletWaveletDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new BestLocalizedWaveletDemo());
        category.add(algorithm);

        category = new DefaultMutableTreeNode("Graphics");
        top.add(category);

        algorithm = new DefaultMutableTreeNode(new ScatterPlotDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new LinePlotDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new StaircasePlotDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new BarPlotDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new BoxPlotDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new HistogramDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new Histogram3Demo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new HeatmapDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new HexmapDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new ContourDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new QQPlotDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new GridDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new SurfaceDemo());
        category.add(algorithm);

        algorithm = new DefaultMutableTreeNode(new SparseMatrixPlotDemo());
        category.add(algorithm);
    }

    private void updateTextArea(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logPane.append(text);
            }
        });
    }

    private void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                updateTextArea(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                updateTextArea(new String(b, off, len));
            }

            @Override
            public void write(byte[] b) throws IOException {
                write(b, 0, b.length);
            }
        };

        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }
    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event dispatch thread.
     */
    public static void createAndShowGUI(boolean exitOnClose) {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ex) {
            try {
                // If Nimbus is not available, try system look and feel.
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println(e);
            }
        }

        //Create and set up the window.
        JFrame frame = new JFrame("Smile Demo");
        frame.setSize(new Dimension(1000, 1000));
        frame.setLocationRelativeTo(null);

        if (exitOnClose)
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        else
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        //Add content to the window.
        frame.add(new SmileDemo());

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        //Schedule a job for the event dispatch thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                createAndShowGUI(true);
            }
        });
    }
}

