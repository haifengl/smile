package smile.regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import smile.base.cart.Node;
import smile.base.cart.InternalNode;
import smile.base.cart.LeafNode;
import smile.base.cart.RegressionNode;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructType;

/**
 * SHAP is a recently proposed algorithm for interpretable machine learning by
 * 
 * <pre>
 * Lundberg, Scott M., and Su-In Lee. “A unified approach to interpreting model predictions.” Advances in Neural Information Processing Systems. 2017
 * </pre>
 * 
 * and tree-SHAP is a specific version of implementation for tree-based machine
 * learning like CART, Random Forest, Gradient Boost, etc.
 * 
 * <pre>
 * Lundberg, Scott M., Gabriel G. Erion, and Su-In Lee. “Consistent individualized feature attribution for tree ensembles.” arXiv preprint arXiv:1802.03888 (2018)
 * </pre>
 * 
 * implementation reference as below:
 * 
 * <pre>
 * https://github.com/slundberg/shap/blob/master/shap/explainers/pytree.py
 * </pre>
 * 
 * support only {@link RegressionTree} or ensemble based on it like
 * {@link smile.regression.GradientTreeBoost}
 * <p>
 * 
 * @author ray
 * @see
 * 
 * <pre>
 * https://christophm.github.io/interpretable-ml-book/shap.html
 * </pre>
 *
 */
public class TreeShapImportance {

	final static int threadNum = Runtime.getRuntime().availableProcessors();

	/** explainer to output shap value */
	public static class TreeShapExplainer {

		private int maxd;
		private ShapTree[] trees;
		private boolean normalize = false;

		/** constructor with tree ensemble */
		public TreeShapExplainer(RegressionTree[] trees) {
			this(trees, false);
		}

		/**
		 * constructor with tree ensemble and normalization choice (if true, shap value
		 * will be normalized)
		 */
		public TreeShapExplainer(RegressionTree[] trees, boolean normalize) {
			this.normalize = normalize;
			this.trees = new ShapTree[trees.length];

			final CountDownLatch concurrencySentinel = new CountDownLatch(trees.length);
			final ExecutorService exec = Executors.newFixedThreadPool(Math.max(threadNum, 1));

			List<ConcurrentShapConstruction> concurrencyRunnables = new ArrayList<ConcurrentShapConstruction>(
					trees.length);
			for (int i = 0; i < trees.length; i++) {
				ConcurrentShapConstruction shapRunnable = new ConcurrentShapConstruction(trees[i], concurrencySentinel,
						this.normalize);
				exec.submit(shapRunnable);
				concurrencyRunnables.add(shapRunnable);
			}

			try {
				System.out.println("----- waiting for all shap trees get created -----");
				concurrencySentinel.await();
				System.out.println("----- all shap trees built complete for this model -----");
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("error creating shap for trees:", e);
			}

			for (int i = 0; i < concurrencyRunnables.size(); i++) {
				this.trees[i] = concurrencyRunnables.get(i).shapTree;
			}

			for (ShapTree t : this.trees) {
				if (t.max_depth > maxd) {
					maxd = t.max_depth;
				}
			}

			// Preallocate space for the unique path data
			maxd += 2;
		}

		/**
		 * return shap value for given data instance, note this might take a fairly
		 * large memory if the dataset is big.
		 * <p>
		 * This is useful if you want to have deep insight about effect of each feature
		 * over prediction by using returned shap value for every data instance to draw
		 * a summary plot between each feature and prediction
		 * 
		 * @param x data {@link Tuple}
		 */
		public double[] shapValues(Tuple x) {
			List<ShapCalculationTemp> calcTemps = new ArrayList<ShapCalculationTemp>();
			calcTemps.add(new ShapCalculationTemp((maxd * (maxd + 1))));
			return shapValues(x, false, calcTemps);
		}

		/**
		 * return shap value (possibly absolute value if chosen) for given data instance
		 * 
		 * @param x              data {@link Tuple}
		 * @param returnAbsolute true if you want the feature importance, just take
		 *                       absolute shap values for every instance, average by
		 *                       feature and then sort, default false
		 * @param calcTemps      calculation registers, one for each tree if ensemble
		 *                       model
		 * @param concurrent     if true then use muti-thread for tree ensemble
		 *                       otherwise run in single thread
		 */
		private double[] shapValues(Tuple x, boolean returnAbsolute, List<ShapCalculationTemp> calcTemps) {
			int length = x.schema().length();
			double[] phi = new double[length + 1];

			int treeLength = this.trees.length;
			for (int tIdx = 0; tIdx < treeLength; tIdx++) {
				try {
					treeShap(this.trees[tIdx], x, phi, 0.0, 0, calcTemps.get(0));
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("error calculating shap for instance: " + x + " using " + tIdx + "th sub-tree.");
				}
			}

			for (int i = 0; i < phi.length; i++) {
				phi[i] /= (double) treeLength;
			}

			if (returnAbsolute) {
				for (int i = 0; i < phi.length; i++) {
					phi[i] = Math.abs(phi[i]);
				}
			}

			return phi;
		}

		/**
		 * return the shap importance for the given model by taking absolute shap values
		 * for every instance and average by feature.
		 * <p>
		 * Note it might take a fairly long time if the dataset is big and the
		 * underlying model is complex (many sub-trees, many nodes, large depth, etc)
		 * 
		 */
		public double[] shapImportance(StructType schema, DataFrame data) {
			double[] meanShap = new double[schema.length()];
			Arrays.setAll(meanShap, num -> 0.0);

			int concurrencyLevel = (data.size() / ConcurrentShapBatchCalculation.operSize) + 1;

			final CountDownLatch concurrencySentinel = new CountDownLatch(concurrencyLevel);
			final ExecutorService exec = Executors.newFixedThreadPool(Math.max(threadNum, 1));

			long startTime = System.currentTimeMillis();
			System.out.println("--- start calculation for data ---");

			List<ConcurrentShapBatchCalculation> operRunnables = new ArrayList<ConcurrentShapBatchCalculation>(
					concurrencyLevel);
			for (int xi = 0; xi < data.size(); xi += ConcurrentShapBatchCalculation.operSize) {
				int endIdx = (xi + ConcurrentShapBatchCalculation.operSize);
				ConcurrentShapBatchCalculation r = new ConcurrentShapBatchCalculation(this, data, concurrencySentinel,
						xi, (endIdx > data.size() ? data.size() : endIdx));
				operRunnables.add(r);
				exec.submit(r);
			}

			try {
				concurrencySentinel.await();
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("error calculating shap values for dataframe:", e);
			}

			System.out.println("--- complete calculation for " + data.size() + " data, took "
					+ (System.currentTimeMillis() - startTime) / 1000 + " seconds -----");

			for (ConcurrentShapBatchCalculation r : operRunnables) {
				for (int i = 0; i < meanShap.length; i++) {
					meanShap[i] += r.means[i];
				}
			}

			System.out.println("--- shap importance ---");
			for (int i = 0; i < meanShap.length; i++) {
				System.out.format("%-15s %.4f%n", schema.fieldName(i), (meanShap[i] / (double) data.size()));
			}

			return meanShap;
		}

		/**************************************************************************
		 * speed up the conversion from smile regression tree to shap structure
		 **************************************************************************
		 */
		private static class ConcurrentShapConstruction implements Runnable {
			private ShapTree shapTree;
			private RegressionTree tree;
			private CountDownLatch concurrencySentinel;
			private boolean normalize;

			ConcurrentShapConstruction(RegressionTree tree, CountDownLatch concurrencySentinel, boolean normalize) {
				this.tree = tree;
				this.concurrencySentinel = concurrencySentinel;
				this.normalize = normalize;
			}

			public void run() {
				try {
					this.shapTree = new ShapTree(this.tree, this.normalize);
					if (this.concurrencySentinel != null) {
						this.concurrencySentinel.countDown();
					}
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException("error creating shap structure for regression tree:", e);
				}
			}
		}

		/**************************************************************************
		 * speed up shap calculation for data instance in concurrent batches
		 **************************************************************************
		 */
		private static class ConcurrentShapBatchCalculation implements Runnable {

			/** data batch size in shap calculation concurrency */
			static final int operSize = 500;

			/** inclusive */
			private int startIdx;
			/** exclusive */
			private int endIdx;

			private CountDownLatch concurrencySentinel;
			private DataFrame data;
			private TreeShapExplainer explainer;
			private double[] means;
			private List<ShapCalculationTemp> calcTemp;

			ConcurrentShapBatchCalculation(TreeShapExplainer explainer, DataFrame data,
					CountDownLatch concurrencySentinel, int startIdx, int endIdx) {
				this.concurrencySentinel = concurrencySentinel;
				this.data = data;
				this.explainer = explainer;
				this.startIdx = startIdx;
				this.endIdx = endIdx;

				this.calcTemp = new ArrayList<ShapCalculationTemp>(1);
				this.calcTemp.add(new ShapCalculationTemp(explainer.maxd * (explainer.maxd + 1)));

				means = new double[data.schema().length()];
				Arrays.setAll(means, num -> 0.0);
			}

			@Override
			public void run() {
				for (int xi = startIdx; xi < endIdx; xi++) {

					if (((xi - startIdx) + 1) % operSize == 0) {
						System.out.print("*");
					}

					Tuple xx = null;
					try {
						xx = data.get(xi);

						double[] shapValues = explainer.shapValues(xx, true, calcTemp);

						for (int i = 0; i < means.length; i++) {
							means[i] += shapValues[i];
						}
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("error calculating shap values for instance: " + xx);
					}
				}

				if (concurrencySentinel != null) {
					concurrencySentinel.countDown();
				}

				System.out.println("complete shap value calculation for data instances at indexes:[" + startIdx + ","
						+ endIdx + ")");
			}
		}

		/**
		 * recyclable class for shap calculation to avoid mass instantiation in memory
		 */
		private static class ShapCalculationTemp {

			private int s;
			private List<MutableInteger> feature_indexes;
			private List<MutableDouble> zero_fractions;
			private List<MutableDouble> one_fractions;
			private List<MutableDouble> pweights;

			ShapCalculationTemp(int s) {
				this.s = s;
				this.feature_indexes = new ArrayList<MutableInteger>(s);
				this.zero_fractions = new ArrayList<MutableDouble>(s);
				this.one_fractions = new ArrayList<MutableDouble>(s);
				this.pweights = new ArrayList<MutableDouble>(s);

				returnTemp(true);
			}

			void returnTemp(boolean init) {
				if (init) {
					for (int i = 0; i < s; i++) {
						feature_indexes.add(new MutableInteger(Integer.MIN_VALUE));
						zero_fractions.add(new MutableDouble(Double.MIN_VALUE));
						one_fractions.add(new MutableDouble(Double.MIN_VALUE));
						pweights.add(new MutableDouble(Double.MIN_VALUE));
					}
				} else {
					for (int i = 0; i < s; i++) {
						setInteger(feature_indexes, i, Integer.MIN_VALUE);
						setDouble(zero_fractions, i, Double.MIN_VALUE);
						setDouble(one_fractions, i, Double.MIN_VALUE);
						setDouble(pweights, i, Double.MIN_VALUE);
					}
				}
			}
		}

		private void treeShap(ShapTree tree, Tuple x, double[] phi, double condition, int condition_feature,
				ShapCalculationTemp calcTemp) {

			// update the bias term, which is the last index in phi
			// (note the paper has this as phi_0 instead of phi_M)
			if (condition == 0) {
				phi[phi.length - 1] += tree.values[0];
			}

			// start the recursive algorithm
			treeShapRecursive(tree.children_left, tree.children_right, tree.children_default, tree.features,
					tree.thresholds, tree.values, tree.node_sample_weight, x, phi, 0, 0, calcTemp.feature_indexes,
					calcTemp.zero_fractions, calcTemp.one_fractions, calcTemp.pweights, 1, 1, -1, condition,
					condition_feature, 1);

			// recycle shap calculation register
			calcTemp.returnTemp(false);
		}

	}

	private static class MutableDouble {
		private double value = Double.MIN_VALUE;

		MutableDouble(double value) {
			this.value = value;
		}

		void setValue(double value) {
			this.value = value;
		}

		double getValue() {
			return this.value;
		}
	}

	private static class MutableInteger {
		private int value = Integer.MIN_VALUE;

		MutableInteger(int value) {
			this.value = value;
		}

		void setValue(int value) {
			this.value = value;
		}

		int getValue() {
			return this.value;
		}
	}

	private static void setDouble(List<MutableDouble> numbers, int idx, double value) {
		numbers.get(idx).setValue(value);
	}

	private static void setInteger(List<MutableInteger> numbers, int idx, int value) {
		numbers.get(idx).setValue(value);
	}

	/**
	 * extend our decision path with a fraction of one and zero extensions
	 */
	private static void extendPath(List<MutableInteger> feature_indexes, List<MutableDouble> zero_fractions,
			List<MutableDouble> one_fractions, List<MutableDouble> pweights, int unique_depth, double zero_fraction,
			double one_fraction, int feature_index) {

		setInteger(feature_indexes, unique_depth, feature_index);
		setDouble(zero_fractions, unique_depth, zero_fraction);
		setDouble(one_fractions, unique_depth, one_fraction);
		setDouble(pweights, unique_depth, unique_depth == 0 ? 1.0 : 0.0);

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			setDouble(pweights, i + 1, pweights.get(i + 1).getValue()
					+ (one_fraction * pweights.get(i).getValue() * (i + 1.0) / uniquePathPlus1));
			setDouble(pweights, i,
					zero_fraction * pweights.get(i).getValue() * (unique_depth - i + 0.0) / uniquePathPlus1);
		}
	}

	/**
	 * undo a previous extension of the decision path
	 */
	private static void unwindPath(List<MutableInteger> feature_indexes, List<MutableDouble> zero_fractions,
			List<MutableDouble> one_fractions, List<MutableDouble> pweights, int unique_depth, int path_index) {

		double one_fraction = one_fractions.get(path_index).getValue();
		double zero_fraction = zero_fractions.get(path_index).getValue();
		double next_one_portion = pweights.get(unique_depth).getValue();

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			if (one_fraction != 0) {
				double tmp = pweights.get(i).getValue();
				setDouble(pweights, i, next_one_portion * uniquePathPlus1 / ((i + 1.0) * one_fraction));
				next_one_portion = tmp
						- pweights.get(i).getValue() * zero_fraction * (unique_depth - i + 0.0) / uniquePathPlus1;
			} else {
				setDouble(pweights, i,
						(pweights.get(i).getValue() * uniquePathPlus1) / (zero_fraction * (unique_depth - i + 0.0)));
			}
		}

		for (int i = path_index; i < unique_depth; i++) {
			setInteger(feature_indexes, i, feature_indexes.get(i + 1).getValue());
			setDouble(zero_fractions, i, zero_fractions.get(i + 1).getValue());
			setDouble(one_fractions, i, one_fractions.get(i + 1).getValue());
		}
	}

	/**
	 * determine what the total permuation weight would be if we unwound a previous
	 * extension in the decision path
	 */
	private static double unwoundPathSum(List<MutableDouble> zero_fractions, List<MutableDouble> one_fractions,
			List<MutableDouble> pweights, int unique_depth, int path_index) {

		double one_fraction = one_fractions.get(path_index).getValue();
		double zero_fraction = zero_fractions.get(path_index).getValue();
		double next_one_portion = pweights.get(unique_depth).getValue();
		double total = 0;

		double uniquePathPlus1 = (unique_depth + 1.0);
		int startIdx = unique_depth - 1;
		for (int i = startIdx; i > -1; i--) {
			if (one_fraction != 0) {
				double tmp = next_one_portion * uniquePathPlus1 / ((i + 1.0) * one_fraction);
				total += tmp;
				next_one_portion = pweights.get(i).getValue()
						- tmp * zero_fraction * ((unique_depth - i + 0.0) / uniquePathPlus1);
			} else {
				double numerator = pweights.get(i).getValue() / zero_fraction;
				double denominator = (unique_depth - i + 0.0) / uniquePathPlus1;
				total += (numerator / denominator);
			}
		}

		return total;
	}

	/** structure for shap value calculation */
	private static class ShapTree {

		boolean normalize = false;
		int[] children_left;
		int[] children_right;
		int[] children_default;
		int[] features;
		Node[] thresholds;
		double[] values;
		double[] node_sample_weight; // equals number of samples for a node divided by total number of samples
		int max_depth;

		private ShapTree(RegressionTree tree) {
			this(tree, false);
		}

		private ShapTree(RegressionTree tree, boolean normalize) {
			this.normalize = normalize;

			List<Integer> childrenLefts = new ArrayList<Integer>();
			List<Integer> childrenRights = new ArrayList<Integer>();
			List<Integer> splitFeatures = new ArrayList<Integer>();
			List<Node> thresholds = new ArrayList<Node>();
			List<Double> values = new ArrayList<Double>();
			List<Double> sampleWeights = new ArrayList<Double>();
			int totalSamples = tree.root().size();
			//
			// we convert smile tree structure to sth favored by shap calculation
			//
			Queue<Node> q = new ConcurrentLinkedQueue<Node>();
			int idx = 0;
			int nodeNum = 0;
			q.add(tree.root());
			Node n = q.poll();

			while (n != null) {
				sampleWeights.add(idx, (double) n.size() / (double) totalSamples);
				values.add(idx, subtreeValues(n));
				thresholds.add(n);

				if (n instanceof InternalNode) {
					splitFeatures.add(idx, ((InternalNode) n).feature());
					q.add(((InternalNode) n).trueChild());
					childrenLefts.add(idx, (++nodeNum));
					q.add(((InternalNode) n).falseChild());
					childrenRights.add(idx, (++nodeNum));
				} else if (n instanceof LeafNode) {
					splitFeatures.add(idx, -1);
					childrenLefts.add(idx, -1);
					childrenRights.add(idx, -1);
				} else {
					System.out.println(
							"error node type for tree structure conversion: " + n.getClass() + ":" + n.toString());
					throw new RuntimeException(
							"error node type for tree structure conversion: " + n.getClass() + ":" + n.toString());
				}

				n = q.poll();
				idx++;
			}

			this.children_left = childrenLefts.stream().mapToInt(Integer::intValue).toArray();
			this.children_right = childrenRights.stream().mapToInt(Integer::intValue).toArray();
			this.children_default = this.children_left; // missing values...
			this.features = splitFeatures.stream().mapToInt(Integer::intValue).toArray();
			this.thresholds = thresholds.toArray(new Node[thresholds.size()]);
			this.node_sample_weight = sampleWeights.stream().mapToDouble(Double::doubleValue).toArray();

			if (this.normalize) {
				double valueSum = values.stream().mapToDouble(Double::doubleValue).sum();
				this.values = values.stream().mapToDouble(num -> num / valueSum).toArray();
			} else {
				this.values = values.stream().mapToDouble(Double::doubleValue).toArray();
			}

			// we recompute the expectations to make sure they follow the SHAP logic
			this.max_depth = computeExpectations(this.children_left, this.children_right, this.node_sample_weight,
					this.values, 0, 0);
			System.out.print(".");
		}
	}

	/** calculate the constant prediction value of each node */
	private static double subtreeValues(Node n) {
		double v = 0;
		if (n instanceof RegressionNode) {
			v = ((RegressionNode) n).output();
			assert (v != Double.NaN);
		} else if (n instanceof InternalNode) {
			Node lc = ((InternalNode) n).trueChild();
			Node rc = ((InternalNode) n).falseChild();
			v = (double) (lc.size() * subtreeValues(lc) + rc.size() * subtreeValues(rc))
					/ (double) (lc.size() + rc.size());
			assert (v != Double.NaN);
		} else {
			System.out.println("error node type for node value calculation: " + n.getClass() + ":" + n.toString());
			throw new RuntimeException(
					"error node type for node value calculation: " + n.getClass() + ":" + n.toString());
		}
		return v;
	}

	private static int computeExpectations(int[] children_left, int[] children_right, double[] node_sample_weight,
			double[] values, int i, int depth) {
		if (depth < 0) {
			depth = 0;
		}

		if (children_right[i] == -1) {
			return 0;
		} else {
			int li = children_left[i];
			int ri = children_right[i];
			int depth_left = computeExpectations(children_left, children_right, node_sample_weight, values, li,
					depth + 1);
			int depth_right = computeExpectations(children_left, children_right, node_sample_weight, values, ri,
					depth + 1);
			double left_weight = node_sample_weight[li];
			double right_weight = node_sample_weight[ri];
			double v = (left_weight * values[li] + right_weight * values[ri]) / (left_weight + right_weight);
			values[i] = v;
			return Math.max(depth_left, depth_right) + 1;
		}
	}

	private static List shallowCopyNumbers(List original, int unique_depth, boolean doubleValue) {
		int uniqueDepthPlus1 = (unique_depth + 1);
		for (int i = 0; i < uniqueDepthPlus1; i++) {
			if (doubleValue) {
				if (original.get(i) != null && ((MutableDouble) original.get(i)).getValue() != Double.MIN_VALUE) {
					setDouble((List<MutableDouble>) original, i + uniqueDepthPlus1,
							((MutableDouble) original.get(i)).getValue());
				}
			} else {
				if (original.get(i) != null && ((MutableInteger) original.get(i)).getValue() != Double.MIN_VALUE) {
					setInteger((List<MutableInteger>) original, i + uniqueDepthPlus1,
							((MutableInteger) original.get(i)).getValue());
				}
			}
		}
		return original.subList(uniqueDepthPlus1, original.size());
	}

	/**
	 * recursive computation of SHAP values for a decision tree
	 */
	private static void treeShapRecursive(int[] children_left, int[] children_right, int[] children_default,
			int[] features, Node[] thresholds, double[] values, double[] node_sample_weight, Tuple x, double[] phi,
			int node_index, int unique_depth, List<MutableInteger> parent_feature_indexes,
			List<MutableDouble> parent_zero_fractions, List<MutableDouble> parent_one_fractions,
			List<MutableDouble> parent_pweights, double parent_zero_fraction, double parent_one_fraction,
			int parent_feature_index, double condition, int condition_feature, double condition_fraction) {

		// stop if we have no weight coming down to us
		if (condition_fraction == 0) {
			return;
		}

		// extend the unique path
		List<MutableInteger> feature_indexes = shallowCopyNumbers(parent_feature_indexes, unique_depth, false);
		List<MutableDouble> zero_fractions = shallowCopyNumbers(parent_zero_fractions, unique_depth, true);
		List<MutableDouble> one_fractions = shallowCopyNumbers(parent_one_fractions, unique_depth, true);
		List<MutableDouble> pweights = shallowCopyNumbers(parent_pweights, unique_depth, true);

		if (condition == 0 || condition_feature != parent_feature_index) {
			extendPath(feature_indexes, zero_fractions, one_fractions, pweights, unique_depth, parent_zero_fraction,
					parent_one_fraction, parent_feature_index);
		}

		int split_index = features[node_index];

		// leaf node
		if (children_right[node_index] == -1) {
			int loopLength = (unique_depth + 1);
			for (int i = 1; i < loopLength; i++) {
				double w = unwoundPathSum(zero_fractions, one_fractions, pweights, unique_depth, i);
				phi[feature_indexes.get(i).getValue()] += w
						* (one_fractions.get(i).getValue() - zero_fractions.get(i).getValue()) * values[node_index]
						* condition_fraction;
			}
		} else {// internal node
			// find which branch is "hot" (meaning x would follow it)
			int hot_index = 0;
			int cleft = children_left[node_index];
			int cright = children_right[node_index];
			if (x.isNullAt(split_index)) {
				hot_index = children_default[node_index];
			} else {
				InternalNode splitNode = (InternalNode) thresholds[node_index];
				if (splitNode.branch(x)) {
					hot_index = cleft;
				} else {
					hot_index = cright;
				}
			}

			int cold_index = hot_index == cleft ? cright : cleft;
			double w = node_sample_weight[node_index];
			double hot_zero_fraction = node_sample_weight[hot_index] / w;
			double cold_zero_fraction = node_sample_weight[cold_index] / w;
			double incoming_zero_fraction = 1;
			double incoming_one_fraction = 1;

			// see if we have already split on this feature,
			// if so we undo that split so we can redo it for this node
			int path_index = 0;
			while (path_index <= unique_depth) {
				if (feature_indexes.get(path_index).getValue() == split_index) {
					break;
				}
				path_index += 1;
			}
			if (path_index != unique_depth + 1) {
				incoming_zero_fraction = zero_fractions.get(path_index).getValue();
				incoming_one_fraction = one_fractions.get(path_index).getValue();
				unwindPath(feature_indexes, zero_fractions, one_fractions, pweights, unique_depth, path_index);
				unique_depth -= 1;
			}

			// divide up the condition_fraction among the recursive calls
			double hot_condition_fraction = condition_fraction;
			double cold_condition_fraction = condition_fraction;
			if (condition > 0 && split_index == condition_feature) {
				cold_condition_fraction = 0;
				unique_depth -= 1;
			} else if (condition < 0 && split_index == condition_feature) {
				hot_condition_fraction *= hot_zero_fraction;
				cold_condition_fraction *= cold_zero_fraction;
				unique_depth -= 1;
			}

			treeShapRecursive(children_left, children_right, children_default, features, thresholds, values,
					node_sample_weight, x, phi, hot_index, unique_depth + 1, feature_indexes, zero_fractions,
					one_fractions, pweights, hot_zero_fraction * incoming_zero_fraction, incoming_one_fraction,
					split_index, condition, condition_feature, hot_condition_fraction);

			treeShapRecursive(children_left, children_right, children_default, features, thresholds, values,
					node_sample_weight, x, phi, cold_index, unique_depth + 1, feature_indexes, zero_fractions,
					one_fractions, pweights, cold_zero_fraction * incoming_zero_fraction, 0, split_index, condition,
					condition_feature, cold_condition_fraction);
		} // end else internal node
	}

}